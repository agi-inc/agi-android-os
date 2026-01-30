# AndroidProducts.mk
# Defines available build targets for AGI-Android OS

PRODUCT_MAKEFILES := \
    $(LOCAL_DIR)/agi_os_arm64.mk \
    $(LOCAL_DIR)/agi_os_x86_64.mk

COMMON_LUNCH_CHOICES := \
    agi_os_arm64-userdebug \
    agi_os_arm64-user \
    agi_os_x86_64-userdebug
