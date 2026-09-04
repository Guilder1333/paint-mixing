package com.paintmixer.core.color

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Perceptual colour distance, PLAN.md section 2.4. Never measure colour
 * distance in RGB -- everything here operates on [Lab].
 *
 * [cie76] is the plain Euclidean distance in Lab: three lines, unblocks the
 * whole pipeline. [ciede2000] corrects for Lab's known non-uniformity
 * (mainly in saturated regions) and is what the app should use once
 * trusted -- verified against the published Sharma/Wu/Dalal 34-pair test
 * dataset in `DeltaETest`, since CIEDE2000 is notoriously easy to get
 * subtly wrong (degenerate hue when chroma is zero, the average-hue
 * quadrant logic, degrees vs. radians).
 */
object DeltaE {

    fun cie76(a: Lab, b: Lab): Double {
        val dl = a.l - b.l
        val da = a.a - b.a
        val db = a.b - b.b
        return sqrt(dl * dl + da * da + db * db)
    }

    fun ciede2000(lab1: Lab, lab2: Lab, kL: Double = 1.0, kC: Double = 1.0, kH: Double = 1.0): Double {
        val (l1, a1, b1) = lab1
        val (l2, a2, b2) = lab2

        val c1 = hypot(a1, b1)
        val c2 = hypot(a2, b2)
        val avgC = (c1 + c2) / 2.0

        val g = 0.5 * (1.0 - sqrt(pow7(avgC) / (pow7(avgC) + POW25_7)))

        val a1p = a1 * (1.0 + g)
        val a2p = a2 * (1.0 + g)

        val c1p = hypot(a1p, b1)
        val c2p = hypot(a2p, b2)
        val avgCp = (c1p + c2p) / 2.0

        val h1p = hueDegrees(b1, a1p)
        val h2p = hueDegrees(b2, a2p)
        val cpProduct = c1p * c2p

        val dLp = l2 - l1
        val dCp = c2p - c1p

        val dhp = when {
            cpProduct == 0.0 -> 0.0
            abs(h2p - h1p) <= 180.0 -> h2p - h1p
            h2p - h1p > 180.0 -> h2p - h1p - 360.0
            else -> h2p - h1p + 360.0
        }
        val dHp = 2.0 * sqrt(cpProduct) * sin(Math.toRadians(dhp / 2.0))

        val avgLp = (l1 + l2) / 2.0
        val avgHp = when {
            cpProduct == 0.0 -> h1p + h2p
            abs(h1p - h2p) <= 180.0 -> (h1p + h2p) / 2.0
            h1p + h2p < 360.0 -> (h1p + h2p + 360.0) / 2.0
            else -> (h1p + h2p - 360.0) / 2.0
        }

        val t = 1.0 -
            0.17 * cos(Math.toRadians(avgHp - 30.0)) +
            0.24 * cos(Math.toRadians(2.0 * avgHp)) +
            0.32 * cos(Math.toRadians(3.0 * avgHp + 6.0)) -
            0.20 * cos(Math.toRadians(4.0 * avgHp - 63.0))

        val dTheta = 30.0 * exp(-square((avgHp - 275.0) / 25.0))
        val rc = 2.0 * sqrt(pow7(avgCp) / (pow7(avgCp) + POW25_7))
        val sl = 1.0 + (0.015 * square(avgLp - 50.0)) / sqrt(20.0 + square(avgLp - 50.0))
        val sc = 1.0 + 0.045 * avgCp
        val sh = 1.0 + 0.015 * avgCp * t
        val rt = -sin(Math.toRadians(2.0 * dTheta)) * rc

        val termL = dLp / (kL * sl)
        val termC = dCp / (kC * sc)
        val termH = dHp / (kH * sh)

        return sqrt(termL * termL + termC * termC + termH * termH + rt * termC * termH)
    }

    private const val POW25_7 = 6103515625.0 // 25^7

    private fun square(x: Double) = x * x
    private fun pow7(x: Double): Double {
        val sq = x * x
        return sq * sq * sq * x
    }

    /** atan2(b, a) in degrees, normalised to [0,360). */
    private fun hueDegrees(b: Double, a: Double): Double {
        if (a == 0.0 && b == 0.0) return 0.0
        val degrees = Math.toDegrees(atan2(b, a))
        return if (degrees < 0.0) degrees + 360.0 else degrees
    }
}
