package com.paintmixer.core.color

import kotlin.test.Test
import kotlin.test.assertEquals

class ColorSpaceTest {

    @Test
    fun `srgb channel to linear and back round trips within float tolerance`() {
        // Tolerance, not bit-exactness: pow(2.4) and pow(1/2.4) are transcendental and don't
        // perfectly invert each other, including right at the two functions' breakpoint.
        val samples = listOf(0.0, 0.01, 0.04045, 0.04046, 0.2, 0.5, 0.7726, 1.0)
        for (s in samples) {
            val back = linearChannelToSrgb(srgbChannelToLinear(s))
            assertEquals(s, back, 1e-6, "channel $s")
        }
    }

    @Test
    fun `srgb to lab to srgb round trips within tolerance`() {
        val colors = listOf(
            Srgb(1.0, 1.0, 1.0),
            Srgb(0.0, 0.0, 0.0),
            Srgb(0.5, 0.5, 0.5),
            Srgb(0.8, 0.2, 0.1),
            Srgb(0.1, 0.6, 0.9),
            Srgb(0.9, 0.9, 0.1),
            Srgb(0.05, 0.05, 0.05)
        )
        for (c in colors) {
            val back = c.toLab().toSrgb()
            assertEquals(c.r, back.r, 1e-6, "r for $c")
            assertEquals(c.g, back.g, 1e-6, "g for $c")
            assertEquals(c.b, back.b, 1e-6, "b for $c")
        }
    }

    @Test
    fun `linear to xyz to linear round trips within tolerance`() {
        // The forward and inverse matrices are independently-published, rounded-to-7-figures
        // constants (PLAN.md 2.2), not an algebraically exact inverse pair -- so this is a
        // tolerance check, not bit-exactness.
        val colors = listOf(
            LinearRgb(1.0, 1.0, 1.0),
            LinearRgb(0.0, 0.0, 0.0),
            LinearRgb(0.2, 0.8, 0.4),
            LinearRgb(1.5, 0.3, 2.7) // legal above 1.0 after white-balance normalisation
        )
        for (c in colors) {
            val back = c.toXyz().toLinearRgb()
            assertEquals(c.r, back.r, 1e-6, "r for $c")
            assertEquals(c.g, back.g, 1e-6, "g for $c")
            assertEquals(c.b, back.b, 1e-6, "b for $c")
        }
    }

    @Test
    fun `D65 white point maps to L=100 a=0 b=0`() {
        val lab = Xyz(0.95047, 1.00000, 1.08883).toLab()
        assertEquals(100.0, lab.l, 1e-3)
        assertEquals(0.0, lab.a, 1e-3)
        assertEquals(0.0, lab.b, 1e-3)
    }

    @Test
    fun `black maps to L=0`() {
        val lab = Xyz(0.0, 0.0, 0.0).toLab()
        assertEquals(0.0, lab.l, 1e-9)
        assertEquals(0.0, lab.a, 1e-9)
        assertEquals(0.0, lab.b, 1e-9)
    }
}
