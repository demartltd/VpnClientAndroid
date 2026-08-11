#!/usr/bin/env bash
# Demar VPN Android — verify control-plane reachability when VPN is OFF
# and that self-exclusion is logged after a TUN establish.
#
# Usage:
#   ./scripts/adb/self_traffic_test.sh
#   ./scripts/adb/self_traffic_test.sh -s SERIAL

set -euo pipefail

PACKAGE="com.demar.vpn"
REMOTE_JOURNAL="/sdcard/Android/data/${PACKAGE}/files/demar/demar_app.log"

usage() {
  cat <<'EOF'
Usage: self_traffic_test.sh [-s serial] [-h]

  Checks Always-on VPN, dumpsys vpn, HTTPS probes to Demar APIs,
  and journal hints (self_disallowed / recent SocketTimeout).
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
FAIL=0

echo "--- Always-on VPN ---"
ALWAYS_ON="$("${ADB[@]}" shell settings get global always_on_vpn_app 2>/dev/null | tr -d '\r')"
LOCKDOWN="$("${ADB[@]}" shell settings get global always_on_vpn_lockdown 2>/dev/null | tr -d '\r')"
echo "always_on_vpn_app=${ALWAYS_ON:-null}"
echo "always_on_vpn_lockdown=${LOCKDOWN:-null}"
if [[ -n "${ALWAYS_ON}" && "${ALWAYS_ON}" != "null" ]]; then
  echo "WARN: Always-on VPN is set (${ALWAYS_ON}); lockdown can block APIs when VPN is off."
fi

echo "--- dumpsys vpn ---"
VPN_DUMP="$("${ADB[@]}" shell dumpsys vpn 2>/dev/null | tr -d '\r' || true)"
if [[ -z "${VPN_DUMP}" ]]; then
  echo "(empty — no VPN manager dump)"
else
  echo "${VPN_DUMP}" | head -40
fi
if echo "${VPN_DUMP}" | grep -qiE 'com\.demar\.vpn|Demar VPN'; then
  echo "WARN: Demar VPN still present in dumpsys vpn (expected OFF for this check)."
  FAIL=1
else
  echo "OK: no Demar VPN interface in dumpsys"
fi

probe_https() {
  local url="$1"
  local code
  code="$("${ADB[@]}" shell "curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 8 --max-time 15 '${url}'" 2>/dev/null | tr -d '\r' || true)"
  if [[ -z "${code}" || "${code}" == "000" ]]; then
    echo "FAIL: ${url} — no HTTP response (timeout/unreachable)"
    FAIL=1
  else
    echo "OK: ${url} → HTTP ${code}"
  fi
}

echo "--- HTTPS probes (VPN OFF) ---"
probe_https "https://fastapi.demar.cc/"
probe_https "https://api.demar.cc/"

echo "--- journal ---"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT
if "${ADB[@]}" pull "$REMOTE_JOURNAL" "$TMP" 2>/dev/null; then
  if grep -E 'tun_apps=self_disallowed' "$TMP" >/dev/null; then
    echo "OK: found tun_apps=self_disallowed"
    grep -E 'tun_apps=self_disallowed' "$TMP" | tail -3
  else
    echo "INFO: no tun_apps=self_disallowed yet (connect VPN once after update)"
  fi
  echo "Recent SocketTimeout / openTun:"
  grep -E 'SocketTimeout|openTun|tun_apps=self_disallowed' "$TMP" | tail -15 || echo "(none)"
else
  echo "INFO: journal mirror missing — open the app once, then retry."
fi

if [[ "$FAIL" -ne 0 ]]; then
  echo "RESULT: FAIL"
  exit 1
fi
echo "RESULT: OK"
