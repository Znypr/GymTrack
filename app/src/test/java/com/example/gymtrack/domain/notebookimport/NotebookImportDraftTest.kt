package com.example.gymtrack.domain.notebookimport

import com.example.gymtrack.domain.model.ExerciseMode
import com.example.gymtrack.domain.model.WeightUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookImportDraftTest {

    @Test
    fun unresolvedRecognitionCanRetainNoValueInsteadOfGuessing() {
        val field = RecognizedField<Long>(
            value = null,
            confidence = RecognitionConfidence(0.34),
            reviewState = ReviewState.NEEDS_REVIEW,
            provenance = provenance(),
        )

        assertTrue(field.needsReview)
        assertTrue(field.isLowConfidence)
    }

    @Test
    fun confirmedRecognitionRequiresExplicitValue() {
        assertThrows(IllegalArgumentException::class.java) {
            RecognizedField<Long>(
                value = null,
                confidence = RecognitionConfidence(0.99),
                reviewState = ReviewState.CONFIRMED,
                provenance = provenance(),
            )
        }
    }

    @Test
    fun importBatchRejectsDuplicatePageFingerprints() {
        assertThrows(IllegalArgumentException::class.java) {
            NotebookImportBatchDraft(
                id = "batch-1",
                pages = listOf(
                    NotebookPageDraft(
                        id = "page-1",
                        position = 0,
                        sourceFingerprintSha256 = "same-fingerprint",
                    ),
                    NotebookPageDraft(
                        id = "page-2",
                        position = 1,
                        sourceFingerprintSha256 = "same-fingerprint",
                    ),
                ),
            )
        }
    }

    @Test
    fun externalProcessingRequiresExplicitConsent() {
        assertThrows(IllegalArgumentException::class.java) {
            NotebookImportBatchDraft(
                id = "batch-1",
                pages = listOf(page()),
                processingLocation = ProcessingLocation.CLOUD_OPT_IN,
                consent = NotebookImportConsent(allowExternalProcessing = false),
            )
        }
    }

    @Test
    fun canonicalHistoryCannotBeWrittenUntilEveryWorkoutIsConfirmed() {
        val batch = NotebookImportBatchDraft(
            id = "batch-1",
            pages = listOf(page()),
            workouts = listOf(workout(reviewState = ReviewState.NEEDS_REVIEW)),
        )

        assertTrue(batch.hasUnresolvedFields)
        assertFalse(batch.canWriteCanonicalHistory)
    }

    @Test
    fun confirmedBatchCanWriteCanonicalHistory() {
        val batch = NotebookImportBatchDraft(
            id = "batch-1",
            pages = listOf(page()),
            workouts = listOf(workout()),
            reviewState = ReviewState.CONFIRMED,
        )

        assertFalse(batch.hasUnresolvedFields)
        assertTrue(batch.canWriteCanonicalHistory)
    }

    @Test
    fun weightedSetRequiresExplicitKnownUnitBeforeCanonicalImport() {
        val set = NotebookSetDraft(
            id = "set-1",
            position = 0,
            repetitions = confirmed(8),
            weight = confirmed(80.0),
            weightUnit = confirmed(WeightUnit.UNKNOWN),
            reviewState = ReviewState.CONFIRMED,
        )

        assertFalse(set.hasUnresolvedFields)
        assertFalse(set.isReadyForCanonicalImport)
    }

    private fun page(): NotebookPageDraft = NotebookPageDraft(
        id = "page-1",
        position = 0,
        sourceFingerprintSha256 = "fingerprint-1",
    )

    private fun workout(reviewState: ReviewState = ReviewState.CONFIRMED): NotebookWorkoutDraft =
        NotebookWorkoutDraft(
            id = "workout-1",
            sourcePageIds = setOf("page-1"),
            startedAtEpochMillis = confirmed(1_000L),
            title = confirmed("Push"),
            exercises = listOf(exercise()),
            reviewState = reviewState,
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

    private fun provenance(lineNumber: Int = 1): NotebookLineProvenance = NotebookLineProvenance(
        pageId = "page-1",
        lineNumber = lineNumber,
        sourceText = "Bench 80 x 8",
    )

    private fun <T> confirmed(value: T): RecognizedField<T> = RecognizedField(
        value = value,
        confidence = RecognitionConfidence(0.99),
        reviewState = ReviewState.CONFIRMED,
        provenance = provenance(),
    )
}
