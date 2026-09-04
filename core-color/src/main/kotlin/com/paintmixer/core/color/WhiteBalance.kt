package com.paintmixer.core.color

/**
 * Von Kries white-balance normalisation against a tapped white/grey
 * reference card -- PLAN.md section 2.6. Mandatory: there is no
 * unnormalised path in this app (see PLAN.md "Scope").
 */
object WhiteBalance {

    private const val NORMALISED_MAX = 4.0

    /**
     * @param linear the raw linear-light pixel sample to normalise.
     * @param whiteReference the raw linear-light sample of the tapped white/grey card.
     * @param referenceReflectance the card's known reflectance (0.90 for white paper, 0.18 for
     *   an 18% grey card -- PLAN.md section 3, `Palette.whiteRefReflectance`).
     *
     * The upper clamp is above 1.0 on purpose: values brighter than the reference are legal and
     * must not be crushed to white.
     */
    fun normalise(linear: LinearRgb, whiteReference: LinearRgb, referenceReflectance: Double): LinearRgb {
        fun channel(sample: Double, white: Double): Double =
            (sample * (referenceReflectance / white)).coerceIn(0.0, NORMALISED_MAX)

        return LinearRgb(
            r = channel(linear.r, whiteReference.r),
            g = channel(linear.g, whiteReference.g),
            b = channel(linear.b, whiteReference.b)
        )
    }
}
