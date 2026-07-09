package com.example.gymtrack.domain.notebookimport

/**
 * Domain-facing persistence boundary for notebook import.
 *
 * Data-layer implementations can later adapt this to Room, file storage, or DataStore. The domain
 * contract keeps import planning transactional and keeps source/intermediate deletion separate from
 * canonical workouts.
 */
interface NotebookImportDraftStore {
    fun saveBatchState(state: NotebookImportBatchState)
    fun loadBatchState(batchId: String): NotebookImportBatchState?
    fun deleteImportData(plan: NotebookImportDeletionPlan): NotebookImportDeletionResult
}

interface NotebookCanonicalImportWriter {
    fun commitImportPlan(plan: NotebookCanonicalImportPlan): NotebookCanonicalImportResult
}

data class NotebookCanonicalImportResult(
    val batchId: String,
    val importedWorkoutIds: List<String>,
) {
    init {
        require(batchId.isNotBlank()) { "Imported batch id must not be blank" }
        require(importedWorkoutIds.isNotEmpty()) { "Import result requires at least one workout id" }
        require(importedWorkoutIds.none { it.isBlank() }) {
            "Imported workout ids must not be blank"
        }
        require(importedWorkoutIds.distinct().size == importedWorkoutIds.size) {
            "Imported workout ids must be unique"
        }
    }
}

data class NotebookImportPreflightReport(
    val draftDuplicateReport: NotebookWorkoutDuplicateReport,
    val canonicalDuplicateReport: NotebookCanonicalDuplicateReport,
) {
    val canCommit: Boolean
        get() = !draftDuplicateReport.hasCandidates && !canonicalDuplicateReport.hasCandidates
}

object NotebookCanonicalImportPreflight {

    fun validate(
        plan: NotebookCanonicalImportPlan,
        draftDuplicateReport: NotebookWorkoutDuplicateReport = NotebookWorkoutDuplicateReport(emptyList()),
        canonicalDuplicateReport: NotebookCanonicalDuplicateReport = NotebookCanonicalDuplicateReport(emptyList()),
    ): NotebookImportPreflightReport {
        require(!draftDuplicateReport.hasCandidates) {
            "Draft duplicate candidates must be resolved before canonical import"
        }
        require(!canonicalDuplicateReport.hasCandidates) {
            "Canonical duplicate candidates must be resolved before canonical import"
        }
        require(plan.workouts.isNotEmpty()) { "Import preflight requires at least one planned workout" }
        return NotebookImportPreflightReport(
            draftDuplicateReport = draftDuplicateReport,
            canonicalDuplicateReport = canonicalDuplicateReport,
        )
    }
}
