# device.mk for AGI-Android OS x86_64 device
#
# This builds for x86_64 to avoid cross-compilation dex2oat issues in Modal

# Inherit from aosp_x86_64 base (avoids cross-compilation)
$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_x86_64.mk)

# Clear inherited HOST_PACKAGES that require timezone APEX modules
PRODUCT_HOST_PACKAGES :=

# Attempt 17: Skip debug ART APEX (it requires boot-image.prof generation)
PRODUCT_ART_TARGET_INCLUDE_DEBUG_BUILD := false

# Device name
PRODUCT_NAME := agi_os_x86_64
PRODUCT_DEVICE := agi_x86_64
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
