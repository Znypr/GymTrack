package com.example.gymtrack.feature.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.util.buildNoteRowMetadata
import com.example.gymtrack.feature.editor.components.hintShownAtKeypad
import com.example.gymtrack.feature.editor.guardrail.GuardrailRule
import com.example.gymtrack.feature.editor.guardrail.SetHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #304 in the editor: hints at Enter never block it or change data; Fix / Keep do the rest. */
class NoteEditorGuardrailFlowTest {

    private fun history(): SetHistory = SetHistory.build(
        (1..4).map { n ->
            val lines = listOf("Bench press", "      8x 80 (2'00'')", "      8x 80 (2'00'')")
            NoteLine(
                title = "",
                text = lines.joinToString("\n"),
                timestamp = n * 86_400_000L,
                rowMetadata = buildNoteRowMetadata(List(3) { "" }, List(3) { ExerciseFlag.BILATERAL }),
            )
        },
        excludeTimestamp = null,
        defaultUnit = "KG",
    )

    private fun NoteEditorDriver.keys(entry: String) =
        state.onKeypadInput(TextFieldValue(entry, TextRange(entry.length)))

    private fun benchWith(entry: String): NoteEditorDriver = NoteEditorDriver().apply {
        state.useHistory(history())
        advance(30)
        typeAndEnter(0, "Bench press")
        advance(90); keys(entry); state.onKeypadNext()
    }

    @Test
    fun `a slip is flagged at Enter but saved exactly as typed and the keypad moves on`() {
        val d = benchWith("8x 825")
        assertEquals("      8x 825 (1'30'')", d.texts[1])
        assertEquals(d.state.lines[2].id, d.state.keypadRowId)
        val hint = d.state.guardHints[d.state.lines[1].id]
        assertEquals(GuardrailRule.DECIMAL_SLIP, hint!!.rule)
        assertEquals(d.state.lines[1].id, d.state.latestHintRowId)
        // Drawn in the keypad row's hint line right under the set.
        assertTrue(d.state.hintShownAtKeypad(d.state.lines[1].id))
    }

    @Test
    fun `Fix replaces only the entry and keeps the times`() {
        val d = benchWith("8x 825")
        d.state.applyGuardHintFix(d.state.lines[1].id)
        assertEquals("      8x 82.5 (1'30'')", d.texts[1])
        assertTrue(d.state.guardHints.isEmpty())
        assertTrue(d.lastSave.text.contains("8x 82.5"))
        assertEquals(d.state.lines[2].id, d.state.keypadRowId)
    }

    @Test
    fun `Keep dismisses the hint and an unchanged re-commit stays quiet`() {
        val d = benchWith("8x 825")
        val row = d.state.lines[1]
        d.state.keepGuardHint(row.id)
        assertTrue(d.state.guardHints.isEmpty())
        assertEquals("      8x 825 (1'30'')", d.texts[1])
        // Edit the kept set and press Next without a change: no hint again.
        d.state.openKeypad(row.id)
        d.state.onKeypadNext()
        assertTrue(d.state.guardHints.isEmpty())
    }

    @Test
    fun `changing a kept set checks it again`() {
        val d = benchWith("8x 825")
        val row = d.state.lines[1]
        d.state.keepGuardHint(row.id)
        d.state.replaceSetEntry(1, "8x 925")
        assertNotNull(d.state.guardHints[row.id])
    }

    @Test
    fun `an Edit hint opens the set on the keypad`() {
        val d = benchWith("5x 250")
        val row = d.state.lines[1]
        assertEquals(GuardrailRule.OVER_RECENT_MAX, d.state.guardHints[row.id]!!.rule)
        d.state.applyGuardHintFix(row.id)
        assertEquals(row.id, d.state.keypadRowId)
        assertEquals(KeypadMode.EDIT, d.state.keypadMode)
        assertEquals("5x 250", d.state.keypadValue.text)
    }

    @Test
    fun `a double log is removed by Fix and comes back with Undo`() {
        val d = NoteEditorDriver().apply {
            advance(30)
            typeAndEnter(0, "Leg press")
            advance(90); keys("10x 100"); state.onKeypadNext()
            advance(3); keys("10x 100"); state.onKeypadNext()
        }
        val dup = d.state.lines[2]
        assertEquals(GuardrailRule.DUPLICATE, d.state.guardHints[dup.id]!!.rule)
        val deleted = d.state.applyGuardHintFix(dup.id)
        assertNotNull(deleted)
        assertFalse(d.state.lines.any { it.id == dup.id })
        d.state.undoDelete(deleted!!)
        assertTrue(d.state.lines.any { it.id == dup.id })
    }

    @Test
    fun `history-free rules run before the history is loaded`() {
        val d = NoteEditorDriver().apply {
            advance(30)
            typeAndEnter(0, "Leg press")
            advance(60); keys("8x"); state.onKeypadNext()
        }
        assertEquals(GuardrailRule.UNPARSED, d.state.guardHints[d.state.lines[1].id]!!.rule)
    }

    @Test
    fun `a hint under an older set moves out of the keypad line once the next set is logged`() {
        val d = benchWith("8x 825")
        val flagged = d.state.lines[1].id
        d.advance(100); d.keys("8x 80"); d.state.onKeypadNext()
        // Still open, now drawn under its own row.
        assertNotNull(d.state.guardHints[flagged])
        assertFalse(d.state.hintShownAtKeypad(flagged))
        // The flagged set is not a reference for the next one.
        assertNull(d.state.guardHints[d.state.lines[2].id])
    }

    @Test
    fun `+ set on an earlier exercise checks the inserted set too`() {
        val d = NoteEditorDriver().apply {
            state.useHistory(history())
            advance(30)
            typeAndEnter(0, "Bench press")
            advance(90); keys("8x 80"); state.onKeypadNext()
            state.onKeypadNext() // next exercise
            type(3, "Squat"); enter(3)
            advance(90); keys("5x 100"); state.onKeypadNext()
        }
        d.state.addSetToExercise(0)
        assertEquals(KeypadMode.INSERT, d.state.keypadMode)
        d.advance(60)
        d.keys("8x 825")
        d.state.onKeypadNext()
        val inserted = d.state.lines[2]
        assertEquals("      8x 825 (1'00'')", inserted.text.value.text)
        assertEquals(GuardrailRule.DECIMAL_SLIP, d.state.guardHints[inserted.id]!!.rule)
        assertTrue(d.state.hintShownAtKeypad(inserted.id))
    }

    @Test
    fun `every committed set reports a commit for the rest timer`() {
        val d = benchWith("8x 80")
        assertEquals(SetCommit(1, "Bench press", ExerciseFlag.BILATERAL), d.state.lastSetCommit)
        d.advance(90); d.keys("8x"); d.state.onKeypadNext()
        assertEquals(2, d.state.lastSetCommit!!.sequence)
        // Ending the exercise is not a set.
        d.state.onKeypadNext()
        assertEquals(2, d.state.lastSetCommit!!.sequence)
    }
}
