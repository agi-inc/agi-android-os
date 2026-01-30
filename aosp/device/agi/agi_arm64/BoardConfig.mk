# BoardConfig.mk for AGI-Android OS
#
# This must be included during the board configuration phase

# Inherit from generic arm64 GSI board config
include build/make/target/board/generic_arm64/BoardConfig.mk

# Allow missing required modules (needed for Modal build environment)
# The timezone apex host modules are missing in this configuration
BUILD_BROKEN_MISSING_REQUIRED_MODULES := true
