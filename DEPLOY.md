# Backend deploy / teardown (save droplet credits)

The MiniConnect backend (TURN + signaling) is fully reproducible, so you can
**destroy the droplet when you're not testing** and rebuild it in ~2 minutes.

## Rebuild on a fresh droplet

1. Create a droplet (Debian 12, closest region, password or SSH).
2. On the droplet:
   ```bash
   apt-get update -y && apt-get install -y git
   git clone <this-repo-url> mc && cd mc
   sudo bash provision.sh my-fixed-turn-password
   ```
   (Pass a fixed TURN password so only the IP changes between rebuilds.)
3. The script prints the new IP. Update
   `app/src/main/java/com/surya/miniconnect/data/webrtc/SignalingConfig.kt`:
   ```kotlin
   const val SERVER_URL = "ws://NEW_IP:8080"
   private const val TURN_HOST = "NEW_IP"
   ```
   Rebuild the app.

## Destroy to stop charges

Dashboard → Droplet → **Destroy**. That halts all compute billing. Nothing else
is left running. (Snapshots/Reserved IPs, if you create them, bill separately.)

## Avoiding the app rebuild each time (optional)

The only thing that changes on rebuild is the **public IP**. To make the app URL
stable so you *don't* rebuild each time:

- **Domain (recommended):** point a subdomain (e.g. the free Namecheap student
  domain) at the droplet with an `A` record. Use `ws://call.yourdomain/…` and
  `turn:call.yourdomain:3478` in `SignalingConfig.kt`. On rebuild, edit one DNS
  record instead of the app.
- **Reserved IP:** keep a static DigitalOcean Reserved IP and reassign it to the
  new droplet (small charge while the droplet is destroyed).

## Fully automated create/destroy (optional, later)

With the DigitalOcean CLI `doctl` (needs a **DigitalOcean API token** from the
dashboard) you can skip the console entirely:

```bash
# create + auto-run provisioning on first boot via cloud-init user-data
doctl compute droplet create mc-backend \
  --region blr1 --image debian-12-x64 --size s-1vcpu-1gb \
  --user-data-file cloud-init.sh

# tear down
doctl compute droplet delete mc-backend
```

`cloud-init.sh` would `git clone` this repo and run `provision.sh` on boot.

## Note for the LiveKit migration

When we move to LiveKit, this gets simpler: **one** service replaces both coturn
and the signaling server. `provision.sh` would install the LiveKit server
instead, generate its API key/secret, and print them the same way.
