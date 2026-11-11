package com.example.gymtrack.feature.editor

import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.DEFAULT_CATEGORIES
import com.example.gymtrack.core.ui.components.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** #296: one category rule; a stored category is never replaced by "Push". */
class EditorCategoryTest {

    private val arms = Category("Arms", 0xFF00AAFF)

    @Test
    fun `a new workout uses the category chosen on Today`() {
        val category = EditorCategory()
        category.startNew(DEFAULT_CATEGORIES, chosenOnToday = "Legs")
        assertEquals("Legs", category.name)
        assertEquals(0xFF34C759, category.color)
    }

    @Test
    fun `a new workout without a choice uses the first category, whatever it is`() {
        val category = EditorCategory()
        category.startNew(listOf(arms) + DEFAULT_CATEGORIES, chosenOnToday = null)
        assertEquals("Arms", category.name)
    }

    @Test
    fun `a choice that no longer exists falls back to the first category, not Push`() {
        val category = EditorCategory()
        category.startNew(listOf(Category("Pull", 1L), arms), chosenOnToday = "Deleted")
        assertEquals("Pull", category.name)
    }

    @Test
    fun `no categories at all leaves the workout without one`() {
        val category = EditorCategory()
        category.startNew(emptyList(), chosenOnToday = null)
        assertNull(category.name)
        assertNull(category.color)
    }

    @Test
    fun `an existing workout keeps its stored category even when settings no longer have it`() {
        val category = EditorCategory()
        category.loadStored("Old split", 0xFF123456)
        // Recomposition or a settings reload must not replace it.
        category.startNew(DEFAULT_CATEGORIES, chosenOnToday = "Push")
        assertEquals("Old split", category.name)
        assertEquals(0xFF123456, category.color)
    }

    @Test
    fun `a legacy workout without a category stays without one`() {
        val category = EditorCategory()
        category.loadStored(null, null)
        category.startNew(DEFAULT_CATEGORIES, chosenOnToday = null)
        assertNull(category.name)
        assertNull(category.color)
    }

    @Test
    fun `a stored colour is kept even if the settings colour changed`() {
        val category = EditorCategory()
        category.loadStored("Push", 0xFF111111)
        assertEquals(0xFF111111, category.color)
    }

    @Test
    fun `only a tap changes it, and later startNew calls never undo the tap`() {
        val category = EditorCategory()
        category.startNew(DEFAULT_CATEGORIES, chosenOnToday = null)
        category.choose(arms)
        category.startNew(DEFAULT_CATEGORIES, chosenOnToday = "Push")
        assertEquals("Arms", category.name)
    }

    @Test
    fun `an unknown category is shown as stored with the neutral mark`() {
        val display = categoryDisplay("Old split", DEFAULT_CATEGORIES)
        assertEquals(CategoryDisplay("Old split", CategoryKind.OTHER, null), display)
    }

    @Test
    fun `a removed default category is shown by name with the neutral mark, not as Push`() {
        val display = categoryDisplay("Push", listOf(Category("Pull", 1L)))
        assertEquals("Push", display.label)
        assertEquals(CategoryKind.OTHER, display.kind)
        assertNull(display.customColor)
    }

    @Test
    fun `known categories get their own mark and user colour`() {
        assertEquals(CategoryKind.PUSH, categoryDisplay("Push", DEFAULT_CATEGORIES).kind)
        assertEquals(CategoryDisplay("Arms", CategoryKind.OTHER, 0xFF00AAFF), categoryDisplay("Arms", listOf(arms)))
    }

    @Test
    fun `no category reads as Workout`() {
        assertEquals("Workout", categoryDisplay(null, DEFAULT_CATEGORIES).label)
    }

    @Test
    fun `newWorkoutCategory picks Today's choice, else the first`() {
        assertEquals("Pull", newWorkoutCategory(DEFAULT_CATEGORIES, "Pull")?.name)
        assertEquals("Push", newWorkoutCategory(DEFAULT_CATEGORIES, null)?.name)
        assertNull(newWorkoutCategory(emptyList(), "Pull"))
    }
}
