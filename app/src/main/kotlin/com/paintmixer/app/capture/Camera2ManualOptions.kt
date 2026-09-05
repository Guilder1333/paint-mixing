package com.paintmixer.app.capture

import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.RggbChannelVector
import android.hardware.camera2.params.TonemapCurve
import androidx.camera.camera2.interop.CaptureRequestOptions
import com.paintmixer.app.data.CaptureSettings

/**
 * The exact manual capture request from PLAN.md section 4.1, built from a
 * stored/locked [CaptureSettings]. Applied via `Camera2CameraControl` so it
 * covers preview *and* still capture identically -- what you see is what
 * gets saved.
 *
 * `SHADING_MODE` is deliberately left ON (`HIGH_QUALITY`) -- see 4.1's note
 * on vignetting corrupting samples near the frame edge. Everything else 3A/
 * post-processing related is forced off so nothing adapts shot to shot.
 */
internal fun manualCaptureRequestOptions(settings: CaptureSettings): CaptureRequestOptions {
    val builder = CaptureRequestOptions.Builder()
        .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF)
        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
        .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, settings.exposureTimeNs)
        .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, settings.iso)
        .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF)
        .setCaptureRequestOption(
            CaptureRequest.COLOR_CORRECTION_MODE,
            CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX
        )
        .setCaptureRequestOption(
            CaptureRequest.COLOR_CORRECTION_GAINS,
            RggbChannelVector(settings.awbGainR, settings.awbGainGEven, settings.awbGainGOdd, settings.awbGainB)
        )
        .setCaptureRequestOption(CaptureRequest.TONEMAP_MODE, CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE)
        .setCaptureRequestOption(CaptureRequest.TONEMAP_CURVE, IDENTITY_TONEMAP_CURVE)
        .setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_OFF)
        .setCaptureRequestOption(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_OFF)
        .setCaptureRequestOption(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_OFF)
        .setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_DISABLED)
        .setCaptureRequestOption(CaptureRequest.SHADING_MODE, CameraMetadata.SHADING_MODE_HIGH_QUALITY)
        .setCaptureRequestOption(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF)

    val focus = settings.focusDistance
    if (focus != null) {
        builder
            .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, focus)
    }

    return builder.build()
}

/** Two-point (0,0)-(1,1) curve per channel -- output equals input, i.e. no tone mapping at all. */
private val IDENTITY_CURVE = floatArrayOf(0f, 0f, 1f, 1f)
private val IDENTITY_TONEMAP_CURVE = TonemapCurve(IDENTITY_CURVE, IDENTITY_CURVE, IDENTITY_CURVE)

/**
 * Auto-exposure metering targets what looks right *after* a normal boosting tonemap curve is
 * applied; the identity curve above removes that boost entirely, so reusing a metered exposure
 * verbatim produces a needlessly dark, low-precision capture -- confirmed empirically on the
 * target device: a white reference read ~90/255 under the metered exposure, using barely a third
 * of the 8-bit range and leaving darker paints only a handful of distinguishable values.
 *
 * Apply this exactly once, at the metered-reading -> locked-and-persisted transition
 * (`PaletteCaptureScreen`), never on replay -- what gets stored already reflects the boost, so
 * replaying a palette's settings verbatim is correct without reapplying this a second time.
 *
 * The factor is an empirical starting point from that one measurement (target ~200/255 for
 * white, leaving headroom against clipping specular highlights on wet/glossy paint -- PLAN.md 4.3's
 * glare warning). [PatchSample.isBlownOut] still catches it if this overshoots on a brighter scene.
 * Retune `LINEAR_EXPOSURE_BOOST_FACTOR` if repeated white-reference readings run persistently high
 * or low.
 */
fun CaptureSettings.withLinearExposureBoost(): CaptureSettings =
    copy(exposureTimeNs = (exposureTimeNs * LINEAR_EXPOSURE_BOOST_FACTOR).toLong())

private const val LINEAR_EXPOSURE_BOOST_FACTOR = 2.2
