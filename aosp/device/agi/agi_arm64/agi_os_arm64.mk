# device.mk for AGI-Android OS ARM64 device
#
# This defines the device-level configurations

# Inherit from aosp_arm64 base (simpler than GSI, better for emulator testing)
$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_arm64.mk)

# Clear inherited HOST_PACKAGES that require timezone APEX modules
# (these cause build failures in Android 13 due to missing host tools)
PRODUCT_HOST_PACKAGES :=

# Device name
PRODUCT_NAME := agi_os_arm64
PRODUCT_DEVICE := agi_arm64
PRODUCT_BRAND := agi
PRODUCT_MODEL := AGI-Android OS
PRODUCT_MANUFACTURER := AGI

# AGI-specific packages
PRODUCT_PACKAGES += \
    AgentSystemService \
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
