package com.paintmixer.core.color

import kotlin.test.Test
import kotlin.test.assertEquals

class WhiteBalanceTest {

    @Test
    fun `the white reference itself normalises to the reference reflectance`() {
        val white = LinearRgb(0.6, 0.55, 0.62) // some raw, uncorrected camera reading of the card
        val result = WhiteBalance.normalise(white, white, referenceReflectance = 0.90)
        assertEquals(0.90, result.r, 1e-9)
        assertEquals(0.90, result.g, 1e-9)
        assertEquals(0.90, result.b, 1e-9)
    }

    @Test
    fun `a sample twice as bright as the reference normalises to twice the reflectance`() {
        val white = LinearRgb(0.5, 0.5, 0.5)
        val sample = LinearRgb(1.0, 1.0, 1.0)
        val result = WhiteBalance.normalise(sample, white, referenceReflectance = 0.90)
        assertEquals(1.80, result.r, 1e-9)
    }

    @Test
    fun `values brighter than the reference are not crushed to white -- clamp is above 1_0`() {
        val white = LinearRgb(0.1, 0.1, 0.1)
        val veryBright = LinearRgb(1.0, 1.0, 1.0) // 10x the reference
        val result = WhiteBalance.normalise(veryBright, white, referenceReflectance = 0.90)
        // 1.0 * (0.90 / 0.1) = 9.0, clamped down to the 4.0 ceiling -- but NOT down to 1.0.
        assertEquals(4.0, result.r, 1e-9)
    }

    @Test
    fun `a grey-card reflectance of 0_18 is honoured, not hardcoded to white paper`() {
        val white = LinearRgb(0.2, 0.2, 0.2)
        val result = WhiteBalance.normalise(white, white, referenceReflectance = 0.18)
        assertEquals(0.18, result.r, 1e-9)
    }

    @Test
    fun `channels normalise independently`() {
        val white = LinearRgb(0.4, 0.5, 0.6) // an imperfectly colour-cast reading of a neutral card
        val sample = LinearRgb(0.4, 0.5, 0.6)
        val result = WhiteBalance.normalise(sample, white, referenceReflectance = 0.90)
        // Each channel divides out its own cast, so a neutral sample stays neutral post-normalisation.
        assertEquals(result.r, result.g, 1e-9)
        assertEquals(result.g, result.b, 1e-9)
    }
}
