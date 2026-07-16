#!/usr/bin/env bash
# Install debug APK and run VPN cycle + server-switch tests on every connected device.
#
# Usage:
#   ./scripts/adb/test_all_devices.sh
#   ./scripts/adb/test_all_devices.sh -n 5

set -euo pipefail

CYCLES=5
HOLD_SECS=0.5

usage() {
  cat <<'EOF'
Usage: test_all_devices.sh [-n cycles] [-d hold_secs] [-h]

  -n  Cycles per test per device (default: 5)
  -d  Hold seconds while connected (default: 0.5)
  -h  Show this help
EOF
  exit "${1:-0}"
}

while getopts "n:d:h" opt; do
  case "$opt" in
    n) CYCLES="$OPTARG" ;;
    d) HOLD_SECS="$OPTARG" ;;
    h) usage 0 ;;
    *) usage 1 ;;
  esac
done

if ! command -v adb &>/dev/null; then
  echo "Error: adb not found." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

DEVICES=()
while IFS= read -r line; do
  [[ -n "$line" ]] && DEVICES+=("$line")
done < <(adb_list_devices)
if [[ ${#DEVICES[@]} -eq 0 ]]; then
  echo "Error: no Android devices connected." >&2
  exit 1
fi

echo "==============================================="
echo "Demar VPN — multi-device test"
echo "==============================================="
echo "Devices:  ${#DEVICES[@]}"
echo "Cycles:   $CYCLES (cycle + server-switch each)"
echo "Hold:     ${HOLD_SECS}s"
echo ""

PACKAGE="com.demar.vpn"

install_apk() {
  local serial="$1"
  local out
  if out="$(adb -s "$serial" install -r "$APK" 2>&1)"; then
    echo "$out"
    return 0
  fi
  echo "$out"
  if [[ "$out" == *"INSTALL_FAILED_UPDATE_INCOMPATIBLE"* || "$out" == *"INSTALL_FAILED_VERSION_DOWNGRADE"* ]]; then
    echo "→ signature mismatch on ${serial}, uninstall + retry"
    adb -s "$serial" uninstall "$PACKAGE" >/dev/null 2>&1 || true
    adb -s "$serial" install -r "$APK"
    return $?
  fi
  return 1
}

cd "$ROOT"
APK="${ROOT}/app/build/outputs/apk/debug/app-debug.apk"
echo "→ ./gradlew :app:assembleDebug"
./gradlew :app:assembleDebug
for serial in "${DEVICES[@]}"; do
  echo "→ adb -s ${serial} install -r"
  install_apk "$serial"
done

FAILED=0
SUMMARY=()

for serial in "${DEVICES[@]}"; do
  echo ""
  echo "###############################################"
  echo "# Device: $serial"
  echo "###############################################"

  export ANDROID_SERIAL="$serial"
  label="$(adb_device_label)"
  cycle_ok=0
  switch_ok=0

  echo ""
  echo "── VPN cycle test ──"
  if "${SCRIPT_DIR}/vpn_cycle_test.sh" -n "$CYCLES" -d "$HOLD_SECS" -s "$serial"; then
    cycle_ok=1
  else
    FAILED=1
  fi

  echo ""
  echo "── Server switch test ──"
  if "${SCRIPT_DIR}/server_switch_test.sh" -n "$CYCLES" -s "$serial"; then
    switch_ok=1
  else
    FAILED=1
  fi

  if [[ "$cycle_ok" -eq 1 && "$switch_ok" -eq 1 ]]; then
    SUMMARY+=("OK   $label")
  else
    parts=""
    [[ "$cycle_ok" -eq 0 ]] && parts+="cycle "
    [[ "$switch_ok" -eq 0 ]] && parts+="switch "
    SUMMARY+=("FAIL $label ($parts)")
  fi
done

echo ""
echo "==============================================="
echo "Multi-device summary"
echo "==============================================="
for line in "${SUMMARY[@]}"; do
  echo "$line"
done
echo ""

if [[ "$FAILED" -ne 0 ]]; then
  echo "Some tests failed."
  exit 1
fi
echo "All devices passed."
exit 0
