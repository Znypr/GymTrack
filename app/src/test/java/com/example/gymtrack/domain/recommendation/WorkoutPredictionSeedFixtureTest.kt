package com.example.gymtrack.domain.recommendation

import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.util.importNote
import com.example.gymtrack.domain.model.Category
import com.example.gymtrack.domain.model.Workout
import com.example.gymtrack.domain.model.WorkoutDetails
import com.example.gymtrack.domain.model.WorkoutRecord
import com.example.gymtrack.domain.model.WorkoutStatus
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPredictionSeedFixtureTest {
    @Test
    fun seedCycleFixturesParseAndPredictLegsAfterPull() {
        val notes = seedFixtureFiles().map { file ->
            importNote(file, Settings()) ?: error("Could not parse fixture ${file.name}")
        }
        val workouts = notes.mapIndexed { index, note ->
            val timestamp = note.timestamp.takeIf { it > 0L } ?: (index + 1L) * MILLIS_PER_DAY
            val category = note.categoryName
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { label ->
                    Category(
                        id = "category-$label",
                        name = label,
                        colorArgb = 0L,
                        position = index,
                        isBuiltIn = false,
                    )
                }
            WorkoutDetails(
                record = WorkoutRecord(
                    workout = Workout(
                        id = "seed-$index-$timestamp",
                        startedAtEpochMillis = timestamp,
                        categoryId = category?.id,
                        title = note.title,
                        status = WorkoutStatus.COMPLETED,
                        createdAtEpochMillis = timestamp,
                        updatedAtEpochMillis = timestamp,
                    ),
                    exercises = emptyList(),
                    sets = emptyList(),
                ),
                exerciseDefinitions = emptyMap(),
                category = category,
            )
        }

        val suggestion = NextWorkoutPredictionService().predictNextWorkout(
            workouts = workouts,
            nowEpochMillis = workouts.maxOf { it.record.workout.startedAtEpochMillis } + MILLIS_PER_DAY,
        )

        requireNotNull(suggestion)
        assertEquals("Legs", suggestion.workoutLabel)
        assertEquals(SuggestionConfidence.MEDIUM, suggestion.confidence)
        assertEquals(PredictionBasis.HISTORICAL_TRANSITION, suggestion.evidence.basis)
        assertEquals("Pull", suggestion.evidence.previousWorkoutLabel)
        assertEquals(1, suggestion.evidence.matchingTransitionCount)
        assertEquals(1, suggestion.evidence.totalTransitionCount)
        assertTrue(suggestion.reason.contains("Pull"))
        assertTrue(suggestion.reason.contains("Legs"))
    }

    private fun seedFixtureFiles(): List<File> = seedFixtureResourceNames.map { resourceName ->
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream(resourceName)) {
            "Missing workout prediction seed fixture resource: $resourceName"
        }
        File.createTempFile("workout-prediction-seed", ".csv").apply {
            deleteOnExit()
            outputStream().use { output -> stream.use { input -> input.copyTo(output) } }
        }
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
        val seedFixtureResourceNames = listOf(
            "workout-prediction-seed-cycle/01-push-2026-07-01-18-00.csv",
            "workout-prediction-seed-cycle/02-pull-2026-07-02-18-00.csv",
            "workout-prediction-seed-cycle/03-legs-2026-07-03-18-00.csv",
            "workout-prediction-seed-cycle/04-push-2026-07-05-18-00.csv",
            "workout-prediction-seed-cycle/05-pull-2026-07-06-18-00.csv",
        )
    }
}
