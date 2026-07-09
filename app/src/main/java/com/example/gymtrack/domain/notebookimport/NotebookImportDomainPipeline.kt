package com.example.gymtrack.domain.notebookimport

import com.example.gymtrack.domain.model.Exercise
import com.example.gymtrack.domain.model.WorkoutRecord

/**
 * Pure-domain pipeline from recognized notebook lines to reviewable import state.
 *
 * This composes existing steps for tests and future use cases. It does not capture images, run OCR,
 * persist state, show UI, or write canonical workouts.
 */
data class NotebookImportDomainPipelineResult(
    val recognitionOutput: NotebookRecognitionOutput,
    val interpretationResult: NotebookTextInterpretationResult,
    val matchingResult: NotebookExerciseMatchingResult,
    val draftDuplicateReport: NotebookWorkoutDuplicateReport,
    val canonicalDuplicateReport: NotebookCanonicalDuplicateReport,
    val reviewQueue: NotebookReviewQueue,
    val metricsReport: NotebookFixtureMetricReport? = null,
) {
    val batch: NotebookImportBatchDraft
        get() = matchingResult.batch

    val requiresReview: Boolean
        get() = interpretationResult.requiresReview ||
            matchingResult.requiresReview ||
            draftDuplicateReport.hasCandidates ||
            canonicalDuplicateReport.hasCandidates ||
            !reviewQueue.isEmpty
}

object NotebookImportDomainPipeline {

    fun run(
        request: NotebookRecognitionRequest,
        provider: NotebookRecognitionProvider,
        exerciseCatalog: List<Exercise>,
        existingHistory: List<WorkoutRecord> = emptyList(),
        fixtureExpectation: NotebookFixtureExpectation? = null,
    ): NotebookImportDomainPipelineResult {
        NotebookRecognitionProviderPolicy.validateProviderAllowed(provider, request.batch.consent)
        val recognitionOutput = provider.recognize(request)
        val interpretationResult = NotebookTextInterpreter.interpret(request, recognitionOutput)
        val matchingResult = NotebookExerciseMatcher.matchExercises(
            batch = interpretationResult.batch,
            exerciseCatalog = exerciseCatalog,
        )
        val draftDuplicateReport = NotebookWorkoutDuplicateDetector.detectWithinBatch(matchingResult.batch)
        val reviewQueue = NotebookImportReviewQueueBuilder.build(matchingResult.batch)
        val provisionalPlan = runCatching {
            NotebookCanonicalImportPlanner.buildPlan(
                batch = matchingResult.batch,
                duplicateReport = draftDuplicateReport,
            )
        }.getOrNull()
        val canonicalDuplicateReport = provisionalPlan?.let {
            NotebookCanonicalDuplicateDetector.detectAgainstExistingHistory(
                plan = it,
                existingHistory = existingHistory,
            )
        } ?: NotebookCanonicalDuplicateReport(emptyList())
        val metricsReport = fixtureExpectation?.let {
            NotebookFixtureMetrics.evaluate(matchingResult.batch, it)
        }

        return NotebookImportDomainPipelineResult(
            recognitionOutput = recognitionOutput,
            interpretationResult = interpretationResult,
            matchingResult = matchingResult,
            draftDuplicateReport = draftDuplicateReport,
            canonicalDuplicateReport = canonicalDuplicateReport,
            reviewQueue = reviewQueue,
            metricsReport = metricsReport,
        )
    }
}
