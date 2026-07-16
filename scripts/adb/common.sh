# shellcheck shell=bash
# Shared ADB helpers — source: source "$(dirname "$0")/common.sh"

_adb_common_init() {
  if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    ADB=(adb -s "$ANDROID_SERIAL")
  else
    ADB=(adb)
  fi
}

adb_require_device() {
  _adb_common_init
  if ! "${ADB[@]}" get-state 2>/dev/null | grep -q '^device$'; then
    echo "Error: device not ready (${ANDROID_SERIAL:-default})." >&2
    exit 1
  fi
}

adb_device_label() {
  _adb_common_init
  local model serial
  model="$("${ADB[@]}" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
  serial="${ANDROID_SERIAL:-$("${ADB[@]}" shell getprop ro.serialno 2>/dev/null | tr -d '\r')}"
  echo "${model:-unknown} (${serial:-?})"
}

adb_list_devices() {
  adb devices 2>/dev/null | while read -r serial state _rest; do
    if [[ -n "$serial" && "$serial" != "List" && "$state" == "device" ]]; then
      echo "$serial"
    fi
  done
}
