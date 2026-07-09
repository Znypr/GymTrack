package com.example.gymtrack.domain.recommendation

import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.util.importNote
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
        val workouts = notes.map { note ->
            WorkoutDetails(
                record = WorkoutRecord(
                    workout = Workout(
                        id = "seed-${note.timestamp}",
                        startedAtEpochMillis = note.timestamp,
                        categoryId = note.categoryName,
                        title = note.title,
                        status = WorkoutStatus.COMPLETED,
                        createdAtEpochMillis = note.timestamp,
                        updatedAtEpochMillis = note.timestamp,
                    ),
                    exercises = emptyList(),
                    sets = emptyList(),
                ),
                exerciseDefinitions = emptyMap(),
                category = null,
            )
        }

        val suggestion = NextWorkoutPredictionService().predictNextWorkout(
            workouts = workouts,
            nowEpochMillis = notes.maxOf { it.timestamp } + MILLIS_PER_DAY,
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

    private fun seedFixtureFiles(): List<File> {
        val resourceRoot = requireNotNull(
            javaClass.classLoader?.getResource("workout-prediction-seed-cycle"),
        ) { "Missing workout prediction seed fixture resources" }
        return File(resourceRoot.toURI())
            .listFiles { file -> file.extension == "csv" }
            ?.sortedBy { it.name }
            ?.also { files -> assertEquals(5, files.size) }
            ?: error("No workout prediction seed fixture CSVs found")
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
