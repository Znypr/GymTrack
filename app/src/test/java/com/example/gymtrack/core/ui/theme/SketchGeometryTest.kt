package com.example.gymtrack.core.ui.theme

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class SketchGeometryTest {

    private fun rect(seed: Int, pass: Int = 0, closed: Boolean = false) = SketchGeometry.roundedRect(
        width = 300f, height = 120f, seed = seed, pass = pass, cornerRadius = 28f,
        wobble = 3f, step = 6f, closed = closed, inset = 6f,
    )

    private fun SketchStroke.length(): Double =
        (1 until size).sumOf { hypot(xs[it] - xs[it - 1], ys[it] - ys[it - 1]).toDouble() }

    @Test
    fun `same seed draws the same outline every time`() {
        val a = rect(7)
        val b = rect(7)
        assertArrayEquals(a.xs, b.xs, 0f)
        assertArrayEquals(a.ys, b.ys, 0f)
        assertArrayEquals(a.pressure, b.pressure, 0f)
    }

    @Test
    fun `different seeds and passes draw differently`() {
        assertFalse(rect(7).xs.contentEquals(rect(8).xs))
        assertFalse(rect(7, pass = 0).xs.contentEquals(rect(7, pass = 1).xs))
        assertFalse(
            SketchGeometry.cornerRadii(300f, 120f, 1, 28f).contentEquals(SketchGeometry.cornerRadii(300f, 120f, 2, 28f)),
        )
    }

    @Test
    fun `outline stays inside the element bounds`() {
        for (seed in 0 until 50) for (pass in 0..1) {
            val s = rect(seed, pass)
            for (i in 0 until s.size) {
                assertTrue(s.xs[i] in 0f..300f)
                assertTrue(s.ys[i] in 0f..120f)
            }
        }
    }

    @Test
    fun `outline wobbles but follows the box`() {
        val s = rect(3)
        val top = (0 until s.size).filter { s.ys[it] < 20f && s.xs[it] in 60f..240f }.map { s.ys[it] }
        assertTrue(top.isNotEmpty())
        assertTrue(top.max() - top.min() > 0.5f)
        assertTrue(top.max() - top.min() < 10f)
    }

    @Test
    fun `closed outline meets itself and open stroke overshoots`() {
        val closed = rect(5, closed = true)
        val gap = hypot(closed.xs.first() - closed.xs.last(), closed.ys.first() - closed.ys.last())
        assertTrue("closed gap $gap", gap < 1f)
        assertTrue(rect(5).length() > closed.length() * 1.02)
    }

    @Test
    fun `open strokes vary in weight and taper at the ends`() {
        val s = rect(11)
        val mid = s.pressure[s.size / 2]
        assertTrue(s.pressure.first() < mid || s.pressure.last() < mid)
        assertTrue(s.pressure.max() - s.pressure.min() > 0.2f)
        assertTrue(s.pressure.all { it in 0.3f..1.25f })
    }

    @Test
    fun `lines, polygons and ellipses are deterministic and near their target`() {
        val l1 = SketchGeometry.line(0f, 0f, 100f, 0f, seed = 4, wobble = 2f, step = 5f)
        val l2 = SketchGeometry.line(0f, 0f, 100f, 0f, seed = 4, wobble = 2f, step = 5f)
        assertArrayEquals(l1.ys, l2.ys, 0f)
        assertTrue(l1.ys.all { abs(it) <= 3f })

        val e = SketchGeometry.ellipse(50f, 50f, 20f, 20f, seed = 1, pass = 0, wobble = 1f, step = 2f)
        for (i in 0 until e.size) {
            val r = hypot(e.xs[i] - 50f, e.ys[i] - 50f)
            assertTrue("r=$r", r in 17f..23f)
        }

        val p = SketchGeometry.polygon(floatArrayOf(0f, 40f, 20f), floatArrayOf(0f, 0f, 30f), seed = 2, pass = 0, wobble = 1f, step = 3f)
        assertTrue(p.size > 10)
    }
}
