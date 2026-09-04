package com.paintmixer.core.color

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Hue-direction tests per PLAN.md section 2.5. These primaries are chosen
 * (not measured) to have the right qualitative per-channel reflectance
 * shape for each pigment name -- e.g. "cyan" absorbs red, reflects
 * green/blue -- which is all single-constant Kubelka-Munk needs to get the
 * subtractive-mixing direction right. Exact reflectance numbers don't
 * matter; which channel dominates the mix does.
 */
class KubelkaMunkTest {

    private val yellow = LinearRgb(0.95, 0.85, 0.05)
    private val cyan = LinearRgb(0.05, 0.85, 0.85)
    private val magenta = LinearRgb(0.85, 0.05, 0.85)
    private val ultramarine = LinearRgb(0.05, 0.10, 0.55)

    private fun mix50(a: LinearRgb, b: LinearRgb) =
        KubelkaMunk.mix(listOf(a, b), listOf(1.0, 1.0), listOf(1.0, 1.0))

    @Test
    fun `yellow plus ultramarine is green -- G is the dominant channel`() {
        val result = mix50(yellow, ultramarine)
        assertTrue(result.g > result.r && result.g > result.b, "expected G-dominant, got $result")
    }

    @Test
    fun `yellow plus cyan is green -- G is the dominant channel`() {
        val result = mix50(yellow, cyan)
        assertTrue(result.g > result.r && result.g > result.b, "expected G-dominant, got $result")
    }

    @Test
    fun `magenta plus yellow is red -- R is the dominant channel`() {
        val result = mix50(magenta, yellow)
        assertTrue(result.r > result.g && result.r > result.b, "expected R-dominant, got $result")
    }

    @Test
    fun `cyan plus magenta is blue -- B is the dominant channel`() {
        val result = mix50(cyan, magenta)
        assertTrue(result.b > result.r && result.b > result.g, "expected B-dominant, got $result")
    }

    @Test
    fun `a naive linear average fails where Kubelka-Munk succeeds`() {
        // Same pair as the first test, but averaged in linear RGB instead of
        // mixed via K-M. PLAN.md is explicit that nobody should "simplify"
        // the mixer into this later, so pin down that it actually breaks.
        val naiveAverage = LinearRgb(
            r = (yellow.r + ultramarine.r) / 2.0,
            g = (yellow.g + ultramarine.g) / 2.0,
            b = (yellow.b + ultramarine.b) / 2.0
        )
        assertTrue(
            naiveAverage.r >= naiveAverage.g,
            "expected the naive average to NOT be green (R should dominate over G), got $naiveAverage"
        )

        val kmResult = mix50(yellow, ultramarine)
        assertTrue(
            kmResult.g > kmResult.r,
            "Kubelka-Munk should give a green result where the naive average doesn't: $kmResult"
        )
    }

    @Test
    fun `identical colours at any ratio give the same colour -- idempotence`() {
        val x = LinearRgb(0.3, 0.6, 0.2)
        val ratios = listOf(1.0 to 1.0, 3.0 to 1.0, 1.0 to 5.0, 7.0 to 2.0)
        for ((a, b) in ratios) {
            val result = KubelkaMunk.mix(listOf(x, x), listOf(a, b), listOf(1.0, 1.0))
            assertEquals(x.r, result.r, 1e-9)
            assertEquals(x.g, result.g, 1e-9)
            assertEquals(x.b, result.b, 1e-9)
        }
    }

    @Test
    fun `parts of 1 to 0 returns exactly the first colour`() {
        val result = KubelkaMunk.mix(listOf(yellow, ultramarine), listOf(1.0, 0.0), listOf(1.0, 1.0))
        assertEquals(yellow.r, result.r, 1e-9)
        assertEquals(yellow.g, result.g, 1e-9)
        assertEquals(yellow.b, result.b, 1e-9)
    }

    @Test
    fun `ks and reflectance round trip`() {
        val samples = listOf(0.01, 0.1, 0.3, 0.5, 0.7, 0.9, 0.99)
        for (r in samples) {
            val back = KubelkaMunk.ksToReflectance(KubelkaMunk.reflectanceToKs(r))
            assertEquals(r, back, 1e-9, "reflectance $r")
        }
    }

    @Test
    fun `tinting strength shifts the mix toward the stronger paint`() {
        val weak = mix50(yellow, ultramarine)
        val strongWhite = KubelkaMunk.mix(
            listOf(yellow, ultramarine), listOf(1.0, 1.0), listOf(1.0, 8.0)
        )
        // Boosting ultramarine's strength should pull every channel closer to ultramarine's own value.
        assertTrue(
            kotlin.math.abs(strongWhite.b - ultramarine.b) < kotlin.math.abs(weak.b - ultramarine.b),
            "expected higher strength to pull the mix toward ultramarine: weak=$weak strong=$strongWhite"
        )
    }
}
