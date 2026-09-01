package com.example.gymtrack.domain.notebookimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookRecognitionProviderTest {

    @Test
    fun externalProviderRequiresExplicitConsent() {
        val provider = NotebookRecognitionProviderDescriptor(
            id = "cloud-provider",
            displayName = "Cloud provider",
            processingLocation = ProcessingLocation.CLOUD_OPT_IN,
        )

        assertThrows(IllegalArgumentException::class.java) {
            NotebookRecognitionProviderPolicy.validateProviderAllowed(
                provider = provider,
                consent = NotebookImportConsent(allowExternalProcessing = false),
            )
        }

        NotebookRecognitionProviderPolicy.validateProviderAllowed(
            provider = provider,
            consent = NotebookImportConsent(allowExternalProcessing = true),
        )
    }

    @Test
    fun fixtureProviderReturnsRecognizedLinesWithProvenance() {
        val provider = FixtureNotebookRecognitionProvider(
            linesByPageId = mapOf(
                "page-1" to listOf("Push", "Bench 80 x 8"),
            )
        )

        val output = provider.recognize(
            NotebookRecognitionRequest(batch = batch())
        )

        val page = output.recognizedPages.single()
        assertEquals("page-1", page.pageId)
        assertEquals(listOf("Push", "Bench 80 x 8"), page.lines.map { it.text })
        assertEquals(2, page.lines[1].lineNumber)
        assertEquals("Bench 80 x 8", page.lines[1].provenance.sourceText)
        assertFalse(output.requiresReview)
    }

    @Test
    fun fixtureProviderRejectsUnknownPageLines() {
        val provider = FixtureNotebookRecognitionProvider(
            linesByPageId = mapOf(
                "page-2" to listOf("Bench 80 x 8"),
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            provider.recognize(NotebookRecognitionRequest(batch = batch()))
        }
    }

    @Test
    fun recognitionRequestRejectsPreprocessingForUnknownPage() {
        assertThrows(IllegalArgumentException::class.java) {
            NotebookRecognitionRequest(
                batch = batch(),
                pagePreprocessingResults = listOf(
                    NotebookPagePreprocessingResult(pageId = "page-2"),
                ),
            )
        }
    }

    @Test
    fun recognizedPageRejectsLinesFromOtherPages() {
        assertThrows(IllegalArgumentException::class.java) {
            RecognizedNotebookPage(
                pageId = "page-1",
                lines = listOf(
                    RecognizedNotebookLine(
                        id = "line-1",
                        pageId = "page-2",
                        lineNumber = 1,
                        text = "Bench 80 x 8",
                        confidence = RecognitionConfidence(1.0),
                    )
                ),
                confidence = RecognitionConfidence(1.0),
            )
        }
    }

    @Test
    fun lowConfidenceLinesRequireReview() {
        val page = RecognizedNotebookPage(
            pageId = "page-1",
            lines = listOf(
                RecognizedNotebookLine(
                    id = "line-1",
                    pageId = "page-1",
                    lineNumber = 1,
                    text = "Bench 80 x ?",
                    confidence = RecognitionConfidence(0.50),
                )
            ),
            confidence = RecognitionConfidence(0.95),
        )
        val output = NotebookRecognitionOutput(
            provider = NotebookRecognitionProviderDescriptor(
                id = "fixture-lines",
                displayName = "Fixture lines",
                processingLocation = ProcessingLocation.ON_DEVICE,
            ),
            recognizedPages = listOf(page),
        )

        assertTrue(page.requiresReview)
        assertTrue(output.requiresReview)
    }

    @Test
    fun warningsRequireReview() {
        val output = NotebookRecognitionOutput(
            provider = NotebookRecognitionProviderDescriptor(
                id = "fixture-lines",
                displayName = "Fixture lines",
                processingLocation = ProcessingLocation.ON_DEVICE,
            ),
            recognizedPages = emptyList(),
            warnings = listOf("No legible text found"),
        )

        assertTrue(output.requiresReview)
    }

    private fun batch(
        consent: NotebookImportConsent = NotebookImportConsent(),
    ): NotebookImportBatchDraft = NotebookImportBatchDraft(
        id = "batch-1",
        pages = listOf(
            NotebookPageDraft(
                id = "page-1",
                position = 0,
                sourceFingerprintSha256 = "fingerprint-page-1",
            )
        ),
        consent = consent,
    )
}
