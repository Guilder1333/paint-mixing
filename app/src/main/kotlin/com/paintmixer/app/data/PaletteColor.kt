package com.paintmixer.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class PaletteColor(
    @PrimaryKey val id: String,
    val paletteId: String,
    val orderIndex: Int, // pick order; default name "Color ${orderIndex + 1}"
    val name: String, // user-editable
    val sampleX: Float, // normalised 0..1, re-samplable
    val sampleY: Float,
    val srgbHex: String, // display + export only
    val linR: Float, // CANONICAL: normalised, linear
    val linG: Float,
    val linB: Float,
    val labL: Float, // cached, derived
    val labA: Float,
    val labB: Float,
    val strength: Float = 1.0f, // tinting strength, see PLAN.md 2.5
    val sampleStdDev: Float // patch variance -> glare / unreliability flag
)
