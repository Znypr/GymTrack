package com.example.gymtrack.feature.editor.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetEntryKeypadInputTest {

    @Test
    fun tokensBuildExistingRepsAndWeightSyntax() {
        var value = TextFieldValue("", TextRange(0))
        value = insertSetEntryToken(value, "1")
        value = insertSetEntryToken(value, "0")
        value = insertSetEntryToken(value, "x ")
        value = insertSetEntryToken(value, "5")
        value = insertSetEntryToken(value, "0")

        assertEquals("10x 50", value.text)
        assertEquals(value.text.length, value.selection.start)
    }

    @Test
    fun tokensBuildMyoRepSyntax() {
        var value = TextFieldValue("", TextRange(0))
        value = insertSetEntryToken(value, "8")
        assertTrue(canInsertMyoRepPlus(value))
        value = insertSetEntryToken(value, "+")
        value = insertSetEntryToken(value, "4")
        assertTrue(canInsertMyoRepPlus(value))
        value = insertSetEntryToken(value, "+")
        value = insertSetEntryToken(value, "4")
        value = insertSetEntryToken(value, "x ")
        value = insertSetEntryToken(value, "5")
        value = insertSetEntryToken(value, "0")

        assertEquals("8+4+4x 50", value.text)
        assertEquals(value.text.length, value.selection.start)
    }

    @Test
    fun plusIsRejectedOutsideRepsSyntax() {
        assertFalse(canInsertMyoRepPlus(TextFieldValue("", TextRange(0))))
        assertFalse(canInsertMyoRepPlus(TextFieldValue("+8", TextRange(0))))
        assertFalse(canInsertMyoRepPlus(TextFieldValue("8+", TextRange(2))))
        assertFalse(canInsertMyoRepPlus(TextFieldValue("8x 50", TextRange(5))))
        assertFalse(canInsertMyoRepPlus(TextFieldValue("8s 30", TextRange(5))))
        assertTrue(canInsertMyoRepPlus(TextFieldValue("8+4", TextRange(3))))
    }

    @Test
    fun insertionReplacesSelectedText() {
        val value = TextFieldValue("10x 50", TextRange(4, 6))
        val updated = insertSetEntryToken(value, "60")

        assertEquals("10x 60", updated.text)
        assertEquals(6, updated.selection.start)
    }

    @Test
    fun backspaceDeletesSelectionOrPreviousCharacter() {
        val selected = backspaceSetEntry(TextFieldValue("10x 50", TextRange(4, 6)))
        assertEquals("10x ", selected.text)
        assertEquals(4, selected.selection.start)

        val single = backspaceSetEntry(TextFieldValue("10x 5", TextRange(5)))
        assertEquals("10x ", single.text)
        assertEquals(4, single.selection.start)
    }
}
