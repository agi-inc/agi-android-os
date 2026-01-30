#!/bin/bash
#
# docker-build.sh
# Build AGI-Android OS in a Docker container (Linux environment)
#
# This avoids macOS SDK compatibility issues with AOSP
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(dirname "$SCRIPT_DIR")"
AOSP_DIR="${AOSP_DIR:-$HOME/aosp}"
# Default to ARM64 target on Apple Silicon (native, fast)
# For x86_64 emulator, use: TARGET=agi_os_x86_64-userdebug (slower, requires emulation)
if [[ "$(uname -m)" == "arm64" ]]; then
    TARGET="${TARGET:-agi_os_arm64-userdebug}"
else
    TARGET="${TARGET:-agi_os_x86_64-userdebug}"
fi
IMAGE_NAME="agi-aosp-builder"

echo "=== AGI-Android OS Docker Build ==="
echo "AOSP directory: $AOSP_DIR"
echo "Target: $TARGET"
echo ""

# Check Docker is running
if ! docker info >/dev/null 2>&1; then
    echo "Error: Docker is not running. Please start Docker Desktop."
    exit 1
fi

# Check AOSP directory exists
if [[ ! -d "$AOSP_DIR/build" ]]; then
    echo "Error: AOSP source not found at $AOSP_DIR"
    exit 1
fi

# Build Docker image if needed
if ! docker image inspect "$IMAGE_NAME" >/dev/null 2>&1; then
    echo "Building Docker image..."
    docker build -t "$IMAGE_NAME" -f "$REPO_DIR/tools/Dockerfile" "$REPO_DIR/tools"
fi

# Apply patches before Docker build
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
cp -r "$REPO_DIR/aidl" "$AOSP_DIR/packages/services/AgentService/"

# Copy SDK
echo "  - AGI-OS SDK"
rm -rf "$AOSP_DIR/frameworks/AgentSDK"
mkdir -p "$AOSP_DIR/frameworks"
cp -r "$REPO_DIR/sdk" "$AOSP_DIR/frameworks/AgentSDK"
cp -r "$REPO_DIR/aidl" "$AOSP_DIR/frameworks/AgentSDK/"

# Fix LICENSE symlinks for Docker (Linux won't see macOS symlinks properly)
echo "  - Fixing LICENSE files"
cp /Volumes/aosp/external/kotlinx.coroutines/LICENSE.txt /Volumes/aosp/external/kotlinx.coroutines/LICENSE 2>/dev/null || true
cp /Volumes/aosp/external/kotlinc/license/LICENSE.txt /Volumes/aosp/external/kotlinc/LICENSE 2>/dev/null || true

echo ""
echo "Starting Docker build..."
echo "This will take several hours on first build..."
echo ""

# Run build in Docker (x86_64 for AOSP prebuilts compatibility)
# --memory-swap=-1 allows unlimited swap to handle soong_build memory spikes
docker run --rm \
    --platform linux/amd64 \
    --memory-swap=-1 \
    -v "$AOSP_DIR":/aosp \
    -v "$REPO_DIR":/agi-android-os \
    -e TARGET="$TARGET" \
    -e JOBS="${JOBS:-4}" \
    -w /aosp \
    "$IMAGE_NAME" \
    bash -c '
        source build/envsetup.sh
        lunch $TARGET

        # Apply patches if not already applied
        cd /aosp/frameworks/base
        git apply /agi-android-os/aosp/patches/frameworks_base/*.patch 2>/dev/null || true

        cd /aosp
        m -j$JOBS
    '

echo ""
echo "Build complete. Copying output..."

# Copy output
mkdir -p "$REPO_DIR/out"
OUT_DIR="$AOSP_DIR/out/target/product"

for dir in "$OUT_DIR"/*/; do
    if [[ -f "${dir}system.img" ]]; then
        cp "${dir}system.img" "$REPO_DIR/out/"
        [[ -f "${dir}vbmeta.img" ]] && cp "${dir}vbmeta.img" "$REPO_DIR/out/"
        break
    fi
done

echo ""
echo "=== Build successful ==="
ls -lh "$REPO_DIR/out/"
