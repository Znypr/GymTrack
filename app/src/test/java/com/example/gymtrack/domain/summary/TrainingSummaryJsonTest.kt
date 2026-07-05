package com.example.gymtrack.domain.summary

import com.example.gymtrack.domain.model.WorkoutStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingSummaryJsonTest {
    @Test
    fun encodesStableSnakeCaseContractWithExplicitNulls() {
        val summary = TrainingSummary(
            workoutId = "workout-1",
            date = "2026-07-05",
            startedAt = "2026-07-05T13:00Z",
            endedAt = null,
            focus = "Pull \"A\"",
            status = WorkoutStatus.PARTIAL,
            durationMinutes = null,
            exerciseCount = 2,
            setCount = 5,
            topLifts = listOf("Row 70 [unit unknown] x 10", "Pullup x 8"),
            energy = null,
            recoveryNote = "Line one\nLine two",
            sourceUpdatedAt = "2026-07-05T14:00Z",
        )

        assertEquals(
            "{\"schema_version\":1," +
                "\"workout_id\":\"workout-1\"," +
                "\"date\":\"2026-07-05\"," +
                "\"started_at\":\"2026-07-05T13:00Z\"," +
                "\"ended_at\":null," +
                "\"focus\":\"Pull \\\"A\\\"\"," +
                "\"status\":\"partial\"," +
                "\"duration_min\":null," +
                "\"exercise_count\":2," +
                "\"set_count\":5," +
                "\"top_lifts\":[\"Row 70 [unit unknown] x 10\",\"Pullup x 8\"]," +
                "\"performance_signal\":\"unknown\"," +
                "\"energy\":null," +
                "\"recovery_note\":\"Line one\\nLine two\"," +
                "\"source\":\"GymTrack\"," +
                "\"source_updated_at\":\"2026-07-05T14:00Z\"}",
            TrainingSummaryJson.encode(summary),
        )
    }
}
