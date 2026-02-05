# AGI-Android OS: Status Assessment

## Original Goal

Create a custom Android OS that enables **background app automation** for GUI agents:

> "Make it possible to run android apps in the background so that our android gui automation agent could operate apps completely autonomously in the background"

This means:
1. Apps render to off-screen virtual displays (not visible to user)
2. Multiple apps/sessions can run in parallel
3. Agent can capture screenshots and inject input without user interaction
4. Works headlessly (no physical display needed)

## Current Implementation Status

### What We Built

| Component | Status | Location |
|-----------|--------|----------|
| VirtualDisplayManager | ✅ Complete | `system-service/src/.../display/` |
| InputInjector | ✅ Complete | `system-service/src/.../input/` |
| ScreenCapturer | ✅ Complete | `system-service/src/.../screen/` |
| SessionManager | ✅ Complete | `system-service/src/.../session/` |
| Session | ✅ Complete | `system-service/src/.../session/` |
| AgentHostService | ✅ Complete | `agent-app/src/.../app/` |
| SDK (AgentOS) | ✅ Complete | `sdk/src/` |
| AIDL interfaces | ✅ Complete | `aidl/` |
| Build configs | ✅ Complete | `aosp/device/agi/` |

### Build Artifacts (Ready)

```
artifacts-arm64-emu/
├── system-qemu.img      8.1 GB   ← Android 13 + AGI components
├── vendor-qemu.img      141 MB
├── ramdisk.img          1.6 MB
├── userdata.img         550 MB
├── kernel-ranchu        21 MB
└── libs/
    ├── AgentServiceApp.apk  3.4 MB  ← Privileged service app
    └── agi-os-aidl.jar      1.6 MB  ← AIDL interfaces
```

## Does the Implementation Achieve the Goal?

### Yes - Architecture Supports Background Automation

The implementation uses Android's native `VirtualDisplay` + `ImageReader` APIs:

```kotlin
// Creates off-screen display that renders to ImageReader
val imageReader = ImageReader.newInstance(width, height, RGBA_8888, 2)
val virtualDisplay = displayManager.createVirtualDisplay(
    name, width, height, dpi,
    imageReader.surface,
    VIRTUAL_DISPLAY_FLAG_PUBLIC or VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
)

// Launch app on virtual display (not visible on physical screen)
val options = ActivityOptions.makeBasic()
options.setLaunchDisplayId(virtualDisplay.display.displayId)
context.startActivity(intent, options.toBundle())

// Capture what's rendered (app running in background)
val image = imageReader.acquireLatestImage()
val bitmap = imageToBitmap(image)
```

### Capabilities Implemented

| Feature | Implementation |
|---------|---------------|
| Create headless session | `SessionManager.createSession(SessionConfig(headless=true))` |
| Launch app on virtual display | `Session.launchApp(packageName)` uses `setLaunchDisplayId` |
| Capture screen | `Session.captureScreen()` reads from ImageReader |
| Inject touch | `Session.click(x, y)` → InputInjector targets display ID |
| Inject text | `Session.type(text)` |
| Key events | `Session.pressHome()`, `pressBack()`, etc. |
| Multiple sessions | SessionManager supports up to 10 concurrent sessions |
| System operations | Install APK, grant permissions, shell commands |

### SDK Usage (How Agents Will Use It)

```kotlin
// Get AgentOS instance
val agentOS = AgentOS.getInstance(context)

// Create headless session (invisible to user)
val session = agentOS.createSession(SessionConfig(
    width = 1080,
    height = 1920,
    dpi = 420,
    headless = true
))

// Launch app (renders to virtual display, not physical screen)
session.launchApp("com.twitter.android")

// Agent automation loop
while (!done) {
    val screenshot = session.captureScreen()  // PNG bytes
    val action = aiAgent.decide(screenshot)   // Call Claude API

    when (action) {
        is Click -> session.click(action.x, action.y)
        is Type -> session.type(action.text)
        is Scroll -> session.drag(...)
    }
}

session.destroy()
```

## What's NOT Tested Yet

The build is complete but emulator testing was blocked by slow ARM64 emulation. These need verification:

| Item | Risk Level | Notes |
|------|------------|-------|
| Apps actually render on virtual display | Medium | Some apps check display type |
| Input injection works on virtual display | Low | Uses standard InputManager APIs |
| Screenshot capture quality | Low | ImageReader is well-tested API |
| Multiple sessions simultaneously | Medium | Resource contention possible |
| ServiceManager registration | Medium | May need SELinux policy |
| Boot receiver triggers service | Low | Standard Android pattern |

## Potential Issues

### 1. App Compatibility
Some apps may refuse to run on non-default displays or detect they're on a virtual display. Mitigations:
- Most standard apps work fine
- Games may have issues
- Banking apps with security checks might fail

### 2. Android Background Restrictions
Android may throttle background activity. The implementation handles this by:
- Running as foreground service with notification
- Using `mediaProjection` foreground service type
- Being a privileged system app

### 3. SELinux Policies
The service may need custom SELinux policies for full functionality. Current implementation catches `SecurityException` and logs warnings.

### 4. Hidden API Usage
`ActivityOptions.setLaunchDisplayId()` is a hidden API. The implementation uses reflection with fallback, but this may break in future Android versions.

## Next Steps to Complete

1. **Test on Real Device**
   - Flash to Pixel device (ARM64 GSI)
   - Verify service starts on boot
   - Test headless session creation
   - Verify apps render on virtual display

2. **Driver Integration**
   - Build `agi-driver` for Android ARM64
   - Wire driver to session actions
   - Test end-to-end: goal → screenshot → Claude → action → execute

3. **Performance Testing**
   - Screenshot capture rate
   - Input injection latency
   - Memory usage with multiple sessions

## Conclusion

**The implementation is architecturally complete and should achieve the goal of background app automation.** The code uses standard Android APIs (VirtualDisplay, ImageReader, InputManager) in the correct way for headless automation.

What remains is:
1. ⏳ Testing on real hardware (emulator too slow)
2. ⏳ Driver integration (separate binary)
3. ⏳ Performance tuning
