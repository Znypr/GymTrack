package com.example.gymtrack.domain.notebookimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookImportPrivacyTest {

    @Test
    fun onDeviceConsentCopyDoesNotAskForExternalProcessing() {
        val copy = NotebookImportPrivacyPolicy.consentCopy(
            processingLocation = ProcessingLocation.ON_DEVICE,
            retentionPolicy = SourceImageRetentionPolicy.DELETE_AFTER_CONFIRMATION,
        )

        assertEquals("Notebook import privacy", copy.title)
        assertEquals("Continue", copy.confirmActionLabel)
        assertTrue(copy.body.contains("not sent to an external service"))
        assertTrue(copy.body.contains("deleted after you confirm or discard"))
        assertTrue(copy.body.contains("not changed until you review and confirm"))
    }

    @Test
    fun cloudConsentCopyRequiresExplicitExternalActionLabel() {
        val copy = NotebookImportPrivacyPolicy.consentCopy(
            processingLocation = ProcessingLocation.CLOUD_OPT_IN,
            retentionPolicy = SourceImageRetentionPolicy.KEEP_UNTIL_USER_DELETES,
        )

        assertEquals("Allow external processing", copy.confirmActionLabel)
        assertTrue(copy.body.contains("external processing service"))
        assertTrue(copy.body.contains("kept as an audit trail"))
    }

    @Test
    fun externalProcessingRequiresConsent() {
        assertThrows(IllegalArgumentException::class.java) {
            NotebookImportPrivacyPolicy.validateConsent(
                processingLocation = ProcessingLocation.HYBRID_OPT_IN,
                consent = NotebookImportConsent(allowExternalProcessing = false),
            )
        }

        NotebookImportPrivacyPolicy.validateConsent(
            processingLocation = ProcessingLocation.HYBRID_OPT_IN,
            consent = NotebookImportConsent(allowExternalProcessing = true),
        )
    }

    @Test
    fun deletionTargetsNeverDeleteCanonicalWorkouts() {
        val deletedKinds = NotebookImportPrivacyPolicy.dataKindsDeletedBy(
            NotebookImportDeletionTarget.SOURCE_AND_INTERMEDIATE_IMPORT_DATA,
        )

        assertTrue(NotebookImportDataKind.SOURCE_IMAGE in deletedKinds)
        assertTrue(NotebookImportDataKind.EXTRACTED_TEXT in deletedKinds)
        assertTrue(NotebookImportDataKind.CONFIDENCE_DATA in deletedKinds)
        assertTrue(NotebookImportDataKind.DRAFT_WORKOUTS in deletedKinds)
        assertFalse(NotebookImportDataKind.CANONICAL_WORKOUTS in deletedKinds)
    }

    @Test
    fun sourceImagesCanAutoDeleteOnlyAfterTerminalState() {
        assertFalse(
            NotebookImportPrivacyPolicy.canDeleteSourceImages(
                batchStatus = NotebookImportBatchStatus.AWAITING_REVIEW,
                retentionPolicy = SourceImageRetentionPolicy.DELETE_AFTER_CONFIRMATION,
            )
        )
        assertTrue(
            NotebookImportPrivacyPolicy.canDeleteSourceImages(
                batchStatus = NotebookImportBatchStatus.IMPORTED,
                retentionPolicy = SourceImageRetentionPolicy.DELETE_AFTER_CONFIRMATION,
            )
        )
        assertTrue(
            NotebookImportPrivacyPolicy.canDeleteSourceImages(
                batchStatus = NotebookImportBatchStatus.CANCELLED,
                retentionPolicy = SourceImageRetentionPolicy.DELETE_AFTER_CONFIRMATION,
            )
        )
    }

    @Test
    fun keepUntilUserDeletesAllowsManualSourceDeletionBeforeImportCompletes() {
        assertTrue(
            NotebookImportPrivacyPolicy.canDeleteSourceImages(
                batchStatus = NotebookImportBatchStatus.AWAITING_REVIEW,
                retentionPolicy = SourceImageRetentionPolicy.KEEP_UNTIL_USER_DELETES,
            )
        )
    }

    @Test
    fun doNotStoreSourceImagesDoesNotExposeSourceDeletionAction() {
        assertFalse(
            NotebookImportPrivacyPolicy.canDeleteSourceImages(
                batchStatus = NotebookImportBatchStatus.IMPORTED,
                retentionPolicy = SourceImageRetentionPolicy.DO_NOT_STORE_SOURCE_IMAGE,
            )
        )
    }

    @Test
    fun defaultDiagnosticsExcludeSensitiveContent() {
        val policy = NotebookImportDiagnosticsPolicy()
        val summary = NotebookImportPrivacyPolicy.buildDiagnosticSummary(
            output = output(warnings = listOf("No legible text found")),
            pageCount = 2,
            policy = policy,
        )

        assertFalse(policy.includeRawSourceImages)
        assertFalse(policy.includeExtractedText)
        assertEquals("fixture-lines", summary.providerId)
        assertEquals(ProcessingLocation.ON_DEVICE, summary.processingLocation)
        assertEquals(2, summary.pageCount)
        assertEquals(1, summary.warningCount)
    }

    @Test
    fun diagnosticsCanSuppressProviderAndWarningCounts() {
        val summary = NotebookImportPrivacyPolicy.buildDiagnosticSummary(
            output = output(warnings = listOf("No legible text found")),
            pageCount = 2,
            policy = NotebookImportDiagnosticsPolicy(
                includeProviderId = false,
                includeWarningCount = false,
            ),
        )

        assertNull(summary.providerId)
        assertNull(summary.warningCount)
        assertEquals(ProcessingLocation.ON_DEVICE, summary.processingLocation)
        assertEquals(2, summary.pageCount)
    }

    @Test
    fun diagnosticsRejectRawSourceImagesAndExtractedText() {
        assertThrows(IllegalArgumentException::class.java) {
            NotebookImportDiagnosticsPolicy(includeRawSourceImages = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NotebookImportDiagnosticsPolicy(includeExtractedText = true)
        }
    }

    private fun output(
        warnings: List<String> = emptyList(),
    ): NotebookRecognitionOutput = NotebookRecognitionOutput(
        provider = NotebookRecognitionProviderDescriptor(
            id = "fixture-lines",
            displayName = "Fixture lines",
            processingLocation = ProcessingLocation.ON_DEVICE,
        ),
        recognizedPages = emptyList(),
        warnings = warnings,
    )
}
