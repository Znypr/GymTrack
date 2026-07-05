package com.example.gymtrack.domain.summary

import com.example.gymtrack.domain.model.Category
import com.example.gymtrack.domain.model.Exercise
import com.example.gymtrack.domain.model.ExerciseMode
import com.example.gymtrack.domain.model.WeightUnit
import com.example.gymtrack.domain.model.Workout
import com.example.gymtrack.domain.model.WorkoutDetails
import com.example.gymtrack.domain.model.WorkoutExercise
import com.example.gymtrack.domain.model.WorkoutRecord
import com.example.gymtrack.domain.model.WorkoutSet
import com.example.gymtrack.domain.model.WorkoutStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TrainingSummaryBuilderTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun buildsCompactSummaryWithoutCrossExerciseRanking() {
        val start = Instant.parse("2026-07-05T13:00:00Z").toEpochMilli()
        val end = Instant.parse("2026-07-05T14:31:30Z").toEpochMilli()
        val updated = Instant.parse("2026-07-05T14:32:00Z").toEpochMilli()

        val details = details(
            workout = Workout(
                id = "workout-1",
                startedAtEpochMillis = start,
                endedAtEpochMillis = end,
                categoryId = "category-push",
                title = "Session title",
                status = WorkoutStatus.COMPLETED,
                createdAtEpochMillis = start,
                updatedAtEpochMillis = updated,
            ),
            category = Category(
                id = "category-push",
                name = "Push",
                colorArgb = 1L,
                position = 0,
                isBuiltIn = true,
            ),
            exercises = listOf(
                exercise("occurrence-bench", "bench", 0),
                exercise("occurrence-pullup", "pullup", 1),
                exercise("occurrence-row", "row", 2, modifier = "Cable"),
                exercise("occurrence-plank", "plank", 3),
            ),
            definitions = listOf(
                definition("bench", "Bench press"),
                definition("pullup", "Pullup"),
                definition("row", "Row"),
                definition("plank", "Plank"),
            ),
            sets = listOf(
                set("bench-1", "occurrence-bench", 0, reps = 8, weight = 80.0, unit = WeightUnit.KILOGRAM),
                set("bench-2", "occurrence-bench", 1, reps = 6, weight = 82.5, unit = WeightUnit.KILOGRAM),
                set("pullup-1", "occurrence-pullup", 0, reps = 10),
                set("row-1", "occurrence-row", 0, reps = 10, weight = 70.0, unit = WeightUnit.UNKNOWN),
                set("plank-1", "occurrence-plank", 0, duration = 90),
            ),
        )

        val summary = TrainingSummaryBuilder().build(details, utc)

        assertEquals(TRAINING_SUMMARY_SCHEMA_VERSION, summary.schemaVersion)
        assertEquals("workout-1", summary.workoutId)
        assertEquals("2026-07-05", summary.date)
        assertEquals("2026-07-05T13:00Z", summary.startedAt)
        assertEquals("2026-07-05T14:31:30Z", summary.endedAt)
        assertEquals("Push", summary.focus)
        assertEquals(WorkoutStatus.COMPLETED, summary.status)
        assertEquals(91, summary.durationMinutes)
        assertEquals(4, summary.exerciseCount)
        assertEquals(5, summary.setCount)
        assertEquals(
            listOf(
                "Bench press 82.5kg x 6",
                "Pullup x 10",
                "Row (Cable) 70 [unit unknown] x 10",
            ),
            summary.topLifts,
        )
        assertEquals(PerformanceSignal.UNKNOWN, summary.performanceSignal)
        assertNull(summary.energy)
        assertNull(summary.recoveryNote)
        assertEquals("GymTrack", summary.source)
        assertEquals("2026-07-05T14:32Z", summary.sourceUpdatedAt)
    }

    @Test
    fun formatsDurationAndDistanceWhenNoResistanceOrRepSetExists() {
        val details = details(
            workout = workout("workout-cardio"),
            exercises = listOf(
                exercise("occurrence-plank", "plank", 0),
                exercise("occurrence-run", "run", 1),
            ),
            definitions = listOf(
                definition("plank", "Plank"),
                definition("run", "Run"),
            ),
            sets = listOf(
                set("plank-1", "occurrence-plank", 0, duration = 60),
                set("plank-2", "occurrence-plank", 1, duration = 90),
                set("run-1", "occurrence-run", 0, distance = 1_000.0),
            ),
        )

        val summary = TrainingSummaryBuilder().build(details, utc)

        assertEquals(listOf("Plank 90s", "Run 1000m"), summary.topLifts)
    }

    @Test
    fun leavesUnavailablePartialWorkoutFieldsNull() {
        val details = details(
            workout = workout("workout-partial").copy(
                title = "",
                status = WorkoutStatus.PARTIAL,
            ),
        )

        val summary = TrainingSummaryBuilder().build(details, utc)

        assertNull(summary.endedAt)
        assertNull(summary.durationMinutes)
        assertNull(summary.focus)
        assertEquals(0, summary.exerciseCount)
        assertEquals(0, summary.setCount)
        assertEquals(emptyList<String>(), summary.topLifts)
        assertEquals(WorkoutStatus.PARTIAL, summary.status)
    }

    @Test
    fun includesOnlyExplicitAnnotations() {
        val details = details(workout = workout("workout-annotations"))

        val summary = TrainingSummaryBuilder().build(
            details = details,
            zoneId = utc,
            annotations = TrainingSummaryAnnotations(
                energy = 7,
                recoveryNote = "  Low sleep, stable output.  ",
            ),
        )

        assertEquals(7, summary.energy)
        assertEquals("Low sleep, stable output.", summary.recoveryNote)
    }

    private fun workout(id: String): Workout {
        val start = Instant.parse("2026-07-05T13:00:00Z").toEpochMilli()
        return Workout(
            id = id,
            startedAtEpochMillis = start,
            endedAtEpochMillis = null,
            title = "",
            status = WorkoutStatus.PARTIAL,
            createdAtEpochMillis = start,
            updatedAtEpochMillis = start,
        )
    }

    private fun exercise(
        id: String,
        exerciseId: String,
        position: Int,
        modifier: String? = null,
    ): WorkoutExercise = WorkoutExercise(
        id = id,
        workoutId = "workout-1",
        exerciseId = exerciseId,
        position = position,
        mode = ExerciseMode.BILATERAL,
        modifier = modifier,
    )

    private fun definition(id: String, name: String): Exercise = Exercise(
        id = id,
        canonicalName = name,
    )

    private fun set(
        id: String,
        occurrenceId: String,
        position: Int,
        reps: Int? = null,
        weight: Double? = null,
        unit: WeightUnit? = null,
        duration: Int? = null,
        distance: Double? = null,
    ): WorkoutSet = WorkoutSet(
        id = id,
        workoutExerciseId = occurrenceId,
        position = position,
        repetitions = reps,
        weight = weight,
        weightUnit = unit,
        durationSeconds = duration,
        distanceMeters = distance,
    )

    private fun details(
        workout: Workout,
        category: Category? = null,
        exercises: List<WorkoutExercise> = emptyList(),
        definitions: List<Exercise> = emptyList(),
        sets: List<WorkoutSet> = emptyList(),
    ): WorkoutDetails {
        val normalizedExercises = exercises.map { occurrence ->
            occurrence.copy(workoutId = workout.id)
        }
        return WorkoutDetails(
            record = WorkoutRecord(
                workout = workout,
                exercises = normalizedExercises,
                sets = sets,
            ),
            exerciseDefinitions = definitions.associateBy { it.id },
            category = category,
        )
    }
}
