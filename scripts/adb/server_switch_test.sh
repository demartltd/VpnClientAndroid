#!/usr/bin/env bash
# Demar VPN Android — hot-switch servers while VPN stays up (ADB).
#
# Usage:
#   ./scripts/adb/server_switch_test.sh
#   ./scripts/adb/server_switch_test.sh -n 10

set -euo pipefail

PACKAGE="com.demar.vpn"
ACTIVITY="${PACKAGE}/.MainActivity"
REMOTE_UI="/sdcard/demar_ui.xml"
REMOTE_JOURNAL="/sdcard/Android/data/${PACKAGE}/files/demar/demar_app.log"

SERVER_A="FIN-DEV-1"
SERVER_B="DE-FREE-1"

CYCLES=10
CONNECT_TIMEOUT=60
POLL_MS=200

usage() {
  cat <<'EOF'
Usage: server_switch_test.sh [-n cycles] [-s serial] [-h]

  -n  Number of server hot-switch cycles (default: 10)
  -s  ADB device serial (default: ANDROID_SERIAL or sole connected device)
  -h  Show this help
EOF
  exit "${1:-0}"
}

while getopts "n:s:h" opt; do
  case "$opt" in
    n) CYCLES="$OPTARG" ;;
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

now_ms() {
  python3 -c 'import time; print(int(time.time() * 1000))'
}

sleep_ms() {
  python3 -c 'import time; time.sleep('"$1"' / 1000)'
}

ui_dump() {
  "${ADB[@]}" shell uiautomator dump "$REMOTE_UI" >/dev/null 2>&1 || true
  "${ADB[@]}" shell cat "$REMOTE_UI" 2>/dev/null || echo ""
}

ui_query() {
  local cmd="$1"
  shift
  local xml
  xml="$(ui_dump)"
  if [[ -z "$xml" ]]; then
    echo ""
    return
  fi
  printf '%s' "$xml" | python3 "$UI_PARSE" "$cmd" "$@"
}

tap_xy() {
  "${ADB[@]}" shell input tap "$1" "$2"
}

tap_toggle() {
  local raw cx cy
  raw="$(ui_query toggle)"
  if [[ -z "$raw" ]]; then
    echo "Error: VPN toggle not found." >&2
    exit 1
  fi
  IFS=',' read -r cx cy _ <<<"$raw"
  tap_xy "$cx" "$cy"
}

tap_tab() {
  local idx="$1"
  local pos
  pos="$(ui_query tab "$idx")"
  if [[ -z "$pos" ]]; then
    echo "Error: tab $idx not found." >&2
    exit 1
  fi
  IFS=',' read -r tx ty <<<"$pos"
  tap_xy "$tx" "$ty"
}

tap_server() {
  local name="$1"
  local pos
  pos="$(ui_query server "$name")"
  if [[ -z "$pos" ]]; then
    echo "Error: server '$name' not found." >&2
    exit 1
  fi
  IFS=',' read -r sx sy <<<"$pos"
  tap_xy "$sx" "$sy"
}

dismiss_vpn_permission_if_needed() {
  local pos
  pos="$(ui_query permission)"
  if [[ -n "$pos" ]]; then
    IFS=',' read -r px py <<<"$pos"
    echo "→ VPN permission dialog, tapping ${px},${py}"
    tap_xy "$px" "$py"
    sleep_ms 1500
  fi
}

wait_until_connected() {
  local deadline="$1"
  tap_tab 0 >/dev/null 2>&1 || true
  sleep_ms 250
  while [[ "$(now_ms)" -lt "$deadline" ]]; do
    if [[ "$(ui_query connected)" == "1" ]]; then
      return 0
    fi
    sleep_ms "$POLL_MS"
  done
  return 1
}

ensure_connected() {
  "${ADB[@]}" shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
  "${ADB[@]}" logcat -c >/dev/null 2>&1 || true
  "${ADB[@]}" shell am start -n "$ACTIVITY" >/dev/null
  sleep 1.5
  dismiss_vpn_permission_if_needed

  if [[ "$(ui_query connected)" == "1" ]]; then
    echo "→ already connected"
    return 0
  fi

  echo "→ initial connect"
  tap_toggle
  dismiss_vpn_permission_if_needed
  if wait_until_connected "$(($(now_ms) + CONNECT_TIMEOUT * 1000))"; then
    echo "✓ connected"
    return 0
  fi
  echo "✗ initial connect failed" >&2
  exit 1
}

RESULT_STATUS=()

echo "==============================================="
echo "Demar VPN — ADB server hot-switch test"
echo "==============================================="
echo "Device: $(adb_device_label)"
echo "Cycles: $CYCLES"
echo ""

ensure_connected

use_a=true
for ((i = 1; i <= CYCLES; i++)); do
  echo ""
  echo "── Switch cycle $i/$CYCLES ──"

  if [[ "$use_a" == true ]]; then
    target="$SERVER_B"
    use_a=false
  else
    target="$SERVER_A"
    use_a=true
  fi

  t0="$(now_ms)"
  echo "→ servers tab"
  tap_tab 1
  sleep_ms 300
  echo "→ select $target"
  tap_server "$target"
  sleep_ms 250
  echo "→ home tab"
  tap_tab 0

  if wait_until_connected $((t0 + CONNECT_TIMEOUT * 1000)); then
    ms="$(( $(now_ms) - t0 ))"
    echo "✓ reconnected in ${ms}ms ($target)"
    RESULT_STATUS+=("OK")
  else
    echo "✗ reconnect timeout ($target)"
    RESULT_STATUS+=("FAIL")
  fi
done

echo ""
echo "==============================================="
echo "Summary"
echo "==============================================="
ok=0
fail=0
for s in "${RESULT_STATUS[@]}"; do
  if [[ "$s" == "OK" ]]; then ((ok++)); else ((fail++)); fi
done
echo "OK: $ok  FAIL: $fail / $CYCLES"

echo ""
echo "==============================================="
echo "Recent logcat"
echo "==============================================="
"${ADB[@]}" logcat -d -s DemarVpn:I DemarVpn:W DemarVpn:E 2>/dev/null | tail -60 || true

echo ""
echo "==============================================="
echo "App journal"
echo "==============================================="
if [[ -x "${SCRIPT_DIR}/journal.sh" ]]; then
  ANDROID_SERIAL="${ANDROID_SERIAL:-}" "${SCRIPT_DIR}/journal.sh" 2>/dev/null | tail -80 || echo "(journal unavailable)"
fi

echo ""
if [[ "$fail" -gt 0 ]]; then
  exit 1
fi
echo "Done."
