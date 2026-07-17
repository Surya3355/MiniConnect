#!/usr/bin/env bash
#
# Provision a LiveKit SFU + token endpoint on a Debian/Ubuntu droplet.
# Replaces the old coturn + Node signaling stack. Run as root:
#
#   sudo bash setup-livekit.sh [API_KEY] [API_SECRET]
#
# If keys are omitted, strong ones are generated and printed. The app only
# needs the droplet IP — the API secret stays here on the token server.
#
set -euo pipefail

API_KEY="${1:-API$(openssl rand -hex 4)}"
API_SECRET="${2:-$(openssl rand -hex 32)}"
TOKEN_DIR="/opt/mc-token"

echo ">> Installing prerequisites (curl, ufw, Docker, Node)..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y curl ufw
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
fi
if ! command -v node >/dev/null 2>&1; then
  curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
  apt-get install -y nodejs
fi

PUBLIC_IP=$(curl -s --max-time 5 http://169.254.169.254/metadata/v1/interfaces/public/0/ipv4/address \
  || curl -s --max-time 5 ifconfig.me || echo "YOUR_DROPLET_IP")

echo ">> Writing /etc/livekit.yaml..."
cat > /etc/livekit.yaml <<EOF
port: 7880
rtc:
  tcp_port: 7881
  port_range_start: 50000
  port_range_end: 50100
  # Advertise ONLY the droplet's public IP. use_external_ip:true would also
  # leak unreachable private/docker candidates and break the data channel.
  node_ip: ${PUBLIC_IP}
  use_external_ip: false
keys:
  ${API_KEY}: ${API_SECRET}
EOF

echo ">> Starting LiveKit server (Docker)..."
docker rm -f livekit 2>/dev/null || true
docker run -d --name livekit --restart unless-stopped \
  --network host \
  -v /etc/livekit.yaml:/etc/livekit.yaml \
  livekit/livekit-server:latest \
  --config /etc/livekit.yaml

echo ">> Writing token server to ${TOKEN_DIR}..."
mkdir -p "$TOKEN_DIR"
cat > "${TOKEN_DIR}/package.json" <<'PKGEOF'
{ "name": "mc-token", "version": "1.0.0", "private": true, "main": "server.js",
  "dependencies": { "livekit-server-sdk": "^2.9.0" } }
PKGEOF

cat > "${TOKEN_DIR}/server.js" <<'SRVEOF'
const http = require("http");
const { AccessToken, RoomServiceClient } = require("livekit-server-sdk");

const API_KEY = process.env.LK_API_KEY;
const API_SECRET = process.env.LK_API_SECRET;
const LK_URL = process.env.LK_URL || "http://localhost:7880";
const PORT = process.env.PORT || 8080;

const roomService = new RoomServiceClient(LK_URL, API_KEY, API_SECRET);

const server = http.createServer(async (req, res) => {
  const json = (code, obj) => {
    res.writeHead(code, { "Content-Type": "application/json" });
    res.end(JSON.stringify(obj));
  };
  try {
    const u = new URL(req.url, `http://${req.headers.host}`);
    if (u.pathname !== "/token") return json(404, { error: "not found" });

    const room = u.searchParams.get("room");
    const identity = u.searchParams.get("identity") || "rider";
    const create = u.searchParams.get("create") === "true";
    if (!room) return json(400, { error: "room required" });

    // Join is only valid for a room that already has participants.
    if (!create) {
      let active = false;
      try {
        const rooms = await roomService.listRooms([room]);
        active = rooms.length > 0 && rooms[0].numParticipants > 0;
      } catch (e) { active = false; }
      if (!active) return json(404, { error: "Call not found. Ask the host for a valid code." });
    }

    const at = new AccessToken(API_KEY, API_SECRET, { identity });
    at.addGrant({ roomJoin: true, room, canPublish: true, canSubscribe: true });
    const token = await at.toJwt();
    return json(200, { token });
  } catch (e) {
    console.error(e);
    return json(500, { error: e.message || "server error" });
  }
});

server.listen(PORT, () => console.log(`Token server on ${PORT}`));
SRVEOF

cd "$TOKEN_DIR"
npm install --omit=dev

echo ">> Creating token-server systemd service..."
cat > /etc/systemd/system/mc-token.service <<EOF
[Unit]
Description=MiniConnect LiveKit Token Server
After=network.target docker.service

[Service]
Type=simple
ExecStart=/usr/bin/node ${TOKEN_DIR}/server.js
Restart=always
RestartSec=3
Environment=PORT=8080
Environment=LK_URL=http://localhost:7880
Environment=LK_API_KEY=${API_KEY}
Environment=LK_API_SECRET=${API_SECRET}
WorkingDirectory=${TOKEN_DIR}

[Install]
WantedBy=multi-user.target
EOF

echo ">> Opening firewall..."
ufw allow 22/tcp        || true
ufw allow 7880/tcp      || true   # signaling (ws)
ufw allow 7881/tcp      || true   # rtc tcp fallback
ufw allow 50000:50100/udp || true # rtc media
ufw allow 8080/tcp      || true   # token server
yes | ufw enable        || true

echo ">> Starting token server..."
systemctl daemon-reload
systemctl enable mc-token
systemctl restart mc-token
sleep 1

echo ""
echo "=================================================="
echo " LiveKit is running"
echo "--------------------------------------------------"
echo " App config (SignalingConfig/LiveKitConfig HOST) : ${PUBLIC_IP}"
echo "   URL       = ws://${PUBLIC_IP}:7880"
echo "   TOKEN_URL = http://${PUBLIC_IP}:8080/token"
echo "--------------------------------------------------"
echo " API key    : ${API_KEY}"
echo " API secret : ${API_SECRET}"
echo "   (kept on the token server; the app does NOT need them)"
echo "--------------------------------------------------"
echo " Checks:  docker logs livekit --tail 20"
echo "          systemctl status mc-token"
echo "          curl 'http://localhost:8080/token?room=x&identity=y&create=true'"
echo "=================================================="
