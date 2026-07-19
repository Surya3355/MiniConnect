# RiderConnect Production Plan

## Vision

RiderConnect is a communication platform built for riders. It enables seamless communication whether riders are side-by-side, separated by kilometers, or connected over the Internet.

MiniConnect serves as the networking laboratory. Every communication technology is validated in MiniConnect first, then integrated into RiderConnect.

## Development Strategy

```
MiniConnect → Prototype & Validate → RiderConnect Integration → Production Hardening
```

## Architecture Principle

Every communication feature is first built and validated inside MiniConnect. Only after it is stable is it integrated into RiderConnect. MiniConnect remains the networking engine and experimental platform. RiderConnect becomes the complete rider ecosystem built on top of that engine.

---

## Phase 1 — Offline P2P Foundation ✅ COMPLETE

**Status:** Done (MiniConnect MVP)
**App:** MiniConnect
**Technology:** Wi-Fi Direct, TCP Sockets

### Features
- Peer discovery (find nearby riders without Internet)
- Device connection (establish direct P2P link)
- Real-time text messaging over TCP
- Single file transfer with progress indicator
- Disconnect and reconnect
- Connection status display
- Runtime permission handling
- Material 3 UI

### Deliverable
MiniConnect MVP — two Android devices can discover, connect, chat, and transfer files over Wi-Fi Direct without Internet.

---

## Phase 2 — Internet Voice

**Priority:** HIGHEST
**App:** MiniConnect (validate) → RiderConnect (integrate)
**Technology:** WebRTC, Signaling Server (WebSocket), SRTP

### Features
- Push-to-talk mode (walkie-talkie style)
- Continuous voice mode (open mic)
- Mute / unmute
- Speaker / earpiece selection
- Connection quality indicator
- Auto-reconnect on network switch
- Background audio (keep voice active when app is backgrounded)
- Low-latency audio pipeline optimized for helmet speakers

### Technical Spec
- **Signaling:** WebSocket server for SDP exchange and ICE candidates
- **Media:** WebRTC peer connection with Opus codec (low bandwidth, low latency)
- **TURN/STUN:** Public STUN servers for NAT traversal; self-hosted TURN as fallback
- **Audio pipeline:** Android AudioRecord → WebRTC → remote peer → AudioTrack
- **Permissions:** RECORD_AUDIO, FOREGROUND_SERVICE (microphone type)
- **Background:** Foreground service with persistent notification
- **Codec:** Opus at 16kHz mono, bitrate 24-32 kbps

### Architecture
```
UI (Compose) → VoiceCallViewModel → WebRTCManager → PeerConnection
                                   → SignalingClient → WebSocket Server
```

### Backend Requirements
- WebSocket signaling server (Node.js or Ktor)
- Room/session management (create room, join room, leave room)
- ICE candidate relay
- No media relay needed (peer-to-peer via WebRTC)

### Acceptance Criteria
- Two devices establish voice call over internet
- Push-to-talk works with < 300ms latency
- Audio continues when app is in background
- Call survives brief network interruptions
- Clean UI showing call state (ringing, connected, muted)

---

## Phase 3 — Internet Messaging

**App:** MiniConnect (validate) → RiderConnect (integrate)
**Technology:** WebSockets, REST API, Backend Database

### Features
- Real-time text messaging over internet
- Message delivery status (sent, delivered, read)
- Typing indicator
- Message timestamps
- Offline queue (messages sent while offline are delivered when back online)
- Message history (persisted on server)
- Image/file sharing over internet

### Technical Spec
- **Transport:** WebSocket for real-time; REST API for history fetch
- **Protocol:** JSON messages over WebSocket
- **Storage:** Server-side message store (PostgreSQL or similar)
- **Local cache:** Room database for offline message access
- **Push notifications:** FCM for message delivery when app is closed

### Message Protocol
```json
{
  "type": "message",
  "id": "uuid",
  "from": "user_id",
  "to": "user_id | room_id",
  "content": "text",
  "contentType": "text | image | file",
  "timestamp": 1234567890,
  "status": "sent | delivered | read"
}
```

### Backend Requirements
- WebSocket server with authentication
- REST API for message history, user lookup
- Database for message persistence
- FCM integration for push notifications
- File upload endpoint (presigned URLs or direct upload)

### Acceptance Criteria
- Two devices exchange messages over internet in real-time
- Messages persist and are available on reconnect
- Delivery and read receipts update in real-time
- Typing indicator shows when the other user is composing
- Messages queue offline and deliver when connectivity returns

---

## Phase 4 — Presence & Status

**App:** RiderConnect
**Technology:** WebSockets

### Features
- Online / offline / riding status
- Last seen timestamp
- Typing indicator (already in Phase 3, refined here)
- Custom status message (e.g., "Riding to Ladakh")
- Rider availability (available, busy, do not disturb)

### Technical Spec
- **Heartbeat:** WebSocket ping/pong every 30s to detect presence
- **Status broadcast:** Server pushes presence changes to subscribed users
- **Storage:** In-memory presence store (Redis) + periodic DB flush

### Acceptance Criteria
- User status updates reflect within 5 seconds
- Last seen timestamp updates when user goes offline
- Custom status visible to connected riders

---

## Phase 5 — Group Communication (Online)

**App:** RiderConnect
**Technology:** WebSockets, WebRTC (SFU for group voice)

### Features
- Create ride group
- Invite riders to group
- Group text chat
- Group voice channel (multiple riders talking)
- Group file sharing
- Admin controls (mute, kick, promote)
- Group notifications

### Technical Spec
- **Group chat:** WebSocket rooms with fan-out messaging
- **Group voice:** SFU (Selective Forwarding Unit) architecture for multi-party audio
  - Each participant sends one audio stream to SFU
  - SFU forwards relevant streams to each participant
  - Scales to 10-20 riders per group
- **SFU options:** mediasoup, Janus, or LiveKit
- **Permissions:** Group roles (admin, member)

### Architecture
```
Rider A ──┐
Rider B ──┼── WebRTC ──→ SFU Server ──→ Mixed/forwarded audio to all
Rider C ──┘
```

### Acceptance Criteria
- Group of 5+ riders can voice chat simultaneously
- Group text messages delivered to all members
- Admin can mute/remove participants
- Audio quality remains usable with 10 participants

---

## Phase 6 — Smart Riding Features

**App:** RiderConnect
**Technology:** Location APIs, Maps SDK, Backend

### Features
- Live rider location sharing (real-time on map)
- Route sharing (share planned route with group)
- GPX import/export
- Ride invitations (invite riders to a planned ride)
- ETA sharing
- Fuel stop coordination (suggest stops, vote)
- Emergency SOS (send location + alert to emergency contacts)
- Crash detection integration (accelerometer-based)

### Technical Spec
- **Location:** Fused Location Provider, updates every 3-5 seconds during ride
- **Maps:** Google Maps SDK or Mapbox
- **SOS:** SMS fallback + server notification + push to emergency contacts
- **Crash detection:** Accelerometer threshold + confirmation prompt (30s timer)
- **GPX:** Standard GPX XML parsing/generation

### Acceptance Criteria
- Riders see each other's live location on map during ride
- SOS sends location to emergency contacts within 10 seconds
- GPX routes can be imported and shared with group
- Crash detection triggers alert with 30-second cancel window

---

## Phase 7 — Offline Voice (P2P)

**App:** MiniConnect (validate) → RiderConnect (integrate)
**Technology:** Audio streaming over Wi-Fi Direct TCP sockets

### Features
- Push-to-talk over Wi-Fi Direct
- Continuous voice mode
- Noise suppression (basic)
- Mute
- Speaker selection
- Works without internet (convoy/group ride in remote areas)

### Technical Spec
- **Audio capture:** AudioRecord at 16kHz mono
- **Codec:** Opus encoding (via native library)
- **Transport:** Raw Opus frames over existing TCP socket (SocketManager)
- **Framing:** New message type (0x04 = audio frame) in socket protocol
- **Latency target:** < 200ms end-to-end
- **Buffer:** Jitter buffer on receive side (50-100ms)

### Architecture
```
AudioRecord → Opus Encoder → SocketManager.sendAudioFrame()
SocketManager.receiveLoop() → Opus Decoder → AudioTrack
```

### Acceptance Criteria
- Two devices in Wi-Fi Direct range can voice chat without internet
- Latency under 200ms
- Audio quality acceptable through helmet speakers
- Push-to-talk toggles cleanly without pops/clicks

---

## Phase 8 — Offline Group (P2P)

**App:** MiniConnect (validate) → RiderConnect (integrate)
**Technology:** Wi-Fi Direct Group, Multi-socket TCP

### Features
- Multi-rider P2P discovery
- Wi-Fi Direct group formation (1 group owner + N clients)
- Group messaging over P2P
- Group file sharing over P2P
- Group voice over P2P

### Technical Spec
- **Group topology:** Wi-Fi Direct group owner acts as hub; all clients connect to owner
- **Socket management:** SocketManager extended to handle Map<PeerAddress, Socket>
- **Message routing:** Group owner relays messages between all clients
- **Scaling:** Practical limit ~8 devices per Wi-Fi Direct group

### Architecture
```
Client A ──┐
Client B ──┼── TCP ──→ Group Owner (relay) ──→ all clients
Client C ──┘
```

### Acceptance Criteria
- 4+ devices discover and form a P2P group
- Group text messages reach all members
- File transfer works to/from any member
- Group voice works with 4 participants

---

## Phase 9 — Hybrid Communication

**App:** RiderConnect
**Technology:** Transport abstraction layer

### Features
- Automatic transport selection (Wi-Fi Direct ↔ local Wi-Fi ↔ Internet)
- Offline-first messaging (queue and deliver)
- Seamless handoff between transports
- Transport priority: Wi-Fi Direct (fastest, no cost) → local Wi-Fi → Internet
- User does not need to know or care how messages are delivered

### Technical Spec
- **Transport interface:** Common abstraction over SocketManager (P2P) and WebSocketClient (Internet)
- **Message queue:** Local Room database queues outbound messages
- **Delivery engine:** Background worker attempts delivery via best available transport
- **Handoff:** Monitor connectivity changes; switch transport without dropping conversation

### Architecture
```
ChatViewModel → MessageRouter → TransportManager
                                  ├── WifiDirectTransport (SocketManager)
                                  ├── LocalWifiTransport
                                  └── InternetTransport (WebSocket)
```

### Acceptance Criteria
- Message sent offline via P2P is seamlessly continued over internet when rider leaves range
- No duplicate messages during transport switch
- User sees no interruption in conversation

---

## Phase 10 — Production Platform

**App:** RiderConnect
**Technology:** Full production stack

### Features
- User authentication (phone/email + OAuth)
- Rider profiles (name, bike, avatar)
- Cloud sync (messages, contacts, ride history)
- Push notifications (FCM)
- End-to-end encryption (Signal Protocol or similar)
- Analytics and crash reporting (Firebase)
- CI/CD pipeline
- Play Store release
- App performance monitoring
- Rate limiting and abuse prevention

### Acceptance Criteria
- App passes Play Store review
- E2E encryption verified for all message types
- Crash-free rate > 99%
- CI/CD deploys to internal track automatically

---

## Final RiderConnect Vision

**Communication**
- Offline messaging (P2P)
- Online messaging (Internet)
- Offline voice (P2P)
- Online voice (Internet)
- File sharing (both transports)
- Group communication (both transports)

**Navigation**
- Route sharing
- Live location
- GPX import/export

**Safety**
- SOS alert
- Emergency contacts
- Crash detection

**Community**
- Ride groups
- Clubs
- Events

---

## Summary Timeline

| Phase | Title | Priority | Complexity |
|-------|-------|----------|------------|
| 1 | Offline P2P Foundation | ✅ Done | — |
| 2 | Internet Voice | 🔴 Critical | High |
| 3 | Internet Messaging | 🔴 Critical | Medium |
| 4 | Presence & Status | 🟡 High | Low |
| 5 | Group Communication (Online) | 🟡 High | High |
| 6 | Smart Riding Features | 🟡 High | High |
| 7 | Offline Voice (P2P) | 🟢 Medium | High |
| 8 | Offline Group (P2P) | 🟢 Medium | Medium |
| 9 | Hybrid Communication | 🟢 Medium | Very High |
| 10 | Production Platform | 🔴 Critical | High |
