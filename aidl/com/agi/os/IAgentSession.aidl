/*
 * IAgentSession.aidl
 *
 * Session interface for screen capture, input injection, app control,
 * and system operations.
 */
package com.agi.os;

import android.os.Bundle;

interface IAgentSession {

    // ========== Identification ==========

    /**
     * Get the unique session ID.
     */
    String getId();

    /**
     * Get the Android display ID this session operates on.
     */
    int getDisplayId();

    // ========== Screen Operations ==========

    /**
     * Capture a screenshot of this session's display.
     * @return PNG-encoded image bytes
     */
    byte[] captureScreen();

    /**
     * Capture screenshot as raw RGBA bytes (faster, no encoding).
     * @return RGBA pixel data, row-major order
     */
    byte[] captureScreenRaw();

    /**
     * Get the screen dimensions.
     * @return [width, height] array
     */
    int[] getScreenSize();

    // ========== Input Operations ==========

    /**
     * Tap at coordinates.
     * @param x X coordinate in pixels from left
     * @param y Y coordinate in pixels from top
     */
    void click(float x, float y);

    /**
     * Long press at coordinates.
     * @param x X coordinate
     * @param y Y coordinate
     * @param durationMs Press duration in milliseconds
     */
    void longPress(float x, float y, long durationMs);

    /**
     * Double tap at coordinates.
     */
    void doubleClick(float x, float y);

    /**
     * Drag/swipe gesture.
     * @param startX Start X coordinate
     * @param startY Start Y coordinate
     * @param endX End X coordinate
     * @param endY End Y coordinate
     * @param durationMs Gesture duration in milliseconds
     */
    void drag(float startX, float startY, float endX, float endY, long durationMs);

    /**
     * Type text. Supports Unicode.
     * @param text Text to type
     */
    void type(String text);

    /**
     * Press a key by Android keycode.
     * @param keyCode KeyEvent keycode (e.g., KeyEvent.KEYCODE_ENTER)
     */
    void pressKey(int keyCode);

    /**
     * Press Home button.
     */
    void pressHome();

    /**
     * Press Back button.
     */
    void pressBack();

    /**
     * Press Recents/Overview button.
     */
    void pressRecents();

    /**
     * Press power button (short press).
     */
    void pressPower();

    /**
     * Press volume up.
     */
    void pressVolumeUp();

    /**
     * Press volume down.
     */
    void pressVolumeDown();

    // ========== App Operations ==========

    /**
     * Launch an app by package name.
     * @param packageName Package name (e.g., "com.twitter.android")
     */
    void launchApp(String packageName);

    /**
     * Launch a specific activity.
     * @param packageName Package name
     * @param activityName Fully qualified activity name
     * @param extras Optional intent extras (can be null)
     */
    void launchActivity(String packageName, String activityName, in Bundle extras);

    /**
     * Get the current foreground app's package name.
     * @return Package name of foreground app
     */
    String getCurrentApp();

    /**
     * Force stop an app.
     * @param packageName Package to kill
     */
    void killApp(String packageName);

    // ========== System Operations ==========

    /**
     * Install an APK file.
     * @param apkPath Absolute path to APK file
     * @return true if install was initiated (async completion)
     */
    boolean installApk(String apkPath);

    /**
     * Uninstall an app.
     * @param packageName Package to uninstall
     * @return true if uninstall was initiated
     */
    boolean uninstallApp(String packageName);

    /**
     * Grant a runtime permission to an app.
     * @param packageName Target app package
     * @param permission Permission string (e.g., "android.permission.CAMERA")
     */
    void grantPermission(String packageName, String permission);

    /**
     * Revoke a runtime permission from an app.
     */
    void revokePermission(String packageName, String permission);

    /**
     * Modify a system setting.
     * @param namespace "system", "secure", or "global"
     * @param key Setting key
     * @param value Setting value
     */
    void setSystemSetting(String namespace, String key, String value);

    /**
     * Get a system setting value.
     * @param namespace "system", "secure", or "global"
     * @param key Setting key
     * @return Setting value, or null if not set
     */
    String getSystemSetting(String namespace, String key);

    // ========== Lifecycle ==========

    /**
     * Destroy this session and release resources.
     * For headless sessions, destroys the virtual display.
     */
    void destroy();
}
