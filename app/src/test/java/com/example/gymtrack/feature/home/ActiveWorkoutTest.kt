package com.example.gymtrack.feature.home

import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.timer.NoteTimerState
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #293: the running workout, the start sheet and the orphan rule. */
class ActiveWorkoutTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun millis(day: Int, hour: Int = 18) =
        LocalDateTime.of(2026, 11, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private val pullTue = NoteLine(
        title = "",
        text = "Lat pulldown\n      10x 65",
        timestamp = millis(4),
        categoryName = "Pull",
    )

    @Test
    fun `no running workout starts right away`() {
        assertEquals(WorkoutStartDecision.StartNow, decideWorkoutStart(null, null, activeIsFinished = false))
    }

    @Test
    fun `an unfinished workout with sets asks first`() {
        val decision = decideWorkoutStart(pullTue.timestamp, pullTue, activeIsFinished = false)
        assertEquals(WorkoutStartDecision.AskFirst(pullTue), decision)
    }

    @Test
    fun `a timer left on a finished workout is cleared without asking`() {
        val decision = decideWorkoutStart(pullTue.timestamp, pullTue, activeIsFinished = true)
        assertEquals(WorkoutStartDecision.ClearStale(pullTue.timestamp, deleteEmptyNote = false), decision)
    }

    @Test
    fun `a timer pointing at a deleted or unsaved workout is cleared`() {
        val decision = decideWorkoutStart(42L, null, activeIsFinished = false)
        assertEquals(WorkoutStartDecision.ClearStale(42L, deleteEmptyNote = false), decision)
    }

    @Test
    fun `an empty running workout is removed instead of asked about`() {
        val empty = pullTue.copy(text = "")
        val decision = decideWorkoutStart(empty.timestamp, empty, activeIsFinished = false)
        assertEquals(WorkoutStartDecision.ClearStale(empty.timestamp, deleteEmptyNote = true), decision)
    }

    @Test
    fun `an empty workout with learnings is kept`() {
        val withLearnings = pullTue.copy(text = "  ", learnings = "felt strong")
        val decision = decideWorkoutStart(withLearnings.timestamp, withLearnings, activeIsFinished = false)
        assertEquals(WorkoutStartDecision.ClearStale(withLearnings.timestamp, deleteEmptyNote = false), decision)
    }

    @Test
    fun `every workout the sheet leaves unfinished can still be finished from History`() {
        // Keep both: the old workout stays unfinished and History offers Mark finished.
        assertTrue(canMarkFinished(pullTue, isFinished = false))
        assertFalse(canMarkFinished(pullTue, isFinished = true))
        assertFalse(canMarkFinished(pullTue.copy(text = ""), isFinished = false))
    }

    @Test
    fun `prompt names the category and the day`() {
        val now = millis(10)
        assertEquals("Finish Pull from Wed first?", orphanPrompt(pullTue.copy(timestamp = millis(4)), now, zone, Locale.ENGLISH))
        assertEquals("Finish Pull from today first?", orphanPrompt(pullTue.copy(timestamp = millis(10, 9)), now, zone, Locale.ENGLISH))
        assertEquals("Finish Pull from yesterday first?", orphanPrompt(pullTue.copy(timestamp = millis(9)), now, zone, Locale.ENGLISH))
        assertEquals("Finish Pull from 1 Nov first?", orphanPrompt(pullTue.copy(timestamp = millis(1)), now, zone, Locale.ENGLISH))
    }

    @Test
    fun `short name falls back from category to title to Workout`() {
        assertEquals("Pull", pullTue.shortName())
        assertEquals("Morning", pullTue.copy(categoryName = null, title = "Morning").shortName())
        assertEquals("Workout", pullTue.copy(categoryName = " ", title = "").shortName())
    }

    @Test
    fun `bar shows the saved unfinished workout that owns the timer`() {
        val timer = NoteTimerState.started(pullTue.timestamp, pullTue.timestamp)
        val info = activeWorkoutInfo(timer, listOf(pullTue)) { false }
        assertEquals(ActiveWorkoutInfo(pullTue.timestamp, pullTue, isRunning = true), info)
    }

    @Test
    fun `bar hides without timer, for finished workouts and for unsaved ones`() {
        val timer = NoteTimerState.started(pullTue.timestamp, pullTue.timestamp)
        assertNull(activeWorkoutInfo(NoteTimerState(), listOf(pullTue)) { false })
        assertNull(activeWorkoutInfo(timer, listOf(pullTue)) { true })
        assertNull(activeWorkoutInfo(timer, emptyList()) { false })
    }

    @Test
    fun `elapsed clock formats minutes and hours`() {
        assertEquals("24:10", formatElapsedClock(24 * 60 + 10L))
        assertEquals("0:05", formatElapsedClock(5L))
        assertEquals("1:02:05", formatElapsedClock(3600 + 125L))
        assertEquals("0:00", formatElapsedClock(-3L))
    }

    @Test
    fun `new workout timestamp never collides with an existing one`() {
        assertEquals(100L, freeWorkoutTimestamp(100L, setOf(99L)))
        assertEquals(102L, freeWorkoutTimestamp(100L, setOf(100L, 101L)))
    }
}
