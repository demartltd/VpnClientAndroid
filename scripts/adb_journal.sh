#!/bin/bash
# Demar VPN Android — pull AppLog journal mirror via ADB (debug + release)

set -euo pipefail

PACKAGE="com.demar.vpn"
REMOTE="/sdcard/Android/data/${PACKAGE}/files/demar/demar_app.log"
OUT=""

usage() {
  echo "Usage: $0 [-o file]"
  echo "  Pull journal mirror (up to 512 lines) from device."
  echo "  Without -o: print to stdout. With -o: save to file."
  exit 1
}

while getopts "o:h" opt; do
  case "$opt" in
    o) OUT="$OPTARG" ;;
    h) usage ;;
    *) usage ;;
  esac
done

if ! command -v adb &>/dev/null; then
  echo "Error: adb not found." >&2
  exit 1
fi

if ! adb devices | grep -q "device$"; then
  echo "Error: No Android device connected." >&2
  exit 1
fi

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

if ! adb pull "$REMOTE" "$TMP" 2>/dev/null; then
  echo "Journal mirror not found on device." >&2
  echo "Path: $REMOTE" >&2
  echo "Launch the app and generate log entries (e.g. connect VPN), then retry." >&2
  exit 1
fi

if [[ ! -s "$TMP" ]]; then
  echo "Journal mirror is empty." >&2
  exit 0
fi

if [[ -n "$OUT" ]]; then
  cp "$TMP" "$OUT"
  echo "Saved to $OUT ($(wc -l < "$OUT" | tr -d ' ') lines)"
else
  cat "$TMP"
fi
