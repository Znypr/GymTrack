package com.example.gymtrack.domain.notebookimport

import com.example.gymtrack.domain.model.ExerciseMode
import com.example.gymtrack.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookImportReviewQueueTest {

    @Test
    fun reviewQueueUsesGloballyUniqueIdsWhenSetIdsRepeatAcrossExercises() {
        val queue = NotebookImportReviewQueueBuilder.build(
            batch = NotebookImportBatchDraft(
                id = "batch-1",
                pages = listOf(
                    NotebookPageDraft(
                        id = "page-1",
                        position = 0,
                        sourceFingerprintSha256 = "fingerprint-page-1",
                    )
                ),
                workouts = listOf(
                    NotebookWorkoutDraft(
                        id = "workout-1",
                        sourcePageIds = setOf("page-1"),
                        startedAtEpochMillis = recognized(1_000L),
                        exercises = listOf(
                            exercise(id = "exercise-1", name = "Bench Press"),
                            exercise(id = "exercise-2", name = "Row"),
                        ),
                    )
                ),
            )
        )

        assertEquals(queue.items.size, queue.items.map { it.id }.distinct().size)
        assertTrue(queue.items.any { it.id.contains("exercise:exercise-1:set:set-1") })
        assertTrue(queue.items.any { it.id.contains("exercise:exercise-2:set:set-1") })
    }

    @Test
    fun confirmedBatchProducesEmptyReviewQueue() {
        val set = NotebookDraftReview.confirmSet(
            set = setDraft(),
            repetitions = 8,
            weight = 80.0,
            weightUnit = WeightUnit.KILOGRAM,
        )
        val exercise = NotebookDraftReview.confirmExercise(
            exercise = exercise(id = "exercise-1", name = "Bench Press", sets = listOf(set)),
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
        val batch = NotebookDraftReview.confirmBatch(
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

        assertTrue(NotebookImportReviewQueueBuilder.build(batch).isEmpty)
    }

    private fun exercise(
        id: String,
        name: String,
        sets: List<NotebookSetDraft> = listOf(setDraft()),
    ): NotebookExerciseDraft = NotebookExerciseDraft(
        id = id,
        position = id.substringAfterLast('-').toInt() - 1,
        recognizedName = recognized(name),
        recognizedMode = recognized(ExerciseMode.BILATERAL),
        sets = sets,
    )

    private fun setDraft(): NotebookSetDraft = NotebookSetDraft(
        id = "set-1",
        position = 0,
        repetitions = recognized(8),
        weight = recognized(80.0),
        weightUnit = recognized(WeightUnit.KILOGRAM),
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
