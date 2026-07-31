#!/usr/bin/env bash
# Demar VPN Android — servers list fetch smoke (ADB).
#
# Requires an already signed-in session on device (API must return servers).
#
# Usage:
#   ./scripts/adb/servers_fetch_test.sh
#   ./scripts/adb/servers_fetch_test.sh -s SERIAL

set -euo pipefail

PACKAGE="com.demar.vpn"
ACTIVITY="${PACKAGE}/.MainActivity"
REMOTE_UI="/sdcard/demar_ui.xml"

usage() {
  cat <<'EOF'
Usage: servers_fetch_test.sh [-s serial] [-h]

  Opens Servers tab, waits for API list, dumps server names from UI + journal.

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

if ! command -v adb &>/dev/null; then
  echo "Error: adb not found." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"
adb_require_device
UI_PARSE="${SCRIPT_DIR}/ui_parse.py"

ui_dump() {
  "${ADB[@]}" shell uiautomator dump "$REMOTE_UI" >/dev/null 2>&1 || true
  "${ADB[@]}" shell cat "$REMOTE_UI" 2>/dev/null || echo ""
}

ui_query() {
  local cmd="$1"
  shift
  local xml
  xml="$(ui_dump)"
  [[ -z "$xml" ]] && { echo ""; return; }
  printf '%s' "$xml" | python3 "$UI_PARSE" "$cmd" "$@"
}

tap_xy() {
  "${ADB[@]}" shell input tap "$1" "$2"
}

tap_tab() {
  local pos
  pos="$(ui_query tab "$1")"
  if [[ -z "$pos" ]]; then
    echo "Error: tab $1 not found." >&2
    exit 1
  fi
  IFS=',' read -r tx ty <<<"$pos"
  tap_xy "$tx" "$ty"
}

echo "=== Servers fetch smoke — $(adb_device_label) ==="
"${ADB[@]}" shell am start -n "$ACTIVITY" >/dev/null
sleep 2.5

echo "→ Servers tab"
pos=""
for _ in 1 2 3 4 5; do
  pos="$(ui_query tab 1)"
  [[ -n "$pos" ]] && break
  sleep 1
done
if [[ -z "$pos" ]]; then
  echo "Error: tab 1 not found." >&2
  exit 1
fi
IFS=',' read -r tx ty <<<"$pos"
tap_xy "$tx" "$ty"
sleep 2.5

names=""
for _ in 1 2 3 4 5 6; do
  names="$(ui_query server_names)"
  [[ -n "$names" ]] && break
  sleep 1
done
echo "→ UI server names: ${names:-"(none)"}"

echo "→ Journal (servers lines)"
ANDROID_SERIAL="${ANDROID_SERIAL:-}" "${SCRIPT_DIR}/journal.sh" 2>/dev/null | grep -i servers || {
  echo "(no servers lines — sign in and pull-to-refresh)"
}

if [[ -z "$names" ]]; then
  # Sign-in hint text may be present
  xml="$(ui_dump)"
  if printf '%s' "$xml" | grep -qi "Войдите"; then
    echo "FAIL: not signed in (servers gated)." >&2
    exit 1
  fi
  echo "FAIL: empty server list." >&2
  exit 1
fi

echo "OK: servers visible (${names})"
