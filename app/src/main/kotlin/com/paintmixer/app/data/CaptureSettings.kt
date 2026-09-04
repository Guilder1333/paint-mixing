package com.paintmixer.app.data

/**
 * Exact manual capture settings, captured once for a [Palette] and REPLAYED
 * for every [TargetShot] taken against it -- see PLAN.md section 3 and 4.1.
 * Embedded (flattened) into both entities rather than stored as its own
 * table, since it never exists independently of a shot.
 */
data class CaptureSettings(
    val exposureTimeNs: Long,
    val iso: Int,
    val awbGainR: Float,
    val awbGainGEven: Float,
    val awbGainGOdd: Float,
    val awbGainB: Float,
    val focusDistance: Float?,
    // true -> the captured image is NOT sRGB-encoded (identity/linear tonemap
    // curve was used), so decoding must skip toLinear(). See PLAN.md 2.6/4.1.
    val linearTonemap: Boolean,
    val manualControlUsed: Boolean
)
