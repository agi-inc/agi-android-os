/*
 * VirtualDisplayManager
 *
 * Creates and manages virtual displays for headless sessions.
 * Each virtual display renders to an ImageReader surface for screenshot capture.
 */
package com.agi.os.display

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.util.Slog

class VirtualDisplayManager(context: Context) {

    companion object {
        private const val TAG = "VirtualDisplayManager"

        // Flags for virtual display creation
        private const val VIRTUAL_DISPLAY_FLAGS =
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
    }

    private val displayManager: DisplayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    /**
     * Create a virtual display with an ImageReader surface for capture.
     *
     * @param name Display name (for debugging)
     * @param width Display width in pixels
     * @param height Display height in pixels
     * @param dpi Display density
     * @return Pair of VirtualDisplay and ImageReader
     */
    fun createVirtualDisplay(
        name: String,
        width: Int,
        height: Int,
        dpi: Int
    ): Pair<VirtualDisplay, ImageReader> {
        Slog.d(TAG, "Creating virtual display: $name (${width}x${height} @ $dpi dpi)")

        // Create ImageReader to receive rendered frames
        // maxImages=2 for double-buffering
        val imageReader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2
        )

        // Create virtual display that renders to the ImageReader's surface
        val virtualDisplay = displayManager.createVirtualDisplay(
            name,
            width,
            height,
            dpi,
            imageReader.surface,
            VIRTUAL_DISPLAY_FLAGS
        ) ?: throw RuntimeException("Failed to create virtual display")

        Slog.i(TAG, "Created virtual display: $name -> displayId=${virtualDisplay.display.displayId}")

        return Pair(virtualDisplay, imageReader)
    }

    /**
     * List all displays (physical + virtual).
     */
    fun listDisplays(): List<DisplayInfo> {
        return displayManager.displays.map { display ->
            val mode = display.mode
            DisplayInfo(
                displayId = display.displayId,
                name = display.name ?: "Unknown",
                width = mode.physicalWidth,
                height = mode.physicalHeight,
                dpi = display.densityDpi,
                isVirtual = display.displayId != 0
            )
        }
    }

    data class DisplayInfo(
        val displayId: Int,
        val name: String,
        val width: Int,
        val height: Int,
        val dpi: Int,
        val isVirtual: Boolean
    )
}
