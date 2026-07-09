package com.example.gymtrack.domain.notebookimport

import com.example.gymtrack.domain.model.ExerciseMode
import com.example.gymtrack.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookImportBatchStateTest {

    @Test
    fun initialStateCreatesPendingStateForEveryPage() {
        val state = NotebookImportBatchStateFactory.initial(
            batch = batch(pages = listOf(page("page-1"), page("page-2"))),
            nowEpochMillis = 1_000L,
        )

        assertEquals(NotebookImportBatchStatus.COLLECTING_PAGES, state.status)
        assertEquals(listOf("page-1", "page-2"), state.pageStates.map { it.pageId })
        assertEquals(
            listOf(NotebookPageProcessingStatus.PENDING, NotebookPageProcessingStatus.PENDING),
            state.pageStates.map { it.status },
        )
        assertTrue(state.canResume)
    }

    @Test
    fun pageStatesMustExactlyMatchBatchPages() {
        assertThrows(IllegalArgumentException::class.java) {
            NotebookImportBatchState(
                batch = batch(pages = listOf(page("page-1"), page("page-2"))),
                pageStates = listOf(pageState("page-1")),
                status = NotebookImportBatchStatus.COLLECTING_PAGES,
                createdAtEpochMillis = 1_000L,
                updatedAtEpochMillis = 1_000L,
            )
        }
    }

    @Test
    fun deriveStatusReportsProcessingWhenAnyPageIsProcessing() {
        val state = NotebookImportBatchStateFactory.deriveStatus(
            batch = batch(),
            pageStates = listOf(pageState("page-1", NotebookPageProcessingStatus.PROCESSING)),
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 2_000L,
        )

        assertEquals(NotebookImportBatchStatus.PROCESSING_PAGES, state.status)
        assertTrue(state.canResume)
    }

    @Test
    fun readyToImportRequiresProcessedPagesConfirmedBatchAndConfirmedWorkouts() {
        val state = NotebookImportBatchStateFactory.deriveStatus(
            batch = confirmedBatch(),
            pageStates = listOf(pageState("page-1", NotebookPageProcessingStatus.PROCESSED)),
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 2_000L,
        )

        assertEquals(NotebookImportBatchStatus.READY_TO_IMPORT, state.status)
        assertEquals(1, state.processedPageCount)
        assertTrue(state.canBeReadyForImport)
    }

    @Test
    fun batchReviewMustBeConfirmedBeforeCanonicalHistoryCanBeWritten() {
        val unconfirmedBatch = confirmedBatch(reviewState = ReviewState.NEEDS_REVIEW)

        assertFalse(unconfirmedBatch.canWriteCanonicalHistory)
        assertThrows(IllegalArgumentException::class.java) {
            NotebookImportBatchState(
                batch = unconfirmedBatch,
                pageStates = listOf(pageState("page-1", NotebookPageProcessingStatus.PROCESSED)),
                status = NotebookImportBatchStatus.READY_TO_IMPORT,
                createdAtEpochMillis = 1_000L,
                updatedAtEpochMillis = 2_000L,
            )
        }
    }

    @Test
    fun importedStatusRejectsUnconfirmedBatch() {
        assertThrows(IllegalArgumentException::class.java) {
            NotebookImportBatchState(
                batch = batch(),
                pageStates = listOf(pageState("page-1", NotebookPageProcessingStatus.PROCESSED)),
                status = NotebookImportBatchStatus.IMPORTED,
                createdAtEpochMillis = 1_000L,
                updatedAtEpochMillis = 2_000L,
            )
        }
    }

    @Test
    fun pageFailureKeepsBatchResumableButNotReadyForImport() {
        val state = NotebookImportBatchStateFactory.deriveStatus(
            batch = confirmedBatch(),
            pageStates = listOf(
                pageState(
                    pageId = "page-1",
                    status = NotebookPageProcessingStatus.FAILED,
                    message = "Image could not be decoded",
                )
            ),
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 2_000L,
        )

        assertEquals(NotebookImportBatchStatus.AWAITING_REVIEW, state.status)
        assertEquals(1, state.failedPageCount)
        assertTrue(state.canResume)
        assertFalse(state.canBeReadyForImport)
    }

    @Test
    fun fatalFailureAndCancelledStatesAreNotResumable() {
        val fatal = NotebookImportBatchState(
            batch = batch(),
            pageStates = listOf(pageState("page-1")),
            status = NotebookImportBatchStatus.FATAL_FAILURE,
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 2_000L,
            failureMessage = "Batch metadata is corrupted",
        )
        val cancelled = NotebookImportBatchState(
            batch = batch(),
            pageStates = listOf(pageState("page-1")),
            status = NotebookImportBatchStatus.CANCELLED,
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 2_000L,
        )

        assertFalse(fatal.canResume)
        assertFalse(cancelled.canResume)
    }

    @Test
    fun onlyFailedPagesMayCarryMessages() {
        assertThrows(IllegalArgumentException::class.java) {
            pageState(
                pageId = "page-1",
                status = NotebookPageProcessingStatus.NEEDS_REVIEW,
                message = "Ambiguous date",
            )
        }
    }

    private fun batch(
        pages: List<NotebookPageDraft> = listOf(page("page-1")),
        workouts: List<NotebookWorkoutDraft> = emptyList(),
        reviewState: ReviewState = ReviewState.NEEDS_REVIEW,
    ): NotebookImportBatchDraft = NotebookImportBatchDraft(
        id = "batch-1",
        pages = pages,
        workouts = workouts,
        reviewState = reviewState,
    )

    private fun confirmedBatch(
        reviewState: ReviewState = ReviewState.CONFIRMED,
    ): NotebookImportBatchDraft = batch(
        pages = listOf(page("page-1")),
        workouts = listOf(workout()),
        reviewState = reviewState,
    )

    private fun page(id: String): NotebookPageDraft = NotebookPageDraft(
        id = id,
        position = id.substringAfterLast('-').toInt() - 1,
        sourceFingerprintSha256 = "fingerprint-$id",
    )

    private fun pageState(
        pageId: String,
        status: NotebookPageProcessingStatus = NotebookPageProcessingStatus.PENDING,
        message: String? = null,
    ): NotebookPageProcessingState = NotebookPageProcessingState(
        pageId = pageId,
        status = status,
        updatedAtEpochMillis = 2_000L,
        message = message,
    )

    private fun workout(): NotebookWorkoutDraft = NotebookWorkoutDraft(
        id = "workout-1",
        sourcePageIds = setOf("page-1"),
        startedAtEpochMillis = confirmed(1_000L),
        title = confirmed("Push"),
        exercises = listOf(exercise()),
        reviewState = ReviewState.CONFIRMED,
    )

    private fun exercise(): NotebookExerciseDraft = NotebookExerciseDraft(
        id = "exercise-draft-1",
        position = 0,
        recognizedName = confirmed("Bench Press"),
        recognizedMode = confirmed(ExerciseMode.BILATERAL),
        exerciseResolution = ExerciseResolution(
            kind = ExerciseResolutionKind.MATCH_EXISTING,
            exerciseId = "exercise-1",
            reviewState = ReviewState.CONFIRMED,
        ),
        sets = listOf(set()),
        reviewState = ReviewState.CONFIRMED,
    )

    private fun set(): NotebookSetDraft = NotebookSetDraft(
        id = "set-1",
        position = 0,
        repetitions = confirmed(8),
        weight = confirmed(80.0),
        weightUnit = confirmed(WeightUnit.KILOGRAM),
        reviewState = ReviewState.CONFIRMED,
    )

    private fun <T> confirmed(value: T): RecognizedField<T> = RecognizedField(
        value = value,
        confidence = RecognitionConfidence(0.99),
        reviewState = ReviewState.CONFIRMED,
        provenance = NotebookLineProvenance(
            pageId = "page-1",
            lineNumber = 1,
            sourceText = "Bench 80 x 8",
        ),
    )
}
