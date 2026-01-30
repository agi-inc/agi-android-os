# macOS Development Setup

AOSP requires a case-sensitive filesystem. macOS uses case-insensitive by default, so we use a sparse disk image.

## Setup (Already Done)

The case-sensitive volume has been created and configured:

```
~/aosp.sparseimage   - 500GB sparse disk image (case-sensitive APFS)
/Volumes/aosp        - Mount point when attached
~/aosp               - Symlink to /Volumes/aosp for convenience
```

## Daily Usage

### Mount the Volume

Before working on AOSP:

```bash
hdiutil attach ~/aosp.sparseimage
```

This mounts at `/Volumes/aosp`. The `~/aosp` symlink will work automatically.

### Unmount When Done

```bash
hdiutil detach /Volumes/aosp
```

Or eject from Finder.

### Check If Mounted

```bash
mount | grep aosp
# or
ls /Volumes/aosp
```

## Auto-Mount (Optional)

To auto-mount on login, add to Login Items in System Preferences, or create a launch agent:

```bash
cat > ~/Library/LaunchAgents/com.user.aosp-mount.plist << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.user.aosp-mount</string>
    <key>ProgramArguments</key>
    <array>
        <string>/usr/bin/hdiutil</string>
        <string>attach</string>
        <string>/Users/jacob/aosp.sparseimage</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
</dict>
</plist>
EOF

launchctl load ~/Library/LaunchAgents/com.user.aosp-mount.plist
```

## Disk Space

The sparse image only uses actual space consumed:

```bash
# Check actual disk usage
du -sh ~/aosp.sparseimage

# Check logical size (what AOSP sees)
du -sh /Volumes/aosp
```

Initial: ~100GB after sync
After build: ~300GB

## Troubleshooting

### "Resource busy" on detach

```bash
# Find what's using it
lsof /Volumes/aosp

# Force detach
hdiutil detach /Volumes/aosp -force
```

### Symlink broken after reboot

The volume isn't mounted yet. Run:
```bash
hdiutil attach ~/aosp.sparseimage
```

### Need more space

Resize the sparse image:
```bash
hdiutil resize -size 600g ~/aosp.sparseimage
```

## Build Commands

All build commands work normally via the `~/aosp` symlink:

```bash
cd ~/aosp
source build/envsetup.sh
lunch agi_os_arm64-userdebug
m -j$(sysctl -n hw.ncpu)
```

## Why Case-Sensitive?

AOSP has both:
- `build/` directory (Android build system)
- `BUILD` file (Bazel build file)

On case-insensitive macOS, these conflict. The case-sensitive volume treats them as separate.
