package com.example.gymtrack.core.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTimerStateTest {

    @Test
    fun `running timer derives elapsed time beyond three minutes`() {
        val state = NoteTimerState.started(
            noteTimestamp = 10L,
            nowEpochMillis = 1_000L,
        )

        assertEquals(240L, state.elapsedSeconds(241_000L))
    }

    @Test
    fun `pause stores elapsed time and stops further accumulation`() {
        val running = NoteTimerState.started(
            noteTimestamp = 10L,
            nowEpochMillis = 1_000L,
        )

        val paused = running.pauseAt(91_000L)

        assertFalse(paused.isRunning)
        assertEquals(90L, paused.elapsedSeconds(500_000L))
    }

    @Test
    fun `resume keeps accumulated time and starts a new running interval`() {
        val paused = NoteTimerState(
            activeNoteTimestamp = 10L,
            accumulatedSeconds = 90L,
            startedAtEpochMillis = null,
            isRunning = false,
        )

        val resumed = paused.resumeAt(
            noteTimestamp = 10L,
            nowEpochMillis = 200_000L,
        )

        assertTrue(resumed.isRunning)
        assertEquals(120L, resumed.elapsedSeconds(230_000L))
    }

    @Test
    fun `switching notes starts a fresh timer`() {
        val previous = NoteTimerState(
            activeNoteTimestamp = 10L,
            accumulatedSeconds = 90L,
            startedAtEpochMillis = null,
            isRunning = false,
        )

        val next = previous.resumeAt(
            noteTimestamp = 20L,
            nowEpochMillis = 300_000L,
        )

        assertEquals(20L, next.activeNoteTimestamp)
        assertEquals(0L, next.accumulatedSeconds)
        assertEquals(15L, next.elapsedSeconds(315_000L))
    }

    @Test
    fun `backward clock movement never creates negative elapsed time`() {
        val state = NoteTimerState.started(
            noteTimestamp = 10L,
            nowEpochMillis = 10_000L,
        )

        assertEquals(0L, state.elapsedSeconds(5_000L))
    }
}
