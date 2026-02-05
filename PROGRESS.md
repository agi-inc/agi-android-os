# Progress Tracker

This document tracks the implementation progress of AGI-Android OS.

## Current Status: BUILD COMPLETE ✅

**ARM64 emulator build completed successfully. Ready for testing on real device.**

### Build Artifacts

Located at `artifacts-arm64-emu/`:
| File | Size | Purpose |
|------|------|---------|
| system-qemu.img | 8.1 GB | Main system image (QEMU format) |
| vendor-qemu.img | 141 MB | Vendor HAL |
| ramdisk.img | 1.6 MB | Initial ramdisk |
| userdata.img | 550 MB | User data partition |
| kernel-ranchu | 21 MB | Emulator kernel |
| AgentServiceApp.apk | 3.4 MB | Privileged service app |
| agi-os-aidl.jar | 1.6 MB | AIDL interfaces |

### Validated Components

- ✅ AgentServiceApp in `/system/priv-app/`
- ✅ `ro.agi.version=1.0.0` in build.prop
- ✅ `ro.agi.sdk.version=1` in build.prop
- ✅ Emulator-format images (QEMU/GPT disk format)

### Completed ✅

#### Architecture & Design
- [x] Overall architecture designed (see `docs/big-picture.md`)
- [x] Session/Driver separation defined
- [x] AIDL interfaces defined
- [x] SDK API designed
- [x] Build integration planned

#### System Service (`system-service/`)
- [x] `AgentSystemService.kt` - Main service entry point
- [x] `SessionBinder.kt` - AIDL binder implementation
- [x] `Session.kt` - Session class with headless/physical modes
- [x] `SessionManager.kt` - Session lifecycle management
- [x] `VirtualDisplayManager.kt` - Virtual display creation
- [x] `InputInjector.kt` - Touch/key injection
- [x] `ScreenCapturer.kt` - Screenshot capture
- [x] `SystemExecutor.kt` - System operations (install, permissions, shell)

#### SDK (`sdk/`)
- [x] `AgentOS.kt` - Main SDK entry point
- [x] `Session.kt` - Session interface
- [x] `SessionImpl.kt` - Session implementation

#### AIDL (`aidl/`)
- [x] `IAgentService.aidl` - Service interface
- [x] `IAgentSession.aidl` - Session interface
- [x] `SessionConfig.aidl` - Config parcelable
- [x] `SessionConfig.kt` - Parcelable implementation

#### Build Integration (`aosp/`)
- [x] `AndroidProducts.mk` - Product definitions
- [x] `agi_os_arm64.mk` - ARM64 GSI config
- [x] `agi_os_x86_64.mk` - x86_64 emulator config
- [x] Framework patch for service registration

#### Tools (`tools/`)
- [x] `setup-build-env.sh` - Build environment setup
- [x] `build.sh` - Build script (native macOS - requires SDK 11/12)
- [x] `docker-build.sh` - Build script (Docker/Linux - recommended)
- [x] `Dockerfile` - AOSP builder Docker image
- [x] `flash.sh` - Flashing script
- [x] `test-emulator.sh` - Emulator testing
- [x] `agi-cli.sh` - Shell CLI tool

#### Build Patches (`aosp/patches/`)
- [x] `frameworks_base/0001-add-agent-system-service.patch` - Register AgentSystemService
- [x] `build_soong/0001-support-newer-macos-sdk-versions.patch` - Add macOS SDK 13-26 support

#### Documentation (`docs/`)
- [x] `README.md` - Project overview
- [x] `architecture.md` - Technical architecture
- [x] `building.md` - Build instructions
- [x] `flashing.md` - Flashing guide
- [x] `sdk-api.md` - SDK reference
- [x] `big-picture.md` - Vision and overview
- [x] `driver-integration.md` - Driver binary integration
- [x] `macos-setup.md` - Case-sensitive volume setup

#### macOS Development Environment
- [x] Case-sensitive disk image created (`~/aosp.sparseimage`, 500GB sparse)
- [x] Mounted at `/Volumes/aosp`
- [x] Symlinked from `~/aosp`
- [x] AOSP source synced (android-13.0.0_r83) - **104GB**

#### Driver Research
- [x] Explored `agi-api-driver` (jacob/agent-driver-module branch)
- [x] Understood JSON lines protocol (stdin/stdout)
- [x] Documented in `docs/driver-integration.md`

### Next Steps 📋

#### Phase 2: Testing (READY)

**Option A: Real ARM64 Device (Recommended)**
```bash
# Flash to Pixel 4+ or other Treble device
# See docs/flashing.md
```

**Option B: EC2 with KVM (Fast but costly)**
```bash
# Launch bare-metal EC2 instance (~$5/hr)
# Run x86_64 emulator with KVM acceleration
```

**Option C: Wait for slow ARM64 emulator**
- ARM64 on macOS without HVF takes 5-10 min to boot

Testing checklist:
- [ ] Verify AgentHostService starts on boot
- [ ] Test headless session creation
- [ ] Verify app launches on virtual display (not visible on screen)
- [ ] Test screenshot capture from virtual display
- [ ] Test input injection to virtual display
- [ ] Test multiple concurrent sessions

#### Phase 3: Driver Integration
- [ ] Add `linux-arm64` target to driver build workflow in `agi-api-driver`
- [ ] Build driver for Android
- [ ] Include driver binary in system image
- [ ] Create `DriverBridge.kt` in system service
- [ ] Wire driver events to session actions
- [ ] Test end-to-end: goal → screenshot → driver → action → execute

#### Phase 4: SDK & Testing
- [ ] Build SDK as .aar
- [ ] Create sample app using SDK
- [ ] Test parallel headless sessions
- [ ] Test physical display control
- [ ] Performance testing (screenshot rate, input latency)

#### Phase 5: GSI Release
- [ ] Build ARM64 GSI
- [ ] Test on real device (Pixel recommended)
- [ ] Document device-specific notes
- [ ] Create release workflow

## Key Decisions

### Session vs Agent Separation
Sessions are infrastructure - they provide display control. Agents (drivers) are intelligence - they decide what to do. This separation allows:
- Sessions without agents (testing, scripting)
- Multiple agents per session
- One agent across multiple sessions
- Different agent implementations (local driver, HTTP API)

### Driver Communication
Using JSON lines over stdio (not HTTP) for local driver because:
- Lower latency
- No network stack needed
- Simple process management
- Same protocol as desktop SDKs (C#, Node, Python)

### Virtual Display Implementation
Using Android's `VirtualDisplay` + `ImageReader` because:
- Native Android API
- Hardware-accelerated rendering
- Proper display targeting for input injection
- Same as Android Auto, Chromecast

## Related Work in Other Repos

### agi-api-driver (jacob/agent-driver-module branch)
The driver binary lives here. Key files:
- `src/agi_driver/` - Driver source (Python, compiled with Nuitka)
- `src/agi_driver/README.md` - Protocol documentation
- `.github/workflows/build-agi-driver.yml` - Build workflow

**Driver Protocol** (JSON lines over stdin/stdout):
```jsonl
← {"event":"ready","version":"0.1.0","step":0}
→ {"command":"start","goal":"...","screenshot":"base64...","screen_width":1080,"screen_height":1920}
← {"event":"action","action":{"type":"click","x":500,"y":750},"step":1}
→ {"command":"screenshot","data":"base64..."}
← {"event":"finished","success":true,"summary":"Done","step":10}
```

To add Android support, need to:
1. Add `linux-arm64` target to build matrix in `.github/workflows/build-agi-driver.yml`
2. Install ARM64 cross-compiler in workflow
3. Build with Nuitka for ARM64

### agi-android (existing app)
The legacy approach using accessibility services. This OS replaces that approach with system-level access.

## Commands Reference

```bash
# Mount AOSP volume (after reboot)
hdiutil attach ~/aosp.sparseimage

# Build using Docker (RECOMMENDED for macOS Sequoia+)
cd ~/Code/agi-android-os
./tools/docker-build.sh

# Build natively (requires macOS SDK 11 or 12)
TARGET=agi_os_x86_64-userdebug ./tools/build.sh  # for emulator
# OR
TARGET=agi_os_arm64-userdebug ./tools/build.sh   # for real device

# Test in emulator
./tools/test-emulator.sh

# Check if AgentSystemService is running (after boot)
adb shell service list | grep agent

# View project structure
find ~/Code/agi-android-os -name "*.kt" -o -name "*.aidl" | head -20
```

## Resuming This Work

**Current state: AOSP synced (104GB). macOS SDK incompatibility requires Docker build.**

1. Mount volume if needed: `hdiutil attach ~/aosp.sparseimage`
2. Build using Docker: `./tools/docker-build.sh`
3. Alternative: Use a Linux VM or cloud build service

### Known Issues

1. **macOS SDK 26.1 incompatibility**: AOSP Android 13 doesn't support `_Float16` type in newer macOS SDKs. Use Docker build instead.
2. **Missing LICENSE files**: Some AOSP modules missing LICENSE files. Symlinks created during build.

## File Locations

| What | Where |
|------|-------|
| AGI-Android OS source | `~/Code/agi-android-os/` |
| AOSP source | `/Volumes/aosp/` (symlink: `~/aosp`) |
| Case-sensitive volume | `~/aosp.sparseimage` |
| Driver source | `~/Code/agi-api-driver/src/agi_driver/` |
| Sync log | `~/aosp-sync.log` |
