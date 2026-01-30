# BoardConfig.mk for AGI-Android OS x86_64
#
# Building for x86_64 avoids cross-compilation dex2oat issues

# Inherit from generic x86_64 board config
include build/make/target/board/generic_x86_64/BoardConfig.mk

# Allow missing required modules (needed for Modal build environment)
BUILD_BROKEN_MISSING_REQUIRED_MODULES := true

# Attempt 16: Minimal config - just disable dexpreopt for eng build
# PRODUCT_* variables are readonly - set via environment in build script
WITH_DEXPREOPT := false
DONT_DEXPREOPT_PREBUILTS := true
