package com.paintmixer.app.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.paintmixer.core.color.LinearRgb
import com.paintmixer.core.color.srgbChannelToLinear
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * PLAN.md section 4.3: decode a captured JPEG the way it should always be
 * read (EXIF-correct, upright) and sample a tapped point as a small patch,
 * not a single pixel -- a per-channel median rejects specular glare
 * outliers that a mean would drag toward white.
 */
object ImageSampling {

    /** Decode [file] and rotate/flip it upright per its EXIF orientation tag. */
    fun decodeUpright(file: File): Bitmap {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: error("could not decode image: $file")
        val orientation = ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Sample a patch around the normalised ([xNorm], [yNorm]) point (0..1, relative to the
     * upright image). Patch size scales with image resolution (a fixed pixel count would cover a
     * different real-world area on a 12MP vs. a 48MP shot) but never drops below [MIN_PATCH_PX].
     */
    fun samplePatch(bitmap: Bitmap, xNorm: Float, yNorm: Float): PatchSample {
        val patchSize = patchSizeFor(bitmap)
        val half = patchSize / 2
        val centerX = (xNorm * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
        val centerY = (yNorm * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
        val left = (centerX - half).coerceIn(0, (bitmap.width - patchSize).coerceAtLeast(0))
        val top = (centerY - half).coerceIn(0, (bitmap.height - patchSize).coerceAtLeast(0))

        val n = patchSize * patchSize
        val reds = IntArray(n)
        val greens = IntArray(n)
        val blues = IntArray(n)
        var i = 0
        for (y in top until top + patchSize) {
            for (x in left until left + patchSize) {
                val pixel = bitmap.getPixel(x, y)
                reds[i] = (pixel shr 16) and 0xFF
                greens[i] = (pixel shr 8) and 0xFF
                blues[i] = pixel and 0xFF
                i++
            }
        }

        return PatchSample(
            medianR = median(reds),
            medianG = median(greens),
            medianB = median(blues),
            stdDevR = stdDev(reds),
            stdDevG = stdDev(greens),
            stdDevB = stdDev(blues),
            patchSizePx = patchSize
        )
    }

    private fun patchSizeFor(bitmap: Bitmap): Int {
        val minDimension = min(bitmap.width, bitmap.height)
        val scaled = (BASE_PATCH_PX * minDimension / BASE_REFERENCE_DIMENSION.toDouble()).roundToInt()
        return scaled.coerceAtLeast(MIN_PATCH_PX)
    }

    private fun median(values: IntArray): Int {
        val sorted = values.sortedArray()
        return sorted[sorted.size / 2]
    }

    private fun stdDev(values: IntArray): Double {
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance)
    }

    // "9x9 at a 1080px short edge" is the plan's stated baseline (section 4.3); scale from there.
    private const val BASE_PATCH_PX = 9
    private const val BASE_REFERENCE_DIMENSION = 1080
    private const val MIN_PATCH_PX = 5
}

/** One sampled patch, 8-bit sRGB-JPEG-channel values (0..255) before any colour-space conversion. */
data class PatchSample(
    val medianR: Int,
    val medianG: Int,
    val medianB: Int,
    val stdDevR: Double,
    val stdDevG: Double,
    val stdDevB: Double,
    val patchSizePx: Int
) {
    /** PLAN.md 4.3: a channel median above this is blown out / glare, not paint colour. */
    val isBlownOut: Boolean get() = medianR > BLOWN_OUT_THRESHOLD || medianG > BLOWN_OUT_THRESHOLD || medianB > BLOWN_OUT_THRESHOLD

    val maxStdDev: Double get() = maxOf(stdDevR, stdDevG, stdDevB)

    /** PLAN.md 4.3: high patch variance means an inconsistent area -- sample a flatter spot. */
    fun isInconsistent(threshold: Double = DEFAULT_INCONSISTENT_STD_DEV_THRESHOLD): Boolean = maxStdDev > threshold

    /**
     * The canonical [LinearRgb] this sample represents, *before* white-balance normalisation.
     * [linearTonemap] must come from the shot's own [com.paintmixer.app.data.CaptureSettings] --
     * PLAN.md 4.1's identity tonemap curve means the JPEG bytes are already linear and
     * `srgbChannelToLinear` must NOT be applied a second time.
     */
    fun toLinearRgb(linearTonemap: Boolean): LinearRgb {
        val r = medianR / 255.0
        val g = medianG / 255.0
        val b = medianB / 255.0
        return if (linearTonemap) {
            LinearRgb(r, g, b)
        } else {
            LinearRgb(srgbChannelToLinear(r), srgbChannelToLinear(g), srgbChannelToLinear(b))
        }
    }

    companion object {
        const val BLOWN_OUT_THRESHOLD = 250
        const val DEFAULT_INCONSISTENT_STD_DEV_THRESHOLD = 12.0
    }
}
