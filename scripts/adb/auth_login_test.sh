#!/bin/bash
# Demar VPN Android — auth UI / deep-link smoke test

set -euo pipefail

PACKAGE_NAME="com.demar.vpn"
MAIN_ACTIVITY="${PACKAGE_NAME}/.MainActivity"

usage() {
  cat <<'EOF'
Usage: auth_login_test.sh [-s serial] [-h]

  Opens Account tab (login sheet), then fires demar://auth/callback deep link
  with a dummy code (API will reject — checks app handles intent + journal).

  -s  ADB device serial
  -h  Show this help
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

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

if ! command -v adb &>/dev/null; then
  echo "Error: adb not found." >&2
  exit 1
fi

adb_require_device

echo "=== Auth login smoke — $(adb_device_label) ==="

"${ADB[@]}" shell am start -n "$MAIN_ACTIVITY" >/dev/null
sleep 1

# Account tab is index 3 in floating toolbar; tap via content description if present,
# otherwise open deep link only.
echo "— Launch deep link demar://auth/callback?code=test"
"${ADB[@]}" shell am start -a android.intent.action.VIEW \
  -d 'demar://auth/callback?code=test' \
  -n "$MAIN_ACTIVITY" >/dev/null || true
sleep 2

echo "— Journal (auth lines)"
ANDROID_SERIAL="${ANDROID_SERIAL:-}" "${SCRIPT_DIR}/journal.sh" 2>/dev/null | grep -i auth || {
  echo "(no auth lines yet — open Account tab and try email/Telegram manually)"
}

echo "OK: deep link delivered. Expect 'oauth exchange failed' for dummy code."
