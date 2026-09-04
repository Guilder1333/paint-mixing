package com.paintmixer.core.color

import kotlin.math.cbrt
import kotlin.math.pow

/**
 * Linear-light RGB in the sRGB primaries. Channels are typically in [0,1]
 * before white-balance normalisation, but legally exceed 1.0 afterwards --
 * see [WhiteBalance.normalise] and PLAN.md section 2.6.
 */
data class LinearRgb(val r: Double, val g: Double, val b: Double)

/** CIE 1931 XYZ, D65 white point. */
data class Xyz(val x: Double, val y: Double, val z: Double)

/** CIELAB, D65 white point. */
data class Lab(val l: Double, val a: Double, val b: Double)

/** Gamma-encoded sRGB, each channel in [0,1]. Display/export only -- PLAN.md section 3. */
data class Srgb(val r: Double, val g: Double, val b: Double) {
    /** #RRGGBB, clamping to [0,1] first -- normalised linear values can exceed the sRGB gamut. */
    fun toHex(): String {
        fun byte(c: Double): Int = Math.round(c.coerceIn(0.0, 1.0) * 255.0).toInt().coerceIn(0, 255)
        return "#%02X%02X%02X".format(byte(r), byte(g), byte(b))
    }
}

// ---- sRGB <-> linear-light, PLAN.md 2.1 ----

fun srgbChannelToLinear(c: Double): Double =
    if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

fun linearChannelToSrgb(c: Double): Double =
    if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1.0 / 2.4) - 0.055

fun Srgb.toLinear(): LinearRgb =
    LinearRgb(srgbChannelToLinear(r), srgbChannelToLinear(g), srgbChannelToLinear(b))

fun LinearRgb.toSrgb(): Srgb =
    Srgb(linearChannelToSrgb(r), linearChannelToSrgb(g), linearChannelToSrgb(b))

// ---- Linear sRGB <-> CIE XYZ, D65, PLAN.md 2.2 ----

fun LinearRgb.toXyz(): Xyz = Xyz(
    x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b,
    y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b,
    z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b
)

/** The inverse of [LinearRgb.toXyz]'s matrix (standard sRGB/D65 XYZ -> linear matrix). */
fun Xyz.toLinearRgb(): LinearRgb = LinearRgb(
    r = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z,
    g = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z,
    b = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z
)

// ---- CIE XYZ <-> CIELAB, D65, PLAN.md 2.3 ----

private const val XN = 0.95047
private const val YN = 1.00000
private const val ZN = 1.08883
private const val LAB_EPSILON = 216.0 / 24389.0 // (6/29)^3
private const val LAB_KAPPA = 841.0 / 108.0     // 3*(29/6)^2
private const val LAB_DELTA = 6.0 / 29.0

private fun labF(t: Double): Double = if (t > LAB_EPSILON) cbrt(t) else LAB_KAPPA * t + 4.0 / 29.0
private fun labFInverse(t: Double): Double = if (t > LAB_DELTA) t * t * t else (t - 4.0 / 29.0) / LAB_KAPPA

fun Xyz.toLab(): Lab {
    val fx = labF(x / XN)
    val fy = labF(y / YN)
    val fz = labF(z / ZN)
    return Lab(l = 116.0 * fy - 16.0, a = 500.0 * (fx - fy), b = 200.0 * (fy - fz))
}

/** The inverse of [Xyz.toLab]. */
fun Lab.toXyz(): Xyz {
    val fy = (l + 16.0) / 116.0
    val fx = fy + a / 500.0
    val fz = fy - b / 200.0
    return Xyz(x = XN * labFInverse(fx), y = YN * labFInverse(fy), z = ZN * labFInverse(fz))
}

/** The full sRGB -> Lab chain. */
fun Srgb.toLab(): Lab = toLinear().toXyz().toLab()

/** The full Lab -> sRGB chain. */
fun Lab.toSrgb(): Srgb = toXyz().toLinearRgb().toSrgb()
