/*
 * SessionConfig
 *
 * Parcelable configuration for creating sessions.
 */
package com.agi.os

import android.os.Parcel
import android.os.Parcelable

data class SessionConfig(
    /**
     * Display width in pixels.
     */
    val width: Int = 1080,

    /**
     * Display height in pixels.
     */
    val height: Int = 1920,

    /**
     * Display density (DPI).
     */
    val dpi: Int = 420,

    /**
     * If true, creates a virtual display (off-screen/headless).
     * If false, operates on the physical display.
     */
    val headless: Boolean = true
) : Parcelable {

    constructor(parcel: Parcel) : this(
        width = parcel.readInt(),
        height = parcel.readInt(),
        dpi = parcel.readInt(),
        headless = parcel.readInt() != 0
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(width)
        parcel.writeInt(height)
        parcel.writeInt(dpi)
        parcel.writeInt(if (headless) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<SessionConfig> {
        override fun createFromParcel(parcel: Parcel): SessionConfig {
            return SessionConfig(parcel)
        }

        override fun newArray(size: Int): Array<SessionConfig?> {
            return arrayOfNulls(size)
        }
    }
}
