package com.paintmixer.core.color

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves the :core-color JVM test wiring works end to end. Replace/extend
 * with the real Phase 1 tests from PLAN.md section 2 (round-trips, hue
 * assertions for Kubelka-Munk mixing, CIEDE2000 against the Sharma dataset).
 */
class ColorEngineSmokeTest {
    @Test
    fun `module is wired up`() {
        assertEquals(0, ColorEngine.PHASE)
    }
}
