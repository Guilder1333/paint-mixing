package com.paintmixer.core.color

import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SolverTest {

    /** A hand-written 12-colour synthetic palette, PLAN.md "Phase 1 -- Done when". */
    private val palette = listOf(
        paint("white", 0.90, 0.90, 0.90, strength = 8.0),
        paint("black", 0.05, 0.05, 0.05, strength = 2.0),
        paint("yellow", 0.95, 0.85, 0.05),
        paint("cyan", 0.05, 0.85, 0.85),
        paint("magenta", 0.85, 0.05, 0.85),
        paint("ultramarine", 0.05, 0.10, 0.55),
        paint("orange", 0.90, 0.45, 0.05),
        paint("green", 0.10, 0.55, 0.15),
        paint("violet", 0.35, 0.10, 0.55),
        paint("brown", 0.35, 0.20, 0.10),
        paint("pink", 0.90, 0.60, 0.65),
        paint("grey", 0.50, 0.50, 0.50)
    )

    private fun paint(name: String, r: Double, g: Double, b: Double, strength: Double = 1.0): PaintColor {
        val linear = LinearRgb(r, g, b)
        return PaintColor(id = name, name = name, linear = linear, lab = linear.toXyz().toLab(), strength = strength)
    }

    private fun byName(name: String) = palette.first { it.name == name }

    @Test
    fun `solver reconstructs a known mix and runs well under 50ms`() {
        val cyan = byName("cyan")
        val yellow = byName("yellow")
        val knownMix = KubelkaMunk.mix(
            colors = listOf(cyan.linear, yellow.linear),
            parts = listOf(1.0, 1.0),
            strengths = listOf(cyan.strength, yellow.strength)
        )
        val target = knownMix.toXyz().toLab()

        lateinit var result: SolveResult
        val elapsedMs = measureNanoTime { result = Solver.solve(palette, target) } / 1_000_000.0

        assertTrue(elapsedMs < 50.0, "solve() took ${elapsedMs}ms, budget is 50ms")
        assertTrue(result.recipes.isNotEmpty())

        val top = result.recipes.first()
        assertTrue(top.deltaE < 0.5, "expected the solver to essentially rediscover the exact mix, got deltaE=${top.deltaE}")
        assertEquals(setOf("cyan", "yellow"), setOfNotNull(top.first.id, top.second?.id))
        assertEquals(GamutIssue.NONE, result.gamutIssue)
    }

    @Test
    fun `recipes are sorted ascending by deltaE and deduplicated by colour pair`() {
        val target = Lab(45.0, 20.0, -10.0)
        val result = Solver.solve(palette, target, maxResults = 5)

        val deltaEs = result.recipes.map { it.deltaE }
        assertEquals(deltaEs.sorted(), deltaEs, "recipes must be sorted ascending by deltaE")

        val pairKeys = result.recipes.map { setOfNotNull(it.first.id, it.second?.id) }
        assertEquals(pairKeys.distinct().size, pairKeys.size, "each colour pair should appear at most once")
    }

    @Test
    fun `a pure palette colour can win outright`() {
        // Ask for exactly what's already on the palette -- the best recipe should be that single colour.
        val grey = byName("grey")
        val result = Solver.solve(palette, grey.lab)
        val top = result.recipes.first()
        assertEquals(null, top.second, "expected a single pure colour to win")
        assertEquals("grey", top.first.id)
        assertTrue(top.deltaE < 1e-6)
    }

    @Test
    fun `a target far too light for the palette is flagged, not silently mixed`() {
        // The main 12-colour palette's "white" is itself so light (L~96) that pure white
        // (L=100) is a legitimately close match -- deliberately test against a palette with no
        // light colours instead, where "add white" is actually the right diagnosis.
        val noHighlights = listOf(
            paint("mid-red", 0.45, 0.10, 0.10),
            paint("mid-green", 0.10, 0.45, 0.15),
            paint("mid-blue", 0.10, 0.15, 0.45)
        )
        val tooLight = Lab(100.0, 0.0, 0.0)
        val result = Solver.solve(noHighlights, tooLight)
        assertEquals(GamutIssue.TOO_LIGHT_FOR_PALETTE, result.gamutIssue)
    }

    @Test
    fun `a target far too dark for the palette is flagged, not silently mixed`() {
        val tooDark = Lab(0.0, 0.0, 0.0)
        val result = Solver.solve(palette, tooDark)
        assertEquals(GamutIssue.TOO_DARK_FOR_PALETTE, result.gamutIssue)
    }

    @Test
    fun `a target far too saturated for the palette is flagged, not silently mixed`() {
        val tooSaturated = Lab(50.0, 200.0, 200.0)
        val result = Solver.solve(palette, tooSaturated)
        assertEquals(GamutIssue.TOO_SATURATED_FOR_PALETTE, result.gamutIssue)
    }

    @Test
    fun `a reachable target has no gamut issue`() {
        val ultramarine = byName("ultramarine")
        val white = byName("white")
        val target = KubelkaMunk.mix(
            listOf(ultramarine.linear, white.linear), listOf(1.0, 2.0), listOf(ultramarine.strength, white.strength)
        ).toXyz().toLab()

        val result = Solver.solve(palette, target)
        assertEquals(GamutIssue.NONE, result.gamutIssue)
    }
}
