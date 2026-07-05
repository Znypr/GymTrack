package com.example.gymtrack.feature.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteEditorExitPolicyTest {

    @Test
    fun `browsing history keeps the active timer`() {
        assertFalse(
            shouldStopTimerOnExit(
                isLastNote = true,
                finishWorkout = false,
            ),
        )
    }

    @Test
    fun `explicit finish clears the active timer`() {
        assertTrue(
            shouldStopTimerOnExit(
                isLastNote = true,
                finishWorkout = true,
            ),
        )
    }

    @Test
    fun `finishing a historical note never clears the active timer`() {
        assertFalse(
            shouldStopTimerOnExit(
                isLastNote = false,
                finishWorkout = true,
            ),
        )
    }
}
