package com.example.gymtrack.feature.settings

import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.DEFAULT_CATEGORIES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryEditingTest {
    private val push = DEFAULT_CATEGORIES[0]

    @Test
    fun defaultsCanBeRemovedAndRestored() {
        val removed = CategoryEditing.remove(DEFAULT_CATEGORIES, push)
        assertFalse(removed.contains(push))
        assertEquals(listOf(push), CategoryEditing.missingDefaults(removed))
        assertEquals(DEFAULT_CATEGORIES.toSet(), CategoryEditing.restoreDefaults(removed).toSet())
    }

    @Test
    fun theLastCategoryCannotBeRemoved() {
        val one = listOf(Category("Arms", 1L))
        assertEquals(one, CategoryEditing.remove(one, one[0]))
    }

    @Test
    fun renameKeepsOrderAndRejectsDuplicates() {
        val renamed = CategoryEditing.update(DEFAULT_CATEGORIES, push, " Chest ", 5L)
        assertEquals(Category("Chest", 5L), renamed[0])
        assertEquals(DEFAULT_CATEGORIES.drop(1), renamed.drop(1))
        // A renamed default counts as missing, so "Restore Push" is offered.
        assertEquals(listOf(push), CategoryEditing.missingDefaults(renamed))

        assertNotNull(CategoryEditing.nameProblem(DEFAULT_CATEGORIES, "pull", except = push))
        assertEquals(DEFAULT_CATEGORIES, CategoryEditing.update(DEFAULT_CATEGORIES, push, "Pull", 5L))
        assertNull(CategoryEditing.nameProblem(DEFAULT_CATEGORIES, "Push", except = push))
    }

    @Test
    fun blankOrTooLongNamesAreRejected() {
        assertNotNull(CategoryEditing.nameProblem(emptyList(), "   "))
        assertNotNull(CategoryEditing.nameProblem(emptyList(), "x".repeat(CategoryEditing.MAX_NAME_LENGTH + 1)))
        assertEquals(DEFAULT_CATEGORIES, CategoryEditing.add(DEFAULT_CATEGORIES, " ", 1L))
        assertEquals(Category("Arms", 1L), CategoryEditing.add(DEFAULT_CATEGORIES, "Arms", 1L).last())
    }
}
