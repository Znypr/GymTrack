package com.example.gymtrack.feature.stats

import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.data.GraphPoint
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.util.buildNoteRowMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsOutlierPolicyTest {
    @Test
    fun calculateExerciseOutliersRequiresEnoughHistory() {
        val points = listOf(
            GraphPoint(1L, 10f),
            GraphPoint(2L, 10.5f),
            GraphPoint(3L, 9.5f),
            GraphPoint(4L, 80f),
            GraphPoint(5L, 10f),
        )

        assertTrue(calculateExerciseOutliers(points).isEmpty())
    }

    @Test
    fun calculateExerciseOutliersUsesConservativeThreshold() {
        val points = listOf(
            GraphPoint(1L, 10f),
            GraphPoint(2L, 10.5f),
            GraphPoint(3L, 11f),
            GraphPoint(4L, 11.5f),
            GraphPoint(5L, 12f),
            GraphPoint(6L, 12.5f),
            GraphPoint(7L, 13f),
            GraphPoint(8L, 80f),
        )

        val outliers = calculateExerciseOutliers(points)

        assertEquals(listOf(80f), outliers.map { it.avgVal })
    }

    @Test
    fun buildWeeklyAverageWorkoutDurationsIgnoresImpossibleDurationRows() {
        val normal = noteWithDuration(timestamp = 1_700_000_000_000L, duration = "1:30:00")
        val impossible = noteWithDuration(timestamp = 1_700_000_100_000L, duration = "9:00:00")

        val weekly = buildWeeklyAverageWorkoutDurations(listOf(normal, impossible))

        assertEquals(1, weekly.size)
        assertEquals(90f, weekly.single().second)
    }

    @Test
    fun countUnreasonableWorkoutDurationsCountsOnlyParsedDurationOutliers() {
        val noDuration = NoteLine(title = "No duration", text = "Bench", timestamp = 1L)
        val normal = noteWithDuration(timestamp = 2L, duration = "2:00:00")
        val impossible = noteWithDuration(timestamp = 3L, duration = "9:00:00")

        assertEquals(1, countUnreasonableWorkoutDurations(listOf(noDuration, normal, impossible)))
    }

    private fun noteWithDuration(timestamp: Long, duration: String): NoteLine {
        return NoteLine(
            title = "Workout",
            text = "Bench press",
            timestamp = timestamp,
            rowMetadata = buildNoteRowMetadata(
                times = listOf(duration),
                flags = listOf(ExerciseFlag.BILATERAL),
            ),
        )
    }
}
