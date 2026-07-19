const { WebSocketServer } = require("ws");
const { randomUUID } = require("crypto");
const url = require("url");

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

  // "join" is only valid for a room that already exists (a host created it).
  // This stops a random/typo code from silently spinning up an empty call.
  const roomExists = rooms.has(roomId) && rooms.get(roomId).size > 0;
  if (mode === "join" && !roomExists) {
    ws.send(JSON.stringify({ type: "error", message: "Call not found. Ask the host for a valid code." }));
    ws.close();
    console.log(`[${roomId}] join rejected — no such active call`);
    return;
  }

  if (!rooms.has(roomId)) rooms.set(roomId, new Map());
  const room = rooms.get(roomId);

  // Build list of existing peers
  const existingPeers = [];
  for (const [id, peer] of room) {
    existingPeers.push({ id, name: peer.name });
  }

  // Add this peer to room
  room.set(peerId, { ws, name: peerName });

  // Tell the new peer about the room
  ws.send(
    JSON.stringify({
      type: "room-joined",
      peerId,
      roomId,
      peers: existingPeers,
    })
  );

  // Notify existing peers
  broadcast(room, peerId, {
    type: "peer-joined",
    peerId,
    peerName,
  });

  console.log(
    `[${roomId}] ${peerName} (${peerId.slice(0, 8)}) joined — ${room.size} in room`
  );

  ws.on("message", (data) => {
    try {
      const msg = JSON.parse(data);
      const target = msg.target;

      if (target && room.has(target)) {
        // Forward with "from" field
        room.get(target).ws.send(
          JSON.stringify({ ...msg, from: peerId, target: undefined })
        );
      }
    } catch (e) {
      console.error("Bad message:", e.message);
    }
  });

  ws.on("close", () => {
    room.delete(peerId);
    broadcast(room, peerId, { type: "peer-left", peerId });
    console.log(
      `[${roomId}] ${peerName} (${peerId.slice(0, 8)}) left — ${room.size} in room`
    );
    if (room.size === 0) rooms.delete(roomId);
  });

  ws.on("error", (err) => {
    console.error(`[${roomId}] WS error for ${peerId.slice(0, 8)}:`, err.message);
  });
});

function broadcast(room, senderPeerId, message) {
  const data = JSON.stringify(message);
  for (const [id, peer] of room) {
    if (id !== senderPeerId && peer.ws.readyState === 1) {
      peer.ws.send(data);
    }
  }
}

console.log(`Signaling server listening on ws://0.0.0.0:${PORT}`);
