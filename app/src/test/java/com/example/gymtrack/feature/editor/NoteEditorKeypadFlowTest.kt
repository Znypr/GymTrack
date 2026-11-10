package com.example.gymtrack.feature.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.util.WorkoutParser
import com.example.gymtrack.core.util.parseNoteText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Editor v2 keypad ownership, row actions and flags (docs/design/EDITOR_V2.md §3, §6). */
class NoteEditorKeypadFlowTest {

    private val parser = WorkoutParser()

    private fun NoteEditorDriver.keys(entry: String) =
        state.onKeypadInput(TextFieldValue(entry, TextRange(entry.length)))

    private fun NoteEditorDriver.next() = state.onKeypadNext()

    private fun NoteEditorDriver.keypadIndex() = state.indexOfRow(state.keypadRowId)

    /** Bench with two sets typed on the keypad; the keypad waits on row 3. */
    private fun bench(): NoteEditorDriver = NoteEditorDriver().apply {
        advance(30)
        typeAndEnter(0, "Bench press")
        advance(90); keys("10x 60"); next()
        advance(150); keys("8x 70"); next()
    }

    private fun sets(d: NoteEditorDriver) = parser.parseWorkout(d.lastSave.text, rowMetadata = d.lastSave.rowMetadata)

    // §8.1 must-keep: one tap per set, double Enter --------------------------------------------

    @Test
    fun `exercise Enter opens the keypad and Next commits one set per tap`() {
        val d = bench()
        assertEquals(listOf("Bench press", "      10x 60 (1'30'')", "      8x 70 (2'30'')", ""), d.texts)
        assertEquals(3, d.keypadIndex())
        assertEquals(KeypadMode.LIVE, d.state.keypadMode)
    }

    @Test
    fun `Next on the empty set row ends the exercise and hands over to the IME`() {
        val d = bench()
        d.next()
        assertNull(d.state.keypadRowId)
        assertEquals(listOf("Bench press", "      10x 60 (1'30'')", "      8x 70 (2'30'')", "", ""), d.texts)
        assertEquals(d.state.lines[4].id, d.state.pendingExerciseFocusRowId)
    }

    @Test
    fun `focusing an exercise row closes the keypad and points the flag segment at it`() {
        val d = bench()
        d.next()
        val newExercise = d.state.lines[4]
        d.state.onExerciseFocused(newExercise.id)
        assertNull(d.state.keypadRowId)
        assertEquals(4, d.state.currentExerciseIndex)
    }

    // #328 ------------------------------------------------------------------------------------

    @Test
    fun `a past workout opens with the keypad closed, the active one on its open set row`() {
        val d = bench()
        d.back()
        val past = NoteEditorDriver(initialNote = d.lastSave.toNoteLine(), startMillis = d.now)
        past.state.restoreKeypadOnOpen(isActiveWorkout = false)
        assertNull(past.state.keypadRowId)

        val active = NoteEditorDriver(initialNote = d.lastSave.toNoteLine(), startMillis = d.now)
        active.state.restoreKeypadOnOpen(isActiveWorkout = true)
        assertEquals(3, active.keypadIndex())
    }

    @Test
    fun `a new workout opens on the exercise line, not the keypad`() {
        val d = NoteEditorDriver()
        d.state.restoreKeypadOnOpen(isActiveWorkout = true)
        assertNull(d.state.keypadRowId)
    }

    // #330: tap a committed set → keypad edit ---------------------------------------------------

    @Test
    fun `editing a committed set changes only its entry, never its times`() {
        val d = bench()
        val timesBefore = d.state.timestamps.toList()
        d.state.openKeypad(d.state.lines[1].id)
        assertEquals(KeypadMode.EDIT, d.state.keypadMode)
        assertEquals("10x 60", d.state.keypadValue.text)

        d.advance(600)
        d.keys("10x 62.5")
        assertEquals("      10x 60 (1'30'')", d.texts[1]) // untouched until Next
        d.next()

        assertEquals("      10x 62.5 (1'30'')", d.texts[1])
        assertEquals(timesBefore, d.state.timestamps.toList())
        assertNull(d.state.keypadRowId)
        assertEquals(62.5f, sets(d).first().weight)
    }

    @Test
    fun `clearing a committed set's entry and pressing Next keeps the set`() {
        val d = bench()
        d.state.openKeypad(d.state.lines[2].id)
        d.keys("")
        d.next()
        assertEquals("      8x 70 (2'30'')", d.texts[2])
        assertEquals(4, d.texts.size)
    }

    @Test
    fun `exercise rows and separators never take the keypad`() {
        val d = bench()
        d.next() // separator at 3, exercise row at 4
        d.state.openKeypad(d.state.lines[0].id)
        assertNull(d.state.keypadRowId)
        d.state.openKeypad(d.state.lines[3].id)
        assertNull(d.state.keypadRowId)
    }

    // #330: + set ------------------------------------------------------------------------------

    @Test
    fun `plus set on the current exercise brings the keypad back to its open row`() {
        val d = bench()
        d.state.closeKeypad()
        d.state.addSetToExercise(0)
        assertEquals(3, d.keypadIndex())
        assertEquals(KeypadMode.LIVE, d.state.keypadMode)
    }

    @Test
    fun `plus set on an earlier exercise inserts a committed set without a blank row`() {
        val d = bench()
        d.next()
        d.typeAndEnter(4, "Squat")
        d.advance(60); d.keys("5x 100"); d.next()

        d.advance(45)
        d.state.addSetToExercise(1) // any row of the bench block
        assertEquals(KeypadMode.INSERT, d.state.keypadMode)
        assertEquals(2, d.keypadIndex()) // anchored on bench's last set
        d.keys("6x")
        d.next()

        assertEquals("      6x (0'45'')", d.texts[3])
        assertEquals("", d.texts[4]) // the separator is still there
        assertEquals("Squat", d.texts[5])
        assertEquals(KeypadMode.INSERT, d.state.keypadMode) // Next = another set
        assertEquals(3, d.keypadIndex())

        d.next() // empty Next closes
        assertNull(d.state.keypadRowId)

        val parsed = parseNoteText(d.lastSave.text, d.lastSave.rowMetadata)
        assertEquals(parsed.first.size, parsed.second.size)
        val bench = sets(d).filter { it.exerciseName.contains("bench", ignoreCase = true) }
        assertEquals(listOf(10 to 60f, 8 to 70f, 6 to 70f), bench.map { it.reps to it.weight })
    }

    @Test
    fun `plus set after a reopened workout without an open row appends one`() {
        val d = NoteEditorDriver()
        d.typeAndEnter(0, "Bench press")
        d.keys("5x 100"); d.next()
        val noOpenRow = d.lastSave.toNoteLine().let { it.copy(text = it.text.lines().dropLast(1).joinToString("\n"), rowMetadata = it.rowMetadata.lines().dropLast(1).joinToString("\n")) }
        val reopened = NoteEditorDriver(initialNote = noOpenRow, startMillis = d.now)
        reopened.state.addSetToExercise(0)
        assertEquals(2, reopened.keypadIndex())
        assertEquals(KeypadMode.LIVE, reopened.state.keypadMode)
    }

    // #330: swipe delete + undo ------------------------------------------------------------------

    @Test
    fun `deleting a set removes text and metadata together and undo puts both back`() {
        val d = bench()
        val textsBefore = d.texts
        val timesBefore = d.state.timestamps.toList()

        val deleted = d.state.deleteSetRow(1)!!
        assertEquals(listOf("Bench press", "      8x 70 (2'30'')", ""), d.texts)
        assertEquals(1, sets(d).size)
        assertEquals(d.lastSave.text.lines().size, d.lastSave.rowMetadata.lines().size)

        d.state.undoDelete(deleted)
        assertEquals(textsBefore, d.texts)
        assertEquals(timesBefore, d.state.timestamps.toList())
        assertEquals(2, sets(d).size)
    }

    @Test
    fun `only set rows can be swiped away`() {
        val d = bench()
        assertNull(d.state.deleteSetRow(0)) // exercise
        assertNull(d.state.deleteSetRow(3)) // the open empty row
        assertEquals(4, d.texts.size)
    }

    @Test
    fun `deleting the set the keypad edits closes the keypad`() {
        val d = bench()
        d.state.openKeypad(d.state.lines[2].id)
        d.state.deleteSetRow(2)
        assertNull(d.state.keypadRowId)
    }

    // Flags (§3) --------------------------------------------------------------------------------

    @Test
    fun `the flag segment sets the exercise and all its sets in one tap`() {
        val d = bench()
        d.state.setFlag(d.state.currentExerciseIndex!!, ExerciseFlag.SUPERSET)
        assertEquals(List(4) { ExerciseFlag.SUPERSET }, d.state.lines.map { it.flag.value })
        d.state.setFlag(0, ExerciseFlag.UNILATERAL)
        assertTrue(sets(d).all { it.isUnilateral })
    }

    @Test
    fun `a set inserted with plus set takes its exercise's flag`() {
        val d = bench()
        d.state.setFlag(0, ExerciseFlag.UNILATERAL)
        d.next()
        d.typeAndEnter(4, "Squat")
        d.keys("5x 100"); d.next()
        d.state.addSetToExercise(0)
        d.keys("6x 70"); d.next()
        assertEquals(ExerciseFlag.UNILATERAL, d.state.lines[3].flag.value)
        assertEquals(ExerciseFlag.BILATERAL, d.state.lines[6].flag.value)
    }

    // Repeated digits (filed bug: 11x 175 saved as 1x 175) -------------------------------------

    /** Taps keys the way EditorSetKeypad does: each tap reads the latest keypadValue. */
    private fun NoteEditorDriver.tap(keys: String) = keys.forEach { key ->
        val token = when (key) {
            'x' -> "x "
            's' -> "s "
            else -> key.toString()
        }
        state.onKeypadInput(com.example.gymtrack.feature.editor.components.insertSetEntryToken(state.keypadValue, token))
    }

    @Test
    fun `rapid repeated digits are all kept on the live row`() {
        val d = NoteEditorDriver()
        d.typeAndEnter(0, "Leg press")
        d.tap("11x175")
        assertEquals("11x 175", d.state.keypadValue.text)
        d.next()
        assertEquals("      11x 175 (0'00'')", d.texts[1])
        d.tap("111x1000")
        assertEquals("111x 1000", d.state.keypadValue.text)
    }

    @Test
    fun `rapid repeated digits are all kept when editing a committed set`() {
        val d = bench()
        d.state.openKeypad(d.state.lines[1].id)
        repeat(6) { d.state.onKeypadInput(com.example.gymtrack.feature.editor.components.backspaceSetEntry(d.state.keypadValue)) }
        d.tap("11x55")
        d.next()
        assertEquals("      11x 55 (1'30'')", d.texts[1])
    }

    // Finish enablement, long-press edit, rename ------------------------------------------------

    @Test
    fun `Finish is enabled only once a set is committed`() {
        val d = NoteEditorDriver()
        d.typeAndEnter(0, "Bench press")
        assertFalse(d.state.hasLoggedSet)
        d.keys("8x 80")
        assertFalse(d.state.hasLoggedSet) // typed, not committed
        d.next()
        assertTrue(d.state.hasLoggedSet)
    }

    @Test
    fun `edit as text can add a unit and keeps indent and times`() {
        val d = bench()
        val times = d.state.timestamps.toList()
        d.state.replaceSetEntry(2, "8x 70 lb")
        assertEquals("      8x 70 lb (2'30'')", d.texts[2])
        assertEquals(times, d.state.timestamps.toList())
        assertEquals("LB", sets(d)[1].weightUnit)
    }

    @Test
    fun `Enter after renaming an exercise that has sets adds no row`() {
        val d = bench()
        d.state.onTextChange(0, TextFieldValue("Bench press bar", TextRange(15)))
        d.enter(0)
        assertEquals(listOf("Bench press bar", "      10x 60 (1'30'')", "      8x 70 (2'30'')", ""), d.texts)
        assertEquals(1, d.state.releaseFocusTick)
    }
}
