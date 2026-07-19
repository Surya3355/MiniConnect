#!/usr/bin/env bash
#
# One-shot coturn (TURN/STUN) setup for MiniConnect.
# Run on a fresh Debian 12 or Ubuntu 22.04/24.04 DigitalOcean droplet as root:
#
#   sudo bash setup-coturn.sh [PASSWORD]
#
# If you don't pass a password, a strong one is generated and printed.
#
set -euo pipefail

TURN_USER="rider"
TURN_PASS="${1:-}"
REALM="miniconnect"

if [ -z "$TURN_PASS" ]; then
  TURN_PASS=$(openssl rand -hex 16)
fi

echo ">> Installing coturn + tools (curl, ufw)..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
# curl and ufw are not always preinstalled on Debian minimal images
apt-get install -y coturn curl ufw

echo ">> Detecting public IP..."
PUBLIC_IP=$(curl -s --max-time 5 http://169.254.169.254/metadata/v1/interfaces/public/0/ipv4/address || true)
if [ -z "$PUBLIC_IP" ]; then
  PUBLIC_IP=$(curl -s --max-time 5 ifconfig.me || true)
fi
if [ -z "$PUBLIC_IP" ]; then
  echo "!! Could not auto-detect public IP. Edit external-ip in /etc/turnserver.conf manually."
  PUBLIC_IP="AUTO_DETECT_FAILED"
fi
echo ">> Public IP: $PUBLIC_IP"

echo ">> Enabling coturn service..."
if grep -q '#TURNSERVER_ENABLED=1' /etc/default/coturn 2>/dev/null; then
  sed -i 's/#TURNSERVER_ENABLED=1/TURNSERVER_ENABLED=1/' /etc/default/coturn
elif ! grep -q 'TURNSERVER_ENABLED=1' /etc/default/coturn 2>/dev/null; then
  echo "TURNSERVER_ENABLED=1" >> /etc/default/coturn
fi

echo ">> Writing /etc/turnserver.conf..."
cat > /etc/turnserver.conf <<EOF
# --- MiniConnect TURN/STUN ---
listening-port=3478
fingerprint
lt-cred-mech
user=${TURN_USER}:${TURN_PASS}
realm=${REALM}
server-name=${REALM}
external-ip=${PUBLIC_IP}
min-port=49152
max-port=65535
no-cli
no-multicast-peers
# Don't relay to private/link-local ranges (basic hardening)
denied-peer-ip=10.0.0.0-10.255.255.255
denied-peer-ip=172.16.0.0-172.31.255.255
denied-peer-ip=192.168.0.0-192.168.255.255
denied-peer-ip=169.254.0.0-169.254.255.255
EOF

echo ">> Configuring firewall (ufw)..."
if command -v ufw >/dev/null 2>&1; then
  ufw allow 22/tcp        || true
  ufw allow 3478/tcp      || true
  ufw allow 3478/udp      || true
  ufw allow 49152:65535/udp || true
  yes | ufw enable        || true
fi

echo ">> Restarting coturn..."
systemctl restart coturn
systemctl enable coturn

echo ""
echo "=================================================="
echo " coturn is running"
echo "--------------------------------------------------"
echo " Public IP : ${PUBLIC_IP}"
echo " Username  : ${TURN_USER}"
echo " Password  : ${TURN_PASS}"
echo "--------------------------------------------------"
echo " Put these into SignalingConfig.kt:"
echo "   TURN_HOST = \"${PUBLIC_IP}\""
echo "   TURN_USER = \"${TURN_USER}\""
echo "   TURN_PASS = \"${TURN_PASS}\""
echo "=================================================="
