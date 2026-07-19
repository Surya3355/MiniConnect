#!/usr/bin/env bash
#
# Deploy the MiniConnect signaling server on a Debian/Ubuntu droplet.
# Installs Node.js, writes the server + a systemd service, opens port 8080.
# Run as root:
#
#   bash setup-signaling.sh
#
set -euo pipefail

APP_DIR="/opt/miniconnect-signaling"
PORT="8080"

echo ">> Installing Node.js 20..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y curl ufw
if ! command -v node >/dev/null 2>&1; then
  curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
  apt-get install -y nodejs
fi
echo ">> Node version: $(node -v)"

echo ">> Writing app to ${APP_DIR}..."
mkdir -p "$APP_DIR"

cat > "${APP_DIR}/package.json" <<'PKGEOF'
{
  "name": "miniconnect-signaling",
  "version": "1.0.0",
  "private": true,
  "main": "server.js",
  "dependencies": {
    "ws": "^8.18.0"
  }
}
PKGEOF

cat > "${APP_DIR}/server.js" <<'SRVEOF'
const { WebSocketServer } = require("ws");
const { randomUUID } = require("crypto");

const PORT = process.env.PORT || 8080;
const wss = new WebSocketServer({ port: PORT });

// Map<roomId, Map<peerId, { ws, name }>>
const rooms = new Map();

wss.on("connection", (ws, req) => {
  const params = new URL(req.url, `http://${req.headers.host}`).searchParams;
  const roomId = params.get("room") || "default";
  const peerName = params.get("name") || "Anonymous";
  const mode = params.get("mode") || "join";
  const peerId = randomUUID();

  const roomExists = rooms.has(roomId) && rooms.get(roomId).size > 0;
  if (mode === "join" && !roomExists) {
    ws.send(JSON.stringify({ type: "error", message: "Call not found. Ask the host for a valid code." }));
    ws.close();
    console.log(`[${roomId}] join rejected — no such active call`);
    return;
  }

  if (!rooms.has(roomId)) rooms.set(roomId, new Map());
  const room = rooms.get(roomId);

  const existingPeers = [];
  for (const [id, peer] of room) existingPeers.push({ id, name: peer.name });

  room.set(peerId, { ws, name: peerName });

  ws.send(JSON.stringify({ type: "room-joined", peerId, roomId, peers: existingPeers }));
  broadcast(room, peerId, { type: "peer-joined", peerId, peerName });

  console.log(`[${roomId}] ${peerName} (${peerId.slice(0, 8)}) joined — ${room.size} in room`);

  ws.on("message", (data) => {
    try {
      const msg = JSON.parse(data);
      const target = msg.target;
      if (target && room.has(target)) {
        room.get(target).ws.send(JSON.stringify({ ...msg, from: peerId, target: undefined }));
      }
    } catch (e) {
      console.error("Bad message:", e.message);
    }
  });

  ws.on("close", () => {
    room.delete(peerId);
    broadcast(room, peerId, { type: "peer-left", peerId });
    console.log(`[${roomId}] ${peerName} (${peerId.slice(0, 8)}) left — ${room.size} in room`);
    if (room.size === 0) rooms.delete(roomId);
  });

  ws.on("error", (err) => {
    console.error(`[${roomId}] WS error for ${peerId.slice(0, 8)}:`, err.message);
  });
});

function broadcast(room, senderPeerId, message) {
  const data = JSON.stringify(message);
  for (const [id, peer] of room) {
    if (id !== senderPeerId && peer.ws.readyState === 1) peer.ws.send(data);
  }
}

console.log(`Signaling server listening on ws://0.0.0.0:${PORT}`);
SRVEOF

echo ">> Installing dependencies..."
cd "$APP_DIR"
npm install --omit=dev

echo ">> Creating systemd service..."
cat > /etc/systemd/system/miniconnect-signaling.service <<EOF
[Unit]
Description=MiniConnect Signaling Server
After=network.target

[Service]
Type=simple
ExecStart=/usr/bin/node ${APP_DIR}/server.js
Restart=always
RestartSec=3
Environment=PORT=${PORT}
WorkingDirectory=${APP_DIR}

[Install]
WantedBy=multi-user.target
EOF

echo ">> Opening firewall port ${PORT}..."
ufw allow ${PORT}/tcp || true

echo ">> Starting service..."
systemctl daemon-reload
systemctl enable miniconnect-signaling
systemctl restart miniconnect-signaling
sleep 1
systemctl --no-pager status miniconnect-signaling | head -n 6 || true

PUBLIC_IP=$(curl -s --max-time 5 http://169.254.169.254/metadata/v1/interfaces/public/0/ipv4/address || curl -s ifconfig.me || echo "YOUR_DROPLET_IP")

echo ""
echo "=================================================="
echo " Signaling server is running"
echo " URL for the app:  ws://${PUBLIC_IP}:${PORT}"
echo "--------------------------------------------------"
echo " Logs:    journalctl -u miniconnect-signaling -f"
echo " Restart: systemctl restart miniconnect-signaling"
echo "=================================================="
