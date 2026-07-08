package com.example.gymtrack.feature.stats

import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.util.buildNoteRowMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class StatsChartModelsTest {
    @Test
    fun buildWeeklyWorkoutCountsGroupsByIsoWeek() {
        val counts = buildWeeklyWorkoutCounts(
            listOf(
                note(timestamp = localTimestamp(2024, 1, 1)),
                note(timestamp = localTimestamp(2024, 1, 2)),
                note(timestamp = localTimestamp(2024, 1, 8)),
            )
        )

        assertEquals(listOf(2, 1), counts.map { it.count })
    }

    @Test
    fun buildTrainingInsightsComparesEarlyAndRecentWorkoutStrategy() {
        val insights = buildTrainingInsights(
            listOf(
                note(
                    text = "Bench Press\n    8x 80kg\n    8x 80kg",
                    timestamp = localTimestamp(2024, 1, 1),
                    categoryName = "Push",
                    duration = "1:00:00",
                ),
                note(
                    text = "Bench Press\n    8x 100kg\n    8x 100kg\nIncline Press\n    10x 70kg",
                    timestamp = localTimestamp(2024, 1, 8),
                    categoryName = "Push",
                    duration = "0:50:00",
                ),
            )
        )

        val allWorkouts = insights.first()

        assertEquals("All workouts", allWorkouts.category)
        assertTrue(allWorkouts.strength.recent > allWorkouts.strength.baseline)
        assertTrue(allWorkouts.density.recent > allWorkouts.density.baseline)
        assertTrue(allWorkouts.duration.recent < allWorkouts.duration.baseline)
        assertEquals(MetricImpact.POSITIVE, allWorkouts.strength.impact)
        assertEquals(MetricImpact.POSITIVE, allWorkouts.density.impact)
        assertEquals(MetricImpact.POSITIVE, allWorkouts.duration.impact)
        assertEquals(2, allWorkouts.trendPoints.size)
    }

    @Test
    fun buildTrainingInsightsKeepsCategorySpecificRows() {
        val insights = buildTrainingInsights(
            listOf(
                note(
                    text = "Squat\n    8x 100kg",
                    timestamp = localTimestamp(2024, 1, 1),
                    categoryName = "Legs",
                ),
                note(
                    text = "Squat\n    8x 120kg",
                    timestamp = localTimestamp(2024, 1, 8),
                    categoryName = "Legs",
                ),
                note(
                    text = "Bench Press\n    8x 80kg",
                    timestamp = localTimestamp(2024, 1, 2),
                    categoryName = "Push",
                ),
                note(
                    text = "Bench Press\n    8x 80kg",
                    timestamp = localTimestamp(2024, 1, 9),
                    categoryName = "Push",
                ),
            )
        )

        val legs = insights.single { it.category == "Legs" }
        val push = insights.single { it.category == "Push" }

        assertTrue(legs.strength.recent > legs.strength.baseline)
        assertEquals(push.strength.baseline, push.strength.recent)
    }

    @Test
    fun buildTrainingInsightsCanTreatLowerRepsAsPositiveWhenOutputImproves() {
        val insights = buildTrainingInsights(
            listOf(
                note(
                    text = "Bench Press\n    10x 80kg\n    10x 80kg",
                    timestamp = localTimestamp(2024, 1, 1),
                    categoryName = "Push",
                    duration = "1:00:00",
                ),
                note(
                    text = "Bench Press\n    5x 110kg\n    5x 110kg",
                    timestamp = localTimestamp(2024, 1, 8),
                    categoryName = "Push",
                    duration = "0:30:00",
                ),
            )
        )

        val allWorkouts = insights.first()

        assertTrue(allWorkouts.reps.recent < allWorkouts.reps.baseline)
        assertEquals(MetricImpact.POSITIVE, allWorkouts.reps.impact)
    }

    private fun note(
        text: String = "Bench Press\n    8x 100kg",
        timestamp: Long = 1L,
        categoryName: String? = null,
        duration: String = "1:00:00",
    ): NoteLine {
        val lineCount = text.lines().size
        return NoteLine(
            title = "Workout",
            text = text,
            timestamp = timestamp,
            categoryName = categoryName,
            rowMetadata = buildNoteRowMetadata(
                times = List(lineCount) { if (it == 0) duration else "" },
                flags = List(lineCount) { ExerciseFlag.BILATERAL },
            ),
        )
    }

    private fun localTimestamp(year: Int, month: Int, day: Int): Long {
        return Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
        }.timeInMillis
    }
}
