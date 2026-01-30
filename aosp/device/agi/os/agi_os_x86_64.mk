# agi_os_x86_64.mk
# Product configuration for AGI-Android OS (x86_64 for emulator)

# Inherit from generic x86_64
$(call inherit-product, $(SRC_TARGET_DIR)/product/generic_system.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit_only.mk)

# For emulator
$(call inherit-product, $(SRC_TARGET_DIR)/product/sdk_phone_x86_64.mk)

# Product identification
PRODUCT_NAME := agi_os_x86_64
PRODUCT_DEVICE := generic_x86_64
PRODUCT_BRAND := agi
PRODUCT_MODEL := AGI-Android OS (Emulator)
PRODUCT_MANUFACTURER := AGI

# AGI-specific packages
PRODUCT_PACKAGES += \
    AgentSystemService \
    agi-os-sdk

# System properties
PRODUCT_SYSTEM_PROPERTIES += \
    ro.agi.version=1.0.0 \
    ro.agi.sdk.version=1 \
    persist.agi.debug=true

# Enable ADB
PRODUCT_SYSTEM_DEFAULT_PROPERTIES += \
    ro.adb.secure=0 \
    persist.sys.usb.config=adb
