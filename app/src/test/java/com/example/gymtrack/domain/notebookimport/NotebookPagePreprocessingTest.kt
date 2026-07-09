package com.example.gymtrack.domain.notebookimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookPagePreprocessingTest {

    @Test
    fun preprocessingResultDerivesOrientationFromDimensions() {
        val portrait = preprocessing(pageId = "page-1", widthPx = 1000, heightPx = 1600)
        val landscape = preprocessing(pageId = "page-2", widthPx = 1600, heightPx = 1000)
        val unknown = preprocessing(pageId = "page-3")

        assertEquals(NotebookPageOrientation.PORTRAIT, portrait.orientation)
        assertEquals(NotebookPageOrientation.LANDSCAPE, landscape.orientation)
        assertEquals(NotebookPageOrientation.UNKNOWN, unknown.orientation)
    }

    @Test
    fun qualityIssuesOrLowConfidenceRequireReview() {
        val glare = preprocessing(
            pageId = "page-1",
            qualityIssues = setOf(NotebookPageQualityIssue.GLARE),
        )
        val lowConfidence = preprocessing(
            pageId = "page-2",
            confidence = RecognitionConfidence(0.40),
        )
        val clean = preprocessing(pageId = "page-3")

        assertTrue(glare.needsManualReview)
        assertTrue(lowConfidence.needsManualReview)
        assertFalse(clean.needsManualReview)
    }

    @Test
    fun invalidRotationIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            preprocessing(pageId = "page-1", rotationDegreesClockwise = 45)
        }
    }

    @Test
    fun orderingUsesDetectedPageNumbersBeforeUploadOrder() {
        val pages = listOf(page("page-a", 0), page("page-b", 1))
        val candidates = NotebookPageOrdering.proposeOrder(
            pages = pages,
            preprocessingResults = listOf(
                preprocessing(pageId = "page-a", detectedPageNumber = 2),
                preprocessing(pageId = "page-b", detectedPageNumber = 1),
            ),
        )

        assertEquals("page-a", candidates[0].pageId)
        assertEquals(1, candidates[0].proposedPosition)
        assertEquals(NotebookPageOrderingReason.DETECTED_PAGE_NUMBER, candidates[0].reason)
        assertTrue(candidates[0].requiresReview)
        assertEquals("page-b", candidates[1].pageId)
        assertEquals(0, candidates[1].proposedPosition)
    }

    @Test
    fun orderingFallsBackToDatesThenUploadOrder() {
        val pages = listOf(page("page-a", 0), page("page-b", 1), page("page-c", 2))
        val candidates = NotebookPageOrdering.proposeOrder(
            pages = pages,
            preprocessingResults = listOf(
                preprocessing(pageId = "page-a", detectedDateEpochMillis = 3_000L),
                preprocessing(pageId = "page-c", detectedDateEpochMillis = 1_000L),
            ),
        )

        val byPageId = candidates.associateBy { it.pageId }
        assertEquals(2, byPageId.getValue("page-a").proposedPosition)
        assertEquals(1, byPageId.getValue("page-b").proposedPosition)
        assertEquals(0, byPageId.getValue("page-c").proposedPosition)
        assertEquals(NotebookPageOrderingReason.DETECTED_DATE, byPageId.getValue("page-a").reason)
        assertEquals(NotebookPageOrderingReason.ORIGINAL_UPLOAD_ORDER, byPageId.getValue("page-b").reason)
    }

    @Test
    fun unchangedUploadOrderDoesNotRequireReviewWhenConfidenceIsHigh() {
        val candidates = NotebookPageOrdering.proposeOrder(
            pages = listOf(page("page-a", 0), page("page-b", 1)),
            preprocessingResults = emptyList(),
        )

        assertFalse(candidates[0].requiresReview)
        assertFalse(candidates[1].requiresReview)
        assertEquals(NotebookPageOrderingReason.ORIGINAL_UPLOAD_ORDER, candidates[0].reason)
    }

    @Test
    fun preprocessingResultsCannotReferenceUnknownPages() {
        assertThrows(IllegalArgumentException::class.java) {
            NotebookPageOrdering.proposeOrder(
                pages = listOf(page("page-a", 0)),
                preprocessingResults = listOf(preprocessing(pageId = "page-b")),
            )
        }
    }

    @Test
    fun duplicatePreprocessingResultsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            NotebookPageOrdering.proposeOrder(
                pages = listOf(page("page-a", 0)),
                preprocessingResults = listOf(
                    preprocessing(pageId = "page-a", detectedPageNumber = 1),
                    preprocessing(pageId = "page-a", detectedPageNumber = 2),
                ),
            )
        }
    }

    @Test
    fun mixedDetectedPageNumberAndDateIsMarkedAsMixedSignals() {
        val candidates = NotebookPageOrdering.proposeOrder(
            pages = listOf(page("page-a", 0)),
            preprocessingResults = listOf(
                preprocessing(
                    pageId = "page-a",
                    detectedPageNumber = 1,
                    detectedDateEpochMillis = 1_000L,
                )
            ),
        )

        assertEquals(NotebookPageOrderingReason.MIXED_SIGNALS, candidates.single().reason)
    }

    private fun page(id: String, position: Int): NotebookPageDraft = NotebookPageDraft(
        id = id,
        position = position,
        sourceFingerprintSha256 = "fingerprint-$id",
    )

    private fun preprocessing(
        pageId: String,
        widthPx: Int? = null,
        heightPx: Int? = null,
        rotationDegreesClockwise: Int = 0,
        qualityIssues: Set<NotebookPageQualityIssue> = emptySet(),
        detectedPageNumber: Int? = null,
        detectedDateEpochMillis: Long? = null,
        confidence: RecognitionConfidence = RecognitionConfidence(1.0),
    ): NotebookPagePreprocessingResult = NotebookPagePreprocessingResult(
        pageId = pageId,
        widthPx = widthPx,
        heightPx = heightPx,
        rotationDegreesClockwise = rotationDegreesClockwise,
        qualityIssues = qualityIssues,
        detectedPageNumber = detectedPageNumber,
        detectedDateEpochMillis = detectedDateEpochMillis,
        confidence = confidence,
    )
}
