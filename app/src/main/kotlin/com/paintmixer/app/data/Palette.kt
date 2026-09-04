package com.paintmixer.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Palette(
    @PrimaryKey val id: String, // UUID
    val name: String,
    val imagePath: String, // app-private internal storage
    val createdAt: Long,
    val whiteRefX: Float, // normalised image coords 0..1
    val whiteRefY: Float,
    val whiteRefReflectance: Float = 0.90f,
    @Embedded val capture: CaptureSettings // replayed for every target shot against this palette
)
