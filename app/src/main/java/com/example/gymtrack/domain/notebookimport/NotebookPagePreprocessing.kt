package com.example.gymtrack.domain.notebookimport

/**
 * Pure page preprocessing and ordering model.
 *
 * This layer records deterministic metadata and review hints only. It does not decode images,
 * mutate bitmap bytes, run OCR, or persist import state.
 */
enum class NotebookPageOrientation {
    UNKNOWN,
    PORTRAIT,
    LANDSCAPE,
}

enum class NotebookPageQualityIssue {
    BLURRY,
    TOO_DARK,
    TOO_BRIGHT,
    LOW_CONTRAST,
    CROPPED_CONTENT,
    SKEWED,
    GLARE,
    MULTIPLE_PAGES_VISIBLE,
}

data class NotebookPagePreprocessingResult(
    val pageId: String,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val rotationDegreesClockwise: Int = 0,
    val qualityIssues: Set<NotebookPageQualityIssue> = emptySet(),
    val detectedPageNumber: Int? = null,
    val detectedDateEpochMillis: Long? = null,
    val confidence: RecognitionConfidence = RecognitionConfidence(1.0),
) {
    init {
        require(pageId.isNotBlank()) { "Preprocessing page id must not be blank" }
        require(widthPx == null || widthPx > 0) { "Page width must be positive" }
        require(heightPx == null || heightPx > 0) { "Page height must be positive" }
        require(rotationDegreesClockwise in setOf(0, 90, 180, 270)) {
            "Page rotation must be 0, 90, 180, or 270 degrees"
        }
        require(detectedPageNumber == null || detectedPageNumber > 0) {
            "Detected page number must be one-based"
        }
        require(detectedDateEpochMillis == null || detectedDateEpochMillis >= 0) {
            "Detected page date must not be negative"
        }
    }

    val orientation: NotebookPageOrientation
        get() = when {
            widthPx == null || heightPx == null -> NotebookPageOrientation.UNKNOWN
            heightPx >= widthPx -> NotebookPageOrientation.PORTRAIT
            else -> NotebookPageOrientation.LANDSCAPE
        }

    val needsManualReview: Boolean
        get() = qualityIssues.isNotEmpty() || confidence.isLowConfidence
}

data class NotebookPageOrderingCandidate(
    val pageId: String,
    val originalPosition: Int,
    val proposedPosition: Int,
    val reason: NotebookPageOrderingReason,
    val confidence: RecognitionConfidence,
) {
    init {
        require(pageId.isNotBlank()) { "Ordering candidate page id must not be blank" }
        require(originalPosition >= 0) { "Original page position must not be negative" }
        require(proposedPosition >= 0) { "Proposed page position must not be negative" }
    }

    val requiresReview: Boolean
        get() = originalPosition != proposedPosition || confidence.isLowConfidence
}

enum class NotebookPageOrderingReason {
    ORIGINAL_UPLOAD_ORDER,
    DETECTED_PAGE_NUMBER,
    DETECTED_DATE,
    MIXED_SIGNALS,
}

object NotebookPageOrdering {

    fun proposeOrder(
        pages: List<NotebookPageDraft>,
        preprocessingResults: List<NotebookPagePreprocessingResult>,
    ): List<NotebookPageOrderingCandidate> {
        require(pages.isNotEmpty()) { "Page ordering requires at least one page" }
        require(pages.map { it.id }.distinct().size == pages.size) {
            "Page ids must be unique before ordering"
        }
        require(preprocessingResults.map { it.pageId }.distinct().size == preprocessingResults.size) {
            "Preprocessing results must be unique per page"
        }

        val resultsByPageId = preprocessingResults.associateBy { it.pageId }
        require(resultsByPageId.keys.all { pageId -> pages.any { it.id == pageId } }) {
            "Preprocessing results can only reference known pages"
        }

        val rankedPages = pages.sortedWith(
            compareBy<NotebookPageDraft> { page -> resultsByPageId[page.id]?.detectedPageNumber ?: Int.MAX_VALUE }
                .thenBy { page -> resultsByPageId[page.id]?.detectedDateEpochMillis ?: Long.MAX_VALUE }
                .thenBy { it.position }
        )

        return rankedPages.mapIndexed { proposedPosition, page ->
            val result = resultsByPageId[page.id]
            NotebookPageOrderingCandidate(
                pageId = page.id,
                originalPosition = page.position,
                proposedPosition = proposedPosition,
                reason = orderingReason(result),
                confidence = orderingConfidence(result, page.position, proposedPosition),
            )
        }.sortedBy { it.originalPosition }
    }

    private fun orderingReason(result: NotebookPagePreprocessingResult?): NotebookPageOrderingReason = when {
        result?.detectedPageNumber != null && result.detectedDateEpochMillis != null -> {
            NotebookPageOrderingReason.MIXED_SIGNALS
        }
        result?.detectedPageNumber != null -> NotebookPageOrderingReason.DETECTED_PAGE_NUMBER
        result?.detectedDateEpochMillis != null -> NotebookPageOrderingReason.DETECTED_DATE
        else -> NotebookPageOrderingReason.ORIGINAL_UPLOAD_ORDER
    }

    private fun orderingConfidence(
        result: NotebookPagePreprocessingResult?,
        originalPosition: Int,
        proposedPosition: Int,
    ): RecognitionConfidence {
        val base = result?.confidence?.value ?: 1.0
        val adjusted = if (originalPosition == proposedPosition) base else minOf(base, 0.84)
        return RecognitionConfidence(adjusted)
    }
}
