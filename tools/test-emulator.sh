#!/bin/bash
#
# test-emulator.sh
# Test AGI-Android OS in the Android emulator
#
# This builds for x86_64 and runs in the emulator.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(dirname "$SCRIPT_DIR")"
AOSP_DIR="${AOSP_DIR:-$HOME/aosp}"

echo "=== AGI-Android OS Emulator Test ==="
echo ""

# Check if we need to build
SYSTEM_IMG="$AOSP_DIR/out/target/product/generic_x86_64/system.img"

if [[ ! -f "$SYSTEM_IMG" ]]; then
    echo "x86_64 build not found. Building for emulator..."
    echo ""

    # Build for emulator
    TARGET="agi_os_x86_64-userdebug" "$SCRIPT_DIR/build.sh"
fi

# Set up environment
cd "$AOSP_DIR"
source build/envsetup.sh
lunch agi_os_x86_64-userdebug

# Run emulator
echo ""
echo "Starting emulator..."
echo "This may take a minute to boot."
echo ""
echo "Tips:"
echo "  - Use Ctrl+C to stop the emulator"
echo "  - ADB will connect automatically"
echo "  - Test with: adb shell service list | grep agent"
echo ""

# Launch emulator with verbose output
emulator -verbose -show-kernel -gpu swiftshader_indirect &
EMU_PID=$!

# Wait for boot
echo "Waiting for device..."
adb wait-for-device

echo "Waiting for boot to complete..."
while [[ "$(adb shell getprop sys.boot_completed 2>/dev/null)" != "1" ]]; do
    sleep 2
done

echo ""
echo "=== Emulator booted ==="
echo ""

# Verify service
echo "Checking AgentSystemService..."
if adb shell service list | grep -q agent; then
    echo "✓ AgentSystemService is running"
else
    echo "✗ AgentSystemService not found"
    echo "Check logcat for errors:"
    echo "  adb logcat -s AgentSystemService"
fi

echo ""
echo "Emulator is running (PID: $EMU_PID)"
echo "Press Ctrl+C to stop"

# Wait for emulator process
wait $EMU_PID
