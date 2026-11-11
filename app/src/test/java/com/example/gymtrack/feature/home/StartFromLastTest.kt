package com.example.gymtrack.feature.home

import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.util.buildNoteRowMetadata
import com.example.gymtrack.feature.today.guessNextCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #305: "Start from last <category>" copies names and order only. */
class StartFromLastTest {
    private val push = Category("Push", 0xFFFF0000)
    private val pull = Category("Pull", 0xFF00FF00)
    private val legs = Category("Legs", 0xFF0000FF)
    private val categories = listOf(push, pull, legs)

    private fun workout(ts: Long, category: String?, text: String, color: Long? = null) =
        NoteLine(title = "", text = text, timestamp = ts, categoryName = category, categoryColor = color)

    private val pullText = "Lat pulldown\n      10x 65 (0'00'')\n      8x 70 (2'10'')\n\nRow\n      12x 50\n\nCurl\n      10x 14"

    @Test
    fun `exercise names keep the order and drop every set value`() {
        val names = exerciseNames(workout(1, "Pull", pullText))
        assertEquals(listOf("Lat pulldown", "Row", "Curl"), names)
        val text = prefillText(names)
        assertEquals("Lat pulldown\n\nRow\n\nCurl", text)
        listOf("10x", "65", "70", "50", "14", "'").forEach { value ->
            assertFalse("prefill must not copy '$value'", text.contains(value))
        }
    }

    @Test
    fun `a repeated exercise is listed once at its first position`() {
        val names = exerciseNames(workout(1, "Pull", "Row\n      10x 50\n\nCurl\n      8x 12\n\nrow\n      8x 55"))
        assertEquals(listOf("Row", "Curl"), names)
    }

    @Test
    fun `stored row metadata and old exercise times do not leak into names`() {
        val text = "Bench (1'05'')\n      8x 80\n\nDips"
        val meta = buildNoteRowMetadata(listOf("0'10''", "1'05''", "", "5'00''"), List(4) { ExerciseFlag.BILATERAL })
        val note = workout(1, "Push", text).copy(rowMetadata = meta)
        assertEquals(listOf("Bench", "Dips"), exerciseNames(note))
    }

    @Test
    fun `the newest workout of the category with exercises is the source`() {
        val notes = listOf(
            workout(1, "Pull", "Old row\n      8x 50"),
            workout(3, "pull ", pullText),
            workout(4, "Pull", ""), // an empty start is skipped
            workout(5, "Push", "Bench\n      8x 80"),
        )
        val prefill = prefillFromLast(notes, "Pull", categories)!!
        assertEquals(3L, prefill.sourceTimestamp)
        assertEquals(listOf("Lat pulldown", "Row", "Curl"), prefill.exercises)
        assertEquals(pull, prefill.category)
    }

    @Test
    fun `the category comes from the source workout when settings no longer have it`() {
        val notes = listOf(workout(1, "Arms", "Curl\n      10x 14", color = 0xFF123456))
        val prefill = prefillFromLast(notes, "Arms", categories)!!
        assertEquals(Category("Arms", 0xFF123456), prefill.category)
    }

    @Test
    fun `no prefill without a workout of that category`() {
        assertNull(prefillFromLast(listOf(workout(1, "Push", "Bench\n 8x 80")), "Legs", categories))
        assertNull(prefillFromLast(emptyList(), "Push", categories))
    }

    @Test
    fun `all prefills in one pass match the single lookups`() {
        val notes = listOf(
            workout(1, "Push", "Bench\n      8x 80"),
            workout(2, "Pull", pullText),
            workout(3, "Push", "Incline\n      8x 30\n\nFly"),
        )
        val all = prefillsFromLast(notes, categories)
        assertEquals(setOf("push", "pull"), all.keys)
        assertEquals(prefillFromLast(notes, "Push", categories), all["push"])
        assertEquals(prefillFromLast(notes, "Pull", categories), all["pull"])
    }

    @Test
    fun `the rotation's next category is the one offered first`() {
        // Push → Pull → Legs → Push: the next one is Pull, prefilled from the last Pull.
        val notes = listOf(
            workout(1, "Push", "Bench\n      8x 80"),
            workout(2, "Pull", pullText),
            workout(3, "Legs", "Squat\n      5x 100"),
            workout(4, "Push", "Bench\n      8x 82"),
        )
        val next = guessNextCategory(notes, categories.map { it.name })
        assertEquals("Pull", next)
        val prefill = prefillFromLast(notes, next!!, categories)!!
        assertEquals(listOf("Lat pulldown", "Row", "Curl"), prefill.exercises)
        assertEquals(pull, prefill.category)
    }

    @Test
    fun `preview shows three names and how many more`() {
        assertEquals("A · B · C +2", prefillPreview(listOf("A", "B", "C", "D", "E")))
        assertEquals("A · B", prefillPreview(listOf("A", "B")))
    }

    @Test
    fun `exercise names alone are not a logged set`() {
        val prefilled = workout(1, "Pull", prefillText(listOf("Row", "Curl")))
        assertFalse(prefilled.hasLoggedSet())
        assertTrue(workout(1, "Pull", "Row\n\nCurl\n      10x 14").hasLoggedSet())
    }

    @Test
    fun `an untouched prefilled workout is cleaned up like an empty one`() {
        val prefilled = workout(7, "Pull", prefillText(listOf("Row", "Curl")))
        assertEquals(
            WorkoutStartDecision.ClearStale(7, deleteEmptyNote = true),
            decideWorkoutStart(7, prefilled, activeIsFinished = false),
        )
        // Once a set is logged, the next start asks first again (#293).
        val logged = prefilled.copy(text = "Row\n      10x 50\n\nCurl")
        assertEquals(WorkoutStartDecision.AskFirst(logged), decideWorkoutStart(7, logged, activeIsFinished = false))
    }
}
