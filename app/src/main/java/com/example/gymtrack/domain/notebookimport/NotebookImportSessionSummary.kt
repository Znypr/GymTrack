package com.example.gymtrack.domain.notebookimport

/**
 * Read-only summary for future import screens.
 *
 * The summary collects progress, review, duplicate, and privacy state without changing the import.
 */
data class NotebookImportSessionSummary(
    val batchId: String,
    val pageCount: Int,
    val processedPageCount: Int,
    val failedPageCount: Int,
    val workoutCount: Int,
    val exerciseCount: Int,
    val setCount: Int,
    val reviewItemCount: Int,
    val draftDuplicateCount: Int,
    val canonicalDuplicateCount: Int,
    val canResume: Boolean,
    val canPlanCanonicalImport: Boolean,
    val canCommitAfterPreflight: Boolean,
    val privacyCopy: NotebookImportConsentCopy,
) {
    init {
        require(batchId.isNotBlank()) { "Session summary batch id must not be blank" }
        require(pageCount >= 0) { "Page count must not be negative" }
        require(processedPageCount >= 0) { "Processed page count must not be negative" }
        require(failedPageCount >= 0) { "Failed page count must not be negative" }
        require(workoutCount >= 0) { "Workout count must not be negative" }
        require(exerciseCount >= 0) { "Exercise count must not be negative" }
        require(setCount >= 0) { "Set count must not be negative" }
        require(reviewItemCount >= 0) { "Review item count must not be negative" }
        require(draftDuplicateCount >= 0) { "Draft duplicate count must not be negative" }
        require(canonicalDuplicateCount >= 0) { "Canonical duplicate count must not be negative" }
    }
}

object NotebookImportSessionSummaryBuilder {

    fun build(
        state: NotebookImportBatchState,
        reviewQueue: NotebookReviewQueue = NotebookImportReviewQueueBuilder.build(state.batch),
        draftDuplicateReport: NotebookWorkoutDuplicateReport =
            NotebookWorkoutDuplicateDetector.detectWithinBatch(state.batch),
        canonicalDuplicateReport: NotebookCanonicalDuplicateReport = NotebookCanonicalDuplicateReport(emptyList()),
    ): NotebookImportSessionSummary {
        val exercises = state.batch.workouts.flatMap { it.exercises }
        val sets = exercises.flatMap { it.sets }
        return NotebookImportSessionSummary(
            batchId = state.batch.id,
            pageCount = state.pageCount,
            processedPageCount = state.processedPageCount,
            failedPageCount = state.failedPageCount,
            workoutCount = state.batch.workouts.size,
            exerciseCount = exercises.size,
            setCount = sets.size,
            reviewItemCount = reviewQueue.pendingCount,
            draftDuplicateCount = draftDuplicateReport.candidates.size,
            canonicalDuplicateCount = canonicalDuplicateReport.candidates.size,
            canResume = state.canResume,
            canPlanCanonicalImport = state.batch.canWriteCanonicalHistory &&
                !draftDuplicateReport.hasCandidates,
            canCommitAfterPreflight = state.batch.canWriteCanonicalHistory &&
                !draftDuplicateReport.hasCandidates &&
                !canonicalDuplicateReport.hasCandidates,
            privacyCopy = NotebookImportPrivacyPolicy.consentCopy(
                processingLocation = state.batch.processingLocation,
                retentionPolicy = state.batch.consent.sourceImageRetentionPolicy,
            ),
        )
    }
}
