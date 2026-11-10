package com.example.gymtrack.feature.editor

import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.ui.theme.NormalDarkTokens
import com.example.gymtrack.feature.editor.components.EditorSetKeypad
import com.example.gymtrack.feature.editor.components.setEntryKeyTestTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * #362: the device simulation typed `11x 175` and saved `1x 175`. These taps are injected with the
 * frame clock paused, so nothing recomposes between them (faster than any human, no idle wait):
 * every tap must still land on the row the keypad owns at that moment.
 */
class EditorKeypadRapidTapTest {
    @get:Rule
    val rule = createComposeRule()

    private fun tap(label: String) {
        rule.onNodeWithTag(setEntryKeyTestTag(label)).performTouchInput { click() }
    }

    private fun type(keys: String) = keys.forEach { key ->
        tap(
            when (key) {
                'x' -> "X"
                '\n' -> "Enter"
                else -> key.toString()
            },
        )
    }

    private fun keypadOn(driver: NoteEditorDriver) {
        rule.setContent { GymTrackTheme(tokens = NormalDarkTokens) { EditorSetKeypad(driver.state) } }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
    }

    @Test
    fun tappingOneTwiceWithoutIdleInsertsTwoOnes() {
        val d = NoteEditorDriver()
        d.typeAndEnter(0, "Leg press")
        keypadOn(d)
        tap("1")
        tap("1")
        assertEquals("11", d.texts[1])
    }

    @Test
    fun digitsRightAfterNextLandOnTheNewRow() {
        val d = NoteEditorDriver()
        d.typeAndEnter(0, "Leg press")
        keypadOn(d)
        d.advance(60)
        type("8x175\n")
        type("11x175")
        assertEquals("      8x 175 (1'00'')", d.texts[1])
        assertEquals("11x 175", d.texts[2])
    }

    @Test
    fun fiftyRapidSetsKeepEveryDigit() {
        val d = NoteEditorDriver()
        d.typeAndEnter(0, "Leg press")
        keypadOn(d)
        repeat(50) {
            d.advance(30)
            type("11x175\n")
        }
        val sets = d.texts.drop(1).filter { it.isNotBlank() }
        assertEquals(50, sets.size)
        sets.forEach { assertEquals("11x 175", it.trim().substringBefore(" (")) }
    }
}
