package com.example.gymtrack.domain.notebookimport

import com.example.gymtrack.domain.model.Exercise
import com.example.gymtrack.domain.model.ExerciseMode
import com.example.gymtrack.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookMatchingDuplicatesMetricsTest {

    @Test
    fun exerciseMatcherProposesExactExistingMatchWithoutConfirmingIt() {
        val result = NotebookExerciseMatcher.matchExercises(
            batch = batchWithWorkout(exerciseName = "Bench Press"),
            exerciseCatalog = listOf(exercise("bench", "Bench Press")),
        )

        val matched = result.batch.workouts.single().exercises.single().exerciseResolution
        assertEquals(ExerciseResolutionKind.MATCH_EXISTING, matched.kind)
        assertEquals("bench", matched.exerciseId)
        assertEquals(ReviewState.NEEDS_REVIEW, matched.reviewState)
        assertFalse(matched.isResolvedForImport)
        assertTrue(result.requiresReview)
    }

    @Test
    fun exerciseMatcherUsesAliases() {
        val result = NotebookExerciseMatcher.matchExercises(
            batch = batchWithWorkout(exerciseName = "bench"),
            exerciseCatalog = listOf(exercise("bench-press", "Barbell Bench Press", aliases = setOf("bench"))),
        )

        val candidate = result.candidatesByExerciseDraftId.getValue("exercise-1").single()
        assertEquals(NotebookExerciseMatchKind.ALIAS, candidate.kind)
        assertEquals("bench-press", candidate.exerciseId)
        assertEquals("Barbell Bench Press", candidate.canonicalName)
    }

    @Test
    fun exerciseMatcherProposesCreateNewWhenNoMatchExists() {
        val result = NotebookExerciseMatcher.matchExercises(
            batch = batchWithWorkout(exerciseName = "Machine Fly"),
            exerciseCatalog = listOf(exercise("bench", "Bench Press")),
        )

        val resolution = result.batch.workouts.single().exercises.single().exerciseResolution
        assertEquals(ExerciseResolutionKind.CREATE_NEW, resolution.kind)
        assertEquals("Machine Fly", resolution.canonicalName)
        assertEquals(ReviewState.NEEDS_REVIEW, resolution.reviewState)
    }

    @Test
    fun exerciseMatcherLeavesAmbiguousAliasUnresolved() {
        val result = NotebookExerciseMatcher.matchExercises(
            batch = batchWithWorkout(exerciseName = "press"),
            exerciseCatalog = listOf(
                exercise("bench", "Bench Press", aliases = setOf("press")),
                exercise("ohp", "Overhead Press", aliases = setOf("press")),
            ),
        )

        val resolution = result.batch.workouts.single().exercises.single().exerciseResolution
        assertEquals(ExerciseResolutionKind.UNRESOLVED, resolution.kind)
        assertTrue(result.warnings.any { it.contains("matched multiple") })
    }

    @Test
    fun duplicateDetectorFlagsExactDraftDuplicate() {
        val report = NotebookWorkoutDuplicateDetector.detectWithinBatch(
            batch = batchWithWorkouts(
                workout("workout-1", startedAt = 1_000L, title = "Push"),
                workout("workout-2", startedAt = 1_000L, title = "Push"),
            )
        )

        assertTrue(report.hasCandidates)
        assertEquals(NotebookDuplicateSeverity.EXACT_DRAFT_DUPLICATE, report.candidates.single().severity)
    }

    @Test
    fun duplicateDetectorFlagsSameDateAsPossibleDuplicate() {
        val report = NotebookWorkoutDuplicateDetector.detectWithinBatch(
            batch = batchWithWorkouts(
                workout("workout-1", startedAt = 1_000L, title = "Push"),
                workout("workout-2", startedAt = 1_000L, title = "Pull", exerciseName = "Row"),
            )
        )

        assertEquals(NotebookDuplicateSeverity.POSSIBLE_DATE_DUPLICATE, report.candidates.single().severity)
    }

    @Test
    fun fixtureMetricsCompareCountsAndUnresolvedLimit() {
        val batch = batchWithWorkouts(
            workout("workout-1", startedAt = 1_000L, title = "Push"),
        )
        val report = NotebookFixtureMetrics.evaluate(
            batch = batch,
            expected = NotebookFixtureExpectation(
                workoutCount = 1,
                exerciseCount = 1,
                setCount = 1,
                unresolvedFieldCountMaximum = 20,
            ),
        )

        assertTrue(report.workoutCountMatches)
        assertTrue(report.exerciseCountMatches)
        assertTrue(report.setCountMatches)
        assertTrue(report.unresolvedFieldsWithinLimit)
        assertTrue(report.passes)
    }

    @Test
    fun fixtureMetricsFailWhenExpectedCountsDoNotMatch() {
        val report = NotebookFixtureMetrics.evaluate(
            batch = batchWithWorkouts(workout("workout-1", startedAt = 1_000L, title = "Push")),
            expected = NotebookFixtureExpectation(
                workoutCount = 2,
                exerciseCount = 1,
                setCount = 1,
                unresolvedFieldCountMaximum = 20,
            ),
        )

        assertFalse(report.workoutCountMatches)
        assertFalse(report.passes)
    }

    private fun exercise(
        id: String,
        canonicalName: String,
        aliases: Set<String> = emptySet(),
    ): Exercise = Exercise(
        id = id,
        canonicalName = canonicalName,
        aliases = aliases,
    )

    private fun batchWithWorkout(exerciseName: String): NotebookImportBatchDraft = batchWithWorkouts(
        workout("workout-1", startedAt = 1_000L, title = "Push", exerciseName = exerciseName),
    )

    private fun batchWithWorkouts(vararg workouts: NotebookWorkoutDraft): NotebookImportBatchDraft =
        NotebookImportBatchDraft(
            id = "batch-1",
            pages = listOf(
                NotebookPageDraft(
                    id = "page-1",
                    position = 0,
                    sourceFingerprintSha256 = "fingerprint-page-1",
                )
            ),
            workouts = workouts.toList(),
        )

    private fun workout(
        id: String,
        startedAt: Long,
        title: String,
        exerciseName: String = "Bench Press",
    ): NotebookWorkoutDraft = NotebookWorkoutDraft(
        id = id,
        sourcePageIds = setOf("page-1"),
        startedAtEpochMillis = recognized(startedAt),
        title = recognized(title),
        exercises = listOf(
            NotebookExerciseDraft(
                id = "exercise-1",
                position = 0,
                recognizedName = recognized(exerciseName),
                recognizedMode = RecognizedField(
                    value = ExerciseMode.BILATERAL,
                    confidence = RecognitionConfidence(0.95),
                    reviewState = ReviewState.NEEDS_REVIEW,
                    provenance = provenance(),
                ),
                sets = listOf(
                    NotebookSetDraft(
                        id = "set-1",
                        position = 0,
                        repetitions = recognized(8),
                        weight = recognized(80.0),
                        weightUnit = recognized(WeightUnit.KILOGRAM),
                    )
                ),
            )
        ),
    )

    private fun provenance(): NotebookLineProvenance = NotebookLineProvenance(pageId = "page-1", lineNumber = 1)

    private fun <T> recognized(value: T): RecognizedField<T> = RecognizedField(
        value = value,
        confidence = RecognitionConfidence(0.95),
        reviewState = ReviewState.NEEDS_REVIEW,
        provenance = provenance(),
    )
}
