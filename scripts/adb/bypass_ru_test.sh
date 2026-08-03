#!/usr/bin/env bash
# Demar VPN Android — smoke-check tun_bypass journal lines via ADB.
#
# Usage:
#   ./scripts/adb/bypass_ru_test.sh
#   ./scripts/adb/bypass_ru_test.sh -s SERIAL

set -euo pipefail

PACKAGE="com.demar.vpn"
REMOTE_JOURNAL="/sdcard/Android/data/${PACKAGE}/files/demar/demar_app.log"

usage() {
  cat <<'EOF'
Usage: bypass_ru_test.sh [-s serial] [-h]

  Greps demar_app.log for tun_bypass=… on|off|gated|auto_on lines.
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

echo "--- tun_bypass journal ---"
if grep -E 'tun_bypass=' "$TMP"; then
  echo "OK: found tun_bypass log lines"
else
  echo "No tun_bypass lines yet (TUN settings → bypass after Plus/VIP/Dev/Tester login)."
fi
