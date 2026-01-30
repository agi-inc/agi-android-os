/*
 * InputInjector
 *
 * Injects touch and key events to specific displays.
 * Requires system privileges to inject to arbitrary displays.
 */
package com.agi.os.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import java.lang.reflect.Method

class InputInjector(context: Context) {

    companion object {
        private const val TAG = "InputInjector"

        // Inject mode constants (from hidden InputManager API)
        private const val INJECT_INPUT_EVENT_MODE_ASYNC = 0
        private const val INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT = 1
        private const val INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH = 2

        // Default timing
        private const val DEFAULT_LONG_PRESS_MS = 500L
        private const val DEFAULT_DOUBLE_TAP_DELAY_MS = 100L
        private const val DRAG_STEP_INTERVAL_MS = 15L
    }

    private val inputManager: InputManager =
        context.getSystemService(Context.INPUT_SERVICE) as InputManager

    // Cache reflection for hidden API
    private val injectInputEventMethod: Method = InputManager::class.java.getMethod(
        "injectInputEvent",
        InputEvent::class.java,
        Int::class.javaPrimitiveType
    )

    /**
     * Inject a single tap at coordinates on the specified display.
     */
    fun injectTap(displayId: Int, x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()

        // DOWN event
        val downEvent = createTouchEvent(
            downTime, downTime,
            MotionEvent.ACTION_DOWN,
            x, y
        ).also { it.setDisplayId(displayId) }
        injectEvent(downEvent)

        // UP event (slight delay)
        val upTime = SystemClock.uptimeMillis()
        val upEvent = createTouchEvent(
            downTime, upTime,
            MotionEvent.ACTION_UP,
            x, y
        ).also { it.setDisplayId(displayId) }
        injectEvent(upEvent)
    }

    /**
     * Inject a long press at coordinates.
     */
    fun injectLongPress(displayId: Int, x: Float, y: Float, durationMs: Long = DEFAULT_LONG_PRESS_MS) {
        val downTime = SystemClock.uptimeMillis()

        // DOWN event
        val downEvent = createTouchEvent(
            downTime, downTime,
            MotionEvent.ACTION_DOWN,
            x, y
        ).also { it.setDisplayId(displayId) }
        injectEvent(downEvent)

        // Wait for long press duration
        Thread.sleep(durationMs)

        // UP event
        val upTime = SystemClock.uptimeMillis()
        val upEvent = createTouchEvent(
            downTime, upTime,
            MotionEvent.ACTION_UP,
            x, y
        ).also { it.setDisplayId(displayId) }
        injectEvent(upEvent)
    }

    /**
     * Inject a double tap.
     */
    fun injectDoubleTap(displayId: Int, x: Float, y: Float) {
        injectTap(displayId, x, y)
        Thread.sleep(DEFAULT_DOUBLE_TAP_DELAY_MS)
        injectTap(displayId, x, y)
    }

    /**
     * Inject a drag/swipe gesture.
     */
    fun injectDrag(
        displayId: Int,
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        durationMs: Long = 300
    ) {
        val downTime = SystemClock.uptimeMillis()
        val steps = maxOf(1, (durationMs / DRAG_STEP_INTERVAL_MS).toInt())
        val stepDelay = durationMs / steps

        // DOWN at start position
        val downEvent = createTouchEvent(
            downTime, downTime,
            MotionEvent.ACTION_DOWN,
            startX, startY
        ).also { it.setDisplayId(displayId) }
        injectEvent(downEvent)

        // MOVE events along the path
        for (i in 1..steps) {
            val progress = i.toFloat() / steps
            val x = startX + (endX - startX) * progress
            val y = startY + (endY - startY) * progress
            val eventTime = downTime + (i * stepDelay)

            val moveEvent = createTouchEvent(
                downTime, eventTime,
                MotionEvent.ACTION_MOVE,
                x, y
            ).also { it.setDisplayId(displayId) }
            injectEvent(moveEvent)

            if (i < steps) {
                Thread.sleep(stepDelay)
            }
        }

        // UP at end position
        val upTime = SystemClock.uptimeMillis()
        val upEvent = createTouchEvent(
            downTime, upTime,
            MotionEvent.ACTION_UP,
            endX, endY
        ).also { it.setDisplayId(displayId) }
        injectEvent(upEvent)
    }

    /**
     * Inject a key event by keycode.
     */
    fun injectKeyEvent(displayId: Int, keyCode: Int) {
        val downTime = SystemClock.uptimeMillis()

        val downEvent = KeyEvent(
            downTime, downTime,
            KeyEvent.ACTION_DOWN, keyCode, 0, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
            KeyEvent.FLAG_FROM_SYSTEM,
            InputDevice.SOURCE_KEYBOARD
        ).also { setKeyEventDisplayId(it, displayId) }

        val upEvent = KeyEvent(
            downTime, SystemClock.uptimeMillis(),
            KeyEvent.ACTION_UP, keyCode, 0, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
            KeyEvent.FLAG_FROM_SYSTEM,
            InputDevice.SOURCE_KEYBOARD
        ).also { setKeyEventDisplayId(it, displayId) }

        injectEvent(downEvent)
        injectEvent(upEvent)
    }

    /**
     * Inject text as a series of key events.
     */
    fun injectText(displayId: Int, text: String) {
        for (char in text) {
            injectCharacter(displayId, char)
        }
    }

    /**
     * Inject a single character.
     */
    private fun injectCharacter(displayId: Int, char: Char) {
        val keyCharacterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
        val events = keyCharacterMap.getEvents(charArrayOf(char))

        if (events != null) {
            for (event in events) {
                val displayEvent = KeyEvent(
                    event.downTime, event.eventTime,
                    event.action, event.keyCode, event.repeatCount,
                    event.metaState, event.deviceId, event.scanCode,
                    event.flags or KeyEvent.FLAG_FROM_SYSTEM,
                    InputDevice.SOURCE_KEYBOARD
                ).also { setKeyEventDisplayId(it, displayId) }
                injectEvent(displayEvent)
            }
        } else {
            // Fallback: use commitText via InputConnection (more complex)
            // For basic ASCII this should work via key events
        }
    }

    // ========== Private Helpers ==========

    private fun createTouchEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float
    ): MotionEvent {
        val properties = MotionEvent.PointerProperties()
        properties.id = 0
        properties.toolType = MotionEvent.TOOL_TYPE_FINGER

        val coords = MotionEvent.PointerCoords()
        coords.x = x
        coords.y = y
        coords.pressure = 1.0f
        coords.size = 1.0f

        return MotionEvent.obtain(
            downTime, eventTime, action,
            1, // pointer count
            arrayOf(properties),
            arrayOf(coords),
            0, 0, // meta state, button state
            1.0f, 1.0f, // x/y precision
            0, 0, // device id, edge flags
            InputDevice.SOURCE_TOUCHSCREEN,
            0 // flags
        )
    }

    private fun MotionEvent.setDisplayId(displayId: Int) {
        // Hidden API - use reflection
        try {
            val method = MotionEvent::class.java.getMethod(
                "setDisplayId",
                Int::class.javaPrimitiveType
            )
            method.invoke(this, displayId)
        } catch (e: NoSuchMethodException) {
            // Android version doesn't have this method, use alternative
            try {
                val field = MotionEvent::class.java.getDeclaredField("mDisplayId")
                field.isAccessible = true
                field.setInt(this, displayId)
            } catch (e2: Exception) {
                // Ignore - events will go to default display
            }
        }
    }

    private fun setKeyEventDisplayId(event: KeyEvent, displayId: Int) {
        // KeyEvent is immutable, but we can try to set the display via hidden field
        try {
            val field = KeyEvent::class.java.getDeclaredField("mDisplayId")
            field.isAccessible = true
            field.setInt(event, displayId)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun injectEvent(event: InputEvent) {
        try {
            // Use the hidden InputManager.injectInputEvent method
            injectInputEventMethod.invoke(
                inputManager,
                event,
                INJECT_INPUT_EVENT_MODE_ASYNC
            )
        } finally {
            if (event is MotionEvent) {
                event.recycle()
            }
        }
    }
}
