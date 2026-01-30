# Progress Tracker

This document tracks the implementation progress of AGI-Android OS.

## Current Status: Ready to Build

**AOSP source synced (104GB). Ready to apply patches and build.**

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
- [x] `build.sh` - Build script
- [x] `flash.sh` - Flashing script
- [x] `test-emulator.sh` - Emulator testing
- [x] `agi-cli.sh` - Shell CLI tool

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

#### Phase 2: Build & Test (READY TO START)
```bash
# Apply AGI-Android OS to AOSP
cd ~/Code/agi-android-os
./tools/build.sh

# Or manually:
cd ~/aosp
cp -r ~/Code/agi-android-os/aosp/device/agi device/
cp -r ~/Code/agi-android-os/system-service packages/services/AgentService
cp -r ~/Code/agi-android-os/sdk frameworks/AgentSDK
cp -r ~/Code/agi-android-os/aidl/com/agi frameworks/base/core/java/com/
cd frameworks/base && git apply ~/Code/agi-android-os/aosp/patches/frameworks_base/*.patch

# Build for emulator first
source build/envsetup.sh
lunch agi_os_x86_64-userdebug
m -j$(sysctl -n hw.ncpu)
```

- [ ] Apply patches to AOSP source
- [ ] Build x86_64 for emulator testing
- [ ] Test AgentSystemService startup
- [ ] Verify virtual display creation
- [ ] Test input injection
- [ ] Test screenshot capture

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

# Build
cd ~/aosp
source build/envsetup.sh
lunch agi_os_x86_64-userdebug  # for emulator
# OR
lunch agi_os_arm64-userdebug   # for real device
m -j$(sysctl -n hw.ncpu)

# Test in emulator
cd ~/Code/agi-android-os && ./tools/test-emulator.sh

# Check if AgentSystemService is running (after boot)
adb shell service list | grep agent

# View project structure
find ~/Code/agi-android-os -name "*.kt" -o -name "*.aidl" | head -20
```

## Resuming This Work

**Current state: AOSP synced (104GB), ready to build.**

1. Mount volume if needed: `hdiutil attach ~/aosp.sparseimage`
2. Apply patches: `~/Code/agi-android-os/tools/build.sh`
3. Or manually apply and build (see Phase 2 steps above)

## File Locations

| What | Where |
|------|-------|
| AGI-Android OS source | `~/Code/agi-android-os/` |
| AOSP source | `/Volumes/aosp/` (symlink: `~/aosp`) |
| Case-sensitive volume | `~/aosp.sparseimage` |
| Driver source | `~/Code/agi-api-driver/src/agi_driver/` |
| Sync log | `~/aosp-sync.log` |
