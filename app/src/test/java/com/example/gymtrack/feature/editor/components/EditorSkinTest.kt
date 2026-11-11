package com.example.gymtrack.feature.editor.components

import androidx.compose.ui.graphics.Color
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.ui.theme.NormalDarkColors
import com.example.gymtrack.core.ui.theme.NormalLightColors
import com.example.gymtrack.core.ui.theme.NotebookColors
import com.example.gymtrack.feature.editor.setRowDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The editor reads design tokens, not Material's default tints (wave-1 device finding). */
class EditorSkinTest {

    @Test
    fun `Normal keypad keys map to surface, surface2 and the amber action`() {
        for (colors in listOf(NormalDarkColors, NormalLightColors)) {
            assertEquals(colors.surface, colors.keypadKeyColors(KeypadKeyRole.DIGIT, notebook = false).container)
            assertEquals(colors.ink, colors.keypadKeyColors(KeypadKeyRole.DIGIT, notebook = false).content)
            assertEquals(colors.surface2, colors.keypadKeyColors(KeypadKeyRole.SPECIAL, notebook = false).container)
            val next = colors.keypadKeyColors(KeypadKeyRole.ACTION, notebook = false)
            assertEquals(colors.actionContainer, next.container)
            assertEquals(colors.onAction, next.content)
        }
        assertEquals(Color(0xFFF2B705), NormalDarkColors.keypadKeyColors(KeypadKeyRole.ACTION, notebook = false).container)
    }

    @Test
    fun `Notebook keys have no opaque fill and Next is blue pen`() {
        val digit = NotebookColors.keypadKeyColors(KeypadKeyRole.DIGIT, notebook = true)
        assertEquals(Color.Transparent, digit.container)
        assertEquals(NotebookColors.ink, digit.outline)
        assertEquals(Color.Transparent, NotebookColors.keypadKeyColors(KeypadKeyRole.SPECIAL, notebook = true).container)
        val next = NotebookColors.keypadKeyColors(KeypadKeyRole.ACTION, notebook = true)
        assertEquals(NotebookColors.action, next.outline)
        assertEquals(NotebookColors.onAction, next.content)
        assertEquals(0.75f, next.container.alpha, 0.01f)
    }

    private val earlier = listOf("      8x 70 (1'30'')")

    @Test
    fun `Notebook shows a ditto mark for a carried weight, Normal shows the weight muted`() {
        val display = setRowDisplay("      8x (2'00'')", earlier, WeightUnit.KG)
        assertEquals(DITTO_MARK, shownWeight(display, notebook = true))
        assertEquals("70", shownWeight(display, notebook = false))
        assertEquals("8 × \"", setDisplayText(display, NotebookColors, notebook = true).text)
        assertEquals("8 × 70", setDisplayText(display, NormalDarkColors, notebook = false).text)
    }

    @Test
    fun `a typed weight is never replaced by the ditto`() {
        val display = setRowDisplay("      8x 70 (2'00'')", earlier, WeightUnit.KG)
        assertEquals("70", shownWeight(display, notebook = true))
    }

    @Test
    fun `bodyweight has no weight to ditto`() {
        val display = setRowDisplay("      12x (2'00'')", emptyList(), WeightUnit.KG)
        assertNull(shownWeight(display, notebook = true))
    }
}
