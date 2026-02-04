# BoardConfig.mk for AGI-Android OS x86_64
#
# Building for x86_64 avoids cross-compilation dex2oat issues

# Inherit from generic x86_64 board config
include build/make/target/board/generic_x86_64/BoardConfig.mk

# Allow missing required modules (needed for Modal build environment)
BUILD_BROKEN_MISSING_REQUIRED_MODULES := true

# Attempt 26: Enable dexpreopt for boot images (required for ART APEX boot-image.prof)
# Only preopt boot images and system server, skip apps for faster eng builds
WITH_DEXPREOPT := true
WITH_DEXPREOPT_BOOT_IMG_AND_SYSTEM_SERVER_ONLY := true
