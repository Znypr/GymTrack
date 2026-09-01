package com.example.gymtrack.domain.notebookimport

/**
 * Pure privacy and consent policy model for notebook imports.
 *
 * This file defines user-facing consent copy, retention behavior, and diagnostic redaction rules.
 * It intentionally does not delete files, persist settings, call external services, or control UI.
 */
enum class NotebookImportDataKind {
    SOURCE_IMAGE,
    EXTRACTED_TEXT,
    CONFIDENCE_DATA,
    DRAFT_WORKOUTS,
    CANONICAL_WORKOUTS,
    DIAGNOSTICS,
}

enum class NotebookImportDeletionTarget {
    SOURCE_IMAGES_ONLY,
    INTERMEDIATE_IMPORT_DATA,
    SOURCE_AND_INTERMEDIATE_IMPORT_DATA,
}

data class NotebookImportConsentCopy(
    val title: String,
    val body: String,
    val confirmActionLabel: String,
) {
    init {
        require(title.isNotBlank()) { "Consent title must not be blank" }
        require(body.isNotBlank()) { "Consent body must not be blank" }
        require(confirmActionLabel.isNotBlank()) { "Consent confirm action label must not be blank" }
    }
}

data class NotebookImportDiagnosticsPolicy(
    val includeProviderId: Boolean = true,
    val includeProcessingLocation: Boolean = true,
    val includePageCount: Boolean = true,
    val includeWarningCount: Boolean = true,
    val includeRawSourceImages: Boolean = false,
    val includeExtractedText: Boolean = false,
) {
    init {
        require(!includeRawSourceImages) {
            "Notebook diagnostics must not include raw source images by default"
        }
        require(!includeExtractedText) {
            "Notebook diagnostics must not include extracted notebook text by default"
        }
    }
}

data class NotebookImportDiagnosticSummary(
    val providerId: String? = null,
    val processingLocation: ProcessingLocation? = null,
    val pageCount: Int? = null,
    val warningCount: Int? = null,
) {
    init {
        require(providerId == null || providerId.isNotBlank()) {
            "Diagnostic provider id must not be blank"
        }
        require(pageCount == null || pageCount >= 0) { "Diagnostic page count must not be negative" }
        require(warningCount == null || warningCount >= 0) {
            "Diagnostic warning count must not be negative"
        }
    }
}

object NotebookImportPrivacyPolicy {

    fun consentCopy(
        processingLocation: ProcessingLocation,
        retentionPolicy: SourceImageRetentionPolicy,
    ): NotebookImportConsentCopy {
        val processingText = when (processingLocation) {
            ProcessingLocation.ON_DEVICE -> {
                "Recognition runs on this device. Your notebook images are not sent to an external service."
            }
            ProcessingLocation.CLOUD_OPT_IN -> {
                "Recognition may send notebook images or extracted notebook content to an external processing service. Use this only if you explicitly choose the more accurate cloud path."
            }
            ProcessingLocation.HYBRID_OPT_IN -> {
                "Recognition may combine on-device processing with an external processing service. Any external processing requires your explicit consent."
            }
        }
        val retentionText = when (retentionPolicy) {
            SourceImageRetentionPolicy.DELETE_AFTER_CONFIRMATION -> {
                "Source images are deleted after you confirm or discard the import."
            }
            SourceImageRetentionPolicy.KEEP_UNTIL_USER_DELETES -> {
                "Source images are kept as an audit trail until you delete them."
            }
            SourceImageRetentionPolicy.DO_NOT_STORE_SOURCE_IMAGE -> {
                "Source images are not stored after extraction."
            }
        }

        return NotebookImportConsentCopy(
            title = "Notebook import privacy",
            body = "$processingText $retentionText Existing workout history is not changed until you review and confirm the reconstructed workouts.",
            confirmActionLabel = if (processingLocation == ProcessingLocation.ON_DEVICE) {
                "Continue"
            } else {
                "Allow external processing"
            },
        )
    }

    fun requiresExternalConsent(processingLocation: ProcessingLocation): Boolean =
        processingLocation != ProcessingLocation.ON_DEVICE

    fun validateConsent(
        processingLocation: ProcessingLocation,
        consent: NotebookImportConsent,
    ) {
        require(!requiresExternalConsent(processingLocation) || consent.allowExternalProcessing) {
            "External notebook processing requires explicit consent"
        }
    }

    fun dataKindsDeletedBy(target: NotebookImportDeletionTarget): Set<NotebookImportDataKind> =
        when (target) {
            NotebookImportDeletionTarget.SOURCE_IMAGES_ONLY -> setOf(NotebookImportDataKind.SOURCE_IMAGE)
            NotebookImportDeletionTarget.INTERMEDIATE_IMPORT_DATA -> setOf(
                NotebookImportDataKind.EXTRACTED_TEXT,
                NotebookImportDataKind.CONFIDENCE_DATA,
                NotebookImportDataKind.DRAFT_WORKOUTS,
            )
            NotebookImportDeletionTarget.SOURCE_AND_INTERMEDIATE_IMPORT_DATA -> setOf(
                NotebookImportDataKind.SOURCE_IMAGE,
                NotebookImportDataKind.EXTRACTED_TEXT,
                NotebookImportDataKind.CONFIDENCE_DATA,
                NotebookImportDataKind.DRAFT_WORKOUTS,
            )
        }

    fun canDeleteSourceImages(
        batchStatus: NotebookImportBatchStatus,
        retentionPolicy: SourceImageRetentionPolicy,
    ): Boolean = when (retentionPolicy) {
        SourceImageRetentionPolicy.DO_NOT_STORE_SOURCE_IMAGE -> false
        SourceImageRetentionPolicy.KEEP_UNTIL_USER_DELETES -> true
        SourceImageRetentionPolicy.DELETE_AFTER_CONFIRMATION -> batchStatus in setOf(
            NotebookImportBatchStatus.IMPORTED,
            NotebookImportBatchStatus.CANCELLED,
            NotebookImportBatchStatus.FATAL_FAILURE,
        )
    }

    fun buildDiagnosticSummary(
        output: NotebookRecognitionOutput,
        pageCount: Int,
        policy: NotebookImportDiagnosticsPolicy = NotebookImportDiagnosticsPolicy(),
    ): NotebookImportDiagnosticSummary = NotebookImportDiagnosticSummary(
        providerId = output.provider.id.takeIf { policy.includeProviderId },
        processingLocation = output.provider.processingLocation.takeIf { policy.includeProcessingLocation },
        pageCount = pageCount.takeIf { policy.includePageCount },
        warningCount = output.warnings.size.takeIf { policy.includeWarningCount },
    )
}
