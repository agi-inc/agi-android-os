# Flashing AGI-Android OS

This guide covers flashing the GSI to compatible devices.

## Prerequisites

- **Built GSI**: `system.img` from the build process
- **Unlocked bootloader**: Device must have unlocked bootloader
- **Project Treble support**: Device must support GSI (most devices from 2018+)
- **ADB/Fastboot**: Platform tools installed
- **USB cable**: Good quality data cable

## Check Device Compatibility

### Verify Treble Support

```bash
# Connect device with USB debugging enabled
adb shell getprop ro.treble.enabled
# Should return: true

# Check system-as-root
adb shell getprop ro.build.system_root_image
# Should return: true (for A/B devices)

# Check architecture
adb shell getprop ro.product.cpu.abi
# Should return: arm64-v8a (most common)
```

### Check Partition Type

```bash
# A/B device check
adb shell getprop ro.build.ab_update
# true = A/B partition scheme

# Or check for _a/_b suffixes
adb shell ls -la /dev/block/by-name/ | grep system
```

## Step 1: Unlock Bootloader

**WARNING**: Unlocking bootloader wipes all data.

### Generic Method

```bash
# Enable OEM unlocking in Developer Options first

# Reboot to bootloader
adb reboot bootloader

# Unlock
fastboot flashing unlock
# Or on older devices:
fastboot oem unlock

# Follow on-screen instructions
```

### Device-Specific

| Device | Unlock Method |
|--------|---------------|
| Google Pixel | `fastboot flashing unlock` |
| OnePlus | `fastboot oem unlock` |
| Samsung | Use official unlock tool |
| Xiaomi | Requires Mi Unlock tool |

## Step 2: Disable Verified Boot (Required for GSI)

```bash
# Boot to fastboot
adb reboot bootloader

# Disable verity (required for GSI)
fastboot flash vbmeta vbmeta.img --disable-verity --disable-verification

# Or use a blank vbmeta
fastboot flash vbmeta vbmeta_disabled.img
```

If you don't have a vbmeta image, create a blank one:
```bash
# Create empty vbmeta that disables verification
dd if=/dev/zero of=vbmeta_disabled.img bs=256 count=64
```

## Step 3: Flash the GSI

### For A/B Devices

```bash
# Reboot to fastbootd (not bootloader)
adb reboot fastboot
# Or from bootloader:
fastboot reboot fastboot

# Erase system partition
fastboot erase system

# Flash GSI
fastboot flash system system.img

# Wipe data (required for clean install)
fastboot -w

# Reboot
fastboot reboot
```

### For A-Only Devices

```bash
# Reboot to bootloader
adb reboot bootloader

# Flash directly
fastboot flash system system.img

# Wipe data
fastboot -w

# Reboot
fastboot reboot
```

## Step 4: First Boot

First boot after flashing GSI takes longer (5-10 minutes). The device will:

1. Optimize apps (ART compilation)
2. Initialize system services
3. Start AgentSystemService

Watch for boot completion:
```bash
adb wait-for-device
adb shell getprop sys.boot_completed
# Returns 1 when fully booted
```

## Step 5: Verify AgentSystemService

```bash
# Check if service is running
adb shell service list | grep agent
# Should show: agent: [com.agi.os.IAgentService]

# Check service status
adb shell dumpsys agent
```

## Troubleshooting

### Device Won't Boot

1. **Bootloop**: GSI may be incompatible with device's vendor partition
   - Try a different AOSP branch GSI
   - Check device-specific forums for compatibility

2. **Stuck on logo**: Wait 10+ minutes on first boot

3. **Recovery mode loop**:
   ```bash
   fastboot flash boot stock_boot.img  # Restore stock boot
   ```

### "Cannot load Android system"

This usually means:
- vbmeta not properly disabled
- GSI incompatible with vendor

Fix:
```bash
adb reboot bootloader
fastboot flash vbmeta vbmeta.img --disable-verity --disable-verification
fastboot reboot
```

### Service Not Starting

```bash
# Check logs
adb logcat -s AgentSystemService

# Check if service is registered
adb shell service check agent

# Verify the APK/service is installed
adb shell pm list packages | grep agi
```

### Poor Performance

Some devices may have vendor-specific optimizations missing with GSI:
- Camera may not work (vendor HAL incompatibility)
- GPU performance may be reduced
- Some sensors may not work

For agent automation, these rarely matter.

## Device-Specific Notes

### Google Pixel (4, 5, 6, 7 series)

Best supported devices for GSI development.

```bash
adb reboot bootloader
fastboot flash vbmeta vbmeta.img --disable-verity --disable-verification
fastboot reboot fastboot
fastboot flash system system.img
fastboot -w
fastboot reboot
```

### Samsung Galaxy

- Requires Odin for some operations
- May need to disable Knox
- Use HOME_CSC for firmware to preserve data

### OnePlus

Generally well-supported:
```bash
adb reboot bootloader
fastboot oem unlock
fastboot flash vbmeta vbmeta.img --disable-verity
fastboot flash system system.img
fastboot -w
fastboot reboot
```

### Xiaomi

- Requires Mi Unlock (account + waiting period)
- Good Treble support on newer devices

## Reverting to Stock

To restore original firmware:

1. Download stock firmware for your device
2. Flash using device-specific tool (Odin, MiFlash, etc.)
3. Or use fastboot to flash all partitions

## OTA Updates

With custom GSI, OTA updates from manufacturer won't work. To update:

1. Build new GSI with changes
2. Flash updated `system.img`
3. Data can typically be preserved (no `-w` flag)

## Automated Flashing

Use the provided script:

```bash
./tools/flash.sh [device_serial]
```

This script:
1. Detects device type (A/B vs A-only)
2. Disables vbmeta verification
3. Flashes GSI
4. Optionally wipes data
5. Reboots and waits for boot completion

## Multiple Devices

For flashing multiple devices:

```bash
# List connected devices
adb devices

# Flash specific device
ANDROID_SERIAL=DEVICE_SERIAL ./tools/flash.sh
```

## Security Notes

- Unlocked bootloader = device is less secure
- Anyone with physical access can flash arbitrary images
- For production, consider:
  - Re-locking bootloader (requires signed images)
  - Using secure boot with your own keys
  - Device attestation configuration
