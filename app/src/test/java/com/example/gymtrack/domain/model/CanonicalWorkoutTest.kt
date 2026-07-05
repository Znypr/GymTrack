package com.example.gymtrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CanonicalWorkoutTest {

    @Test
    fun validWorkoutRecordPreservesExplicitOrderingAndReferences() {
        val workout = Workout(
            id = "workout-1",
            startedAtEpochMillis = 1_000L,
            endedAtEpochMillis = 2_000L,
            status = WorkoutStatus.COMPLETED,
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 2_000L,
        )
        val exercise = WorkoutExercise(
            id = "workout-exercise-1",
            workoutId = workout.id,
            exerciseId = "exercise-1",
            position = 0,
            mode = ExerciseMode.BILATERAL,
        )
        val set = WorkoutSet(
            id = "set-1",
            workoutExerciseId = exercise.id,
            position = 0,
            repetitions = 8,
            weight = 80.0,
            weightUnit = WeightUnit.KILOGRAM,
        )

        val record = WorkoutRecord(
            workout = workout,
            exercises = listOf(exercise),
            sets = listOf(set),
        )

        assertEquals("workout-1", record.workout.id)
        assertEquals(0, record.exercises.single().position)
        assertEquals(0, record.sets.single().position)
    }

    @Test
    fun workoutIdentityIsIndependentFromWorkoutStartTime() {
        val first = workout(id = "workout-a", startedAt = 10_000L)
        val second = workout(id = "workout-b", startedAt = 10_000L)

        assertEquals(first.startedAtEpochMillis, second.startedAtEpochMillis)
        assert(first.id != second.id)
    }

    @Test
    fun setRequiresAnActualPerformanceMetric() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutSet(
                id = "set-1",
                workoutExerciseId = "workout-exercise-1",
                position = 0,
            )
        }
    }

    @Test
    fun weightRequiresAnExplicitUnit() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutSet(
                id = "set-1",
                workoutExerciseId = "workout-exercise-1",
                position = 0,
                repetitions = 8,
                weight = 80.0,
            )
        }
    }

    @Test
    fun recordRejectsSetsThatReferenceAnotherAggregate() {
        val workout = workout(id = "workout-1", startedAt = 1_000L)
        val exercise = WorkoutExercise(
            id = "workout-exercise-1",
            workoutId = workout.id,
            exerciseId = "exercise-1",
            position = 0,
        )

        assertThrows(IllegalArgumentException::class.java) {
            WorkoutRecord(
                workout = workout,
                exercises = listOf(exercise),
                sets = listOf(
                    WorkoutSet(
                        id = "set-1",
                        workoutExerciseId = "different-workout-exercise",
                        position = 0,
                        repetitions = 8,
                    )
                ),
            )
        }
    }

    @Test
    fun recordRejectsDuplicateExercisePositions() {
        val workout = workout(id = "workout-1", startedAt = 1_000L)
        val first = WorkoutExercise(
            id = "workout-exercise-1",
            workoutId = workout.id,
            exerciseId = "exercise-1",
            position = 0,
        )
        val second = WorkoutExercise(
            id = "workout-exercise-2",
            workoutId = workout.id,
            exerciseId = "exercise-2",
            position = 0,
        )

        assertThrows(IllegalArgumentException::class.java) {
            WorkoutRecord(workout, listOf(first, second), emptyList())
        }
    }

    private fun workout(id: String, startedAt: Long): Workout = Workout(
        id = id,
        startedAtEpochMillis = startedAt,
        createdAtEpochMillis = startedAt,
        updatedAtEpochMillis = startedAt,
    )
}
