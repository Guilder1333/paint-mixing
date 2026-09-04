package com.paintmixer.app.capture

import com.paintmixer.app.data.CaptureSettings

/**
 * Carries one in-progress palette capture across the Capture -> White
 * reference -> Picking screens (PLAN.md section 5, screens 2-4). Nothing
 * here is persisted until Picking's Save -- a cheap plain holder, not a
 * ViewModel, since it only needs to outlive the composition of the shared
 * [com.paintmixer.app.ui.nav.PaintMixerNavHost], not process death.
 */
class PendingPaletteCapture {
    var imagePath: String? = null
    var capture: CaptureSettings? = null
    var whiteRefX: Float? = null
    var whiteRefY: Float? = null
    var whiteRefReflectance: Float = 0.90f

    val isReadyToSave: Boolean
        get() = imagePath != null && capture != null && whiteRefX != null && whiteRefY != null

    fun reset() {
        imagePath = null
        capture = null
        whiteRefX = null
        whiteRefY = null
        whiteRefReflectance = 0.90f
    }
}
