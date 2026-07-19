# MiniConnect TURN server (coturn on DigitalOcean)

A TURN server relays audio when two phones can't reach each other directly
(common on mobile data / CGNAT). Hosting it in **your own region** keeps the
relayed audio local instead of routing through a distant free relay — which is
what fixes the "voice breaking" on far-away calls.

## 1. Create the droplet

In the DigitalOcean dashboard:

- **Create → Droplet**
- **Region:** the one closest to you (e.g. Bangalore for India)
- **Image:** Debian 12 (or Ubuntu 24.04 LTS) — the script handles both
- **Size:** Basic → Regular → **$6/mo** (1 GB RAM is plenty for voice relay)
- **Authentication:** SSH key (recommended) or password
- Create, then copy the droplet's **public IPv4** address.

## 2. Run the setup script

SSH into the droplet:

```bash
ssh root@YOUR_DROPLET_IP
```

Copy `setup-coturn.sh` onto it (paste it into a file, or use `scp`), then:

```bash
sudo bash setup-coturn.sh
```

It installs coturn, writes the config, opens the firewall, and prints:

```
 Public IP : YOUR_DROPLET_IP
 Username  : rider
 Password  : <generated>
```

To choose your own password instead:

```bash
sudo bash setup-coturn.sh "my-strong-password"
```

## 3. Point the app at it

Edit `app/src/main/java/com/surya/miniconnect/data/webrtc/SignalingConfig.kt`:

```kotlin
private const val TURN_HOST = "YOUR_DROPLET_IP"
private const val TURN_USER = "rider"
private const val TURN_PASS = "<the password from the script>"
```

Rebuild and reinstall. Done.

## 4. Verify it works

- **Trickle ICE test:** open https://webrtc.github.io/samples/src/content/peerconnection/trickle-ice/
  - Add `turn:YOUR_DROPLET_IP:3478`, username `rider`, password `<yours>`
  - Click *Gather candidates* — you should see a candidate of type **`relay`**.
    If you do, TURN works.
- **In the app:** make a call across networks. The per-person row shows a
  `RELAY` badge when it's using TURN. With your regional server the latency
  under that badge should be far lower than before.

## Ports opened

| Port | Proto | Purpose |
|------|-------|---------|
| 3478 | UDP + TCP | STUN/TURN control + relay |
| 49152–65535 | UDP | relay media range |

## Optional hardening / upgrades (later)

- **TURN over TLS on 443** — helps when networks block port 3478. Needs a
  domain + Let's Encrypt cert (`tls-listening-port=443`, `cert=`, `pkey=`).
- **Time-limited credentials** — swap static `user=` for `use-auth-secret`
  with a shared secret, and have the signaling server hand out short-lived
  credentials. Better for production; static is fine for testing.
- **Check logs:** `journalctl -u coturn -f`
