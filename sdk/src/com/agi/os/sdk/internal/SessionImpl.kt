/*
 * SessionImpl
 *
 * Internal implementation of Session interface that wraps the AIDL binder.
 */
package com.agi.os.sdk.internal

import android.os.Bundle
import android.os.RemoteException
import com.agi.os.IAgentSession
import com.agi.os.sdk.Session
import com.agi.os.sdk.SessionException

internal class SessionImpl(
    private val binder: IAgentSession
) : Session {

    override val id: String
        get() = try {
            binder.id
        } catch (e: RemoteException) {
            throw SessionException("Failed to get session ID", e)
        }

    override val displayId: Int
        get() = try {
            binder.displayId
        } catch (e: RemoteException) {
            throw SessionException("Failed to get display ID", e)
        }

    // ========== Screen Operations ==========

    override fun captureScreen(): ByteArray {
        return try {
            binder.captureScreen()
        } catch (e: RemoteException) {
            throw SessionException("Failed to capture screen", e)
        }
    }

    override fun captureScreenRaw(): ByteArray {
        return try {
            binder.captureScreenRaw()
        } catch (e: RemoteException) {
            throw SessionException("Failed to capture screen raw", e)
        }
    }

    override fun getScreenSize(): Pair<Int, Int> {
        return try {
            val size = binder.screenSize
            Pair(size[0], size[1])
        } catch (e: RemoteException) {
            throw SessionException("Failed to get screen size", e)
        }
    }

    // ========== Input Operations ==========

    override fun click(x: Float, y: Float) {
        try {
            binder.click(x, y)
        } catch (e: RemoteException) {
            throw SessionException("Failed to click", e)
        }
    }

    override fun longPress(x: Float, y: Float, durationMs: Long) {
        try {
            binder.longPress(x, y, durationMs)
        } catch (e: RemoteException) {
            throw SessionException("Failed to long press", e)
        }
    }

    override fun doubleClick(x: Float, y: Float) {
        try {
            binder.doubleClick(x, y)
        } catch (e: RemoteException) {
            throw SessionException("Failed to double click", e)
        }
    }

    override fun drag(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        durationMs: Long
    ) {
        try {
            binder.drag(startX, startY, endX, endY, durationMs)
        } catch (e: RemoteException) {
            throw SessionException("Failed to drag", e)
        }
    }

    override fun type(text: String) {
        try {
            binder.type(text)
        } catch (e: RemoteException) {
            throw SessionException("Failed to type", e)
        }
    }

    override fun pressKey(keyCode: Int) {
        try {
            binder.pressKey(keyCode)
        } catch (e: RemoteException) {
            throw SessionException("Failed to press key", e)
        }
    }

    override fun pressHome() {
        try {
            binder.pressHome()
        } catch (e: RemoteException) {
            throw SessionException("Failed to press home", e)
        }
    }

    override fun pressBack() {
        try {
            binder.pressBack()
        } catch (e: RemoteException) {
            throw SessionException("Failed to press back", e)
        }
    }

    override fun pressRecents() {
        try {
            binder.pressRecents()
        } catch (e: RemoteException) {
            throw SessionException("Failed to press recents", e)
        }
    }

    override fun pressPower() {
        try {
            binder.pressPower()
        } catch (e: RemoteException) {
            throw SessionException("Failed to press power", e)
        }
    }

    override fun pressVolumeUp() {
        try {
            binder.pressVolumeUp()
        } catch (e: RemoteException) {
            throw SessionException("Failed to press volume up", e)
        }
    }

    override fun pressVolumeDown() {
        try {
            binder.pressVolumeDown()
        } catch (e: RemoteException) {
            throw SessionException("Failed to press volume down", e)
        }
    }

    // ========== App Operations ==========

    override fun launchApp(packageName: String) {
        try {
            binder.launchApp(packageName)
        } catch (e: RemoteException) {
            throw SessionException("Failed to launch app: $packageName", e)
        }
    }

    override fun launchActivity(
        packageName: String,
        activityName: String,
        extras: Bundle?
    ) {
        try {
            binder.launchActivity(packageName, activityName, extras)
        } catch (e: RemoteException) {
            throw SessionException("Failed to launch activity", e)
        }
    }

    override fun getCurrentApp(): String {
        return try {
            binder.currentApp
        } catch (e: RemoteException) {
            throw SessionException("Failed to get current app", e)
        }
    }

    override fun killApp(packageName: String) {
        try {
            binder.killApp(packageName)
        } catch (e: RemoteException) {
            throw SessionException("Failed to kill app: $packageName", e)
        }
    }

    // ========== System Operations ==========

    override fun installApk(apkPath: String): Boolean {
        return try {
            binder.installApk(apkPath)
        } catch (e: RemoteException) {
            throw SessionException("Failed to install APK", e)
        }
    }

    override fun uninstallApp(packageName: String): Boolean {
        return try {
            binder.uninstallApp(packageName)
        } catch (e: RemoteException) {
            throw SessionException("Failed to uninstall app", e)
        }
    }

    override fun grantPermission(packageName: String, permission: String) {
        try {
            binder.grantPermission(packageName, permission)
        } catch (e: RemoteException) {
            throw SessionException("Failed to grant permission", e)
        }
    }

    override fun revokePermission(packageName: String, permission: String) {
        try {
            binder.revokePermission(packageName, permission)
        } catch (e: RemoteException) {
            throw SessionException("Failed to revoke permission", e)
        }
    }

    override fun setSystemSetting(namespace: String, key: String, value: String) {
        try {
            binder.setSystemSetting(namespace, key, value)
        } catch (e: RemoteException) {
            throw SessionException("Failed to set system setting", e)
        }
    }

    override fun getSystemSetting(namespace: String, key: String): String? {
        return try {
            binder.getSystemSetting(namespace, key)
        } catch (e: RemoteException) {
            throw SessionException("Failed to get system setting", e)
        }
    }

    // ========== Lifecycle ==========

    override fun destroy() {
        try {
            binder.destroy()
        } catch (e: RemoteException) {
            // Ignore - session may already be destroyed
        }
    }
}
