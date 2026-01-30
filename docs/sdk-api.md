# SDK API Reference

The AGI-Android OS SDK provides programmatic access to virtual display sessions and system automation. It's a pure infrastructure API - what you build on top (agents, test frameworks, automation scripts) is up to you.

## Overview

The SDK enables:
- Creating headless virtual display sessions
- Capturing screenshots from any session
- Injecting input (taps, gestures, text, keys)
- Launching and controlling apps
- System-level operations (install, permissions, settings)

## Getting Started

### Add Dependency

```gradle
// In your app's build.gradle
dependencies {
    implementation 'com.agi.os:sdk:1.0.0'
}
```

Or if building within AOSP:
```
// Android.bp
static_libs: ["agi-os-sdk"],
```

### Basic Usage

```kotlin
import com.agi.os.sdk.AgentOS
import com.agi.os.sdk.SessionConfig

// Get the system service client
val agiOS = AgentOS.getInstance(context)

// Create a headless session
val session = agiOS.createSession(
    SessionConfig(
        width = 1080,
        height = 1920,
        dpi = 420,
        headless = true
    )
)

// Launch an app
session.launchApp("com.example.myapp")

// Interact
session.click(540f, 960f)
session.type("Hello world")

// Capture screenshot
val screenshot: ByteArray = session.captureScreen()

// Clean up
session.destroy()
```

## Core Classes

### AgentOS

Main entry point for the SDK.

```kotlin
object AgentOS {
    /**
     * Get the AgentOS instance.
     * @param context Android context
     * @return AgentOS instance
     * @throws SecurityException if not running on AGI-Android OS
     */
    fun getInstance(context: Context): AgentOS

    /**
     * Create a new session (headless or physical).
     */
    fun createSession(config: SessionConfig): Session

    /**
     * Get an existing session by ID.
     */
    fun getSession(sessionId: String): Session?

    /**
     * List all active sessions.
     */
    fun listSessions(): List<SessionInfo>

    /**
     * Take control of the physical display (display 0).
     * Only one controller at a time.
     */
    fun controlPrimaryDisplay(): Session

    /**
     * Release control of the physical display.
     */
    fun releasePrimaryDisplay()

    /**
     * Get list of installed packages on device.
     */
    fun getInstalledPackages(): List<String>

    /**
     * Execute a shell command with system privileges.
     */
    fun executeShell(command: String): ShellResult
}
```

### SessionConfig

Configuration for creating sessions.

```kotlin
data class SessionConfig(
    /**
     * Display width in pixels.
     * Default: 1080
     */
    val width: Int = 1080,

    /**
     * Display height in pixels.
     * Default: 1920
     */
    val height: Int = 1920,

    /**
     * Display density (DPI).
     * Default: 420
     */
    val dpi: Int = 420,

    /**
     * If true, creates a virtual display (off-screen).
     * If false, uses the physical display.
     * Default: true
     */
    val headless: Boolean = true
)
```

### Session

Represents an active session (virtual or physical display).

```kotlin
interface Session {
    /** Unique session identifier */
    val id: String

    /** Android display ID */
    val displayId: Int

    /** Session configuration */
    val config: SessionConfig

    /** Whether this is a headless (virtual) session */
    val isHeadless: Boolean

    // ========== Screen Operations ==========

    /**
     * Capture a screenshot of this session's display.
     * @return PNG-encoded image bytes
     */
    fun captureScreen(): ByteArray

    /**
     * Capture screenshot as raw RGBA bytes (faster, no encoding).
     * @return RGBA pixel data, row-major order
     */
    fun captureScreenRaw(): ByteArray

    /**
     * Get the screen dimensions.
     * @return Pair of (width, height)
     */
    fun getScreenSize(): Pair<Int, Int>

    // ========== Input Operations ==========

    /**
     * Tap at coordinates.
     * @param x X coordinate in pixels
     * @param y Y coordinate in pixels
     */
    fun click(x: Float, y: Float)

    /**
     * Long press at coordinates.
     * @param x X coordinate
     * @param y Y coordinate
     * @param durationMs Duration in milliseconds (default: 500)
     */
    fun longPress(x: Float, y: Float, durationMs: Long = 500)

    /**
     * Double tap at coordinates.
     */
    fun doubleClick(x: Float, y: Float)

    /**
     * Drag/swipe gesture.
     * @param startX Start X
     * @param startY Start Y
     * @param endX End X
     * @param endY End Y
     * @param durationMs Gesture duration (default: 300)
     */
    fun drag(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        durationMs: Long = 300
    )

    /**
     * Type text. Supports Unicode.
     * @param text Text to type
     */
    fun type(text: String)

    /**
     * Press a key by keycode.
     * @param keyCode Android KeyEvent keycode
     */
    fun pressKey(keyCode: Int)

    /**
     * Press Home button.
     */
    fun pressHome()

    /**
     * Press Back button.
     */
    fun pressBack()

    /**
     * Press Recents/Overview button.
     */
    fun pressRecents()

    /**
     * Press power button (short press).
     */
    fun pressPower()

    /**
     * Press volume up.
     */
    fun pressVolumeUp()

    /**
     * Press volume down.
     */
    fun pressVolumeDown()

    // ========== App Operations ==========

    /**
     * Launch an app by package name.
     * @param packageName Package name (e.g., "com.twitter.android")
     */
    fun launchApp(packageName: String)

    /**
     * Launch a specific activity.
     * @param packageName Package name
     * @param activityName Fully qualified activity name
     * @param extras Optional intent extras
     */
    fun launchActivity(
        packageName: String,
        activityName: String,
        extras: Bundle? = null
    )

    /**
     * Get the current foreground app's package name.
     */
    fun getCurrentApp(): String

    /**
     * Force stop an app.
     */
    fun killApp(packageName: String)

    // ========== System Operations ==========

    /**
     * Install an APK file.
     * @param apkPath Path to APK file
     * @return true if install initiated (async completion)
     */
    fun installApk(apkPath: String): Boolean

    /**
     * Uninstall an app.
     * @param packageName Package to uninstall
     */
    fun uninstallApp(packageName: String): Boolean

    /**
     * Grant a runtime permission to an app.
     * @param packageName Target app
     * @param permission Permission string (e.g., "android.permission.CAMERA")
     */
    fun grantPermission(packageName: String, permission: String)

    /**
     * Revoke a runtime permission.
     */
    fun revokePermission(packageName: String, permission: String)

    /**
     * Modify a system setting.
     * @param namespace "system", "secure", or "global"
     * @param key Setting key
     * @param value Setting value
     */
    fun setSystemSetting(namespace: String, key: String, value: String)

    /**
     * Get a system setting value.
     */
    fun getSystemSetting(namespace: String, key: String): String?

    // ========== Lifecycle ==========

    /**
     * Destroy this session and release resources.
     * For headless sessions, destroys the virtual display.
     */
    fun destroy()
}
```

### SessionInfo

Summary information about a session (for listing).

```kotlin
data class SessionInfo(
    val id: String,
    val displayId: Int,
    val isHeadless: Boolean,
    val width: Int,
    val height: Int,
    val createdAt: Long,
    val foregroundApp: String?
)
```

### ShellResult

Result of shell command execution.

```kotlin
data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)
```

## Examples

### Multiple Parallel Sessions

```kotlin
val agiOS = AgentOS.getInstance(context)

// Create 3 headless sessions
val sessions = (1..3).map { i ->
    agiOS.createSession(SessionConfig(headless = true))
}

// Launch different apps in each
sessions[0].launchApp("com.twitter.android")
sessions[1].launchApp("com.instagram.android")
sessions[2].launchApp("com.whatsapp")

// Interact with each independently
sessions.forEachIndexed { i, session ->
    Thread.sleep(2000) // Wait for app load

    // Capture what each session sees
    val screenshot = session.captureScreen()
    File("/data/local/tmp/session_$i.png").writeBytes(screenshot)
}

// Clean up
sessions.forEach { it.destroy() }
```

### Automation Script (No Agent)

```kotlin
val agiOS = AgentOS.getInstance(context)
val session = agiOS.createSession(SessionConfig(headless = true))

// Simple automation: open settings, toggle wifi
session.launchApp("com.android.settings")
Thread.sleep(1000)

// Scroll to find Network
session.drag(540f, 1500f, 540f, 500f, durationMs = 400)
Thread.sleep(500)

// Tap Network & internet (coordinates from your device)
session.click(540f, 300f)
Thread.sleep(500)

// Tap Wi-Fi toggle
session.click(980f, 250f)

session.destroy()
```

### Physical + Headless Combo

```kotlin
val agiOS = AgentOS.getInstance(context)

// User uses physical display
// Meanwhile, automation runs in background

val backgroundSession = agiOS.createSession(SessionConfig(headless = true))

// Do stuff in background without user seeing
backgroundSession.launchApp("com.example.dataprocessor")
backgroundSession.click(100f, 200f)
// ...

// Later, take over physical display if needed
val physicalSession = agiOS.controlPrimaryDisplay()
physicalSession.launchApp("com.example.showresults")
// User now sees this

// Release when done
agiOS.releasePrimaryDisplay()
backgroundSession.destroy()
```

### Install and Configure App

```kotlin
val agiOS = AgentOS.getInstance(context)
val session = agiOS.createSession(SessionConfig(headless = true))

// Install APK
session.installApk("/sdcard/Download/myapp.apk")
Thread.sleep(5000) // Wait for install

// Grant permissions it needs
session.grantPermission("com.example.myapp", "android.permission.CAMERA")
session.grantPermission("com.example.myapp", "android.permission.RECORD_AUDIO")

// Launch and configure
session.launchApp("com.example.myapp")
Thread.sleep(2000)

// Skip onboarding, etc.
session.click(540f, 1800f) // "Skip" button
session.click(540f, 1800f) // "Next"
session.click(540f, 1800f) // "Done"

session.destroy()
```

### Shell Commands

```kotlin
val agiOS = AgentOS.getInstance(context)

// List files
val result = agiOS.executeShell("ls -la /data/local/tmp")
println(result.stdout)

// Get device info
val props = agiOS.executeShell("getprop ro.product.model")
println("Device: ${props.stdout.trim()}")

// Interact with other system tools
agiOS.executeShell("am broadcast -a com.example.CUSTOM_ACTION")
```

## Coordinate System

All coordinates are in **pixels** from the top-left corner of the display.

```
(0, 0) ─────────────────────────── (width, 0)
   │                                    │
   │                                    │
   │          Display Area              │
   │                                    │
   │                                    │
(0, height) ─────────────────── (width, height)
```

- For a 1080x1920 session, valid X: 0-1079, valid Y: 0-1919
- Same coordinate system applies to screenshots and input

## Threading

- All SDK methods are **synchronous** and **thread-safe**
- Methods block until the operation completes (except async operations like `installApk`)
- You can call methods from any thread
- For UI responsiveness, call from background threads

```kotlin
// Example: capture in background
CoroutineScope(Dispatchers.IO).launch {
    val screenshot = session.captureScreen()
    // Process screenshot...
}
```

## Error Handling

```kotlin
try {
    val session = agiOS.createSession(config)
    session.launchApp("com.nonexistent.app")
} catch (e: SessionException) {
    // Session-related error (creation failed, session destroyed, etc.)
} catch (e: AppNotFoundException) {
    // Package not found
} catch (e: PermissionDeniedException) {
    // Operation not permitted
} catch (e: RemoteException) {
    // IPC failure (service crashed, etc.)
}
```

## Permissions

Apps using the SDK need to be system apps or have the signature permission:

```xml
<!-- In AndroidManifest.xml -->
<uses-permission android:name="com.agi.os.permission.CONTROL_SESSIONS"
    android:protectionLevel="signature|privileged" />
```

For development/testing, you can grant via shell:
```bash
adb shell pm grant com.your.app com.agi.os.permission.CONTROL_SESSIONS
```

## Best Practices

1. **Always destroy sessions** when done to free resources
2. **Use headless sessions** for background automation
3. **Wait for apps to load** before interacting (capture screenshot to verify)
4. **Handle errors gracefully** - sessions can be destroyed externally
5. **Limit concurrent sessions** - each virtual display uses GPU memory

## Limitations

- **Max sessions**: Depends on device RAM/GPU, typically 5-10 concurrent headless sessions
- **Screenshot rate**: ~30fps max for continuous capture
- **Text input**: Complex IME behaviors may not work; basic text input works
- **DRM content**: Protected content (Netflix, etc.) won't render in virtual displays
- **Hardware features**: Camera, sensors don't work in virtual displays
