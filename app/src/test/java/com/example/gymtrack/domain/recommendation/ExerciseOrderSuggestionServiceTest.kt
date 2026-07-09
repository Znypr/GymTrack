package com.example.gymtrack.domain.recommendation

import com.example.gymtrack.domain.model.Category
import com.example.gymtrack.domain.model.Exercise
import com.example.gymtrack.domain.model.Workout
import com.example.gymtrack.domain.model.WorkoutDetails
import com.example.gymtrack.domain.model.WorkoutExercise
import com.example.gymtrack.domain.model.WorkoutRecord
import com.example.gymtrack.domain.model.WorkoutStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseOrderSuggestionServiceTest {
    private val service = ExerciseOrderSuggestionService()

    @Test
    fun suggestsStableExerciseOrderForMatchingWorkoutLabel() {
        val suggestion = service.suggestExerciseOrder(
            workouts = listOf(
                workoutDetails("Push", day = 1, exercises = listOf("Bench Press", "Pec Deck", "Lateral Raise")),
                workoutDetails("Pull", day = 2, exercises = listOf("Lat Pulldown", "Row")),
                workoutDetails("Push", day = 3, exercises = listOf("Bench Press", "Pec Deck", "Triceps Pushdown", "Lateral Raise")),
                workoutDetails("Push", day = 4, exercises = listOf("Pec Deck", "Bench Press", "Lateral Raise")),
            ),
            workoutLabel = "Push",
        )

        requireNotNull(suggestion)
        assertEquals("Push", suggestion.workoutLabel)
        assertEquals(3, suggestion.matchingWorkoutCount)
        assertEquals(
            listOf("Bench Press", "Pec Deck", "Lateral Raise"),
            suggestion.exercises.map { it.name },
        )
        assertEquals(listOf(3, 3, 3), suggestion.exercises.map { it.supportCount })
    }

    @Test
    fun returnsNullWithoutEnoughMatchingWorkoutHistory() {
        val suggestion = service.suggestExerciseOrder(
            workouts = listOf(workoutDetails("Push", day = 1, exercises = listOf("Bench Press"))),
            workoutLabel = "Push",
        )

        assertNull(suggestion)
    }

    private fun workoutDetails(
        label: String,
        day: Int,
        exercises: List<String>,
    ): WorkoutDetails {
        val start = day * MILLIS_PER_DAY
        val category = Category(
            id = "category-$label",
            name = label,
            colorArgb = 0L,
            position = 0,
            isBuiltIn = false,
        )
        val definitions = exercises.distinct().associate { name ->
            exerciseId(name) to Exercise(
                id = exerciseId(name),
                canonicalName = name,
            )
        }
        val occurrences = exercises.mapIndexed { index, name ->
            WorkoutExercise(
                id = "workout-$day-occurrence-$index",
                workoutId = "workout-$day",
                exerciseId = exerciseId(name),
                position = index,
            )
        }
        return WorkoutDetails(
            record = WorkoutRecord(
                workout = Workout(
                    id = "workout-$day",
                    startedAtEpochMillis = start,
                    categoryId = category.id,
                    title = label,
                    status = WorkoutStatus.COMPLETED,
                    createdAtEpochMillis = start,
                    updatedAtEpochMillis = start,
                ),
                exercises = occurrences,
                sets = emptyList(),
            ),
            exerciseDefinitions = definitions,
            category = category,
        )
    }

    private fun exerciseId(name: String): String = name.lowercase().replace(" ", "-")

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
