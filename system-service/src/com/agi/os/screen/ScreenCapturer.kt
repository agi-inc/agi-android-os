/*
 * ScreenCapturer
 *
 * Captures screenshots from virtual displays (via ImageReader) and
 * physical display (via SurfaceControl).
 */
package com.agi.os.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.view.SurfaceControl
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class ScreenCapturer(private val context: Context) {

    companion object {
        private const val TAG = "ScreenCapturer"
        private const val PNG_QUALITY = 100
        private const val JPEG_QUALITY = 90
    }

    /**
     * Capture screenshot from a virtual display via its ImageReader.
     * Returns PNG-encoded bytes.
     */
    fun captureVirtualDisplay(imageReader: ImageReader): ByteArray {
        val bitmap = captureVirtualDisplayBitmap(imageReader)
        return encodePng(bitmap).also { bitmap.recycle() }
    }

    /**
     * Capture raw RGBA bytes from virtual display.
     */
    fun captureVirtualDisplayRaw(imageReader: ImageReader): ByteArray {
        val image = imageReader.acquireLatestImage()
            ?: throw RuntimeException("No image available from ImageReader")

        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * image.width

            // If there's no padding, we can copy directly
            return if (rowPadding == 0) {
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                bytes
            } else {
                // Handle row padding
                val width = image.width
                val height = image.height
                val output = ByteArray(width * height * 4)
                var outputOffset = 0

                for (row in 0 until height) {
                    buffer.position(row * rowStride)
                    buffer.get(output, outputOffset, width * pixelStride)
                    outputOffset += width * pixelStride
                }
                output
            }
        } finally {
            image.close()
        }
    }

    /**
     * Capture screenshot from physical display (display 0).
     * Returns PNG-encoded bytes.
     */
    fun capturePhysicalDisplay(): ByteArray {
        val bitmap = capturePhysicalDisplayBitmap()
        return encodePng(bitmap).also { bitmap.recycle() }
    }

    /**
     * Capture raw RGBA bytes from physical display.
     */
    fun capturePhysicalDisplayRaw(): ByteArray {
        val bitmap = capturePhysicalDisplayBitmap()
        val buffer = ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(buffer)
        bitmap.recycle()
        return buffer.array()
    }

    // ========== Private Helpers ==========

    private fun captureVirtualDisplayBitmap(imageReader: ImageReader): Bitmap {
        val image = imageReader.acquireLatestImage()
            ?: throw RuntimeException("No image available from ImageReader")

        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride

            // Create bitmap with possible row padding
            val bitmap = Bitmap.createBitmap(
                rowStride / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            // Crop to actual dimensions if there was padding
            return if (bitmap.width != image.width) {
                Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height).also {
                    bitmap.recycle()
                }
            } else {
                bitmap
            }
        } finally {
            image.close()
        }
    }

    private fun capturePhysicalDisplayBitmap(): Bitmap {
        // Use SurfaceControl.screenshot (system API)
        // This requires CAPTURE_VIDEO_OUTPUT permission (system only)

        val displayManager = context.getSystemService(DisplayManager::class.java)
        val display = displayManager.displays[0]
        val mode = display.mode

        // SurfaceControl.screenshot methods are hidden, use reflection
        try {
            // Try the newer API first (Android 12+)
            val screenshotMethod = SurfaceControl::class.java.getMethod(
                "screenshot",
                android.graphics.Rect::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )

            val bitmap = screenshotMethod.invoke(
                null,
                android.graphics.Rect(0, 0, mode.physicalWidth, mode.physicalHeight),
                mode.physicalWidth,
                mode.physicalHeight,
                0 // rotation
            ) as Bitmap

            return bitmap
        } catch (e: NoSuchMethodException) {
            // Try older API
            try {
                // Get display token
                val getPhysicalDisplayTokenMethod = SurfaceControl::class.java.getMethod(
                    "getPhysicalDisplayToken",
                    Long::class.javaPrimitiveType
                )

                val getPhysicalDisplayIdsMethod = SurfaceControl::class.java.getMethod(
                    "getPhysicalDisplayIds"
                )

                val displayIds = getPhysicalDisplayIdsMethod.invoke(null) as LongArray
                val displayToken = getPhysicalDisplayTokenMethod.invoke(null, displayIds[0])

                // Take screenshot
                val screenshotMethod = SurfaceControl::class.java.getMethod(
                    "screenshot",
                    displayToken.javaClass
                )

                return screenshotMethod.invoke(null, displayToken) as Bitmap
            } catch (e2: Exception) {
                throw RuntimeException("Cannot capture physical display", e2)
            }
        }
    }

    private fun encodePng(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, stream)
        return stream.toByteArray()
    }

    private fun encodeJpeg(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        return stream.toByteArray()
    }
}
