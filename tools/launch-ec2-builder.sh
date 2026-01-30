#!/bin/bash
# Launch EC2 instance for AGI-Android OS build
#
# Prerequisites:
#   aws sso login --profile sso
#
# Usage:
#   ./tools/launch-ec2-builder.sh
#
# This will:
#   1. Launch an m6i.4xlarge EC2 instance (16 vCPU, 64GB RAM)
#   2. Attach a 600GB gp3 EBS volume
#   3. Run the setup script
#   4. Output connection details

set -e

PROFILE="${AWS_PROFILE:-sso}"
INSTANCE_TYPE="m6i.4xlarge"
AMI_ID="ami-0c7217cdde317cfec"  # Ubuntu 20.04 LTS us-east-1
KEY_NAME="${KEY_NAME:-aosp-builder}"
VOLUME_SIZE=600
SECURITY_GROUP="aosp-builder-sg"

echo "=== Launching AGI-Android OS Build Server ==="
echo "Instance type: $INSTANCE_TYPE (16 vCPU, 64GB RAM)"
echo "Storage: ${VOLUME_SIZE}GB gp3"
echo ""

# Check AWS credentials
echo "Checking AWS credentials..."
if ! aws sts get-caller-identity --profile "$PROFILE" > /dev/null 2>&1; then
    echo "ERROR: AWS credentials not valid. Run:"
    echo "  aws sso login --profile $PROFILE"
    exit 1
fi

# Check if security group exists, create if not
echo "Checking security group..."
SG_ID=$(aws ec2 describe-security-groups --profile "$PROFILE" \
    --filters "Name=group-name,Values=$SECURITY_GROUP" \
    --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || echo "None")

if [ "$SG_ID" == "None" ] || [ -z "$SG_ID" ]; then
    echo "Creating security group..."
    SG_ID=$(aws ec2 create-security-group --profile "$PROFILE" \
        --group-name "$SECURITY_GROUP" \
        --description "Security group for AOSP builder" \
        --query 'GroupId' --output text)

    # Allow SSH
    aws ec2 authorize-security-group-ingress --profile "$PROFILE" \
        --group-id "$SG_ID" \
        --protocol tcp --port 22 --cidr 0.0.0.0/0
    echo "Created security group: $SG_ID"
else
    echo "Using existing security group: $SG_ID"
fi

# Check if key pair exists
echo "Checking key pair..."
if ! aws ec2 describe-key-pairs --profile "$PROFILE" --key-names "$KEY_NAME" > /dev/null 2>&1; then
    echo "Creating key pair..."
    aws ec2 create-key-pair --profile "$PROFILE" \
        --key-name "$KEY_NAME" \
        --query 'KeyMaterial' --output text > ~/.ssh/${KEY_NAME}.pem
    chmod 600 ~/.ssh/${KEY_NAME}.pem
    echo "Created key pair: ~/.ssh/${KEY_NAME}.pem"
fi

# User data script for setup
USER_DATA=$(cat << 'USERDATA'
#!/bin/bash
set -e

# Update and install dependencies
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y \
    git-core gnupg flex bison build-essential zip curl \
    zlib1g-dev gcc-multilib g++-multilib libc6-dev-i386 \
    libncurses5 lib32ncurses5-dev x11proto-core-dev libx11-dev \
    lib32z1-dev libgl1-mesa-dev libxml2-utils xsltproc unzip \
    fontconfig openjdk-11-jdk rsync bc ccache lz4 wget pv \
    python3 python3-pip jq

# Install repo tool
curl https://storage.googleapis.com/git-repo-downloads/repo -o /usr/local/bin/repo
chmod a+x /usr/local/bin/repo

# Setup directories
mkdir -p /aosp /ccache
chown -R ubuntu:ubuntu /aosp /ccache

# Configure ccache
su - ubuntu -c "ccache -M 50G"

# Git config
su - ubuntu -c 'git config --global user.email "build@agi.inc"'
su - ubuntu -c 'git config --global user.name "AGI Builder"'
su - ubuntu -c 'git config --global color.ui false'

# Signal ready
touch /tmp/setup-complete
echo "Setup complete! Ready for AOSP build."
USERDATA
)

# Launch instance
echo "Launching EC2 instance..."
INSTANCE_ID=$(aws ec2 run-instances --profile "$PROFILE" \
    --image-id "$AMI_ID" \
    --instance-type "$INSTANCE_TYPE" \
    --key-name "$KEY_NAME" \
    --security-group-ids "$SG_ID" \
    --block-device-mappings "DeviceName=/dev/sda1,Ebs={VolumeSize=$VOLUME_SIZE,VolumeType=gp3,DeleteOnTermination=true}" \
    --user-data "$USER_DATA" \
    --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=agi-android-os-builder}]" \
    --query 'Instances[0].InstanceId' --output text)

echo "Instance ID: $INSTANCE_ID"
echo "Waiting for instance to be running..."

aws ec2 wait instance-running --profile "$PROFILE" --instance-ids "$INSTANCE_ID"

# Get public IP
PUBLIC_IP=$(aws ec2 describe-instances --profile "$PROFILE" \
    --instance-ids "$INSTANCE_ID" \
    --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)

echo ""
echo "=== Instance Launched Successfully! ==="
echo ""
echo "Instance ID: $INSTANCE_ID"
echo "Public IP: $PUBLIC_IP"
echo ""
echo "Connect with:"
echo "  ssh -i ~/.ssh/${KEY_NAME}.pem ubuntu@$PUBLIC_IP"
echo ""
echo "Wait ~5 minutes for setup to complete, then check:"
echo "  ssh -i ~/.ssh/${KEY_NAME}.pem ubuntu@$PUBLIC_IP 'ls /tmp/setup-complete'"
echo ""
echo "To start the AOSP build:"
echo "  1. Clone the repo: git clone https://github.com/agi-inc/agi-android-os.git"
echo "  2. Copy build script: scp tools/build-on-ec2.sh ubuntu@$PUBLIC_IP:~/"
echo "  3. Run build: ssh ubuntu@$PUBLIC_IP 'nohup ./build-on-ec2.sh > build.log 2>&1 &'"
echo ""
echo "Estimated cost: ~\$0.77/hour"
echo "Don't forget to terminate when done:"
echo "  aws ec2 terminate-instances --profile $PROFILE --instance-ids $INSTANCE_ID"
