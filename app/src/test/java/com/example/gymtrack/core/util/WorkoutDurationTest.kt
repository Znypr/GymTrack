package com.example.gymtrack.core.util

import com.example.gymtrack.core.data.ExerciseFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The duration rule of docs/WORKOUT_DURATION.md (#321, #323). */
class WorkoutDurationTest {
    private val min = 60

    @Test
    fun finishedWorkout_runsToFinishNotToTheLastSet() { // #323
        val d = workoutDuration(listOf(0, 20 * min, 55 * min), finishedOffsetSeconds = 62 * min)!!
        assertEquals(62 * min, d.seconds)
        assertFalse(d.endedByGap)
    }

    @Test
    fun unfinishedWorkout_runsToTheLastLoggedRow() {
        val d = workoutDuration(listOf(55 * min, 0, 20 * min))!!
        assertEquals(55 * min, d.seconds)
        assertFalse(d.endedByGap)
    }

    @Test
    fun overnightResume_endsBeforeTheGapAndIgnoresFinishNextMorning() { // #321
        // Evening sets up to 60 min, Back, one forgotten set 13 h later, then Finish.
        val d = workoutDuration(listOf(0, 30 * min, 60 * min, 804 * min), finishedOffsetSeconds = 806 * min)!!
        assertEquals(60 * min, d.seconds)
        assertTrue(d.endedByGap)
    }

    @Test
    fun gapOfExactlyTheThreshold_doesNotEndTheSession() {
        val gap = WORKOUT_SESSION_GAP_SECONDS
        assertEquals(10 + gap, workoutDuration(listOf(10, 10 + gap))!!.seconds)
        val cut = workoutDuration(listOf(10, 10 + gap + 1))!!
        assertEquals(10, cut.seconds)
        assertTrue(cut.endedByGap)
    }

    @Test
    fun finishLongAfterTheLastSet_doesNotCountTheIdleTime() {
        // Left the app open, tapped Finish 5 h after the last set.
        val d = workoutDuration(listOf(0, 50 * min), finishedOffsetSeconds = 50 * min + 5 * 3600)!!
        assertEquals(50 * min, d.seconds)
        assertTrue(d.endedByGap)
    }

    @Test
    fun startToFirstRow_isNeverCut() {
        assertEquals(4 * 3600, workoutDuration(listOf(4 * 3600))!!.seconds)
    }

    @Test
    fun noRows_usesFinishOnlyWithinOneSession() {
        assertNull(workoutDuration(emptyList()))
        assertEquals(40 * min, workoutDuration(emptyList(), finishedOffsetSeconds = 40 * min)!!.seconds)
        // A CSV import or a months-later "finish" with nothing logged gives no duration.
        assertNull(workoutDuration(emptyList(), finishedOffsetSeconds = 30 * 24 * 3600))
    }

    @Test
    fun finishBeforeTheLastRow_keepsTheLastRow() {
        assertEquals(30 * min, workoutDuration(listOf(0, 30 * min), finishedOffsetSeconds = 10 * min)!!.seconds)
    }

    @Test
    fun noteWorkoutDuration_readsTheElapsedColumnAndFinishTime() {
        val start = 1_800_000_000_000L
        val text = "Bench\n      8x 80 (2'00'')\n      8x 80 (2'30'')\n"
        val metadata = buildNoteRowMetadata(
            times = listOf("0'00''", "42'00''", "44'30''", ""),
            flags = List(4) { ExerciseFlag.BILATERAL },
        )
        assertEquals(44 * min + 30, noteWorkoutDuration(text, metadata, start)!!.seconds)
        val finishedAt = start + 50L * 60_000L
        assertEquals(50 * min, noteWorkoutDuration(text, metadata, start, finishedAt)!!.seconds)
    }
}
