package com.example.gymtrack.domain.notebookimport

/**
 * Pure resumability model for notebook imports.
 *
 * This class describes where an import batch can resume after interruption. It intentionally does
 * not choose a persistence backend. Room/DataStore mapping belongs to a later data-layer slice.
 */
enum class NotebookImportBatchStatus {
    COLLECTING_PAGES,
    PROCESSING_PAGES,
    AWAITING_REVIEW,
    READY_TO_IMPORT,
    IMPORTED,
    FATAL_FAILURE,
    CANCELLED,
}

enum class NotebookPageProcessingStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    NEEDS_REVIEW,
    DUPLICATE_REQUIRES_REVIEW,
    FAILED,
}

data class NotebookPageProcessingState(
    val pageId: String,
    val status: NotebookPageProcessingStatus = NotebookPageProcessingStatus.PENDING,
    val updatedAtEpochMillis: Long,
    val message: String? = null,
) {
    init {
        require(pageId.isNotBlank()) { "Page processing state id must not be blank" }
        require(updatedAtEpochMillis >= 0) { "Page processing update time must not be negative" }
        require(message == null || message.isNotBlank()) { "Page processing message must not be blank" }
        require(status == NotebookPageProcessingStatus.FAILED || message == null) {
            "Only failed page states may carry a failure message"
        }
    }
}

data class NotebookImportBatchState(
    val batch: NotebookImportBatchDraft,
    val pageStates: List<NotebookPageProcessingState>,
    val status: NotebookImportBatchStatus,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val failureMessage: String? = null,
) {
    init {
        require(createdAtEpochMillis >= 0) { "Batch state creation time must not be negative" }
        require(updatedAtEpochMillis >= createdAtEpochMillis) {
            "Batch state update time must not be before creation time"
        }
        require(pageStates.isNotEmpty()) { "Batch state requires page processing state" }
        require(pageStates.map { it.pageId }.distinct().size == pageStates.size) {
            "Page processing states must be unique per page"
        }
        require(pageStates.map { it.pageId }.toSet() == batch.pages.map { it.id }.toSet()) {
            "Page processing states must exactly match batch pages"
        }
        require(failureMessage == null || failureMessage.isNotBlank()) {
            "Batch failure message must not be blank"
        }
        require(status == NotebookImportBatchStatus.FATAL_FAILURE || failureMessage == null) {
            "Only fatal batch failures may carry a batch failure message"
        }
        require(status != NotebookImportBatchStatus.READY_TO_IMPORT || canBeReadyForImport) {
            "A batch cannot be ready for import until pages and workouts are fully confirmed"
        }
        require(status != NotebookImportBatchStatus.IMPORTED || canBeReadyForImport) {
            "A batch cannot be marked imported unless it was valid for canonical import"
        }
        require(status != NotebookImportBatchStatus.PROCESSING_PAGES || pageStates.any { it.status == NotebookPageProcessingStatus.PROCESSING }) {
            "Processing status requires at least one processing page"
        }
    }

    val processedPageCount: Int
        get() = pageStates.count { it.status == NotebookPageProcessingStatus.PROCESSED }

    val failedPageCount: Int
        get() = pageStates.count { it.status == NotebookPageProcessingStatus.FAILED }

    val pageCount: Int
        get() = pageStates.size

    val canResume: Boolean
        get() = status !in setOf(
            NotebookImportBatchStatus.IMPORTED,
            NotebookImportBatchStatus.FATAL_FAILURE,
            NotebookImportBatchStatus.CANCELLED,
        )

    val canBeReadyForImport: Boolean
        get() = batch.canWriteCanonicalHistory &&
            pageStates.all { it.status == NotebookPageProcessingStatus.PROCESSED }
}

object NotebookImportBatchStateFactory {

    fun initial(
        batch: NotebookImportBatchDraft,
        nowEpochMillis: Long,
    ): NotebookImportBatchState = NotebookImportBatchState(
        batch = batch,
        pageStates = batch.pages.map { page ->
            NotebookPageProcessingState(
                pageId = page.id,
                updatedAtEpochMillis = nowEpochMillis,
            )
        },
        status = NotebookImportBatchStatus.COLLECTING_PAGES,
        createdAtEpochMillis = nowEpochMillis,
        updatedAtEpochMillis = nowEpochMillis,
    )

    fun deriveStatus(
        batch: NotebookImportBatchDraft,
        pageStates: List<NotebookPageProcessingState>,
        createdAtEpochMillis: Long,
        updatedAtEpochMillis: Long,
    ): NotebookImportBatchState {
        val status = when {
            pageStates.any { it.status == NotebookPageProcessingStatus.PROCESSING } -> {
                NotebookImportBatchStatus.PROCESSING_PAGES
            }
            batch.canWriteCanonicalHistory &&
                pageStates.all { it.status == NotebookPageProcessingStatus.PROCESSED } -> {
                NotebookImportBatchStatus.READY_TO_IMPORT
            }
            pageStates.any { it.status != NotebookPageProcessingStatus.PENDING } -> {
                NotebookImportBatchStatus.AWAITING_REVIEW
            }
            else -> NotebookImportBatchStatus.COLLECTING_PAGES
        }

        return NotebookImportBatchState(
            batch = batch,
            pageStates = pageStates,
            status = status,
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }
}
