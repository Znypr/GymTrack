package com.example.gymtrack.domain.notebookimport

/**
 * Provider boundary for handwritten notebook recognition.
 *
 * Implementations may later wrap on-device OCR or explicit opt-in external services. The provider
 * boundary returns reviewable recognition output only. It never writes canonical workout history.
 */
data class NotebookRecognitionProviderDescriptor(
    val id: String,
    val displayName: String,
    val processingLocation: ProcessingLocation,
) {
    init {
        require(id.isNotBlank()) { "Recognition provider id must not be blank" }
        require(displayName.isNotBlank()) { "Recognition provider display name must not be blank" }
    }

    val requiresExternalConsent: Boolean
        get() = processingLocation != ProcessingLocation.ON_DEVICE
}

data class NotebookRecognitionRequest(
    val batch: NotebookImportBatchDraft,
    val pagePreprocessingResults: List<NotebookPagePreprocessingResult> = emptyList(),
) {
    init {
        require(pagePreprocessingResults.map { it.pageId }.distinct().size == pagePreprocessingResults.size) {
            "Recognition preprocessing inputs must be unique per page"
        }
        val knownPageIds = batch.pages.map { it.id }.toSet()
        require(pagePreprocessingResults.all { it.pageId in knownPageIds }) {
            "Recognition preprocessing inputs can only reference pages from the batch"
        }
    }
}

data class RecognizedNotebookLine(
    val id: String,
    val pageId: String,
    val lineNumber: Int,
    val text: String,
    val confidence: RecognitionConfidence,
) {
    init {
        require(id.isNotBlank()) { "Recognized line id must not be blank" }
        require(pageId.isNotBlank()) { "Recognized line page id must not be blank" }
        require(lineNumber > 0) { "Recognized line number must be one-based" }
        require(text.isNotBlank()) { "Recognized line text must not be blank" }
    }

    val provenance: NotebookLineProvenance
        get() = NotebookLineProvenance(
            pageId = pageId,
            lineNumber = lineNumber,
            sourceText = text,
        )
}

data class RecognizedNotebookPage(
    val pageId: String,
    val lines: List<RecognizedNotebookLine>,
    val confidence: RecognitionConfidence,
) {
    init {
        require(pageId.isNotBlank()) { "Recognized page id must not be blank" }
        require(lines.all { it.pageId == pageId }) {
            "Recognized lines must belong to their recognized page"
        }
        require(lines.map { it.id }.distinct().size == lines.size) {
            "Recognized line ids must be unique within a page"
        }
        require(lines.map { it.lineNumber }.distinct().size == lines.size) {
            "Recognized line numbers must be unique within a page"
        }
    }

    val requiresReview: Boolean
        get() = confidence.isLowConfidence || lines.any { it.confidence.isLowConfidence }
}

data class NotebookRecognitionOutput(
    val provider: NotebookRecognitionProviderDescriptor,
    val recognizedPages: List<RecognizedNotebookPage>,
    val warnings: List<String> = emptyList(),
) {
    init {
        require(recognizedPages.map { it.pageId }.distinct().size == recognizedPages.size) {
            "Recognition output must contain at most one result per page"
        }
        require(warnings.none { it.isBlank() }) { "Recognition warnings must not be blank" }
    }

    val requiresReview: Boolean
        get() = warnings.isNotEmpty() || recognizedPages.any { it.requiresReview }
}

interface NotebookRecognitionProvider {
    val descriptor: NotebookRecognitionProviderDescriptor

    fun recognize(request: NotebookRecognitionRequest): NotebookRecognitionOutput
}

object NotebookRecognitionProviderPolicy {

    fun validateProviderAllowed(
        provider: NotebookRecognitionProviderDescriptor,
        consent: NotebookImportConsent,
    ) {
        require(!provider.requiresExternalConsent || consent.allowExternalProcessing) {
            "External notebook recognition requires explicit user consent"
        }
    }
}

/**
 * Deterministic fixture provider for tests and future sample-data evaluation.
 *
 * It is not OCR. It lets later parser work consume recognition-like lines without depending on
 * Android camera/gallery code or an ML dependency.
 */
class FixtureNotebookRecognitionProvider(
    private val linesByPageId: Map<String, List<String>>,
    override val descriptor: NotebookRecognitionProviderDescriptor = NotebookRecognitionProviderDescriptor(
        id = "fixture-lines",
        displayName = "Fixture lines",
        processingLocation = ProcessingLocation.ON_DEVICE,
    ),
) : NotebookRecognitionProvider {

    init {
        require(linesByPageId.keys.none { it.isBlank() }) { "Fixture page ids must not be blank" }
        require(linesByPageId.values.flatten().none { it.isBlank() }) {
            "Fixture recognition lines must not be blank"
        }
    }

    override fun recognize(request: NotebookRecognitionRequest): NotebookRecognitionOutput {
        NotebookRecognitionProviderPolicy.validateProviderAllowed(
            provider = descriptor,
            consent = request.batch.consent,
        )
        val knownPageIds = request.batch.pages.map { it.id }.toSet()
        require(linesByPageId.keys.all { it in knownPageIds }) {
            "Fixture lines can only reference pages from the recognition request"
        }

        val recognizedPages = request.batch.pages.mapNotNull { page ->
            val pageLines = linesByPageId[page.id] ?: return@mapNotNull null
            RecognizedNotebookPage(
                pageId = page.id,
                lines = pageLines.mapIndexed { index, text ->
                    RecognizedNotebookLine(
                        id = "${page.id}-line-${index + 1}",
                        pageId = page.id,
                        lineNumber = index + 1,
                        text = text,
                        confidence = RecognitionConfidence(1.0),
                    )
                },
                confidence = RecognitionConfidence(1.0),
            )
        }

        return NotebookRecognitionOutput(
            provider = descriptor,
            recognizedPages = recognizedPages,
        )
    }
}
