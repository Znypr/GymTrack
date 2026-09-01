package com.example.gymtrack.domain.notebookimport

/**
 * Deletion planning for notebook import source and intermediate data.
 *
 * Plans produced here are explicit about what may be deleted and intentionally exclude canonical
 * workouts. Actual deletion belongs to the data layer behind NotebookImportDraftStore.
 */
data class NotebookImportDeletionPlan(
    val batchId: String,
    val target: NotebookImportDeletionTarget,
    val dataKinds: Set<NotebookImportDataKind>,
    val pageIds: Set<String> = emptySet(),
) {
    init {
        require(batchId.isNotBlank()) { "Deletion plan batch id must not be blank" }
        require(dataKinds.isNotEmpty()) { "Deletion plan requires at least one data kind" }
        require(NotebookImportDataKind.CANONICAL_WORKOUTS !in dataKinds) {
            "Notebook import deletion plans must never delete canonical workouts"
        }
        require(pageIds.none { it.isBlank() }) { "Deletion plan page ids must not be blank" }
        require(
            NotebookImportDataKind.SOURCE_IMAGE in dataKinds || pageIds.isEmpty()
        ) { "Page-specific deletion is only valid for source image deletion" }
    }
}

data class NotebookImportDeletionResult(
    val batchId: String,
    val deletedDataKinds: Set<NotebookImportDataKind>,
    val deletedPageIds: Set<String> = emptySet(),
) {
    init {
        require(batchId.isNotBlank()) { "Deletion result batch id must not be blank" }
        require(deletedDataKinds.isNotEmpty()) { "Deletion result requires at least one data kind" }
        require(NotebookImportDataKind.CANONICAL_WORKOUTS !in deletedDataKinds) {
            "Notebook import deletion results must never include canonical workouts"
        }
        require(deletedPageIds.none { it.isBlank() }) { "Deleted page ids must not be blank" }
    }
}

object NotebookImportDeletionPlanner {

    fun planDeletion(
        state: NotebookImportBatchState,
        target: NotebookImportDeletionTarget,
    ): NotebookImportDeletionPlan {
        val dataKinds = NotebookImportPrivacyPolicy.dataKindsDeletedBy(target)
        require(NotebookImportDataKind.CANONICAL_WORKOUTS !in dataKinds) {
            "Notebook import deletion targets must never include canonical workouts"
        }
        val pageIds = if (NotebookImportDataKind.SOURCE_IMAGE in dataKinds) {
            require(
                NotebookImportPrivacyPolicy.canDeleteSourceImages(
                    batchStatus = state.status,
                    retentionPolicy = state.batch.consent.sourceImageRetentionPolicy,
                )
            ) { "Source images are not eligible for deletion in the current batch state" }
            state.batch.pages.map { it.id }.toSet()
        } else {
            emptySet()
        }
        return NotebookImportDeletionPlan(
            batchId = state.batch.id,
            target = target,
            dataKinds = dataKinds,
            pageIds = pageIds,
        )
    }
}
