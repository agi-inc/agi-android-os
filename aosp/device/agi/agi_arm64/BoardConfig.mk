# BoardConfig.mk for AGI-Android OS ARM64 Emulator
#
# This must be included during the board configuration phase

# Inherit from emulator arm64 board config (produces QEMU-compatible images)
include device/generic/goldfish/arm64-kernel.mk
include device/generic/goldfish/board/kernel.mk
include build/make/target/board/BoardConfigGsiCommon.mk
include build/make/target/board/BoardConfigEmuCommon.mk

# ARM64 specific settings
TARGET_ARCH := arm64
TARGET_ARCH_VARIANT := armv8-a
TARGET_CPU_ABI := arm64-v8a
TARGET_CPU_VARIANT := generic

# Secondary arch (32-bit support)
TARGET_2ND_ARCH := arm
TARGET_2ND_ARCH_VARIANT := armv8-a
TARGET_2ND_CPU_ABI := armeabi-v7a
TARGET_2ND_CPU_ABI2 := armeabi
TARGET_2ND_CPU_VARIANT := generic

# Allow missing required modules
BUILD_BROKEN_MISSING_REQUIRED_MODULES := true

# Enable dexpreopt for boot images (required for ART APEX boot-image.prof)
WITH_DEXPREOPT := true
WITH_DEXPREOPT_BOOT_IMG_AND_SYSTEM_SERVER_ONLY := true
