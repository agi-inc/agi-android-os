#!/bin/bash
#
# setup-build-env.sh
# Set up AOSP build environment for AGI-Android OS
#
# This script:
# 1. Installs required dependencies (Ubuntu)
# 2. Downloads repo tool
# 3. Initializes AOSP source
# 4. Syncs the source code
#
# Run once on a fresh machine. Takes several hours.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(dirname "$SCRIPT_DIR")"
AOSP_DIR="${AOSP_DIR:-$HOME/aosp}"
AOSP_BRANCH="${AOSP_BRANCH:-android-13.0.0_r83}"

echo "=== AGI-Android OS Build Environment Setup ==="
echo "AOSP directory: $AOSP_DIR"
echo "AOSP branch: $AOSP_BRANCH"
echo ""

# Check OS
if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    echo "Detected Linux"

    # Install dependencies (Ubuntu/Debian)
    if command -v apt-get &> /dev/null; then
        echo "Installing dependencies via apt..."
        sudo apt-get update
        sudo apt-get install -y \
            git-core gnupg flex bison build-essential zip curl zlib1g-dev \
            libc6-dev-i386 libncurses5 lib32ncurses5-dev x11proto-core-dev \
            libx11-dev lib32z1-dev libgl1-mesa-dev libxml2-utils xsltproc \
            unzip fontconfig python3 python3-pip openjdk-11-jdk \
            libssl-dev bc rsync ccache
    else
        echo "Warning: Not a Debian-based system. Install dependencies manually."
        echo "See docs/building.md for requirements."
    fi

elif [[ "$OSTYPE" == "darwin"* ]]; then
    echo "Detected macOS"

    # Check for Homebrew
    if ! command -v brew &> /dev/null; then
        echo "Installing Homebrew..."
        /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    fi

    echo "Installing dependencies via Homebrew..."
    brew install git gnupg coreutils findutils gnu-sed gnu-tar \
        python@3 openjdk@11 ccache repo

    echo ""
    echo "Add to your shell profile:"
    echo '  export PATH="/usr/local/opt/coreutils/libexec/gnubin:$PATH"'
    echo '  export PATH="/usr/local/opt/findutils/libexec/gnubin:$PATH"'
    echo '  export PATH="/usr/local/opt/gnu-sed/libexec/gnubin:$PATH"'
    echo '  export PATH="/usr/local/opt/gnu-tar/libexec/gnubin:$PATH"'
else
    echo "Unsupported OS: $OSTYPE"
    exit 1
fi

# Install repo tool
echo ""
echo "Setting up repo tool..."
mkdir -p ~/.bin
if [[ ! -f ~/.bin/repo ]]; then
    curl -s https://storage.googleapis.com/git-repo-downloads/repo > ~/.bin/repo
    chmod a+x ~/.bin/repo
fi

# Add to PATH if not already
if [[ ":$PATH:" != *":$HOME/.bin:"* ]]; then
    export PATH="$HOME/.bin:$PATH"
    echo 'export PATH="$HOME/.bin:$PATH"' >> ~/.bashrc
fi

# Configure git if needed
if [[ -z "$(git config --global user.email)" ]]; then
    echo ""
    echo "Git not configured. Please set:"
    echo "  git config --global user.name 'Your Name'"
    echo "  git config --global user.email 'your@email.com'"
fi

# Initialize AOSP
echo ""
echo "Initializing AOSP source in $AOSP_DIR..."
mkdir -p "$AOSP_DIR"
cd "$AOSP_DIR"

if [[ ! -d .repo ]]; then
    ~/.bin/repo init -u https://android.googlesource.com/platform/manifest -b "$AOSP_BRANCH"
fi

# Sync (this takes a long time)
echo ""
echo "Syncing AOSP source (this will take several hours)..."
echo "You can run this in the background: nohup repo sync -c -j8 &"
echo ""
read -p "Start sync now? [y/N] " -n 1 -r
echo ""

if [[ $REPLY =~ ^[Yy]$ ]]; then
    ~/.bin/repo sync -c -j$(nproc) --force-sync --no-clone-bundle --no-tags

    echo ""
    echo "=== Sync complete ==="
    echo ""
    echo "Next steps:"
    echo "  1. cd $REPO_DIR"
    echo "  2. ./tools/build.sh"
else
    echo ""
    echo "To sync later, run:"
    echo "  cd $AOSP_DIR && repo sync -c -j8"
fi

echo ""
echo "=== Setup complete ==="
