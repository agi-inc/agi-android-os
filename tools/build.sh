#!/bin/bash
#
# build.sh
# Build AGI-Android OS GSI
#
# This script:
# 1. Applies patches to AOSP
# 2. Copies AGI components into AOSP tree
# 3. Builds the GSI
# 4. Copies output to out/ directory

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(dirname "$SCRIPT_DIR")"
AOSP_DIR="${AOSP_DIR:-$HOME/aosp}"
TARGET="${TARGET:-agi_os_arm64-userdebug}"
# Get CPU count (macOS uses sysctl, Linux uses nproc)
if [[ "$(uname)" == "Darwin" ]]; then
    JOBS="${JOBS:-$(sysctl -n hw.ncpu)}"
else
    JOBS="${JOBS:-$(nproc)}"
fi

echo "=== AGI-Android OS Build ==="
echo "AOSP directory: $AOSP_DIR"
echo "Target: $TARGET"
echo "Jobs: $JOBS"
echo ""

# Check AOSP exists
if [[ ! -d "$AOSP_DIR/build" ]]; then
    echo "Error: AOSP source not found at $AOSP_DIR"
    echo "Run ./tools/setup-build-env.sh first"
    exit 1
fi

# Apply patches and copy components
echo "Applying AGI components to AOSP..."

# Copy device configuration
echo "  - Device configuration"
rm -rf "$AOSP_DIR/device/agi/os"
mkdir -p "$AOSP_DIR/device/agi"
cp -r "$REPO_DIR/aosp/device/agi/os" "$AOSP_DIR/device/agi/"

# Copy system service
echo "  - AgentSystemService"
rm -rf "$AOSP_DIR/packages/services/AgentService"
mkdir -p "$AOSP_DIR/packages/services"
cp -r "$REPO_DIR/system-service" "$AOSP_DIR/packages/services/AgentService"

# Copy SDK
echo "  - AGI-OS SDK"
rm -rf "$AOSP_DIR/frameworks/AgentSDK"
mkdir -p "$AOSP_DIR/frameworks"
cp -r "$REPO_DIR/sdk" "$AOSP_DIR/frameworks/AgentSDK"
# Copy AIDL files alongside SDK for local reference
cp -r "$REPO_DIR/aidl" "$AOSP_DIR/frameworks/AgentSDK/"

# Copy AIDL to system service too
echo "  - AIDL definitions"
cp -r "$REPO_DIR/aidl" "$AOSP_DIR/packages/services/AgentService/"

# Apply framework patches (if any)
if [[ -d "$REPO_DIR/aosp/patches/frameworks_base" ]]; then
    echo "  - Framework patches"
    cd "$AOSP_DIR/frameworks/base"
    for patch in "$REPO_DIR/aosp/patches/frameworks_base"/*.patch; do
        if [[ -f "$patch" ]]; then
            echo "    Applying: $(basename "$patch")"
            git apply "$patch" 2>/dev/null || echo "    (already applied or conflict)"
        fi
    done
fi

# Apply build/soong patches (macOS SDK version support)
if [[ -d "$REPO_DIR/aosp/patches/build_soong" ]]; then
    echo "  - Build system patches"
    cd "$AOSP_DIR/build/soong"
    for patch in "$REPO_DIR/aosp/patches/build_soong"/*.patch; do
        if [[ -f "$patch" ]]; then
            echo "    Applying: $(basename "$patch")"
            git apply "$patch" 2>/dev/null || echo "    (already applied or conflict)"
        fi
    done
fi

# Set up build environment
echo ""
echo "Setting up build environment..."
cd "$AOSP_DIR"
source build/envsetup.sh

# Select target
echo "Selecting target: $TARGET"
lunch "$TARGET"

# Enable ccache
export USE_CCACHE=1
export CCACHE_DIR="${CCACHE_DIR:-$HOME/.ccache}"
if command -v ccache &> /dev/null; then
    ccache -M 100G 2>/dev/null || true
fi

# Build
echo ""
echo "Building (this will take a while)..."
echo "Started at: $(date)"
echo ""

m -j"$JOBS"

echo ""
echo "Build completed at: $(date)"

# Copy output
echo ""
echo "Copying build output..."
mkdir -p "$REPO_DIR/out"

OUT_DIR="$AOSP_DIR/out/target/product"
PRODUCT_DIR=""

# Find the product output directory
for dir in "$OUT_DIR"/*/; do
    if [[ -f "${dir}system.img" ]]; then
        PRODUCT_DIR="$dir"
        break
    fi
done

if [[ -n "$PRODUCT_DIR" ]]; then
    cp "$PRODUCT_DIR/system.img" "$REPO_DIR/out/"

    if [[ -f "$PRODUCT_DIR/vbmeta.img" ]]; then
        cp "$PRODUCT_DIR/vbmeta.img" "$REPO_DIR/out/"
    fi

    echo ""
    echo "=== Build successful ==="
    echo ""
    echo "Output files:"
    ls -lh "$REPO_DIR/out/"
    echo ""
    echo "To flash:"
    echo "  ./tools/flash.sh"
    echo ""
    echo "To test in emulator:"
    echo "  ./tools/test-emulator.sh"
else
    echo "Warning: Could not find system.img in build output"
    echo "Check $OUT_DIR for output"
fi
