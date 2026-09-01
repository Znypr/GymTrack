package com.example.gymtrack.domain.notebookimport

import com.example.gymtrack.domain.model.Exercise
import com.example.gymtrack.domain.model.ExerciseMode
import com.example.gymtrack.domain.model.WeightUnit
import com.example.gymtrack.domain.model.Workout
import com.example.gymtrack.domain.model.WorkoutExercise
import com.example.gymtrack.domain.model.WorkoutRecord
import com.example.gymtrack.domain.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookImportDomainPipelineTest {

    @Test
    fun pipelineProducesReviewableDraftMatchingQueueAndMetricsFromFixtureLines() {
        val result = NotebookImportDomainPipeline.run(
            request = request(),
            provider = FixtureNotebookRecognitionProvider(
                linesByPageId = mapOf(
                    "page-1" to listOf(
                        "2026-07-09 Push",
                        "Bench Press 80 kg x 8",
                        "Bench Press 82.5 kg x 6",
                    )
                )
            ),
            exerciseCatalog = listOf(
                Exercise(
                    id = "bench",
                    canonicalName = "Barbell Bench Press",
                    aliases = setOf("Bench Press"),
                )
            ),
            fixtureExpectation = NotebookFixtureExpectation(
                workoutCount = 1,
                exerciseCount = 1,
                setCount = 2,
                unresolvedFieldCountMaximum = 20,
            ),
        )

        val workout = result.batch.workouts.single()
        val exercise = workout.exercises.single()

        assertTrue(result.requiresReview)
        assertEquals("Push", workout.title?.value)
        assertEquals(ExerciseResolutionKind.MATCH_EXISTING, exercise.exerciseResolution.kind)
        assertEquals("bench", exercise.exerciseResolution.exerciseId)
        assertFalse(exercise.exerciseResolution.isResolvedForImport)
        assertFalse(result.draftDuplicateReport.hasCandidates)
        assertTrue(result.reviewQueue.pendingCount > 0)
        assertNotNull(result.metricsReport)
        assertTrue(result.metricsReport?.passes == true)
    }

    @Test
    fun pipelineKeepsUnknownExerciseAsCreateNewProposalWithoutConfirmation() {
        val result = NotebookImportDomainPipeline.run(
            request = request(),
            provider = FixtureNotebookRecognitionProvider(
                linesByPageId = mapOf("page-1" to listOf("Machine Fly 12 kg x 10")),
            ),
            exerciseCatalog = emptyList(),
        )

        val resolution = result.batch.workouts.single().exercises.single().exerciseResolution
        assertEquals(ExerciseResolutionKind.CREATE_NEW, resolution.kind)
        assertEquals("Machine Fly", resolution.canonicalName)
        assertEquals(ReviewState.NEEDS_REVIEW, resolution.reviewState)
        assertTrue(result.reviewQueue.items.any { it.kind == NotebookReviewItemKind.EXERCISE_RESOLUTION })
    }

    @Test
    fun sessionSummaryReportsCountsAndPrivacyCopy() {
        val pipeline = NotebookImportDomainPipeline.run(
            request = request(),
            provider = FixtureNotebookRecognitionProvider(
                linesByPageId = mapOf("page-1" to listOf("Bench Press 80 kg x 8")),
            ),
            exerciseCatalog = listOf(Exercise(id = "bench", canonicalName = "Bench Press")),
        )
        val state = NotebookImportBatchState(
            batch = pipeline.batch,
            pageStates = listOf(
                NotebookPageProcessingState(
                    pageId = "page-1",
                    status = NotebookPageProcessingStatus.PROCESSED,
                    updatedAtEpochMillis = 1L,
                )
            ),
            status = NotebookImportBatchStatus.AWAITING_REVIEW,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )

        val summary = NotebookImportSessionSummaryBuilder.build(
            state = state,
            reviewQueue = pipeline.reviewQueue,
            draftDuplicateReport = pipeline.draftDuplicateReport,
            canonicalDuplicateReport = pipeline.canonicalDuplicateReport,
        )

        assertEquals("batch-1", summary.batchId)
        assertEquals(1, summary.pageCount)
        assertEquals(1, summary.processedPageCount)
        assertEquals(1, summary.workoutCount)
        assertEquals(1, summary.exerciseCount)
        assertEquals(1, summary.setCount)
        assertTrue(summary.reviewItemCount > 0)
        assertTrue(summary.privacyCopy.body.contains("not sent to an external service"))
        assertTrue(summary.canResume)
        assertFalse(summary.canCommitAfterPreflight)
    }

    @Test
    fun pipelineCanDetectCanonicalDuplicatesAfterManualReviewProducesPlan() {
        val reviewedBatch = reviewedBatch()
        val plan = NotebookCanonicalImportPlanner.buildPlan(reviewedBatch)
        val canonicalDuplicateReport = NotebookCanonicalDuplicateDetector.detectAgainstExistingHistory(
            plan = plan,
            existingHistory = listOf(existingWorkout()),
        )

        assertTrue(canonicalDuplicateReport.hasCandidates)
        assertEquals(
            NotebookCanonicalDuplicateSeverity.EXACT_CANONICAL_DUPLICATE,
            canonicalDuplicateReport.candidates.single().severity,
        )
    }

    private fun request(): NotebookRecognitionRequest = NotebookRecognitionRequest(
        batch = NotebookImportBatchDraft(
            id = "batch-1",
            pages = listOf(
                NotebookPageDraft(
                    id = "page-1",
                    position = 0,
                    sourceFingerprintSha256 = "fingerprint-page-1",
                )
            ),
        )
    )

    private fun reviewedBatch(): NotebookImportBatchDraft {
        val set = NotebookDraftReview.confirmSet(
            set = NotebookSetDraft(
                id = "set-1",
                position = 0,
                repetitions = recognized(8),
                weight = recognized(80.0),
                weightUnit = recognized(WeightUnit.KILOGRAM),
            ),
            repetitions = 8,
            weight = 80.0,
            weightUnit = WeightUnit.KILOGRAM,
        )
        val exercise = NotebookDraftReview.confirmExercise(
            exercise = NotebookExerciseDraft(
                id = "exercise-1",
                position = 0,
                recognizedName = recognized("Bench Press"),
                recognizedMode = recognized(ExerciseMode.BILATERAL),
                sets = listOf(set),
            ),
            mode = ExerciseMode.BILATERAL,
            resolution = NotebookDraftReview.confirmExistingExerciseResolution(
                exerciseId = "bench",
                canonicalName = "Bench Press",
            ),
        )
        val workout = NotebookDraftReview.confirmWorkout(
            workout = NotebookWorkoutDraft(
                id = "workout-1",
                sourcePageIds = setOf("page-1"),
                startedAtEpochMillis = recognized(1_000L),
                title = recognized("Push"),
                exercises = listOf(exercise),
            ),
            startedAtEpochMillis = 1_000L,
            title = "Push",
        )
        return NotebookDraftReview.confirmBatch(
            NotebookImportBatchDraft(
                id = "batch-1",
                pages = listOf(
                    NotebookPageDraft(
                        id = "page-1",
                        position = 0,
                        sourceFingerprintSha256 = "fingerprint-page-1",
                    )
                ),
                workouts = listOf(workout),
            )
        )
    }

    private fun existingWorkout(): WorkoutRecord = WorkoutRecord(
        workout = Workout(
            id = "existing-workout-1",
            startedAtEpochMillis = 1_000L,
            title = "Push",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        ),
        exercises = listOf(
            WorkoutExercise(
                id = "existing-exercise-1",
                workoutId = "existing-workout-1",
                exerciseId = "bench",
                position = 0,
            )
        ),
        sets = listOf(
            WorkoutSet(
                id = "existing-set-1",
                workoutExerciseId = "existing-exercise-1",
                position = 0,
                repetitions = 8,
                weight = 80.0,
                weightUnit = WeightUnit.KILOGRAM,
            )
        ),
    )

    private fun provenance(): NotebookLineProvenance = NotebookLineProvenance(
        pageId = "page-1",
        lineNumber = 1,
    )

    private fun <T> recognized(value: T): RecognizedField<T> = RecognizedField(
        value = value,
        confidence = RecognitionConfidence(0.95),
        reviewState = ReviewState.NEEDS_REVIEW,
        provenance = provenance(),
    )
}
