#!/bin/bash
#
# flash.sh
# Flash AGI-Android OS GSI to a device
#
# Usage:
#   ./flash.sh              # Flash to connected device
#   ./flash.sh SERIAL       # Flash to specific device

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(dirname "$SCRIPT_DIR")"
SYSTEM_IMG="$REPO_DIR/out/system.img"
VBMETA_IMG="$REPO_DIR/out/vbmeta.img"

# Device serial (optional)
DEVICE_SERIAL="${1:-}"

echo "=== AGI-Android OS Flash Tool ==="
echo ""

# Check for system.img
if [[ ! -f "$SYSTEM_IMG" ]]; then
    echo "Error: system.img not found at $SYSTEM_IMG"
    echo "Run ./tools/build.sh first"
    exit 1
fi

# Set device serial if provided
if [[ -n "$DEVICE_SERIAL" ]]; then
    export ANDROID_SERIAL="$DEVICE_SERIAL"
    echo "Using device: $DEVICE_SERIAL"
fi

# Check for connected device
echo "Checking for connected devices..."
DEVICES=$(adb devices | grep -v "List" | grep -v "^$" | wc -l)

if [[ "$DEVICES" -eq 0 ]]; then
    echo "Error: No devices connected"
    echo "Connect a device with USB debugging enabled"
    exit 1
elif [[ "$DEVICES" -gt 1 && -z "$DEVICE_SERIAL" ]]; then
    echo "Multiple devices connected. Specify a serial number:"
    adb devices
    exit 1
fi

# Get device info
echo ""
echo "Device info:"
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release

# Check Treble support
TREBLE=$(adb shell getprop ro.treble.enabled 2>/dev/null || echo "false")
if [[ "$TREBLE" != "true" ]]; then
    echo ""
    echo "Warning: Device may not support GSI (ro.treble.enabled != true)"
    read -p "Continue anyway? [y/N] " -n 1 -r
    echo ""
    [[ ! $REPLY =~ ^[Yy]$ ]] && exit 1
fi

# Check A/B partition scheme
AB_UPDATE=$(adb shell getprop ro.build.ab_update 2>/dev/null || echo "false")
echo ""
echo "Partition scheme: $([ "$AB_UPDATE" = "true" ] && echo "A/B" || echo "A-only")"

# Confirm flash
echo ""
echo "This will:"
echo "  1. Reboot to fastboot"
echo "  2. Disable verified boot (vbmeta)"
echo "  3. Flash system.img"
echo "  4. Wipe user data (optional)"
echo ""
echo "WARNING: This will replace the system partition!"
read -p "Proceed? [y/N] " -n 1 -r
echo ""
[[ ! $REPLY =~ ^[Yy]$ ]] && exit 0

# Ask about data wipe
echo ""
read -p "Wipe user data? (recommended for clean install) [Y/n] " -n 1 -r
echo ""
WIPE_DATA=true
[[ $REPLY =~ ^[Nn]$ ]] && WIPE_DATA=false

# Reboot to bootloader
echo ""
echo "Rebooting to bootloader..."
adb reboot bootloader
sleep 5

# Wait for fastboot
echo "Waiting for fastboot..."
fastboot devices | grep -q . || {
    echo "Device not found in fastboot mode"
    echo "Manually boot to fastboot and try again"
    exit 1
}

# Disable vbmeta verification
echo ""
echo "Disabling verified boot..."
if [[ -f "$VBMETA_IMG" ]]; then
    fastboot flash vbmeta "$VBMETA_IMG" --disable-verity --disable-verification
else
    # Create empty vbmeta
    dd if=/dev/zero of=/tmp/vbmeta_disabled.img bs=256 count=64 2>/dev/null
    fastboot flash vbmeta /tmp/vbmeta_disabled.img --disable-verity --disable-verification
fi

# For A/B devices, need to use fastbootd
if [[ "$AB_UPDATE" == "true" ]]; then
    echo ""
    echo "A/B device detected, rebooting to fastbootd..."
    fastboot reboot fastboot
    sleep 5

    # Wait for fastbootd
    fastboot devices | grep -q . || {
        echo "Device not found in fastbootd mode"
        exit 1
    }
fi

# Flash system
echo ""
echo "Flashing system.img (this may take a few minutes)..."
fastboot flash system "$SYSTEM_IMG"

# Wipe data if requested
if [[ "$WIPE_DATA" == "true" ]]; then
    echo ""
    echo "Wiping user data..."
    fastboot -w
fi

# Reboot
echo ""
echo "Rebooting..."
fastboot reboot

echo ""
echo "=== Flash complete ==="
echo ""
echo "First boot may take 5-10 minutes."
echo "Watch for boot completion:"
echo "  adb wait-for-device && adb shell getprop sys.boot_completed"
echo ""
echo "After boot, verify AgentSystemService:"
echo "  adb shell service list | grep agent"
