#!/usr/bin/env bash
# Build OpenFlux arm64 for Android with CGO (Bionic DNS). Pure-Go linux/arm64
# fails on device: lookup → [::1]:53.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JNI="$ROOT/app/src/main/jniLibs/arm64-v8a/libopenflux.so"
ASSET="$ROOT/app/src/main/assets/openflux/openflux-arm64"
REF="${OPENFLUX_REF:-v0.13.0}"
API="${OPENFLUX_ANDROID_API:-33}"
WORKDIR="${TMPDIR:-/tmp}/openflux-android-build-$$"

cleanup() { rm -rf "$WORKDIR"; }
trap cleanup EXIT

command -v go >/dev/null || { echo "need go"; exit 1; }

resolve_ndk() {
  if [[ -n "${ANDROID_NDK_HOME:-}" && -d "$ANDROID_NDK_HOME" ]]; then
    echo "$ANDROID_NDK_HOME"
    return
  fi
  local root="${ANDROID_HOME:-${HOME}/Library/Android/sdk}/ndk"
  ls -d "$root"/* 2>/dev/null | sort -V | tail -1
}

NDK="$(resolve_ndk || true)"
[[ -n "$NDK" && -d "$NDK" ]] || { echo "need Android NDK (ANDROID_NDK_HOME or \$ANDROID_HOME/ndk)"; exit 1; }

CC="$(ls "$NDK"/toolchains/llvm/prebuilt/*/bin/aarch64-linux-android${API}-clang 2>/dev/null | head -1 || true)"
[[ -n "$CC" && -x "$CC" ]] || { echo "NDK clang aarch64-linux-android${API}-clang not found under $NDK"; exit 1; }

mkdir -p "$(dirname "$JNI")" "$(dirname "$ASSET")" "$WORKDIR"
echo "==> clone OpenFlux $REF"
git clone --depth 1 --branch "$REF" https://github.com/p1neappleXpress/OpenFlux.git "$WORKDIR/src"
cd "$WORKDIR/src"
echo "==> CGO android/arm64 API=$API"
# -checklinkname=0: anet go:linkname on Go 1.23+
CGO_ENABLED=1 GOOS=android GOARCH=arm64 CC="$CC" \
  go build -trimpath -ldflags='-s -w -checklinkname=0' -o "$JNI" .
chmod +x "$JNI"
cp -f "$JNI" "$ASSET"
chmod +x "$ASSET"
echo "==> wrote $JNI ($(wc -c < "$JNI") bytes)"
file "$JNI" || true
