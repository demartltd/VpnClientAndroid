#!/usr/bin/env bash
# Demar VPN Android — automated connect/disconnect cycle benchmark via ADB.
#
# Launches the app, finds the home VPN toggle, runs N cycles:
#   connect → hold → disconnect
# Then prints a timing table and recent journal/logcat excerpts.
#
# Usage:
#   ./scripts/adb/vpn_cycle_test.sh
#   ./scripts/adb/vpn_cycle_test.sh -n 5 -d 0.5

set -euo pipefail

PACKAGE="com.demar.vpn"
ACTIVITY="${PACKAGE}/.MainActivity"
REMOTE_UI="/sdcard/demar_ui.xml"
REMOTE_JOURNAL="/sdcard/Android/data/${PACKAGE}/files/demar/demar_app.log"

CYCLES=10
HOLD_SECS=0.5
CONNECT_TIMEOUT=45
DISCONNECT_TIMEOUT=20
POLL_MS=200

usage() {
  cat <<'EOF'
Usage: vpn_cycle_test.sh [-n cycles] [-d hold_secs] [-s serial] [-h]

  -n  Number of connect/disconnect cycles (default: 10)
  -d  Seconds to stay connected each cycle (default: 0.5)
  -s  ADB device serial (default: ANDROID_SERIAL or sole connected device)
  -h  Show this help
EOF
  exit "${1:-0}"
}

while getopts "n:d:s:h" opt; do
  case "$opt" in
    n) CYCLES="$OPTARG" ;;
    d) HOLD_SECS="$OPTARG" ;;
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
  local xml
  xml="$(ui_dump)"
  if [[ -z "$xml" ]]; then
    echo ""
    return
  fi
  printf '%s' "$xml" | python3 "$UI_PARSE" "$cmd"
}

tap_xy() {
  "${ADB[@]}" shell input tap "$1" "$2"
}

find_toggle() {
  local raw cx cy enabled
  raw="$(ui_query toggle)"
  if [[ -z "$raw" ]]; then
    return 1
  fi
  IFS=',' read -r cx cy enabled <<<"$raw"
  TOGGLE_X="$cx"
  TOGGLE_Y="$cy"
  TOGGLE_ENABLED="$enabled"
}

tap_toggle() {
  if ! find_toggle; then
    echo "Error: VPN toggle button not found on screen." >&2
    exit 1
  fi
  tap_xy "$TOGGLE_X" "$TOGGLE_Y"
}

dismiss_vpn_permission_if_needed() {
  local pos
  pos="$(ui_query permission)"
  if [[ -n "$pos" ]]; then
    IFS=',' read -r px py <<<"$pos"
    echo "→ VPN permission dialog detected, tapping ${px},${py}"
    tap_xy "$px" "$py"
    sleep_ms 1500
  fi
}

wait_until_connected() {
  local deadline="$1"
  while [[ "$(now_ms)" -lt "$deadline" ]]; do
    if [[ "$(ui_query connected)" == "1" ]]; then
      return 0
    fi
    sleep_ms "$POLL_MS"
  done
  return 1
}

wait_until_disconnected() {
  local deadline="$1"
  while [[ "$(now_ms)" -lt "$deadline" ]]; do
    if [[ "$(ui_query connected)" == "0" ]]; then
      raw="$(ui_query toggle)"
      if [[ -n "$raw" ]]; then
        IFS=',' read -r _ _ enabled <<<"$raw"
        if [[ "$enabled" == "true" ]]; then
          return 0
        fi
      fi
    fi
    sleep_ms "$POLL_MS"
  done
  return 1
}

ensure_home_tab() {
  "${ADB[@]}" shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
  "${ADB[@]}" logcat -c >/dev/null 2>&1 || true
  "${ADB[@]}" shell am start -n "$ACTIVITY" >/dev/null
  sleep 1.5
  dismiss_vpn_permission_if_needed
  if ! find_toggle; then
    echo "Error: home screen toggle not found after launch." >&2
    exit 1
  fi
  echo "→ toggle at ${TOGGLE_X},${TOGGLE_Y} (enabled=${TOGGLE_ENABLED})"
}

# Arrays for results (bash 3.2 compatible — no declare -a with -g)
RESULT_CONNECT=()
RESULT_DISCONNECT=()
RESULT_STATUS=()

MARKER_FILE="$(mktemp)"
trap 'rm -f "$MARKER_FILE"' EXIT

journal_pull() {
  local tmp
  tmp="$(mktemp)"
  if "${ADB[@]}" pull "$REMOTE_JOURNAL" "$tmp" >/dev/null 2>&1; then
    cat "$tmp"
  fi
  rm -f "$tmp"
}

echo "==============================================="
echo "Demar VPN — ADB toggle cycle test"
echo "==============================================="
echo "Device:   $(adb_device_label)"
echo "Cycles:   $CYCLES"
echo "Hold:     ${HOLD_SECS}s"
echo ""

ensure_home_tab

for ((i = 1; i <= CYCLES; i++)); do
  echo ""
  echo "── Cycle $i/$CYCLES ──"

  journal_pull | wc -l | tr -d ' ' >"$MARKER_FILE"

  t0="$(now_ms)"
  echo "→ connect tap"
  tap_toggle
  dismiss_vpn_permission_if_needed

  connect_ms="-"
  if wait_until_connected $((t0 + CONNECT_TIMEOUT * 1000)); then
    connect_ms="$(( $(now_ms) - t0 ))"
    echo "✓ connected in ${connect_ms}ms"
    status="OK"

    echo "→ hold ${HOLD_SECS}s"
    sleep "$HOLD_SECS"

    t1="$(now_ms)"
    echo "→ disconnect tap"
    tap_toggle

    if wait_until_disconnected $((t1 + DISCONNECT_TIMEOUT * 1000)); then
      disconnect_ms="$(( $(now_ms) - t1 ))"
      echo "✓ disconnected in ${disconnect_ms}ms"
    else
      disconnect_ms=">${DISCONNECT_TIMEOUT}s"
      status="DISCONNECT_TIMEOUT"
      echo "✗ disconnect timeout"
    fi
  else
    connect_ms=">${CONNECT_TIMEOUT}s"
    disconnect_ms="-"
    status="CONNECT_FAIL"
    echo "✗ connect timeout / failed"
  fi

  RESULT_CONNECT+=("$connect_ms")
  RESULT_DISCONNECT+=("$disconnect_ms")
  RESULT_STATUS+=("$status")
done

echo ""
echo "==============================================="
echo "Timing summary"
echo "==============================================="
printf "%-6s %-12s %-14s %-10s\n" "Cycle" "Connect" "Disconnect" "Status"
printf "%-6s %-12s %-14s %-10s\n" "-----" "-------" "----------" "------"
for ((i = 0; i < ${#RESULT_CONNECT[@]}; i++)); do
  printf "%-6s %-12s %-14s %-10s\n" "$((i + 1))" "${RESULT_CONNECT[$i]}" "${RESULT_DISCONNECT[$i]}" "${RESULT_STATUS[$i]}"
done

# Averages for numeric results only
avg_ms() {
  python3 - "$@" <<'PY'
import sys
vals = []
for a in sys.argv[1:]:
    if a.endswith("ms") and a[0].isdigit():
        vals.append(int(a.replace("ms", "")))
    elif a.isdigit():
        vals.append(int(a))
if not vals:
    print("-")
else:
    print(f"{sum(vals) // len(vals)}ms")
PY
}

echo ""
echo "Avg connect:    $(avg_ms "${RESULT_CONNECT[@]}")"
echo "Avg disconnect: $(avg_ms "${RESULT_DISCONNECT[@]}")"

echo ""
echo "==============================================="
echo "Recent logcat (DemarVpn, VpnTunnelProbe)"
echo "==============================================="
"${ADB[@]}" logcat -d -s DemarVpn:I DemarVpn:W DemarVpn:E VpnTunnelProbe:D 2>/dev/null | tail -80 || true

echo ""
echo "==============================================="
echo "App journal (AppLog mirror)"
echo "==============================================="
if [[ -x "${SCRIPT_DIR}/journal.sh" ]]; then
  ANDROID_SERIAL="${ANDROID_SERIAL:-}" "${SCRIPT_DIR}/journal.sh" 2>/dev/null | tail -120 || echo "(journal unavailable)"
else
  journal_pull | tail -120 || echo "(journal unavailable)"
fi

echo ""
echo "Done."
