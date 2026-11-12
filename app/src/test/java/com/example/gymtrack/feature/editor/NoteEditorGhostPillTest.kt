package com.example.gymtrack.feature.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.feature.suggest.COACHING_DISCLAIMER
import com.example.gymtrack.feature.suggest.LoadUnit
import com.example.gymtrack.feature.suggest.PastSession
import com.example.gymtrack.feature.suggest.PastSet
import com.example.gymtrack.feature.suggest.SetSuggestionKind
import com.example.gymtrack.feature.suggest.SuggestionHistory
import com.example.gymtrack.feature.suggest.TrainingGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #308 in the editor: the pill only fills the empty open row on a tap; Next still commits. */
class NoteEditorGhostPillTest {

    private fun history(vararg sessions: List<Pair<Int, Double>>) = SuggestionHistory(
        mapOf(
            "Bench press" to sessions.mapIndexed { i, sets ->
                PastSession((sessions.size - i).toLong(), sets.map { (r, w) -> PastSet(r, w, LoadUnit.KG) })
            },
        ),
    )

    private fun NoteEditorDriver.keys(entry: String) =
        state.onKeypadInput(TextFieldValue(entry, TextRange(entry.length)))

    private fun bench(history: SuggestionHistory, settings: Settings = Settings()) = NoteEditorDriver(settings = settings).apply {
        state.useSuggestionHistory(history)
        advance(30)
        typeAndEnter(0, "Bench press")
    }

    private val keypadIndex: NoteEditorDriver.() -> Int get() = { state.indexOfRow(state.keypadRowId) }

    @Test
    fun `the open set row offers last session's matching set and a tap only fills it`() {
        val d = bench(history(listOf(8 to 80.0, 8 to 80.0, 6 to 80.0)))
        val index = d.keypadIndex()
        val suggestion = d.state.ghostSuggestion(index)!!.next!!
        assertEquals(SetSuggestionKind.REPEAT, suggestion.kind)
        assertEquals("8x 80", suggestion.entry(LoadUnit.KG))

        val savesBefore = d.saves.size
        assertTrue(d.state.applySuggestion(d.state.keypadRowId!!, suggestion.entry(LoadUnit.KG)))
        assertEquals("8x 80", d.texts[index])
        // Filled, not logged: no time yet, keypad still on the row, the pill gone.
        assertEquals("", d.state.timestamps[index])
        assertEquals(d.state.lines[index].id, d.state.keypadRowId)
        assertEquals(savesBefore, d.saves.size)
        assertNull(d.state.ghostSuggestion(index))

        // Next commits it like a typed set and the next row gets set 2.
        d.advance(60)
        d.state.onKeypadNext()
        assertEquals("      8x 80 (1'00'')", d.texts[index])
        assertEquals(2, d.state.ghostSuggestion(d.keypadIndex())!!.next!!.setNumber)
    }

    @Test
    fun `typing overwrites and hides the pill, a filled row is never replaced`() {
        val d = bench(history(listOf(8 to 80.0)))
        val rowId = d.state.keypadRowId!!
        d.keys("1")
        assertNull(d.state.ghostSuggestion(d.keypadIndex()))
        assertFalse(d.state.applySuggestion(rowId, "8x 80"))
        assertEquals("1", d.texts[d.keypadIndex()])
    }

    @Test
    fun `double progression after every set reached the top`() {
        val d = bench(history(listOf(8 to 80.0, 8 to 80.0, 8 to 80.0), listOf(8 to 77.5, 7 to 77.5, 6 to 77.5)))
        val s = d.state.ghostSuggestion(d.keypadIndex())!!
        assertEquals(SetSuggestionKind.PROGRESS, s.next!!.kind)
        assertEquals(82.5, s.next!!.load, 0.0)
        // No goal: history only, no coaching text.
        assertTrue(s.whyLines(LoadUnit.KG).none { COACHING_DISCLAIMER in it.text })
    }

    @Test
    fun `a goal adds coaching lines with the disclaimer`() {
        val d = bench(history(listOf(8 to 80.0)), Settings(trainingGoal = TrainingGoal.GET_STRONGER))
        val lines = d.state.ghostSuggestion(d.keypadIndex())!!.whyLines(LoadUnit.KG)
        assertTrue(lines.any { "Get stronger" in it.text })
        assertEquals(COACHING_DISCLAIMER, lines.last().text)
    }

    @Test
    fun `no pill without history or after a flagged set`() {
        assertNull(bench(SuggestionHistory.EMPTY).let { it.state.ghostSuggestion(it.keypadIndex()) })

        val d = bench(history(listOf(8 to 80.0, 8 to 80.0, 8 to 80.0)))
        d.state.useHistory(
            com.example.gymtrack.feature.editor.guardrail.SetHistory.build(
                (1..4).map { n ->
                    com.example.gymtrack.core.data.NoteLine(
                        title = "",
                        text = "Bench press\n      8x 80 (2'00'')\n      8x 80 (2'00'')",
                        timestamp = n * 86_400_000L,
                        rowMetadata = com.example.gymtrack.core.util.buildNoteRowMetadata(
                            List(3) { "" },
                            List(3) { com.example.gymtrack.core.data.ExerciseFlag.BILATERAL },
                        ),
                    )
                },
                excludeTimestamp = null,
                defaultUnit = "KG",
            ),
        )
        d.advance(60)
        d.keys("8x 825")
        d.state.onKeypadNext()
        assertNotNull(d.state.guardHints[d.state.lines[d.keypadIndex() - 1].id])
        assertNull(d.state.ghostSuggestion(d.keypadIndex()))
    }
}
