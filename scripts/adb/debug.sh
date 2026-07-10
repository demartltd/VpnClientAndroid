#!/bin/bash
# Demar VPN Android — ADB diagnostics

set -euo pipefail

PACKAGE_NAME="com.demar.vpn"
TAGS="DemarVpn:D VpnTunnelProbe:D SingBoxCore:D DemarLibboxPlatform:D InternetProbe:D"

usage() {
  cat <<'EOF'
Usage: debug.sh [-s serial] [-h]

  -s  ADB device serial (default: all connected devices)
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

run_debug() {
  adb_require_device

  echo "==================================="
  echo "Demar VPN Android — Debug"
  echo "==================================="
  echo "Device: $(adb_device_label)"
  echo ""

  echo "=== Device Info ==="
  "${ADB[@]}" shell getprop ro.product.model
  "${ADB[@]}" shell getprop ro.build.version.release
  echo ""

  echo "=== VPN Service State ==="
  "${ADB[@]}" shell dumpsys activity service ${PACKAGE_NAME}/.vpn.DemarVpnService 2>/dev/null || echo "Service not running"
  echo ""

  echo "=== Recent VPN Logs ==="
  "${ADB[@]}" logcat -d -s ${TAGS} | tail -50
  echo ""

  echo "=== Network Interfaces ==="
  "${ADB[@]}" shell ip addr show 2>/dev/null | grep -E "(tun|vpn|wlan|rmnet)" || echo "No interfaces"
  echo ""

  echo "=== TUN Routing ==="
  "${ADB[@]}" shell ip route show table all 2>/dev/null | grep -E "(tun0|dev tun)" || echo "No TUN routes"
  echo ""

  echo "=== App Journal (AppLog) ==="
  ANDROID_SERIAL="${ANDROID_SERIAL:-}" "${SCRIPT_DIR}/journal.sh" 2>/dev/null || echo "Journal mirror not available (launch app first)"
  echo ""
}

if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  run_debug
  echo "=== Log Stream (Ctrl+C to stop) ==="
  "${ADB[@]}" logcat -c 2>/dev/null || true
  exec "${ADB[@]}" logcat -s ${TAGS}
fi

DEVICES=()
while IFS= read -r line; do
  [[ -n "$line" ]] && DEVICES+=("$line")
done < <(adb_list_devices)

if [[ ${#DEVICES[@]} -eq 0 ]]; then
  echo "Error: no Android devices connected." >&2
  exit 1
fi

for serial in "${DEVICES[@]}"; do
  export ANDROID_SERIAL="$serial"
  run_debug
done

if [[ ${#DEVICES[@]} -eq 1 ]]; then
  export ANDROID_SERIAL="${DEVICES[0]}"
  _adb_common_init
  echo "=== Log Stream (Ctrl+C to stop) ==="
  "${ADB[@]}" logcat -c 2>/dev/null || true
  exec "${ADB[@]}" logcat -s ${TAGS}
fi

echo "Multiple devices connected — log stream skipped (use -s SERIAL)."
