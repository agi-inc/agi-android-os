/*
 * Session
 *
 * Represents an active automation session, either backed by a virtual display
 * (headless) or the physical display.
 */
package com.agi.os.session

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.KeyEvent
import com.agi.os.SessionConfig
import com.agi.os.display.VirtualDisplayManager
import com.agi.os.input.InputInjector
import com.agi.os.screen.ScreenCapturer
import java.io.ByteArrayOutputStream
import java.util.UUID

class Session(
    val id: String,
    val displayId: Int,
    val config: SessionConfig,
    private val context: Context,
    private val virtualDisplay: VirtualDisplay?,
    private val imageReader: ImageReader?,
    private val inputInjector: InputInjector,
    private val screenCapturer: ScreenCapturer,
    private val systemExecutor: SystemExecutor
) {
    val isHeadless: Boolean = virtualDisplay != null
    val createdAt: Long = System.currentTimeMillis()

    @Volatile
    private var destroyed = false

    companion object {
        /**
         * Create a headless session with a virtual display.
         */
        fun createHeadless(
            context: Context,
            config: SessionConfig,
            displayManager: VirtualDisplayManager,
            inputInjector: InputInjector,
            screenCapturer: ScreenCapturer,
            systemExecutor: SystemExecutor
        ): Session {
            val id = UUID.randomUUID().toString()

            // Create virtual display with ImageReader surface
            val (virtualDisplay, imageReader) = displayManager.createVirtualDisplay(
                name = "agi-session-$id",
                width = config.width,
                height = config.height,
                dpi = config.dpi
            )

            return Session(
                id = id,
                displayId = virtualDisplay.display.displayId,
                config = config,
                context = context,
                virtualDisplay = virtualDisplay,
                imageReader = imageReader,
                inputInjector = inputInjector,
                screenCapturer = screenCapturer,
                systemExecutor = systemExecutor
            )
        }

        /**
         * Create a session for the physical display.
         */
        fun createPhysical(
            context: Context,
            inputInjector: InputInjector,
            screenCapturer: ScreenCapturer,
            systemExecutor: SystemExecutor
        ): Session {
            val displayManager = context.getSystemService(DisplayManager::class.java)
            val primaryDisplay = displayManager.displays[0]
            val mode = primaryDisplay.mode
            val metrics = DisplayMetrics()
            primaryDisplay.getMetrics(metrics)

            return Session(
                id = "physical-0",
                displayId = 0,
                config = SessionConfig(
                    width = mode.physicalWidth,
                    height = mode.physicalHeight,
                    dpi = metrics.densityDpi,
                    headless = false
                ),
                context = context,
                virtualDisplay = null,
                imageReader = null,
                inputInjector = inputInjector,
                screenCapturer = screenCapturer,
                systemExecutor = systemExecutor
            )
        }
    }

    private fun ensureNotDestroyed() {
        if (destroyed) {
            throw IllegalStateException("Session $id has been destroyed")
        }
    }

    // ========== Screen Operations ==========

    fun captureScreen(): ByteArray {
        ensureNotDestroyed()
        return if (isHeadless) {
            screenCapturer.captureVirtualDisplay(imageReader!!)
        } else {
            screenCapturer.capturePhysicalDisplay()
        }
    }

    fun captureScreenRaw(): ByteArray {
        ensureNotDestroyed()
        return if (isHeadless) {
            screenCapturer.captureVirtualDisplayRaw(imageReader!!)
        } else {
            screenCapturer.capturePhysicalDisplayRaw()
        }
    }

    fun getScreenSize(): Pair<Int, Int> {
        return Pair(config.width, config.height)
    }

    // ========== Input Operations ==========

    fun click(x: Float, y: Float) {
        ensureNotDestroyed()
        inputInjector.injectTap(displayId, x, y)
    }

    fun longPress(x: Float, y: Float, durationMs: Long = 500) {
        ensureNotDestroyed()
        inputInjector.injectLongPress(displayId, x, y, durationMs)
    }

    fun doubleClick(x: Float, y: Float) {
        ensureNotDestroyed()
        inputInjector.injectDoubleTap(displayId, x, y)
    }

    fun drag(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300) {
        ensureNotDestroyed()
        inputInjector.injectDrag(displayId, startX, startY, endX, endY, durationMs)
    }

    fun type(text: String) {
        ensureNotDestroyed()
        inputInjector.injectText(displayId, text)
    }

    fun pressKey(keyCode: Int) {
        ensureNotDestroyed()
        inputInjector.injectKeyEvent(displayId, keyCode)
    }

    fun pressHome() = pressKey(KeyEvent.KEYCODE_HOME)
    fun pressBack() = pressKey(KeyEvent.KEYCODE_BACK)
    fun pressRecents() = pressKey(KeyEvent.KEYCODE_APP_SWITCH)
    fun pressPower() = pressKey(KeyEvent.KEYCODE_POWER)
    fun pressVolumeUp() = pressKey(KeyEvent.KEYCODE_VOLUME_UP)
    fun pressVolumeDown() = pressKey(KeyEvent.KEYCODE_VOLUME_DOWN)

    // ========== App Operations ==========

    fun launchApp(packageName: String) {
        ensureNotDestroyed()
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName)
            ?: throw IllegalArgumentException("Package not found: $packageName")

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        val options = ActivityOptions.makeBasic()
        // ActivityOptions.setLaunchDisplayId is a hidden API, needs reflection or framework build
        setLaunchDisplayId(options, displayId)

        context.startActivity(intent, options.toBundle())
    }

    fun launchActivity(packageName: String, activityName: String, extras: Bundle?) {
        ensureNotDestroyed()
        val intent = Intent().apply {
            component = ComponentName(packageName, activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            extras?.let { putExtras(it) }
        }

        val options = ActivityOptions.makeBasic()
        setLaunchDisplayId(options, displayId)

        context.startActivity(intent, options.toBundle())
    }

    fun getCurrentApp(): String {
        ensureNotDestroyed()
        // Use ActivityManager to get the foreground task for this display
        // This requires system permission
        return systemExecutor.getCurrentForegroundApp(displayId)
    }

    fun killApp(packageName: String) {
        ensureNotDestroyed()
        systemExecutor.forceStopApp(packageName)
    }

    // ========== System Operations ==========

    fun installApk(apkPath: String): Boolean {
        return systemExecutor.installApk(apkPath)
    }

    fun uninstallApp(packageName: String): Boolean {
        return systemExecutor.uninstallApp(packageName)
    }

    fun grantPermission(packageName: String, permission: String) {
        systemExecutor.grantPermission(packageName, permission)
    }

    fun revokePermission(packageName: String, permission: String) {
        systemExecutor.revokePermission(packageName, permission)
    }

    fun setSystemSetting(namespace: String, key: String, value: String) {
        systemExecutor.setSystemSetting(namespace, key, value)
    }

    fun getSystemSetting(namespace: String, key: String): String? {
        return systemExecutor.getSystemSetting(namespace, key)
    }

    // ========== Lifecycle ==========

    fun destroy() {
        if (destroyed) return
        destroyed = true

        imageReader?.close()
        virtualDisplay?.release()
    }

    // ========== Private Helpers ==========

    private fun setLaunchDisplayId(options: ActivityOptions, displayId: Int) {
        // Hidden API - use reflection or compile against framework
        try {
            val method = ActivityOptions::class.java.getMethod(
                "setLaunchDisplayId",
                Int::class.javaPrimitiveType
            )
            method.invoke(options, displayId)
        } catch (e: Exception) {
            // Fallback: try the field directly
            try {
                val field = ActivityOptions::class.java.getDeclaredField("mLaunchDisplayId")
                field.isAccessible = true
                field.setInt(options, displayId)
            } catch (e2: Exception) {
                throw RuntimeException("Cannot set launch display ID", e2)
            }
        }
    }
}
