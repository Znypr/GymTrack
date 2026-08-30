package com.example.gymtrack.feature.editor.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastSetEntryTargetTest {

    @Test
    fun `blank separator before next exercise is not a fast set target`() {
        assertFalse(
            isFastSetEntryCandidate(
                index = 3,
                currentText = "",
                previousText = "      8x 80 (0'45'')",
                nextText = "Incline Press",
                timestamp = "",
            ),
        )
    }

    @Test
    fun `blank separator before still-blank new exercise is not a fast set target`() {
        assertFalse(
            isFastSetEntryCandidate(
                index = 3,
                currentText = "",
                previousText = "      8x 80 (0'45'')",
                nextText = "",
                timestamp = "",
            ),
        )
    }

    @Test
    fun `blank row after current exercise is a fast set target`() {
        assertTrue(
            isFastSetEntryCandidate(
                index = 5,
                currentText = "",
                previousText = "Incline Press",
                nextText = null,
                timestamp = "",
            ),
        )
    }

    @Test
    fun `completed set with timestamp is not a fast set target`() {
        assertFalse(
            isFastSetEntryCandidate(
                index = 2,
                currentText = "      8x 80 (0'45'')",
                previousText = "Bench Press",
                nextText = "",
                timestamp = "03'12''",
            ),
        )
    }

    @Test
    fun `exercise row after separator is never a fast set target`() {
        assertFalse(
            isFastSetEntryCandidate(
                index = 4,
                currentText = "Incline Press",
                previousText = "",
                nextText = "",
                timestamp = "",
            ),
        )
    }
}
