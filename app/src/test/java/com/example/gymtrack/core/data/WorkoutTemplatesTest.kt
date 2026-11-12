package com.example.gymtrack.core.data

import com.example.gymtrack.feature.home.startCategory
import com.example.gymtrack.feature.home.templateFromNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Planner v1 templates (#314): storage round-trip, naming and save rules. */
class WorkoutTemplatesTest {

    private val push = WorkoutTemplate("Push", "Push", listOf("Bench Press", "Incline DB", "Fly"))

    @Test
    fun roundTripKeepsAwkwardCharacters() {
        val odd = WorkoutTemplate("A|B; 100%", "Pull", listOf("Row; heavy", "Curl | EZ", "Line\nbreak"))
        val decoded = TemplatePrefs.decode(TemplatePrefs.encode(listOf(push, odd)))
        assertEquals(listOf(push, odd), decoded)
    }

    @Test
    fun missingOrBrokenRecordsAreSkipped() {
        assertTrue(TemplatePrefs.decode(null).isEmpty())
        assertTrue(TemplatePrefs.decode("").isEmpty())
        val stored = TemplatePrefs.encode(listOf(push)) + "\nnot a record\n|Legs|" + "\nEmpty|Legs|"
        assertEquals(listOf(push), TemplatePrefs.decode(stored))
    }

    @Test
    fun namesAreMadeUnique() {
        assertEquals("Push", uniqueTemplateName("Push", emptyList()))
        assertEquals("push 2", uniqueTemplateName("push", listOf(push)))
        assertEquals("Push 3", uniqueTemplateName("Push", listOf(push, push.copy(name = "Push 2"))))
        assertEquals("Workout", uniqueTemplateName("  ", emptyList()))
    }

    @Test
    fun savingReplacesSameNameAndStopsAtTheLimit() {
        val renamed = push.copy(exercises = listOf("Dips"))
        assertEquals(listOf(renamed), listOf(push).withSaved(renamed))
        val full = (1..TemplatePrefs.MAX_TEMPLATES).map { push.copy(name = "T$it") }
        assertNull(full.withSaved(push))
    }

    @Test
    fun templateFromNoteTakesExerciseNamesOnly() {
        val note = NoteLine(
            title = "Evening",
            text = "Bench Press\n      8x 80\n      8x\n\nFly\n      12x 20",
            timestamp = 1L,
            categoryName = "Push",
        )
        val t = templateFromNote(note, listOf(push))!!
        assertEquals("Push 2", t.name)
        assertEquals("Push", t.categoryName)
        assertEquals(listOf("Bench Press", "Fly"), t.exercises)
        assertNull(templateFromNote(note.copy(text = ""), emptyList()))
    }

    @Test
    fun startCategoryPrefersTheUsersCategory() {
        val cats = listOf(Category("Push", 0xFFFF0000))
        assertEquals(cats[0], push.copy(categoryName = "push").startCategory(cats))
        assertEquals("Arms", push.copy(categoryName = "Arms").startCategory(cats)!!.name)
        assertNull(push.copy(categoryName = "").startCategory(cats))
    }
}
