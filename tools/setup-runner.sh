#!/bin/bash
# Setup script for AGI-Android OS build runner
#
# Run this on a fresh Ubuntu 20.04/22.04 EC2 instance:
#   - Instance type: m6i.4xlarge (16 vCPU, 64GB RAM)
#   - Storage: 600GB gp3 EBS volume
#   - AMI: Ubuntu 20.04 LTS
#
# Usage:
#   curl -sSL https://raw.githubusercontent.com/agi-inc/agi-android-os/main/tools/setup-runner.sh | bash
#
# After running this script:
#   1. Configure GitHub Actions runner (see instructions printed at end)
#   2. Start the runner: cd actions-runner && ./run.sh

set -e

echo "=== AGI-Android OS Build Runner Setup ==="
echo ""

# Update system
echo "Updating system..."
sudo apt-get update
sudo DEBIAN_FRONTEND=noninteractive apt-get upgrade -y

# Install AOSP build dependencies
echo "Installing AOSP build dependencies..."
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y \
  git-core gnupg flex bison build-essential zip curl \
  zlib1g-dev gcc-multilib g++-multilib libc6-dev-i386 \
  libncurses5 lib32ncurses5-dev x11proto-core-dev libx11-dev \
  lib32z1-dev libgl1-mesa-dev libxml2-utils xsltproc unzip \
  fontconfig openjdk-11-jdk rsync bc ccache lz4 wget pv \
  python3 python3-pip jq

# Install repo tool
echo "Installing repo tool..."
sudo curl https://storage.googleapis.com/git-repo-downloads/repo -o /usr/local/bin/repo
sudo chmod a+x /usr/local/bin/repo

# Configure git
echo "Configuring git..."
git config --global user.email "build@agi.inc"
git config --global user.name "AGI Builder"
git config --global color.ui false

# Setup directories
echo "Setting up directories..."
sudo mkdir -p /aosp /ccache
sudo chown -R $USER:$USER /aosp /ccache

# Configure ccache
echo "Configuring ccache..."
ccache -M 50G

# Set environment variables
echo "Setting environment variables..."
cat >> ~/.bashrc << 'EOF'

# AGI-Android OS build environment
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
export USE_CCACHE=1
export CCACHE_DIR=/ccache
export CCACHE_MAXSIZE=50G
EOF

source ~/.bashrc

# Download GitHub Actions runner
echo "Downloading GitHub Actions runner..."
mkdir -p ~/actions-runner && cd ~/actions-runner
curl -o actions-runner-linux-x64-2.321.0.tar.gz -L https://github.com/actions/runner/releases/download/v2.321.0/actions-runner-linux-x64-2.321.0.tar.gz
tar xzf ./actions-runner-linux-x64-2.321.0.tar.gz

echo ""
echo "=== Setup Complete! ==="
echo ""
echo "Next steps:"
echo ""
echo "1. Go to your GitHub repo: https://github.com/YOUR_ORG/agi-android-os/settings/actions/runners/new"
echo ""
echo "2. Copy the configuration command (starts with ./config.sh ...)"
echo ""
echo "3. Run the configuration in ~/actions-runner:"
echo "   cd ~/actions-runner"
echo "   ./config.sh --url https://github.com/YOUR_ORG/agi-android-os --token YOUR_TOKEN --labels aosp-builder"
echo ""
echo "4. Start the runner:"
echo "   ./run.sh"
echo ""
echo "   Or install as service:"
echo "   sudo ./svc.sh install"
echo "   sudo ./svc.sh start"
echo ""
echo "5. Trigger the build workflow from GitHub Actions"
echo ""
