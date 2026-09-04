package com.paintmixer.core.color

import kotlin.math.hypot

/**
 * One paint on the palette, as the solver sees it -- PLAN.md section 2.7.
 * [id] is opaque (the app supplies its own `PaletteColor.id`); [linear] is
 * the canonical normalised-linear value from PLAN.md section 3, [lab] its
 * cached derivation, [strength] the tinting-strength scalar from 2.5.
 */
data class PaintColor(
    val id: String,
    val name: String,
    val linear: LinearRgb,
    val lab: Lab,
    val strength: Double = 1.0
)

/** One candidate mix: a pure colour when [second] is null, else two mixed at [partsFirst]:[partsSecond]. */
data class Recipe(
    val first: PaintColor,
    val second: PaintColor?,
    val partsFirst: Double,
    val partsSecond: Double,
    val predictedLab: Lab,
    val deltaE: Double
)

/** Why the best available recipe is a poor match, PLAN.md section 2.7 "Gamut diagnosis". */
enum class GamutIssue {
    /** The best recipe is close enough (deltaE within budget) -- no gamut problem. */
    NONE,
    TOO_LIGHT_FOR_PALETTE,
    TOO_DARK_FOR_PALETTE,
    TOO_SATURATED_FOR_PALETTE,

    /** Best recipe is still poor, but none of the specific diagnoses above explain why. */
    UNREACHABLE_OTHER
}

data class SolveResult(val recipes: List<Recipe>, val gamutIssue: GamutIssue)

/**
 * Brute-force recipe search, PLAN.md section 2.7. Two-colour mixes over a
 * hand-mixable ratio grid, plus every pure colour, sorted by [deltaE] and
 * deduplicated to the best ratio per colour pair. At 20 palette colours
 * that's ~5,700 evaluations -- sub-millisecond; run on a background
 * dispatcher regardless, never on the caller's thread if that's UI-bound.
 *
 * The 3-colour fallback search described alongside this in the plan is
 * deferred to Phase 6 (it's explicitly UI-gated there, "offer it behind a
 * button rather than by default").
 */
object Solver {

    /** Ratios a person can mix by eye, PLAN.md section 2.7 -- not a fine grid. */
    val PRACTICAL_RATIOS: List<Pair<Double, Double>> = run {
        val base = listOf(
            1.0 to 1.0, 2.0 to 1.0, 3.0 to 1.0, 4.0 to 1.0, 5.0 to 1.0, 6.0 to 1.0, 8.0 to 1.0,
            3.0 to 2.0, 5.0 to 2.0, 7.0 to 2.0, 4.0 to 3.0, 5.0 to 3.0, 5.0 to 4.0, 7.0 to 3.0
        )
        base + base.filter { it.first != it.second }.map { it.second to it.first }
    }

    private const val DEFAULT_MAX_RESULTS = 5
    private const val GAMUT_DELTA_E_THRESHOLD = 6.0
    private const val GAMUT_LIGHTNESS_MARGIN = 3.0

    fun solve(
        palette: List<PaintColor>,
        target: Lab,
        maxResults: Int = DEFAULT_MAX_RESULTS,
        deltaE: (Lab, Lab) -> Double = DeltaE::ciede2000
    ): SolveResult {
        require(palette.isNotEmpty()) { "palette must not be empty" }

        val candidates = mutableListOf<Recipe>()

        // A pure colour may be the best answer.
        for (color in palette) {
            candidates += Recipe(color, null, 1.0, 0.0, color.lab, deltaE(color.lab, target))
        }

        // Every unordered pair, at every practical ratio.
        for (i in palette.indices) {
            for (j in i + 1 until palette.size) {
                val a = palette[i]
                val b = palette[j]
                for ((partsA, partsB) in PRACTICAL_RATIOS) {
                    val predicted = KubelkaMunk.mix(
                        colors = listOf(a.linear, b.linear),
                        parts = listOf(partsA, partsB),
                        strengths = listOf(a.strength, b.strength)
                    ).toXyz().toLab()
                    candidates += Recipe(a, b, partsA, partsB, predicted, deltaE(predicted, target))
                }
            }
        }

        val best = candidates
            .sortedBy { it.deltaE }
            .distinctBy { colorPairKey(it) }
            .take(maxResults)

        val gamutIssue = when {
            best.isEmpty() -> GamutIssue.NONE
            best.first().deltaE <= GAMUT_DELTA_E_THRESHOLD -> GamutIssue.NONE
            else -> diagnoseGamut(palette, target)
        }

        return SolveResult(best, gamutIssue)
    }

    private fun colorPairKey(recipe: Recipe): Set<String> = setOfNotNull(recipe.first.id, recipe.second?.id)

    private fun diagnoseGamut(palette: List<PaintColor>, target: Lab): GamutIssue {
        val maxL = palette.maxOf { it.lab.l }
        val minL = palette.minOf { it.lab.l }
        val maxChroma = palette.maxOf { chroma(it.lab) }
        return when {
            target.l > maxL + GAMUT_LIGHTNESS_MARGIN -> GamutIssue.TOO_LIGHT_FOR_PALETTE
            target.l < minL - GAMUT_LIGHTNESS_MARGIN -> GamutIssue.TOO_DARK_FOR_PALETTE
            chroma(target) > maxChroma -> GamutIssue.TOO_SATURATED_FOR_PALETTE
            else -> GamutIssue.UNREACHABLE_OTHER
        }
    }

    private fun chroma(lab: Lab): Double = hypot(lab.a, lab.b)
}
