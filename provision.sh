#!/usr/bin/env bash
#
# Provision the MiniConnect backend on a fresh Debian/Ubuntu droplet.
# Now uses LiveKit (SFU + token server). Run as root:
#
#   sudo bash provision.sh [API_KEY] [API_SECRET]
#
# Omit the keys to auto-generate them. The script prints the droplet IP +
# the values to put in LiveKitConfig.kt.
#
# (Legacy coturn + Node signaling scripts remain under turn-server/ and
#  signaling-server/ but are no longer used by the LiveKit build.)
#
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
bash "$HERE/livekit-server/setup-livekit.sh" "${1:-}" "${2:-}"
