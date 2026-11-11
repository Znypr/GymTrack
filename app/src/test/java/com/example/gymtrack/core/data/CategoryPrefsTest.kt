package com.example.gymtrack.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #296: defaults can be removed for good, renames stick, unknown categories are kept as-is. */
class CategoryPrefsTest {

    private fun roundTrip(list: List<Category>): List<Category> =
        CategoryPrefs.decode(stored = CategoryPrefs.encode(list), legacy = CategoryPrefs.encodeLegacy(list))

    @Test
    fun freshInstallHasTheDefaults() {
        assertEquals(DEFAULT_CATEGORIES, CategoryPrefs.decode(stored = null, legacy = null))
    }

    @Test
    fun removedDefaultStaysRemoved() {
        val withoutLegs = DEFAULT_CATEGORIES.filter { it.name != "Legs" }
        val loaded = roundTrip(withoutLegs)
        assertEquals(withoutLegs, loaded)
        assertFalse(loaded.any { it.name == "Legs" })
        // Loading again (next app start) still does not bring it back.
        assertEquals(withoutLegs, roundTrip(loaded))
    }

    @Test
    fun renamedDefaultDoesNotComeBack() {
        val renamed = DEFAULT_CATEGORIES.map { if (it.name == "Push") it.copy(name = "Chest & Tris") else it }
        val loaded = roundTrip(renamed)
        assertEquals(renamed, loaded)
        assertFalse(loaded.any { it.name == "Push" })
    }

    @Test
    fun unknownCategoryIsKeptAsIsInOrder() {
        val list = listOf(Category("Arms", 0xFF123456), Category("Cardio: Zone 2 | easy", 0xFF00FF00), Category("100% Legs", 7L)) +
            DEFAULT_CATEGORIES
        assertEquals(list, roundTrip(list))
    }

    @Test
    fun legacyValueStillLoadsTheOldWay() {
        // Written by v1: only user categories, defaults implied.
        val loaded = CategoryPrefs.decode(stored = null, legacy = "Arms:4279383126")
        assertEquals(listOf(Category("Arms", 4279383126L)) + DEFAULT_CATEGORIES, loaded)
    }

    @Test
    fun legacyValueKeepsOlderVersionsWorking() {
        val legacy = CategoryPrefs.encodeLegacy(listOf(Category("Arms", 1L)) + DEFAULT_CATEGORIES)
        assertEquals("Arms:1", legacy)
    }

    @Test
    fun emptyStoredListIsRespected() {
        assertTrue(CategoryPrefs.decode(stored = "", legacy = "Arms:1").isEmpty())
    }
}
