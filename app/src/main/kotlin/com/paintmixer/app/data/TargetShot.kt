package com.paintmixer.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class TargetShot(
    @PrimaryKey val id: String,
    val paletteId: String,
    val imagePath: String,
    val pickX: Float,
    val pickY: Float,
    val srgbHex: String,
    val linR: Float,
    val linG: Float,
    val linB: Float,
    val labL: Float,
    val labA: Float,
    val labB: Float,
    val whiteRefX: Float,
    val whiteRefY: Float,
    @Embedded val capture: CaptureSettings, // must equal the palette's -- see PLAN.md section 3
    val createdAt: Long
)
