package com.example.gymtrack.domain.recommendation

import com.example.gymtrack.domain.model.Category
import com.example.gymtrack.domain.model.Workout
import com.example.gymtrack.domain.model.WorkoutDetails
import com.example.gymtrack.domain.model.WorkoutRecord
import com.example.gymtrack.domain.model.WorkoutStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextWorkoutPredictionServiceTest {
    private val service = NextWorkoutPredictionService()

    @Test
    fun returnsNullWithoutCompletedLabeledHistory() {
        val suggestion = service.predictNextWorkout(
            workouts = listOf(
                workoutDetails(label = "Push", status = WorkoutStatus.DRAFT, day = 1),
                workoutDetails(label = "", status = WorkoutStatus.COMPLETED, day = 2),
            ),
            nowEpochMillis = dayMillis(3),
        )

        assertNull(suggestion)
    }

    @Test
    fun predictsNextWorkoutFromRepeatedTransitions() {
        val suggestion = service.predictNextWorkout(
            workouts = listOf(
                workoutDetails(label = "Push", day = 1),
                workoutDetails(label = "Pull", day = 2),
                workoutDetails(label = "Legs", day = 3),
                workoutDetails(label = "Push", day = 4),
                workoutDetails(label = "Pull", day = 5),
            ),
            nowEpochMillis = dayMillis(6),
        )

        requireNotNull(suggestion)
        assertEquals("Legs", suggestion.workoutLabel)
        assertEquals(SuggestionConfidence.MEDIUM, suggestion.confidence)
        assertEquals(PredictionBasis.HISTORICAL_TRANSITION, suggestion.evidence.basis)
        assertEquals("Pull", suggestion.evidence.previousWorkoutLabel)
        assertEquals(1, suggestion.evidence.matchingTransitionCount)
        assertEquals(1, suggestion.evidence.totalTransitionCount)
    }

    @Test
    fun raisesConfidenceWithRepeatedSupportedTransitions() {
        val suggestion = service.predictNextWorkout(
            workouts = listOf(
                workoutDetails(label = "Push", day = 1),
                workoutDetails(label = "Pull", day = 2),
                workoutDetails(label = "Legs", day = 3),
                workoutDetails(label = "Push", day = 4),
                workoutDetails(label = "Pull", day = 5),
                workoutDetails(label = "Legs", day = 6),
                workoutDetails(label = "Push", day = 7),
                workoutDetails(label = "Pull", day = 8),
            ),
            nowEpochMillis = dayMillis(9),
        )

        requireNotNull(suggestion)
        assertEquals("Legs", suggestion.workoutLabel)
        assertEquals(SuggestionConfidence.HIGH, suggestion.confidence)
        assertEquals(2, suggestion.evidence.matchingTransitionCount)
        assertEquals(2, suggestion.evidence.totalTransitionCount)
    }

    @Test
    fun usesCategoryNameBeforeWorkoutTitle() {
        val suggestion = service.predictNextWorkout(
            workouts = listOf(
                workoutDetails(label = "Ignored title", categoryName = "Push", day = 1),
                workoutDetails(label = "Ignored title", categoryName = "Pull", day = 2),
                workoutDetails(label = "Ignored title", categoryName = "Push", day = 3),
            ),
            nowEpochMillis = dayMillis(4),
        )

        requireNotNull(suggestion)
        assertEquals("Pull", suggestion.workoutLabel)
        assertEquals(listOf("Push", "Pull", "Push"), suggestion.evidence.recentLabels)
    }

    @Test
    fun fallsBackToLeastRecentlyTrainedRecurringWorkout() {
        val suggestion = service.predictNextWorkout(
            workouts = listOf(
                workoutDetails(label = "Push", day = 1),
                workoutDetails(label = "Pull", day = 2),
                workoutDetails(label = "Push", day = 3),
                workoutDetails(label = "Core", day = 4),
            ),
            nowEpochMillis = dayMillis(6),
        )

        requireNotNull(suggestion)
        assertEquals("Push", suggestion.workoutLabel)
        assertEquals(SuggestionConfidence.LOW, suggestion.confidence)
        assertEquals(PredictionBasis.LEAST_RECENT_RECURRING_WORKOUT, suggestion.evidence.basis)
        assertEquals("Core", suggestion.evidence.previousWorkoutLabel)
        assertEquals(3, suggestion.evidence.daysSinceSuggested)
    }

    @Test
    fun suggestsSameWorkoutWhenOnlyOneCompletedLabelExists() {
        val suggestion = service.predictNextWorkout(
            workouts = listOf(
                workoutDetails(label = "Full Body", day = 1),
                workoutDetails(label = "Full Body", day = 2),
                workoutDetails(label = "Full Body", day = 3),
            ),
            nowEpochMillis = dayMillis(5),
        )

        requireNotNull(suggestion)
        assertEquals("Full Body", suggestion.workoutLabel)
        assertEquals(SuggestionConfidence.MEDIUM, suggestion.confidence)
        assertEquals(PredictionBasis.SINGLE_OBSERVED_WORKOUT, suggestion.evidence.basis)
        assertEquals(2, suggestion.evidence.daysSinceSuggested)
    }

    private fun workoutDetails(
        label: String,
        day: Int,
        status: WorkoutStatus = WorkoutStatus.COMPLETED,
        categoryName: String? = null,
    ): WorkoutDetails {
        val start = dayMillis(day)
        val category = categoryName?.let {
            Category(
                id = "category-$it",
                name = it,
                colorArgb = 0xFF000000,
                position = 0,
                isBuiltIn = false,
            )
        }
        return WorkoutDetails(
            record = WorkoutRecord(
                workout = Workout(
                    id = "workout-$day-$label-${categoryName.orEmpty()}",
                    startedAtEpochMillis = start,
                    categoryId = category?.id,
                    title = label,
                    status = status,
                    createdAtEpochMillis = start,
                    updatedAtEpochMillis = start,
                ),
                exercises = emptyList(),
                sets = emptyList(),
            ),
            exerciseDefinitions = emptyMap(),
            category = category,
        )
    }

    private fun dayMillis(day: Int): Long = day * 86_400_000L
}
