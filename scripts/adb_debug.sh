#!/bin/bash
# Demar VPN Android — ADB diagnostics

set -e

PACKAGE_NAME="com.demar.vpn"
TAGS="DemarVpn:D VpnTunnelProbe:D SingBoxCore:D DemarLibboxPlatform:D"

echo "==================================="
echo "Demar VPN Android — Debug"
echo "==================================="

if ! command -v adb &> /dev/null; then
    echo "Error: adb not found."
    exit 1
fi

if ! adb devices | grep -q "device$"; then
    echo "Error: No Android device connected."
    exit 1
fi

echo ""
echo "=== Device Info ==="
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
echo ""

echo "=== VPN Service State ==="
adb shell dumpsys activity service ${PACKAGE_NAME}/.vpn.DemarVpnService 2>/dev/null || echo "Service not running"
echo ""

echo "=== Recent VPN Logs ==="
adb logcat -d -s ${TAGS} | tail -50
echo ""

echo "=== Network Interfaces ==="
adb shell ip addr show 2>/dev/null | grep -E "(tun|vpn|wlan|rmnet)" || echo "No interfaces"
echo ""

echo "=== TUN Routing ==="
adb shell ip route show table all 2>/dev/null | grep -E "(tun0|dev tun)" || echo "No TUN routes"
echo ""

echo "=== Log Stream (Ctrl+C to stop) ==="
adb logcat -c 2>/dev/null || true
adb logcat -s ${TAGS}
