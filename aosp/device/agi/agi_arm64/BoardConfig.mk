# BoardConfig.mk for AGI-Android OS ARM64
#
# This must be included during the board configuration phase

# Inherit from generic arm64 GSI board config
include build/make/target/board/generic_arm64/BoardConfig.mk

# Allow missing required modules
BUILD_BROKEN_MISSING_REQUIRED_MODULES := true

# Enable dexpreopt for boot images (required for ART APEX boot-image.prof)
WITH_DEXPREOPT := true
WITH_DEXPREOPT_BOOT_IMG_AND_SYSTEM_SERVER_ONLY := true
