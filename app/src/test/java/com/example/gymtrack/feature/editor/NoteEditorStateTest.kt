package com.example.gymtrack.feature.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.util.WorkoutParser
import com.example.gymtrack.core.util.parseNoteText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteEditorStateTest {

    private val parser = WorkoutParser()

    /** Bench press with two sets, keypad row waiting at index 3. */
    private fun benchWithTwoSets(driver: NoteEditorDriver = NoteEditorDriver()): NoteEditorDriver = driver.apply {
        advance(30)
        typeAndEnter(0, "Bench press")
        advance(90)
        typeAndEnter(1, "10x 60")
        advance(150)
        typeAndEnter(2, "8x 70")
    }

    private fun flagsOf(save: SavedEditorNote): List<ExerciseFlag> =
        parseNoteText(save.text, save.rowMetadata).third

    // #316 ------------------------------------------------------------------------------------

    @Test
    fun `toggling the flag after sets exist applies to the exercise and all its sets`() {
        val d = benchWithTwoSets()
        d.state.toggleFlag(0)

        assertEquals(List(4) { ExerciseFlag.UNILATERAL }, d.state.lines.map { it.flag.value })
        assertEquals(List(4) { ExerciseFlag.UNILATERAL }, flagsOf(d.lastSave))
        assertTrue(parser.parseWorkout(d.lastSave.text, rowMetadata = d.lastSave.rowMetadata).all { it.isUnilateral })

        // The next set typed after the toggle keeps the exercise's flag.
        d.advance(120)
        d.typeAndEnter(3, "8x")
        d.finish()
        val sets = parser.parseWorkout(d.lastSave.text, rowMetadata = d.lastSave.rowMetadata)
        assertEquals(3, sets.size)
        assertTrue(sets.all { it.isUnilateral })
    }

    @Test
    fun `toggling one exercise never touches the neighbouring exercises`() {
        val d = benchWithTwoSets()
        d.enter(3) // ends bench, new exercise row at 4
        d.typeAndEnter(4, "Squat")
        d.typeAndEnter(5, "5x 100")
        d.state.toggleFlag(4)

        val flags = d.state.lines.map { it.flag.value }
        assertEquals(List(4) { ExerciseFlag.BILATERAL }, flags.subList(0, 4))
        assertEquals(List(3) { ExerciseFlag.UNILATERAL }, flags.subList(4, 7))
    }

    // #317 ------------------------------------------------------------------------------------

    @Test
    fun `a new exercise row starts bilateral even after a uni exercise`() {
        val d = NoteEditorDriver()
        d.type(0, "Lateral raise")
        d.state.toggleFlag(0) // Uni before Enter
        d.enter(0)
        assertEquals(ExerciseFlag.UNILATERAL, d.state.lines[1].flag.value) // set row inherits
        d.typeAndEnter(1, "15x 10")
        d.enter(2) // empty Enter ends the exercise
        val nextExercise = d.state.lines[3]
        assertEquals(ExerciseFlag.BILATERAL, nextExercise.flag.value)

        d.typeAndEnter(3, "Cable fly")
        d.typeAndEnter(4, "12x 20")
        d.finish()
        val sets = parser.parseWorkout(d.lastSave.text, rowMetadata = d.lastSave.rowMetadata)
        assertEquals(listOf(true, false), sets.map { it.isUnilateral })
    }

    // #327 ------------------------------------------------------------------------------------

    @Test
    fun `re-Enter on a committed row keeps its times and inserts no row`() {
        val d = benchWithTwoSets()
        val textsBefore = d.texts
        val timesBefore = d.state.timestamps.toList()

        d.advance(600)
        d.enter(2) // committed set row, next row is the empty keypad row

        assertEquals(textsBefore, d.texts)
        assertEquals(timesBefore, d.state.timestamps.toList())
    }

    @Test
    fun `re-Enter mid-exercise still opens a row for an extra set but keeps every committed time`() {
        val d = benchWithTwoSets()
        val timesBefore = d.state.timestamps.toList()

        d.advance(600)
        d.enter(1) // committed set, followed by another set: opens an empty row in between

        assertEquals(listOf("Bench press", "      10x 60 (1'30'')", "", "      8x 70 (2'30'')", ""), d.texts)
        assertEquals(listOf(timesBefore[0], timesBefore[1], "", timesBefore[2], timesBefore[3]), d.state.timestamps.toList())
    }

    @Test
    fun `re-Enter does not shift the next set's rest time`() {
        val d = benchWithTwoSets()
        d.advance(60)
        d.enter(2) // re-Enter on the committed last set
        d.advance(60)
        d.typeAndEnter(3, "6x 70")
        assertEquals("      6x 70 (2'00'')", d.texts[3])
    }

    @Test
    fun `a time stamped by a save before Enter is provisional, Enter still stamps and starts the rest`() {
        val d = NoteEditorDriver()
        d.type(0, "Leg extension")
        d.advance(10)
        d.state.toggleFlag(0) // saves, stamps the un-entered row at 0'10''
        d.advance(20)
        d.enter(0)
        assertEquals("0'30''", d.state.timestamps[0])
        d.advance(65)
        d.typeAndEnter(1, "12x 45")
        assertEquals("      12x 45 (1'05'')", d.texts[1])
    }

    // #329 ------------------------------------------------------------------------------------

    @Test
    fun `IME Enter mid-text commits the row without embedding a newline`() {
        val d = NoteEditorDriver()
        d.advance(20)
        d.type(0, "Bench press")
        // Cursor after "Bench", IME inserts the newline there.
        d.state.onTextChange(0, TextFieldValue("Bench\n press", TextRange(6)))

        assertEquals(listOf("Bench press", ""), d.texts)
        assertTrue(d.state.lines.none { '\n' in it.text.value.text })
        assertEquals("0'20''", d.state.timestamps[0])
    }

    @Test
    fun `rows and metadata stay aligned after a mid-text Enter and a reload`() {
        val d = benchWithTwoSets()
        d.state.onTextChange(2, TextFieldValue("      8x\n 70 (2'30'')", TextRange(9)))
        d.back()

        val reopened = NoteEditorDriver(initialNote = d.lastSave.toNoteLine(), startMillis = d.now)
        assertEquals(d.texts, reopened.texts)
        assertEquals(d.state.timestamps.toList(), reopened.state.timestamps.toList())
        val parsed = parseNoteText(d.lastSave.text, d.lastSave.rowMetadata)
        assertEquals(parsed.first.size, parsed.second.size)
        assertEquals(d.lastSave.text.lines().size, d.lastSave.rowMetadata.lines().size)
    }

    // #319 ------------------------------------------------------------------------------------

    @Test
    fun `absolute times count from the workout start, not the first exercise Enter`() {
        val d = NoteEditorDriver(startMillis = 1_000_000L)
        d.advance(70) // walking to the first machine after the + tap
        d.typeAndEnter(0, "Bench press")
        d.advance(60)
        d.typeAndEnter(1, "10x 60")

        assertEquals("1'10''", d.state.timestamps[0])
        assertEquals("2'10''", d.state.timestamps[1])
        assertEquals(1_000_000L, d.lastSave.timestamp)
    }

    @Test
    fun `reopening keeps the same time base and rest continues from the last set`() {
        val d = NoteEditorDriver(startMillis = 1_000_000L)
        d.advance(70)
        d.typeAndEnter(0, "Bench press")
        d.advance(60)
        d.typeAndEnter(1, "10x 60")
        d.back()

        val reopened = NoteEditorDriver(initialNote = d.lastSave.toNoteLine(), startMillis = d.now)
        reopened.advance(120)
        reopened.typeAndEnter(2, "8x 60")
        assertEquals("4'10''", reopened.state.timestamps[2])
        assertEquals("      8x 60 (2'00'')", reopened.texts[2])
    }

    // #320 ------------------------------------------------------------------------------------

    @Test
    fun `ON_STOP keeps a half-typed set unstamped so it reopens as the keypad row`() {
        val d = benchWithTwoSets()
        d.advance(90)
        d.type(3, "8x 8")
        d.stop()

        val draft = d.lastSave
        assertFalse(draft.finalized)
        assertEquals("8x 8", draft.text.lines()[3])
        assertEquals("", parseNoteText(draft.text, draft.rowMetadata).second[3])

        val reopened = NoteEditorDriver(initialNote = draft.toNoteLine(), startMillis = d.now)
        assertEquals("", reopened.state.timestamps[3])
        reopened.advance(30)
        reopened.type(3, "8x 80")
        reopened.enter(3)
        assertEquals("      8x 80 (2'00'')", reopened.texts[3])
    }

    @Test
    fun `Finish after a kill does not save the half-typed set`() {
        val d = benchWithTwoSets()
        d.type(3, "8x 8")
        d.stop()

        val reopened = NoteEditorDriver(initialNote = d.lastSave.toNoteLine(), startMillis = d.now + 60_000L)
        reopened.finish()
        val final = reopened.lastSave
        assertTrue(final.finalized)
        val sets = parser.parseWorkout(final.text, rowMetadata = final.rowMetadata)
        assertEquals(listOf(10 to 60f, 8 to 70f), sets.map { it.reps to it.weight })
        assertEquals(1, sets.map { it.exerciseName }.distinct().size) // no nameless extra exercise
        assertEquals(final.text.lines().size, final.rowMetadata.lines().size)
    }

    @Test
    fun `entered sets and exercise names are still stamped on save`() {
        val d = NoteEditorDriver()
        d.advance(40)
        d.type(0, "Bench press") // typed, never Entered
        d.back()
        assertEquals("0'40''", parseNoteText(d.lastSave.text, d.lastSave.rowMetadata).second[0])
    }
}
