package com.paintmixer.core.color

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeltaETest {

    @Test
    fun `cie76 is zero for identical colours`() {
        val lab = Lab(50.0, 10.0, -20.0)
        assertEquals(0.0, DeltaE.cie76(lab, lab), 1e-12)
    }

    @Test
    fun `cie76 is plain euclidean distance in Lab`() {
        val a = Lab(50.0, 0.0, 0.0)
        val b = Lab(53.0, 4.0, 0.0)
        // 3-4-5 triangle on L/a.
        assertEquals(5.0, DeltaE.cie76(a, b), 1e-9)
    }

    @Test
    fun `ciede2000 is zero for identical colours`() {
        val lab = Lab(62.0, -14.0, 33.0)
        assertEquals(0.0, DeltaE.ciede2000(lab, lab), 1e-9)
    }

    @Test
    fun `ciede2000 is symmetric`() {
        val a = Lab(50.0, 2.5, 0.0)
        val b = Lab(73.0, 25.0, -18.0)
        assertEquals(DeltaE.ciede2000(a, b), DeltaE.ciede2000(b, a), 1e-9)
    }

    /**
     * The published Sharma/Wu/Dalal (2005) 34-pair CIEDE2000 test dataset --
     * "The CIEDE2000 Color-Difference Formula: Implementation Notes,
     * Supplementary Test Data, and Mathematical Observations". These pairs
     * are specifically chosen to exercise the formula's sharp edges: zero
     * chroma, the a'/hue-angle correction near the negative a* axis, and
     * the average-hue quadrant logic -- exactly where a naive
     * transcription of the formula tends to go subtly wrong. Values as
     * published (4 decimal places); PLAN.md section 2.4 requires validating
     * against this dataset before trusting the implementation.
     */
    private data class Case(val lab1: Lab, val lab2: Lab, val expected: Double)

    private val sharmaDataset = listOf(
        Case(Lab(50.0000, 2.6772, -79.7751), Lab(50.0000, 0.0000, -82.7485), 2.0425),
        Case(Lab(50.0000, 3.1571, -77.2803), Lab(50.0000, 0.0000, -82.7485), 2.8615),
        Case(Lab(50.0000, 2.8361, -74.0200), Lab(50.0000, 0.0000, -82.7485), 3.4412),
        Case(Lab(50.0000, -1.3802, -84.2814), Lab(50.0000, 0.0000, -82.7485), 1.0000),
        Case(Lab(50.0000, -1.1848, -84.8006), Lab(50.0000, 0.0000, -82.7485), 1.0000),
        Case(Lab(50.0000, -0.9009, -85.5211), Lab(50.0000, 0.0000, -82.7485), 1.0000),
        Case(Lab(50.0000, 0.0000, 0.0000), Lab(50.0000, -1.0000, 2.0000), 2.3669),
        Case(Lab(50.0000, -1.0000, 2.0000), Lab(50.0000, 0.0000, 0.0000), 2.3669),
        Case(Lab(50.0000, 2.4900, -0.0010), Lab(50.0000, -2.4900, 0.0009), 7.1792),
        Case(Lab(50.0000, 2.4900, -0.0010), Lab(50.0000, -2.4900, 0.0010), 7.1792),
        Case(Lab(50.0000, 2.4900, -0.0010), Lab(50.0000, -2.4900, 0.0011), 7.2195),
        Case(Lab(50.0000, 2.4900, -0.0010), Lab(50.0000, -2.4900, 0.0012), 7.2195),
        Case(Lab(50.0000, -0.0010, 2.4900), Lab(50.0000, 0.0009, -2.4900), 4.8045),
        Case(Lab(50.0000, -0.0010, 2.4900), Lab(50.0000, 0.0010, -2.4900), 4.8045),
        Case(Lab(50.0000, -0.0010, 2.4900), Lab(50.0000, 0.0011, -2.4900), 4.7461),
        Case(Lab(50.0000, 2.5000, 0.0000), Lab(50.0000, 0.0000, -2.5000), 4.3065),
        Case(Lab(50.0000, 2.5000, 0.0000), Lab(73.0000, 25.0000, -18.0000), 27.1492),
        Case(Lab(50.0000, 2.5000, 0.0000), Lab(61.0000, -5.0000, 29.0000), 22.8977),
        Case(Lab(50.0000, 2.5000, 0.0000), Lab(56.0000, -27.0000, -3.0000), 31.9030),
        Case(Lab(50.0000, 2.5000, 0.0000), Lab(58.0000, 24.0000, 15.0000), 19.4535),
        Case(Lab(50.0000, 2.5000, 0.0000), Lab(50.0000, 3.1736, 0.5854), 1.0000),
        Case(Lab(50.0000, 2.5000, 0.0000), Lab(50.0000, 3.2972, 0.0000), 1.0000),
        Case(Lab(50.0000, 2.5000, 0.0000), Lab(50.0000, 1.8634, 0.5757), 1.0000),
        Case(Lab(50.0000, 2.5000, 0.0000), Lab(50.0000, 3.2592, 0.3350), 1.0000),
        Case(Lab(60.2574, -34.0099, 36.2677), Lab(60.4626, -34.1751, 39.4387), 1.2644),
        Case(Lab(63.0109, -31.0961, -5.8663), Lab(62.8187, -29.7946, -4.0864), 1.2630),
        Case(Lab(61.2901, 3.7196, -5.3901), Lab(61.4292, 2.2480, -4.9620), 1.8731),
        Case(Lab(35.0831, -44.1164, 3.7933), Lab(35.0232, -40.0716, 1.5901), 1.8645),
        Case(Lab(22.7233, 20.0904, -46.6940), Lab(23.0331, 14.9730, -42.5619), 2.0373),
        Case(Lab(36.4612, 47.8580, 18.3852), Lab(36.2715, 50.5065, 21.2231), 1.4146),
        Case(Lab(90.8027, -2.0831, 1.4410), Lab(91.1528, -1.6435, 0.0447), 1.4441),
        Case(Lab(90.9257, -0.5406, -0.9208), Lab(88.6381, -0.8985, -0.7239), 1.5381),
        Case(Lab(6.7747, -0.2908, -2.4247), Lab(5.8714, -0.0985, -2.2286), 0.6377),
        Case(Lab(2.0776, 0.0795, -1.1350), Lab(0.9033, -0.0636, -0.5514), 0.9082)
    )

    @Test
    fun `ciede2000 matches the Sharma et al 34-pair reference dataset`() {
        val failures = sharmaDataset.mapIndexedNotNull { index, case ->
            val actual = DeltaE.ciede2000(case.lab1, case.lab2)
            if (abs(actual - case.expected) > 1e-4) {
                "row ${index + 1}: expected ${case.expected}, got $actual (${case.lab1} vs ${case.lab2})"
            } else {
                null
            }
        }
        assertTrue(failures.isEmpty(), "CIEDE2000 mismatches:\n${failures.joinToString("\n")}")
    }
}
