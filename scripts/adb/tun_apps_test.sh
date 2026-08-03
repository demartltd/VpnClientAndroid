#!/usr/bin/env bash
# Demar VPN Android — smoke-check tun_apps journal lines via ADB.
#
# Usage:
#   ./scripts/adb/tun_apps_test.sh
#   ./scripts/adb/tun_apps_test.sh -s SERIAL

set -euo pipefail

PACKAGE="com.demar.vpn"
REMOTE_JOURNAL="/sdcard/Android/data/${PACKAGE}/files/demar/demar_app.log"

usage() {
  cat <<'EOF'
Usage: tun_apps_test.sh [-s serial] [-h]

  Greps demar_app.log for tun_apps=… lines (exclude-package settings).
EOF
  exit "${1:-0}"
}

while getopts "s:h" opt; do
  case "$opt" in
    s) ANDROID_SERIAL="$OPTARG" ;;
    h) usage 0 ;;
    *) usage 1 ;;
  esac
done

if ! command -v adb &>/dev/null; then
  echo "Error: adb not found." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"
adb_require_device

echo "Device: $(adb_device_label)"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

if ! "${ADB[@]}" pull "$REMOTE_JOURNAL" "$TMP" 2>/dev/null; then
  echo "Journal mirror not found — open the app once, then retry." >&2
  exit 1
fi

echo "--- tun_apps journal ---"
if grep -E 'tun_apps=' "$TMP"; then
  echo "OK: found tun_apps log lines"
else
  echo "No tun_apps lines yet (App settings → TUN settings → Add app)."
fi
