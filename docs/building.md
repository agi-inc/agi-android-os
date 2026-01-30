# Building AGI-Android OS

This guide covers setting up the AOSP build environment and building the GSI.

## Prerequisites

### Hardware Requirements
- **Disk**: 400GB+ free space (AOSP source is ~100GB, build artifacts ~200GB)
- **RAM**: 32GB minimum, 64GB recommended
- **CPU**: Modern multi-core (build times scale linearly with cores)

### Software Requirements
- **OS**: Ubuntu 20.04 or 22.04 LTS (recommended), or macOS
- **Python**: 3.8+
- **Git**: 2.30+
- **Java**: OpenJDK 11

## Step 1: Install Dependencies

### Ubuntu

```bash
sudo apt-get update
sudo apt-get install -y \
    git-core gnupg flex bison build-essential zip curl zlib1g-dev \
    libc6-dev-i386 libncurses5 lib32ncurses5-dev x11proto-core-dev \
    libx11-dev lib32z1-dev libgl1-mesa-dev libxml2-utils xsltproc \
    unzip fontconfig python3 python3-pip openjdk-11-jdk \
    libssl-dev bc rsync ccache

# Set Java 11 as default
sudo update-alternatives --set java /usr/lib/jvm/java-11-openjdk-amd64/bin/java
```

### macOS

```bash
# Install Xcode Command Line Tools
xcode-select --install

# Install Homebrew packages
brew install git gnupg coreutils findutils gnu-sed gnu-tar \
    python@3 openjdk@11 ccache

# Add GNU tools to PATH (in ~/.zshrc or ~/.bashrc)
export PATH="/usr/local/opt/coreutils/libexec/gnubin:$PATH"
export PATH="/usr/local/opt/findutils/libexec/gnubin:$PATH"
export PATH="/usr/local/opt/gnu-sed/libexec/gnubin:$PATH"
export PATH="/usr/local/opt/gnu-tar/libexec/gnubin:$PATH"
```

## Step 2: Install Repo Tool

```bash
mkdir -p ~/.bin
curl https://storage.googleapis.com/git-repo-downloads/repo > ~/.bin/repo
chmod a+x ~/.bin/repo

# Add to PATH (in ~/.bashrc or ~/.zshrc)
export PATH="$HOME/.bin:$PATH"
```

## Step 3: Configure Git

```bash
git config --global user.name "Your Name"
git config --global user.email "your@email.com"
```

## Step 4: Download AOSP Source

```bash
# Create working directory
mkdir -p ~/aosp
cd ~/aosp

# Initialize repo with Android 13 (API 33) branch
repo init -u https://android.googlesource.com/platform/manifest -b android-13.0.0_r83

# Sync (this takes a long time - 2-6 hours depending on connection)
repo sync -c -j$(nproc) --force-sync --no-clone-bundle --no-tags

# After sync, you'll have ~100GB of source code
```

### Alternative: Download Specific GSI Branch

For GSI-specific builds:

```bash
repo init -u https://android.googlesource.com/platform/manifest \
    -b android13-gsi \
    --depth=1

repo sync -c -j$(nproc)
```

## Step 5: Apply AGI-Android OS Patches

```bash
# From your agi-android-os repo
cd ~/Code/agi-android-os

# Copy device config
cp -r aosp/device/agi ~/aosp/device/

# Apply patches to frameworks/base
cd ~/aosp/frameworks/base
git apply ~/Code/agi-android-os/aosp/patches/frameworks_base/*.patch

# Copy system service source
cp -r ~/Code/agi-android-os/system-service ~/aosp/packages/services/AgentService

# Copy SDK source
cp -r ~/Code/agi-android-os/sdk ~/aosp/frameworks/AgentSDK

# Copy AIDL definitions
cp -r ~/Code/agi-android-os/aidl/* ~/aosp/frameworks/base/core/java/
```

## Step 6: Set Up Build Environment

```bash
cd ~/aosp

# Source the build environment
source build/envsetup.sh

# Select the build target
lunch agi_os_arm64-userdebug

# For x86_64 emulator testing:
# lunch agi_os_x86_64-userdebug
```

### Build Variants

| Variant | Description |
|---------|-------------|
| `agi_os_arm64-userdebug` | ARM64 GSI with debug enabled |
| `agi_os_arm64-user` | ARM64 GSI release build |
| `agi_os_x86_64-userdebug` | x86_64 for emulator testing |

## Step 7: Build

```bash
# Full build (first time takes 2-8 hours)
m -j$(nproc)

# Or build just the GSI system image
m -j$(nproc) systemimage

# Output will be in:
# out/target/product/agi_os_arm64/system.img
```

### Build Tips

**Enable ccache** (speeds up rebuilds):
```bash
export USE_CCACHE=1
export CCACHE_DIR=~/.ccache
ccache -M 100G
```

**Incremental builds**: After changes, just run `m` again - it only rebuilds changed components.

**Clean build** (if needed):
```bash
make clean
# or for a full clean:
make clobber
```

## Step 8: Build Output

After successful build, you'll have:

```
out/target/product/agi_os_arm64/
├── system.img          # The GSI system image (what you flash)
├── system-qemu.img     # For emulator
├── vbmeta.img          # Verified boot metadata
├── boot.img            # Boot image (if building full ROM)
└── ...
```

The main file for GSI flashing is `system.img`.

## Building Individual Components

### Just the AgentSystemService

```bash
cd ~/aosp
source build/envsetup.sh
lunch agi_os_arm64-userdebug

# Build just the service
mmm packages/services/AgentService
```

### Just the SDK

```bash
mmm frameworks/AgentSDK
```

## Testing in Emulator

Before flashing to a real device, test in the Android emulator:

```bash
# Build for x86_64
lunch agi_os_x86_64-userdebug
m -j$(nproc)

# Run emulator
emulator -verbose -show-kernel
```

Or use the provided script:
```bash
./tools/test-emulator.sh
```

## Troubleshooting

### "No rule to make target"
- Run `source build/envsetup.sh` and `lunch` again
- Check that all patches were applied correctly

### Out of memory during build
- Reduce parallel jobs: `m -j4` instead of `m -j$(nproc)`
- Add swap space
- Close other applications

### Java version errors
- Ensure OpenJDK 11 is set as default
- Run: `java -version` to verify

### Patch conflicts
- If patches don't apply cleanly, manually apply changes
- Check that you're on the correct AOSP branch

## Automated Build Script

Use the provided build script:

```bash
cd ~/Code/agi-android-os
./tools/build.sh
```

This script:
1. Checks dependencies
2. Applies patches
3. Sets up environment
4. Runs the build
5. Copies output to `out/` directory

## CI/CD Considerations

For automated builds:

```yaml
# Example GitHub Actions workflow
name: Build GSI

on:
  push:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-22.04
    steps:
      - uses: actions/checkout@v3

      - name: Set up build environment
        run: ./tools/setup-build-env.sh

      - name: Cache AOSP
        uses: actions/cache@v3
        with:
          path: ~/aosp
          key: aosp-android-13

      - name: Build
        run: ./tools/build.sh

      - name: Upload artifact
        uses: actions/upload-artifact@v3
        with:
          name: system.img
          path: out/system.img
```

Note: Full AOSP builds in CI require large runners (400GB+ disk, 64GB+ RAM).

## Next Steps

After building, see [flashing.md](flashing.md) for instructions on flashing to a device.
