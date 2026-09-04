package com.paintmixer.core.color

import kotlin.math.sqrt

/**
 * Subtractive (paint) colour mixing via single-constant Kubelka-Munk,
 * applied per linear-RGB channel -- PLAN.md section 2.5. A practical
 * approximation of a spectral model, not physically exact (EVALUATION.md
 * assumption 1), but the thing that makes "yellow + blue = green" come out
 * right instead of the grey mud a linear RGB average produces.
 *
 * Never average colours in sRGB or linear RGB directly to mix paint --
 * that models mixing *light*. Always go through [mix].
 */
object KubelkaMunk {

    private const val REFLECTANCE_MIN = 1e-3
    private const val REFLECTANCE_MAX = 1.0 - 1e-6

    /** Reflectance -> K/S (absorption-to-scattering ratio). */
    fun reflectanceToKs(reflectance: Double): Double {
        val r = reflectance.coerceIn(REFLECTANCE_MIN, REFLECTANCE_MAX)
        return (1.0 - r) * (1.0 - r) / (2.0 * r)
    }

    /** K/S -> reflectance, the inverse of [reflectanceToKs]. */
    fun ksToReflectance(ks: Double): Double = 1.0 + ks - sqrt(ks * ks + 2.0 * ks)

    /**
     * Mix [colors] in the given [parts] (a ratio, need not sum to 1),
     * weighted by each colour's tinting [strengths] (PLAN.md section 2.5 --
     * default 1.0 per paint; white/black need much higher values or they
     * come out under-weighted, since single-constant K-M doesn't otherwise
     * account for real paints' very different hiding power).
     *
     * [colors], [parts] and [strengths] must be the same size and index-
     * aligned (colors[i] is mixed with strengths[i] at parts[i]).
     */
    fun mix(colors: List<LinearRgb>, parts: List<Double>, strengths: List<Double>): LinearRgb {
        require(colors.size == parts.size && colors.size == strengths.size) {
            "colors, parts and strengths must be the same size"
        }
        require(colors.isNotEmpty()) { "need at least one colour to mix" }

        val weights = colors.indices.map { i -> parts[i] * strengths[i] }
        val totalWeight = weights.sum()
        require(totalWeight > 0.0) { "total weight (parts * strength) must be positive" }
        val normalisedWeights = weights.map { it / totalWeight }

        fun mixChannel(channel: (LinearRgb) -> Double): Double {
            val ks = colors.indices.sumOf { i -> normalisedWeights[i] * reflectanceToKs(channel(colors[i])) }
            return ksToReflectance(ks)
        }

        return LinearRgb(
            r = mixChannel(LinearRgb::r),
            g = mixChannel(LinearRgb::g),
            b = mixChannel(LinearRgb::b)
        )
    }
}
