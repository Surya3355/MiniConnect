# MiniConnect LiveKit backend

Runs a **LiveKit SFU** + a small **token endpoint** on one droplet. Replaces the
old coturn + Node signaling stack. Group voice scales past mesh, reconnection is
handled by the SDK, and audio is **end-to-end encrypted** (SFrame) with a
per-room key the SFU never sees.

## Deploy

On a fresh Debian 12 droplet (as root):

```bash
apt-get update -y && apt-get install -y git
git clone <this-repo-url> mc && cd mc
sudo bash provision.sh          # or: sudo bash livekit-server/setup-livekit.sh
```

It installs Docker + Node, runs the LiveKit server (Docker, host networking),
starts the token server (systemd), opens the firewall, and prints:

```
URL       = ws://<IP>:7880
TOKEN_URL = http://<IP>:8080/token
API key / secret   (kept on the droplet; the app does NOT need them)
```

## Point the app at it

Edit `app/.../data/livekit/LiveKitConfig.kt`:

```kotlin
private const val HOST = "<DROPLET_IP>"
```

That single value feeds both `URL` and `TOKEN_URL`. Rebuild the app.

## Ports

| Port | Proto | Purpose |
|------|-------|---------|
| 7880 | TCP | LiveKit signaling (ws) |
| 7881 | TCP | RTC TCP fallback |
| 50000–50100 | UDP | RTC media |
| 8080 | TCP | token endpoint |

## How create vs join is enforced

The token server issues a **join** token only if the room already has
participants (checked via LiveKit's `RoomServiceClient.listRooms`). A random or
mistyped code returns HTTP 404 → the app shows "Call not found". **Create**
always issues a token (LiveKit auto-creates the room on connect).

## Checks

```bash
docker logs livekit --tail 20
systemctl status mc-token
curl 'http://localhost:8080/token?room=x&identity=y&create=true'
```

## No domain yet

Running plain `ws://`/`http://` (the app allows cleartext for testing). When you
add a domain: point it at the droplet, get a Let's Encrypt cert, front LiveKit
(7880) and the token server (8080) with TLS, and switch the app to
`wss://` / `https://`.
