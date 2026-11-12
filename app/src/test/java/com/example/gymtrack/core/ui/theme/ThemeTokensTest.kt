package com.example.gymtrack.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.example.gymtrack.core.data.ThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** WCAG contrast for every theme (#333 acceptance: text at least 4.5:1, graphics 3:1). */
class ThemeTokensTest {

    private val all = mapOf(
        "Normal dark" to NormalDarkTokens,
        "Normal light" to NormalLightTokens,
        "Notebook" to NotebookTokens,
    )

    @Test
    fun `text roles reach 4_5 to 1 on page and surface`() {
        all.forEach { (name, tokens) ->
            val c = tokens.colors
            for (page in listOf(c.bg, c.surface)) {
                assertContrast("$name ink", c.ink, page, 4.5)
                assertContrast("$name inkMuted", c.inkMuted, page, 4.5)
                assertContrast("$name action", c.action, page, 4.5)
                assertContrast("$name warn", c.warn, page, 4.5)
                assertContrast("$name error", c.error, page, 4.5)
            }
            assertContrast("$name onAction", c.onAction, c.actionContainer.compositeOver(c.bg), 4.5)
        }
    }

    @Test
    fun `category marks reach 3 to 1 on the page`() {
        all.forEach { (name, tokens) ->
            val c = tokens.colors
            listOf(c.catPush, c.catPull, c.catLegs).forEach { assertContrast("$name category", it, c.bg, 3.0) }
        }
    }

    @Test
    fun `chart series reach 3 to 1 on page and surface and stay distinct`() {
        all.forEach { (name, tokens) ->
            val c = tokens.colors
            assertTrue("$name has at least 6 series", c.series.size >= 6)
            assertEquals("$name series are distinct", c.series.size, c.series.toSet().size)
            c.series.forEachIndexed { i, color ->
                assertContrast("$name series $i on page", color, c.bg, 3.0)
                assertContrast("$name series $i on surface", color, c.surface, 3.0)
            }
        }
    }

    @Test
    fun `ink stays readable on the Notebook highlighter`() {
        val c = NotebookColors
        assertContrast("ink on highlighter", c.ink, c.active.compositeOver(c.bg), 4.5)
    }

    @Test
    fun `notebook action button text is readable on its wash`() {
        val c = NotebookColors
        assertContrast("blue pen on wash", c.onAction, c.actionContainer.copy(alpha = 0.75f).compositeOver(c.bg), 4.5)
    }

    @Test
    fun `choices resolve to the right token set`() {
        assertSame(NormalDarkTokens, tokensFor(ThemeChoice.NORMAL_DARK, systemDark = false))
        assertSame(NormalLightTokens, tokensFor(ThemeChoice.NORMAL_LIGHT, systemDark = true))
        assertSame(NotebookTokens, tokensFor(ThemeChoice.NOTEBOOK, systemDark = true))
        assertSame(NormalDarkTokens, tokensFor(ThemeChoice.SYSTEM, systemDark = true))
        assertSame(NormalLightTokens, tokensFor(ThemeChoice.SYSTEM, systemDark = false))
    }

    @Test
    fun `scale matches the spec`() {
        val t = GymTrackSpacing()
        assertEquals(listOf(4f, 8f, 12f, 16f, 24f, 32f), listOf(t.xxs, t.xs, t.sm, t.md, t.lg, t.xl).map { it.value })
        assertTrue(t.touchTarget.value >= 48f)
        val r = GymTrackRadii()
        assertEquals(listOf(8f, 14f, 24f), listOf(r.small, r.card, r.sheet).map { it.value })
        val n = NormalType
        assertEquals(listOf(28f, 22f, 15f, 14f, 12f), listOf(n.display, n.title, n.body, n.small, n.label).map { it.fontSize.value })
        all.values.forEach { assertTrue(it.type.setNumber.fontSize.value >= 17f) }
    }

    private fun assertContrast(what: String, fg: Color, bg: Color, min: Double) {
        val ratio = contrast(fg.compositeOver(bg), bg)
        assertTrue("$what contrast ${"%.2f".format(ratio)} below $min", ratio >= min)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(c: Color): Double {
        fun ch(v: Float): Double = if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }
}
