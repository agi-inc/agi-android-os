# Architecture

## Overview

AGI-Android OS extends Android 13 with a system-level service (`AgentSystemService`) that provides programmatic control over the device, including the ability to create headless virtual displays for parallel agent operation.

## Core Components

### 1. AgentSystemService

A system service that runs within `system_server` with full system privileges. This is the core of the infrastructure.

**Location**: `frameworks/base/services/core/java/com/agi/os/service/`

**Responsibilities**:
- Lifecycle management (starts on boot)
- Session creation/destruction
- Binder interface for SDK communication
- Coordination of all subsystems

**Key Code Path**:
```
SystemServer.startOtherServices()
  └─> AgentSystemService.onStart()
        └─> Register IAgentService binder
        └─> Initialize SessionManager
```

### 2. SessionManager

Manages all active agent sessions (both headless and physical display).

**Key Data Structure**:
```kotlin
data class Session(
    val id: String,                    // UUID
    val displayId: Int,                // Android display ID
    val virtualDisplay: VirtualDisplay?, // null if physical display
    val imageReader: ImageReader?,     // For screenshot capture
    val config: SessionConfig,
    val createdAt: Long,
    var foregroundApp: String?
)
```

**Session Types**:
1. **Headless Session**: Creates a VirtualDisplay that renders to an ImageReader surface
2. **Physical Session**: Controls display 0 (the actual screen)

**Concurrency**: Sessions are thread-safe. Multiple agents can operate different sessions in parallel.

### 3. VirtualDisplayManager

Creates and manages Android VirtualDisplay instances for headless sessions.

**How Virtual Displays Work**:

```
┌─────────────────────────────────────────────────────────┐
│                    VirtualDisplay                        │
│                                                          │
│  ┌─────────────────┐      ┌─────────────────────────┐  │
│  │ Display Surface │ ───> │ ImageReader             │  │
│  │ (apps render    │      │ (captures frames as     │  │
│  │  here)          │      │  Bitmap/ByteArray)      │  │
│  └─────────────────┘      └─────────────────────────┘  │
│                                                          │
│  display_id = N (assigned by DisplayManager)            │
│  Flags: VIRTUAL_DISPLAY_FLAG_PUBLIC                     │
│         VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY           │
└─────────────────────────────────────────────────────────┘
```

**Creation**:
```kotlin
fun createVirtualDisplay(config: SessionConfig): Pair<VirtualDisplay, ImageReader> {
    val imageReader = ImageReader.newInstance(
        config.width,
        config.height,
        PixelFormat.RGBA_8888,
        2  // maxImages - double buffer
    )

    val virtualDisplay = displayManager.createVirtualDisplay(
        "agi-session-${UUID.randomUUID()}",
        config.width,
        config.height,
        config.dpi,
        imageReader.surface,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
    )

    return Pair(virtualDisplay, imageReader)
}
```

**Display Flags Explained**:
- `VIRTUAL_DISPLAY_FLAG_PUBLIC`: Apps can be launched on this display
- `VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY`: Content from this display can't be mirrored elsewhere
- NOT using `VIRTUAL_DISPLAY_FLAG_SECURE`: Allows screenshot capture

### 4. ScreenCapturer

Captures screenshots from any display (virtual or physical).

**For Virtual Displays** (headless sessions):
```kotlin
fun captureVirtualDisplay(imageReader: ImageReader): ByteArray {
    val image = imageReader.acquireLatestImage()
    val plane = image.planes[0]
    val buffer = plane.buffer
    val bitmap = Bitmap.createBitmap(
        image.width, image.height, Bitmap.Config.ARGB_8888
    )
    bitmap.copyPixelsFromBuffer(buffer)
    image.close()

    // Encode to PNG
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    return stream.toByteArray()
}
```

**For Physical Display** (display 0):
```kotlin
fun capturePhysicalDisplay(): ByteArray {
    // Use SurfaceControl.screenshot (system API)
    val displayToken = SurfaceControl.getPhysicalDisplayToken(
        SurfaceControl.getPhysicalDisplayIds()[0]
    )
    val bitmap = SurfaceControl.screenshot(displayToken)
    // ... encode to PNG
}
```

### 5. InputInjector

Injects touch and key events to specific displays.

**Key Implementation**:
```kotlin
class InputInjector(private val context: Context) {

    private val inputManager: InputManager =
        context.getSystemService(InputManager::class.java)

    fun injectTap(displayId: Int, x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()

        // DOWN event
        val downEvent = MotionEvent.obtain(
            downTime, downTime,
            MotionEvent.ACTION_DOWN,
            x, y, 0
        ).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        injectEvent(downEvent, displayId)

        // UP event
        val upEvent = MotionEvent.obtain(
            downTime, downTime + 50,
            MotionEvent.ACTION_UP,
            x, y, 0
        ).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        injectEvent(upEvent, displayId)
    }

    fun injectDrag(
        displayId: Int,
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        durationMs: Long = 300
    ) {
        val downTime = SystemClock.uptimeMillis()
        val steps = 20
        val stepDelay = durationMs / steps

        // DOWN
        injectEvent(createTouchEvent(
            downTime, downTime, MotionEvent.ACTION_DOWN, startX, startY
        ), displayId)

        // MOVE events
        for (i in 1..steps) {
            val progress = i.toFloat() / steps
            val x = startX + (endX - startX) * progress
            val y = startY + (endY - startY) * progress
            val eventTime = downTime + (i * stepDelay)

            injectEvent(createTouchEvent(
                downTime, eventTime, MotionEvent.ACTION_MOVE, x, y
            ), displayId)

            Thread.sleep(stepDelay)
        }

        // UP
        injectEvent(createTouchEvent(
            downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, endX, endY
        ), displayId)
    }

    fun injectKeyEvent(displayId: Int, keyCode: Int) {
        val downTime = SystemClock.uptimeMillis()

        val downEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0)
        val upEvent = KeyEvent(downTime, downTime + 50, KeyEvent.ACTION_UP, keyCode, 0)

        injectEvent(downEvent, displayId)
        injectEvent(upEvent, displayId)
    }

    fun injectText(displayId: Int, text: String) {
        // Use InputConnection or iterate characters as key events
        // For complex text, use the InputMethodManager approach
        for (char in text) {
            // Send as key event if printable ASCII, else use IME
            injectCharacter(displayId, char)
        }
    }

    private fun injectEvent(event: InputEvent, displayId: Int) {
        // Hidden API - requires system privileges
        // InputManager.injectInputEvent(event, displayId, INJECT_INPUT_EVENT_MODE_ASYNC)
        val method = InputManager::class.java.getMethod(
            "injectInputEvent",
            InputEvent::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        method.invoke(inputManager, event, displayId, 0) // 0 = ASYNC mode
    }
}
```

**Input Event Display Targeting**:
- Android's InputManager can target specific displays
- System services have permission to inject to any display
- Each virtual display has its own input pipeline

### 6. SystemExecutor

Executes system-level operations that require elevated privileges.

```kotlin
class SystemExecutor(private val context: Context) {

    private val packageManager = context.packageManager
    private val packageInstaller = packageManager.packageInstaller

    fun installApk(apkPath: String): Boolean {
        val apkFile = File(apkPath)
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )

        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        session.openWrite("base.apk", 0, apkFile.length()).use { out ->
            apkFile.inputStream().use { input ->
                input.copyTo(out)
            }
            session.fsync(out)
        }

        val intent = Intent(context, InstallResultReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, sessionId, intent, PendingIntent.FLAG_MUTABLE
        )

        session.commit(pendingIntent.intentSender)
        return true // Async - result comes via broadcast
    }

    fun uninstallApp(packageName: String): Boolean {
        val intent = Intent(context, UninstallResultReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_MUTABLE
        )
        packageInstaller.uninstall(packageName, pendingIntent.intentSender)
        return true
    }

    fun grantPermission(packageName: String, permission: String) {
        // Requires GRANT_RUNTIME_PERMISSIONS (system only)
        packageManager.grantRuntimePermission(packageName, permission, Process.myUserHandle())
    }

    fun revokePermission(packageName: String, permission: String) {
        packageManager.revokeRuntimePermission(packageName, permission, Process.myUserHandle())
    }

    fun executeShell(command: String): String {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        val output = process.inputStream.bufferedReader().readText()
        val error = process.errorStream.bufferedReader().readText()
        process.waitFor()
        return if (error.isNotEmpty()) "ERROR: $error" else output
    }

    fun setSystemSetting(namespace: String, key: String, value: String) {
        when (namespace) {
            "system" -> Settings.System.putString(context.contentResolver, key, value)
            "secure" -> Settings.Secure.putString(context.contentResolver, key, value)
            "global" -> Settings.Global.putString(context.contentResolver, key, value)
        }
    }

    fun getInstalledPackages(): List<String> {
        return packageManager.getInstalledApplications(0).map { it.packageName }
    }
}
```

## AIDL Interface

The SDK communicates with AgentSystemService via AIDL (Android Interface Definition Language).

**IAgentService.aidl**:
```aidl
package com.agi.os;

import com.agi.os.IAgentSession;
import com.agi.os.SessionConfig;

interface IAgentService {
    // Session management
    IAgentSession createSession(in SessionConfig config);
    void destroySession(String sessionId);
    List<String> listSessions();
    IAgentSession getSession(String sessionId);

    // Physical display control
    IAgentSession controlPrimaryDisplay();
    void releasePrimaryDisplay();

    // System operations (not session-specific)
    List<String> getInstalledPackages();
    String executeShell(String command);
}
```

**IAgentSession.aidl**:
```aidl
package com.agi.os;

interface IAgentSession {
    String getId();
    int getDisplayId();

    // Screen
    byte[] captureScreen();
    int[] getScreenSize(); // [width, height]

    // Input
    void click(float x, float y);
    void longPress(float x, float y);
    void doubleClick(float x, float y);
    void drag(float startX, float startY, float endX, float endY, long durationMs);
    void type(String text);
    void pressKey(int keyCode);
    void pressHome();
    void pressBack();
    void pressRecents();

    // App control
    void launchApp(String packageName);
    void launchActivity(String packageName, String activityName);
    String getCurrentApp();
    void killApp(String packageName);

    // System (operates on device, not session-specific but accessed via session)
    boolean installApk(String path);
    boolean uninstallApp(String packageName);
    void grantPermission(String packageName, String permission);
    void revokePermission(String packageName, String permission);
    void setSystemSetting(String namespace, String key, String value);
}
```

## App Launching on Virtual Displays

Critical for headless sessions: apps must be launched on the correct display.

```kotlin
fun launchAppOnDisplay(packageName: String, displayId: Int) {
    val intent = packageManager.getLaunchIntentForPackage(packageName)
        ?: throw IllegalArgumentException("Package not found: $packageName")

    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    val options = ActivityOptions.makeBasic()
    options.launchDisplayId = displayId

    context.startActivity(intent, options.toBundle())
}

fun launchActivityOnDisplay(
    packageName: String,
    activityName: String,
    displayId: Int,
    extras: Bundle? = null
) {
    val intent = Intent().apply {
        component = ComponentName(packageName, activityName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        extras?.let { putExtras(it) }
    }

    val options = ActivityOptions.makeBasic()
    options.launchDisplayId = displayId

    context.startActivity(intent, options.toBundle())
}
```

**Display Affinity**: Once an app is launched on a display, its activities typically stay on that display unless explicitly moved.

## Coordinate Systems

Important for input injection and screenshot interpretation.

```
┌─────────────────────────────────────────┐
│ Virtual Display (1080 x 1920)           │
│                                          │
│  (0,0) ─────────────────────── (1080,0) │
│    │                               │     │
│    │                               │     │
│    │         App renders           │     │
│    │           here                │     │
│    │                               │     │
│    │                               │     │
│  (0,1920) ───────────────── (1080,1920) │
└─────────────────────────────────────────┘

Coordinates are in pixels, origin at top-left.
Same coordinate system used for:
  - Screenshot (PNG pixel coordinates)
  - Input injection (touch event coordinates)
```

## Boot Sequence

```
1. Kernel boots
2. init starts zygote
3. zygote forks system_server
4. SystemServer.main()
   └─> startBootstrapServices()
   └─> startCoreServices()
   └─> startOtherServices()
         └─> AgentSystemService.Lifecycle registered
               └─> onStart() called
                     └─> publishBinderService("agent", mService)
                     └─> SessionManager initialized
5. System ready
6. AgentSystemService ready for SDK connections
```

## Security Model

For this phase: **Full system access, no restrictions**.

The AgentSystemService runs with system UID and has access to:
- All hidden/system APIs
- All permissions (inject events, install apps, etc.)
- All displays
- All files (system has root-equivalent file access)

Future considerations:
- Per-session permission scoping
- Rate limiting
- Audit logging
- Network access control

## Performance Considerations

### Screenshot Capture
- ImageReader with double-buffering (maxImages=2)
- PNG encoding is CPU-intensive; consider JPEG for faster capture
- For high-frequency capture, use raw RGBA and encode on client

### Input Injection
- Events are asynchronous (MODE_ASYNC)
- Drag gestures should have ~15ms between MOVE events for smooth animation
- Text input via key events is slow; consider clipboard paste for long text

### Virtual Displays
- Each virtual display consumes GPU memory (framebuffer)
- Recommended: Max 5-10 concurrent sessions depending on device RAM
- Destroy unused sessions promptly

### Memory
- Screenshots are large (1080x1920 RGBA = ~8MB raw, ~500KB-2MB PNG)
- ImageReader holds up to maxImages frames in memory
- Release Image objects promptly after capture

## Thread Model

```
┌─────────────────────────────────────────────────────────┐
│ system_server process                                   │
│                                                          │
│  ┌────────────────────────────────────────────────────┐ │
│  │ Binder thread pool (handles SDK calls)             │ │
│  │ - createSession(), captureScreen(), click(), etc.  │ │
│  └────────────────────────────────────────────────────┘ │
│                         │                                │
│                         ▼                                │
│  ┌────────────────────────────────────────────────────┐ │
│  │ AgentSystemService                                 │ │
│  │ - SessionManager (synchronized)                    │ │
│  │ - Operations execute on caller's binder thread     │ │
│  └────────────────────────────────────────────────────┘ │
│                                                          │
│  ┌────────────────────────────────────────────────────┐ │
│  │ ImageReader callback threads (one per session)     │ │
│  │ - Notified when new frame available                │ │
│  └────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

All SessionManager operations are synchronized. Individual session operations are thread-safe.
