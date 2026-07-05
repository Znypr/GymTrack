package com.example.gymtrack.feature.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveWorkoutControlsTest {

    @Test
    fun `new workout shows active controls before timer state is persisted`() {
        assertTrue(
            shouldShowActiveWorkoutControls(
                noteId = -1L,
                activeNoteTimestamp = null,
            ),
        )
    }

    @Test
    fun `active existing workout shows controls`() {
        assertTrue(
            shouldShowActiveWorkoutControls(
                noteId = 123L,
                activeNoteTimestamp = 123L,
            ),
        )
    }

    @Test
    fun `completed newest workout hides controls when no workout is active`() {
        assertFalse(
            shouldShowActiveWorkoutControls(
                noteId = 123L,
                activeNoteTimestamp = null,
            ),
        )
    }

    @Test
    fun `historical workout hides controls while another workout is active`() {
        assertFalse(
            shouldShowActiveWorkoutControls(
                noteId = 123L,
                activeNoteTimestamp = 456L,
            ),
        )
    }
}
