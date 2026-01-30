#!/bin/bash
# Build AGI-Android OS on EC2 instance
#
# Run this script on the EC2 instance after setup completes:
#   nohup ./build-on-ec2.sh > build.log 2>&1 &
#
# Monitor progress:
#   tail -f build.log

set -e

AOSP_BRANCH="android-13.0.0_r83"
TARGET="${TARGET:-agi_os_x86_64-eng}"
AOSP_DIR="/aosp"

echo "=== AGI-Android OS Build Script ==="
echo "Target: $TARGET"
echo "AOSP Branch: $AOSP_BRANCH"
echo "Started: $(date)"
echo ""

# Check disk space
echo "=== Disk Space ==="
df -h /aosp
echo ""

# Phase 1: Clone AGI-Android OS code
echo "=== Phase 1: Cloning AGI-Android OS ==="
if [ ! -d ~/agi-android-os ]; then
    git clone https://github.com/agi-inc/agi-android-os.git ~/agi-android-os
else
    cd ~/agi-android-os && git pull
fi

# Phase 2: Sync AOSP
echo "=== Phase 2: Syncing AOSP $AOSP_BRANCH ==="
cd $AOSP_DIR

if [ ! -d ".repo" ]; then
    echo "Initializing repo..."
    repo init \
        -u https://android.googlesource.com/platform/manifest \
        -b $AOSP_BRANCH \
        --depth=1
fi

echo "Syncing AOSP (this takes ~2 hours)..."
for i in 1 2 3; do
    echo "Sync attempt $i..."
    repo sync -j$(nproc) -c --no-tags --no-clone-bundle --optimized-fetch && break
    echo "Sync failed, retrying in 30s..."
    sleep 30
done

if [ ! -f "build/envsetup.sh" ]; then
    echo "ERROR: AOSP sync failed - build/envsetup.sh not found"
    exit 1
fi

echo "AOSP sync complete!"
df -h /aosp

# Phase 3: Apply AGI components
echo "=== Phase 3: Applying AGI Components ==="

# Device configuration
echo "Copying device config..."
mkdir -p device/agi
cp -r ~/agi-android-os/aosp/device/agi/* device/agi/

# AgentSystemService
echo "Copying AgentSystemService..."
mkdir -p packages/services
cp -r ~/agi-android-os/system-service packages/services/AgentService

# AGI-OS SDK
echo "Copying AGI-OS SDK..."
mkdir -p frameworks
cp -r ~/agi-android-os/sdk frameworks/AgentSDK

# AIDL interfaces
if [ -d ~/agi-android-os/aidl ]; then
    echo "Copying AIDL interfaces..."
    cp -r ~/agi-android-os/aidl/* packages/services/AgentService/
    cp -r ~/agi-android-os/aidl/* frameworks/AgentSDK/
fi

# Fix LICENSE files
echo "Fixing LICENSE files..."
for path in "external/kotlinx.coroutines:LICENSE.txt" "external/kotlinc:license/LICENSE.txt"; do
    dir="${path%%:*}"
    src="${path##*:}"
    if [ -f "$dir/$src" ] && [ ! -f "$dir/LICENSE" ]; then
        cp "$dir/$src" "$dir/LICENSE"
        echo "  Fixed $dir/LICENSE"
    fi
done

echo "AGI components applied!"

# Phase 4: Build
echo "=== Phase 4: Building $TARGET ==="
echo "Build started at $(date)"

source build/envsetup.sh
lunch $TARGET

# Configure for eng build
export WITH_DEXPREOPT=false
export DONT_DEXPREOPT_PREBUILTS=true
export ART_BUILD_HOST_DEBUG=false
export SKIP_BOOT_JARS_CHECK=true

# Enable ccache
export USE_CCACHE=1
export CCACHE_DIR=/ccache
export CCACHE_MAXSIZE=50G

# Build
echo "Starting make with $(nproc) cores..."
m -j$(nproc) BUILD_BROKEN_MISSING_REQUIRED_MODULES=true

echo "Build completed at $(date)"
df -h /aosp

# Phase 5: Collect artifacts
echo "=== Phase 5: Collecting Artifacts ==="

OUT_DIR=$(find out/target/product -maxdepth 1 -type d -name "agi_*" | head -1)

if [ -z "$OUT_DIR" ]; then
    echo "ERROR: No output directory found"
    exit 1
fi

echo "Output directory: $OUT_DIR"

mkdir -p ~/artifacts
for img in system.img vbmeta.img boot.img vendor.img userdata.img; do
    if [ -f "$OUT_DIR/$img" ]; then
        echo "Copying $img..."
        cp "$OUT_DIR/$img" ~/artifacts/
    fi
done

echo ""
echo "=== Build Complete! ==="
echo "Artifacts in ~/artifacts/:"
ls -lh ~/artifacts/
echo ""
echo "To download artifacts to local machine:"
echo "  scp -i ~/.ssh/aosp-builder.pem ubuntu@<IP>:~/artifacts/* ./"
