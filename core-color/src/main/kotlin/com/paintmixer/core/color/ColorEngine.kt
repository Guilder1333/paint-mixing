package com.paintmixer.core.color

/**
 * Marker for the colour engine module described in PLAN.md section 2:
 * sRGB<->linear, linear sRGB<->XYZ<->CIELAB, Delta-E, Kubelka-Munk mixing,
 * white-balance normalisation and the recipe solver.
 *
 * Deliberately empty until Phase 1. No android.graphics.* imports may ever
 * land in this module -- that is what keeps it testable on the plain JVM.
 */
object ColorEngine {
    const val PHASE: Int = 0
}
