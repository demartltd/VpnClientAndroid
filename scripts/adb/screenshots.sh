#!/usr/bin/env bash
# Capture main-tab screenshots via ADB (starts emulator if needed).
#
# Usage: ./scripts/adb/screenshots.sh [-s serial] [-o dir] [--no-build] [--keep-emulator]

set -euo pipefail

PACKAGE="com.demar.vpn"
ACTIVITY="${PACKAGE}/.MainActivity"
REMOTE_UI="/sdcard/demar_ui.xml"
AVD="Medium_Phone_API_36.1"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
EMULATOR="$SDK/emulator/emulator"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="$REPO_ROOT/docs/screenshots"
DO_BUILD=1
KEEP_EMU=0
STARTED_EMU=""

usage() {
  cat <<'EOF'
Usage: screenshots.sh [-s serial] [-o dir] [--no-build] [--keep-emulator] [-h]

  -s  ADB device serial
  -o  Output directory (default: docs/screenshots)
  --no-build         Skip gradlew installDebug (still installs if this script started the emulator)
  --keep-emulator    Do not kill emulator started by this script
EOF
  exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -s) ANDROID_SERIAL="$2"; shift 2 ;;
    -o) OUT_DIR="$2"; shift 2 ;;
    --no-build) DO_BUILD=0; shift ;;
    --keep-emulator) KEEP_EMU=1; shift ;;
    -h) usage 0 ;;
    *) echo "Unknown option: $1" >&2; usage 1 ;;
  esac
done

command -v adb &>/dev/null || { echo "Error: adb not found." >&2; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"
UI_PARSE="${SCRIPT_DIR}/ui_parse.py"

sleep_ms() { python3 -c 'import time; time.sleep('"$1"' / 1000)'; }

ui_dump() {
  "${ADB[@]}" shell uiautomator dump "$REMOTE_UI" >/dev/null 2>&1 || true
  "${ADB[@]}" shell cat "$REMOTE_UI" 2>/dev/null || echo ""
}

ui_query() {
  local xml
  xml="$(ui_dump)"
  [[ -n "$xml" ]] || { echo ""; return 0; }
  printf '%s' "$xml" | python3 "$UI_PARSE" "$@"
}

tap_query() {
  local pos="$1"
  [[ -n "$pos" ]] || return 1
  IFS=',' read -r x y <<<"$pos"
  "${ADB[@]}" shell input tap "$x" "$y"
}

tap_tab() {
  local idx="$1" tries=0 pos
  while (( tries < 20 )); do
    pos="$(ui_query tab "$idx")"
    if [[ -n "$pos" ]]; then
      tap_query "$pos" && return 0
    fi
    sleep_ms 500
    tries=$((tries + 1))
  done
  return 1
}

tap_toggle() {
  local raw cx cy _
  raw="$(ui_query toggle)" || return 1
  [[ -n "$raw" ]] || return 1
  IFS=',' read -r cx cy _ <<<"$raw"
  "${ADB[@]}" shell input tap "$cx" "$cy"
}

dismiss_vpn_permission() {
  local pos
  pos="$(ui_query permission)"
  if [[ -n "$pos" ]]; then
    tap_query "$pos"
    sleep_ms 1500
  fi
}

wait_connected() {
  local tries=0
  while (( tries < 90 )); do
    [[ "$(ui_query connected)" == "1" ]] && return 0
    dismiss_vpn_permission
    sleep_ms 500
    tries=$((tries + 1))
  done
  return 1
}

wait_boot() {
  local deadline=$((SECONDS + 120))
  while (( SECONDS < deadline )); do
    if [[ "$("${ADB[@]}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
      return 0
    fi
    sleep 2
  done
  echo "Error: emulator boot timeout." >&2
  return 1
}

ensure_device() {
  _adb_common_init
  if "${ADB[@]}" get-state 2>/dev/null | grep -q '^device$'; then
    return 0
  fi
  [[ -x "$EMULATOR" ]] || { echo "Error: no device and no emulator at $EMULATOR" >&2; exit 1; }
  echo "Starting emulator ${AVD}..."
  "$EMULATOR" -avd "$AVD" -no-snapshot-load -no-boot-anim -gpu host >/dev/null 2>&1 &
  STARTED_EMU=1
  adb wait-for-device
  _adb_common_init
  wait_boot
  echo "Emulator ready — will install latest build."
}

prepare_device() {
  "${ADB[@]}" shell cmd uimode night yes >/dev/null 2>&1 || true
  for scale in window_animation_scale transition_animation_scale animator_duration_scale; do
    "${ADB[@]}" shell settings put global "$scale" 0 >/dev/null 2>&1 || true
  done
}

build_and_install() {
  local props="$REPO_ROOT/local.properties"
  [[ -f "$props" ]] || printf 'sdk.dir=%s\n' "$SDK" >"$props"
  echo "Building and installing debug APK..."
  (cd "$REPO_ROOT" && ./gradlew :app:installDebug)
}

launch_app() {
  "${ADB[@]}" shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
  "${ADB[@]}" shell am start -n "$ACTIVITY" >/dev/null 2>&1 || true
  sleep_ms 2000
  dismiss_vpn_permission
}

capture() {
  mkdir -p "$(dirname "$1")"
  "${ADB[@]}" exec-out screencap -p >"$1"
}

try_vpn_connected() {
  local out="$1"
  tap_toggle || { echo "Skip $out (toggle not found)"; return 0; }
  dismiss_vpn_permission
  if wait_connected; then
    sleep_ms 1500
    capture "$out"
    echo "OK $out"
  else
    echo "Skip $out (VPN connect failed)"
  fi
}

cleanup() {
  [[ "$KEEP_EMU" == 1 || -z "$STARTED_EMU" ]] && return
  "${ADB[@]}" emu kill >/dev/null 2>&1 || true
}
trap cleanup EXIT

ensure_device
adb_require_device
prepare_device
if [[ "$DO_BUILD" == 1 || -n "$STARTED_EMU" ]]; then
  build_and_install
fi
launch_app
mkdir -p "$OUT_DIR"

for entry in "04-account:3" "01-home:0" "02-servers:1" "03-settings:2"; do
  name="${entry%%:*}"
  tab="${entry##*:}"
  tap_tab "$tab"
  sleep_ms 600
  out="$OUT_DIR/${name}.png"
  capture "$out"
  echo "OK $out"
  [[ "$name" == "01-home" ]] && try_vpn_connected "$OUT_DIR/05-home-connected.png"
done

echo ""
echo "Screenshots saved to $OUT_DIR"
