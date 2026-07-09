package com.example.gymtrack.domain.notebookimport

/**
 * Broad notebook format buckets used before interpretation.
 *
 * A profile is a hint, not a source of truth. It helps route OCR text through conservative parsers
 * while still requiring user review before canonical writes.
 */
enum class NotebookFormatProfile {
    PREDEFINED_TABLE,
    HANDWRITTEN_COMPACT_ROWS,
    HANDWRITTEN_FREEFORM_LOG,
    MIXED_OR_UNKNOWN,
}

data class NotebookFormatDetectionResult(
    val profile: NotebookFormatProfile,
    val confidence: RecognitionConfidence,
    val reasons: List<String>,
) {
    init {
        require(reasons.isNotEmpty()) { "Notebook format detection needs at least one reason" }
        require(reasons.none { it.isBlank() }) { "Notebook format detection reasons must not be blank" }
    }
}

object NotebookFormatDetector {
    private val headerTerms = Regex(
        """\b(exercise|sets|notes|workout|notebook|gymbook|training)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val simpleSetRow = Regex(
        """.+\s+\d+(?:[.,]\d+)?\s*(?:kg|kgs|lb|lbs)?\s*[x×]\s*\d+""",
        RegexOption.IGNORE_CASE,
    )
    private val numberToken = Regex("""\?|\d+(?:[.,]\d+)?""")
    private val wordToken = Regex("""[A-Za-zÄÖÜäöüß]{2,}""")

    fun detect(lines: List<RecognizedNotebookLine>): NotebookFormatDetectionResult {
        val texts = lines.map { it.text.trim() }.filter { it.isNotBlank() }
        if (texts.isEmpty()) {
            return NotebookFormatDetectionResult(
                profile = NotebookFormatProfile.MIXED_OR_UNKNOWN,
                confidence = RecognitionConfidence(0.0),
                reasons = listOf("No OCR text lines were available"),
            )
        }

        val headerCount = texts.count { headerTerms.containsMatchIn(it) }
        val simpleSetCount = texts.count { simpleSetRow.matches(it) }
        val numericOnlyCount = texts.count { text ->
            val numbers = numberToken.findAll(text).count()
            numbers > 0 && !wordToken.containsMatchIn(text)
        }
        val exerciseLikeCount = texts.count { text ->
            wordToken.containsMatchIn(text) && numberToken.findAll(text).count() <= 1 && !headerTerms.containsMatchIn(text)
        }
        val longFreeformCount = texts.count { text ->
            wordToken.findAll(text).count() >= 4 && numberToken.findAll(text).count() <= 1
        }

        return when {
            headerCount > 0 && numericOnlyCount > 0 && exerciseLikeCount > 0 -> {
                NotebookFormatDetectionResult(
                    profile = NotebookFormatProfile.PREDEFINED_TABLE,
                    confidence = RecognitionConfidence(0.85),
                    reasons = listOf("Detected printed table headers, exercise labels, and numeric set rows"),
                )
            }
            numericOnlyCount >= 2 && exerciseLikeCount >= 1 -> {
                NotebookFormatDetectionResult(
                    profile = NotebookFormatProfile.PREDEFINED_TABLE,
                    confidence = RecognitionConfidence(0.72),
                    reasons = listOf("Detected exercise labels followed by numeric-only set rows"),
                )
            }
            simpleSetCount >= 1 -> {
                NotebookFormatDetectionResult(
                    profile = NotebookFormatProfile.HANDWRITTEN_COMPACT_ROWS,
                    confidence = RecognitionConfidence(0.82),
                    reasons = listOf("Detected compact exercise/weight/repetition rows"),
                )
            }
            longFreeformCount >= 1 -> {
                NotebookFormatDetectionResult(
                    profile = NotebookFormatProfile.HANDWRITTEN_FREEFORM_LOG,
                    confidence = RecognitionConfidence(0.55),
                    reasons = listOf("Detected longer handwritten text that does not fit table or compact-row patterns"),
                )
            }
            else -> {
                NotebookFormatDetectionResult(
                    profile = NotebookFormatProfile.MIXED_OR_UNKNOWN,
                    confidence = RecognitionConfidence(0.35),
                    reasons = listOf("No known notebook layout matched confidently"),
                )
            }
        }
    }
}
