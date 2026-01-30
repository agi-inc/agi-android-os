/*
 * SessionBinder
 *
 * AIDL Binder implementation for IAgentSession.
 * Wraps a Session object and exposes its methods over IPC.
 */
package com.agi.os.service

import android.os.Bundle
import android.util.Slog
import com.agi.os.IAgentSession
import com.agi.os.session.Session

class SessionBinder(private val session: Session) : IAgentSession.Stub() {

    companion object {
        private const val TAG = "SessionBinder"
    }

    override fun getId(): String = session.id

    override fun getDisplayId(): Int = session.displayId

    // ========== Screen Operations ==========

    override fun captureScreen(): ByteArray {
        Slog.v(TAG, "captureScreen: session=${session.id}")
        return session.captureScreen()
    }

    override fun captureScreenRaw(): ByteArray {
        return session.captureScreenRaw()
    }

    override fun getScreenSize(): IntArray {
        val (width, height) = session.getScreenSize()
        return intArrayOf(width, height)
    }

    // ========== Input Operations ==========

    override fun click(x: Float, y: Float) {
        Slog.v(TAG, "click: session=${session.id}, x=$x, y=$y")
        session.click(x, y)
    }

    override fun longPress(x: Float, y: Float, durationMs: Long) {
        session.longPress(x, y, durationMs)
    }

    override fun doubleClick(x: Float, y: Float) {
        session.doubleClick(x, y)
    }

    override fun drag(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        durationMs: Long
    ) {
        Slog.v(TAG, "drag: session=${session.id}, ($startX,$startY) -> ($endX,$endY)")
        session.drag(startX, startY, endX, endY, durationMs)
    }

    override fun type(text: String) {
        Slog.v(TAG, "type: session=${session.id}, text=${text.take(20)}...")
        session.type(text)
    }

    override fun pressKey(keyCode: Int) {
        session.pressKey(keyCode)
    }

    override fun pressHome() {
        session.pressHome()
    }

    override fun pressBack() {
        session.pressBack()
    }

    override fun pressRecents() {
        session.pressRecents()
    }

    override fun pressPower() {
        session.pressPower()
    }

    override fun pressVolumeUp() {
        session.pressVolumeUp()
    }

    override fun pressVolumeDown() {
        session.pressVolumeDown()
    }

    // ========== App Operations ==========

    override fun launchApp(packageName: String) {
        Slog.v(TAG, "launchApp: session=${session.id}, package=$packageName")
        session.launchApp(packageName)
    }

    override fun launchActivity(packageName: String, activityName: String, extras: Bundle?) {
        session.launchActivity(packageName, activityName, extras)
    }

    override fun getCurrentApp(): String {
        return session.getCurrentApp()
    }

    override fun killApp(packageName: String) {
        session.killApp(packageName)
    }

    // ========== System Operations ==========

    override fun installApk(apkPath: String): Boolean {
        Slog.d(TAG, "installApk: $apkPath")
        return session.installApk(apkPath)
    }

    override fun uninstallApp(packageName: String): Boolean {
        Slog.d(TAG, "uninstallApp: $packageName")
        return session.uninstallApp(packageName)
    }

    override fun grantPermission(packageName: String, permission: String) {
        Slog.d(TAG, "grantPermission: $packageName, $permission")
        session.grantPermission(packageName, permission)
    }

    override fun revokePermission(packageName: String, permission: String) {
        session.revokePermission(packageName, permission)
    }

    override fun setSystemSetting(namespace: String, key: String, value: String) {
        session.setSystemSetting(namespace, key, value)
    }

    override fun getSystemSetting(namespace: String, key: String): String? {
        return session.getSystemSetting(namespace, key)
    }

    // ========== Lifecycle ==========

    override fun destroy() {
        Slog.d(TAG, "destroy: session=${session.id}")
        session.destroy()
    }
}
