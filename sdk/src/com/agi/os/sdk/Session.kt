/*
 * Session Interface
 *
 * Public API for interacting with a session (headless or physical display).
 */
package com.agi.os.sdk

import android.os.Bundle

/**
 * A session represents a controllable display - either a headless virtual
 * display or the physical device display.
 *
 * Sessions provide:
 * - Screenshot capture
 * - Input injection (taps, gestures, text, keys)
 * - App launching and control
 * - System operations (install, permissions, settings)
 *
 * Obtain sessions via [AgentOS.createSession] or [AgentOS.controlPrimaryDisplay].
 * Always call [destroy] when done to release resources.
 */
interface Session {

    /** Unique session identifier */
    val id: String

    /** Android display ID this session operates on */
    val displayId: Int

    // ========== Screen Operations ==========

    /**
     * Capture a screenshot of this session's display.
     *
     * @return PNG-encoded image bytes
     */
    fun captureScreen(): ByteArray

    /**
     * Capture screenshot as raw RGBA bytes.
     *
     * Faster than PNG encoding - use when performance matters.
     * Format: RGBA_8888, row-major order, top-left origin.
     *
     * @return Raw RGBA pixel data
     */
    fun captureScreenRaw(): ByteArray

    /**
     * Get the screen dimensions.
     *
     * @return Pair of (width, height) in pixels
     */
    fun getScreenSize(): Pair<Int, Int>

    // ========== Input Operations ==========

    /**
     * Tap at coordinates.
     *
     * @param x X coordinate in pixels from left edge
     * @param y Y coordinate in pixels from top edge
     */
    fun click(x: Float, y: Float)

    /**
     * Convenience overload with Int coordinates.
     */
    fun click(x: Int, y: Int) = click(x.toFloat(), y.toFloat())

    /**
     * Long press at coordinates.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param durationMs Press duration in milliseconds (default: 500)
     */
    fun longPress(x: Float, y: Float, durationMs: Long = 500)

    /**
     * Double tap at coordinates.
     */
    fun doubleClick(x: Float, y: Float)

    /**
     * Drag/swipe gesture from one point to another.
     *
     * @param startX Starting X coordinate
     * @param startY Starting Y coordinate
     * @param endX Ending X coordinate
     * @param endY Ending Y coordinate
     * @param durationMs Gesture duration in milliseconds (default: 300)
     */
    fun drag(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        durationMs: Long = 300
    )

    /**
     * Type text. Supports Unicode characters.
     *
     * @param text The text to type
     */
    fun type(text: String)

    /**
     * Press a key by Android keycode.
     *
     * @param keyCode KeyEvent keycode (e.g., KeyEvent.KEYCODE_ENTER)
     */
    fun pressKey(keyCode: Int)

    /** Press the Home button. */
    fun pressHome()

    /** Press the Back button. */
    fun pressBack()

    /** Press the Recents/Overview button. */
    fun pressRecents()

    /** Press the Power button (short press). */
    fun pressPower()

    /** Press Volume Up. */
    fun pressVolumeUp()

    /** Press Volume Down. */
    fun pressVolumeDown()

    // ========== App Operations ==========

    /**
     * Launch an app by package name.
     *
     * Launches the app's default/main activity on this session's display.
     *
     * @param packageName Package name (e.g., "com.twitter.android")
     * @throws IllegalArgumentException if package not found
     */
    fun launchApp(packageName: String)

    /**
     * Launch a specific activity.
     *
     * @param packageName Package name
     * @param activityName Fully qualified activity class name
     * @param extras Optional intent extras
     */
    fun launchActivity(
        packageName: String,
        activityName: String,
        extras: Bundle? = null
    )

    /**
     * Get the current foreground app's package name on this display.
     *
     * @return Package name of the foreground app
     */
    fun getCurrentApp(): String

    /**
     * Force stop an app.
     *
     * @param packageName Package to kill
     */
    fun killApp(packageName: String)

    // ========== System Operations ==========

    /**
     * Install an APK file.
     *
     * Installation is asynchronous - method returns after initiating install.
     *
     * @param apkPath Absolute path to the APK file
     * @return true if install was initiated successfully
     */
    fun installApk(apkPath: String): Boolean

    /**
     * Uninstall an app.
     *
     * @param packageName Package to uninstall
     * @return true if uninstall was initiated
     */
    fun uninstallApp(packageName: String): Boolean

    /**
     * Grant a runtime permission to an app.
     *
     * @param packageName Target app
     * @param permission Full permission string (e.g., "android.permission.CAMERA")
     */
    fun grantPermission(packageName: String, permission: String)

    /**
     * Revoke a runtime permission from an app.
     */
    fun revokePermission(packageName: String, permission: String)

    /**
     * Set a system setting.
     *
     * @param namespace Settings namespace: "system", "secure", or "global"
     * @param key Setting key
     * @param value Setting value
     */
    fun setSystemSetting(namespace: String, key: String, value: String)

    /**
     * Get a system setting value.
     *
     * @param namespace Settings namespace: "system", "secure", or "global"
     * @param key Setting key
     * @return Setting value, or null if not set
     */
    fun getSystemSetting(namespace: String, key: String): String?

    // ========== Lifecycle ==========

    /**
     * Destroy this session and release resources.
     *
     * For headless sessions, this destroys the virtual display.
     * After calling destroy, the session should not be used.
     */
    fun destroy()
}
