# device.mk for AGI-Android OS x86_64 device
#
# This builds for x86_64 to avoid cross-compilation dex2oat issues in Modal

# IMPORTANT: Set before inherit-product so runtime_libart.mk uses this value
# Attempt 20: Skip debug ART APEX (it requires boot-image.prof which fails on eng builds)
PRODUCT_ART_TARGET_INCLUDE_DEBUG_BUILD := false

# Inherit from aosp_x86_64 base (avoids cross-compilation)
$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_x86_64.mk)

# Clear inherited HOST_PACKAGES that require timezone APEX modules
PRODUCT_HOST_PACKAGES :=

# Attempt 22: Enable boot image preopt to generate boot-image.prof for ART APEX
# The ART APEX requires boot-image.prof which is only generated when boot images are preopted.
# Setting ENABLE_PREOPT_BOOT_IMAGES=true enables this while keeping WITH_DEXPREOPT=false for apps.
ENABLE_PREOPT_BOOT_IMAGES := true

# Remove debug ART APEX (we use release APEX instead)
PRODUCT_PACKAGES := $(filter-out com.android.art.debug,$(PRODUCT_PACKAGES))
PRODUCT_PACKAGES += com.android.art

# Device name
PRODUCT_NAME := agi_os_x86_64
PRODUCT_DEVICE := agi_x86_64
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
