# device.mk for AGI-Android OS ARM64 Emulator
#
# This defines the device-level configurations for Android Emulator on Apple Silicon

# IMPORTANT: Set before inherit-product so runtime_libart.mk uses this value
PRODUCT_ART_TARGET_INCLUDE_DEBUG_BUILD := false

# Inherit from sdk_phone_arm64 base (produces proper emulator images with -qemu.img variants)
$(call inherit-product, $(SRC_TARGET_DIR)/product/sdk_phone_arm64.mk)

# Clear inherited HOST_PACKAGES that require timezone APEX modules
PRODUCT_HOST_PACKAGES :=

# Enable boot image preopt to generate boot-image.prof for ART APEX
ENABLE_PREOPT_BOOT_IMAGES := true

# Use release ART APEX instead of debug
PRODUCT_PACKAGES := $(filter-out com.android.art.debug,$(PRODUCT_PACKAGES))
PRODUCT_PACKAGES += com.android.art

# Device name
PRODUCT_NAME := agi_os_arm64
PRODUCT_DEVICE := agi_arm64
PRODUCT_BRAND := agi
PRODUCT_MODEL := AGI-Android OS
PRODUCT_MANUFACTURER := AGI

# AGI-specific packages
# AgentServiceApp - priv-app that hosts the agent service (starts on boot)
# agi-os-sdk - client SDK library for apps
PRODUCT_PACKAGES += \
    AgentServiceApp \
    agi-os-sdk

# System properties
PRODUCT_SYSTEM_PROPERTIES += \
    ro.agi.version=1.0.0 \
    ro.agi.sdk.version=1 \
    persist.agi.debug=false

# GSI-specific settings
PRODUCT_SYSTEM_EXT_PROPERTIES += \
    ro.system.build.type=userdebug

# Enable ADB by default for development
PRODUCT_SYSTEM_DEFAULT_PROPERTIES += \
    ro.adb.secure=0 \
    persist.sys.usb.config=mtp,adb

# Required for virtual display support
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.software.companion_device_setup.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/permissions/android.software.companion_device_setup.xml

# Build type
PRODUCT_BUILD_PROP_OVERRIDES += \
    BUILD_DISPLAY_ID="AGI-Android-OS-1.0.0" \
    BUILD_VERSION_TAGS=release-keys
