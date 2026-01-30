# Dockerfile for building AGI-Android OS (AOSP)
# Based on Ubuntu 20.04 which is supported by AOSP Android 13
#
# Uses x86_64 platform for AOSP prebuilt compatibility
# On Apple Silicon, runs via Rosetta 2 emulation

FROM --platform=linux/amd64 ubuntu:20.04

ENV DEBIAN_FRONTEND=noninteractive

# Install AOSP build dependencies
RUN apt-get update && apt-get install -y \
    git-core \
    gnupg \
    flex \
    bison \
    build-essential \
    zip \
    curl \
    zlib1g-dev \
    gcc-multilib \
    g++-multilib \
    libc6-dev-i386 \
    libncurses5 \
    lib32ncurses5-dev \
    x11proto-core-dev \
    libx11-dev \
    lib32z1-dev \
    libgl1-mesa-dev \
    libxml2-utils \
    xsltproc \
    unzip \
    fontconfig \
    python3 \
    python3-pip \
    openjdk-11-jdk \
    rsync \
    bc \
    ccache \
    lz4 \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Set up ccache
ENV USE_CCACHE=1
ENV CCACHE_DIR=/aosp/.ccache
RUN ccache -M 50G

# Set up Python
RUN update-alternatives --install /usr/bin/python python /usr/bin/python3 1

# Set up Java
ENV JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
ENV PATH=$JAVA_HOME/bin:$PATH

# Working directory
WORKDIR /aosp

# Default command
CMD ["bash"]
