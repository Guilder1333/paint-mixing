package com.paintmixer.app.probe

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Range

/**
 * One camera's manual-control capabilities, per PLAN.md section 4.0. Reading
 * [CameraCharacteristics] needs no runtime permission -- only opening the
 * camera to capture does -- so this can run the moment the screen opens.
 */
data class CameraProbeResult(
    val cameraId: String,
    val lensFacing: String,
    val hardwareLevel: String,
    val hasManualSensor: Boolean,
    val hasManualPostProcessing: Boolean,
    val hasRaw: Boolean,
    val exposureTimeRangeNs: Range<Long>?,
    val sensitivityRange: Range<Int>?,
    val availableToneMapModes: List<String>
) {
    /** The capture path this camera falls into, per the PLAN.md 4.0 table. */
    val capturePath: String
        get() = when {
            hasManualSensor && hasManualPostProcessing && hasRaw ->
                "Full manual JPEG (4.1); RAW/DNG (4.2) also available"
            hasManualSensor && hasManualPostProcessing ->
                "Full manual JPEG (4.1)"
            else ->
                "Neither MANUAL_SENSOR nor MANUAL_POST_PROCESSING -- fall back to AE/AWB lock at shutter"
        }
}

object CameraProbe {
    fun probeAll(context: Context): List<CameraProbeResult> {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return manager.cameraIdList.map { id -> probeOne(manager, id) }
    }

    private fun probeOne(manager: CameraManager, id: String): CameraProbeResult {
        val chars = manager.getCameraCharacteristics(id)
        val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        val toneModes = chars.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES) ?: intArrayOf()
        return CameraProbeResult(
            cameraId = id,
            lensFacing = lensFacingName(chars.get(CameraCharacteristics.LENS_FACING)),
            hardwareLevel = hardwareLevelName(chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)),
            hasManualSensor = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in capabilities,
            hasManualPostProcessing = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING in capabilities,
            hasRaw = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW in capabilities,
            exposureTimeRangeNs = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE),
            sensitivityRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE),
            availableToneMapModes = toneModes.map(::toneMapModeName)
        )
    }

    private fun lensFacingName(value: Int?): String = when (value) {
        CameraCharacteristics.LENS_FACING_BACK -> "Back"
        CameraCharacteristics.LENS_FACING_FRONT -> "Front"
        CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
        else -> "Unknown"
    }

    private fun hardwareLevelName(value: Int?): String = when (value) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
        else -> "UNKNOWN"
    }

    private fun toneMapModeName(value: Int): String = when (value) {
        CameraCharacteristics.TONEMAP_MODE_CONTRAST_CURVE -> "CONTRAST_CURVE"
        CameraCharacteristics.TONEMAP_MODE_FAST -> "FAST"
        CameraCharacteristics.TONEMAP_MODE_HIGH_QUALITY -> "HIGH_QUALITY"
        CameraCharacteristics.TONEMAP_MODE_GAMMA_VALUE -> "GAMMA_VALUE"
        CameraCharacteristics.TONEMAP_MODE_PRESET_CURVE -> "PRESET_CURVE"
        else -> "UNKNOWN($value)"
    }
}

/** Plain-text report of all probed cameras, meant to be copied and pasted into the repo. */
fun List<CameraProbeResult>.toReportText(): String = buildString {
    appendLine("Paint Mixer -- device capability probe (PLAN.md 4.0)")
    appendLine("Model: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (API ${android.os.Build.VERSION.SDK_INT})")
    this@toReportText.forEach { r ->
        appendLine("---")
        appendLine("Camera ${r.cameraId} (${r.lensFacing})")
        appendLine("  hardware level: ${r.hardwareLevel}")
        appendLine("  MANUAL_SENSOR: ${r.hasManualSensor}")
        appendLine("  MANUAL_POST_PROCESSING: ${r.hasManualPostProcessing}")
        appendLine("  RAW: ${r.hasRaw}")
        appendLine("  exposure time range (ns): ${r.exposureTimeRangeNs}")
        appendLine("  sensitivity (ISO) range: ${r.sensitivityRange}")
        appendLine("  tonemap modes: ${r.availableToneMapModes.joinToString()}")
        appendLine("  capture path: ${r.capturePath}")
    }
}
