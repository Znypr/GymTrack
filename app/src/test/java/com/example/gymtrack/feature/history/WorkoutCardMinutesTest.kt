package com.example.gymtrack.feature.home.components

import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.util.buildNoteRowMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

/** #323: the Home card shows the time at Finish, not at the last set. */
class WorkoutCardMinutesTest {
    private val start = 1_800_000_000_000L

    private fun note(vararg times: String) = NoteLine(
        title = "",
        text = List(times.size) { if (it == 0) "Bench" else "      8x 80" }.joinToString("\n"),
        timestamp = start,
        rowMetadata = buildNoteRowMetadata(times.toList(), List(times.size) { ExerciseFlag.BILATERAL }),
    )

    @Test
    fun finishedWorkout_showsMinutesUntilFinish() {
        val finishedAt = start + 58L * 60_000L
        assertEquals(58, workoutCardMinutes(note("0'00''", "20'00''", "51'10''"), finishedAt))
    }

    @Test
    fun unfinishedWorkout_showsMinutesUntilTheLastSet() {
        assertEquals(51, workoutCardMinutes(note("0'00''", "20'00''", "51'10''"), finishedAtMillis = null))
    }

    @Test
    fun overnightResume_showsTheEveningSessionOnly() { // #321
        val finishedAt = start + 806L * 60_000L
        assertEquals(60, workoutCardMinutes(note("0'00''", "60'00''", "804'00''"), finishedAt))
    }

    @Test
    fun emptyWorkout_showsZero() {
        assertEquals(0, workoutCardMinutes(NoteLine(title = "", text = "", timestamp = start), null))
    }
}
