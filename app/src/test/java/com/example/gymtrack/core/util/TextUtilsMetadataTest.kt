package com.example.gymtrack.core.util

import com.example.gymtrack.core.data.ExerciseFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextUtilsMetadataTest {
    @Test
    fun `typed metadata preserves visible note text without hidden separators`() {
        val text = "Bench Press\n    5x 100kg"
        val metadata = buildNoteRowMetadata(
            times = listOf("0'00", "0'45"),
            flags = listOf(ExerciseFlag.BILATERAL, ExerciseFlag.UNILATERAL),
        )

        val parsed = parseNoteText(text, metadata)

        assertEquals(listOf("Bench Press", "    5x 100kg"), parsed.first)
        assertEquals(listOf("0'00", "0'45"), parsed.second)
        assertEquals(listOf(ExerciseFlag.BILATERAL, ExerciseFlag.UNILATERAL), parsed.third)
        assertFalse(text.contains('\u200B'))
        assertFalse(text.contains('\u200C'))
    }

    @Test
    fun `legacy hidden separator notes still parse`() {
        val legacy = combineTextAndTimes(
            text = "Bench Press\n    5x 100kg",
            times = listOf("0'00", "0'45"),
            flags = listOf(ExerciseFlag.BILATERAL, ExerciseFlag.UNILATERAL),
        )

        val parsed = parseNoteText(legacy)

        assertEquals(listOf("Bench Press", "    5x 100kg"), parsed.first)
        assertEquals(listOf("0'00", "0'45"), parsed.second)
        assertEquals(listOf(ExerciseFlag.BILATERAL, ExerciseFlag.UNILATERAL), parsed.third)
        assertTrue(legacy.contains('\u200B'))
        assertTrue(legacy.contains('\u200C'))
    }
}
