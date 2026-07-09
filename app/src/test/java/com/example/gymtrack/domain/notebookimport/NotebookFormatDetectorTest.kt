package com.example.gymtrack.domain.notebookimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookFormatDetectorTest {

    @Test
    fun detectsPredefinedTableNotebookWithHeaders() {
        val result = NotebookFormatDetector.detect(
            lines(
                "EXERCISE",
                "SETS 1 2 3 4 5",
                "Schulter",
                "14 12 10",
                "11 11 15",
            )
        )

        assertEquals(NotebookFormatProfile.PREDEFINED_TABLE, result.profile)
        assertTrue(result.confidence.value >= 0.8)
    }

    @Test
    fun detectsPredefinedTableNotebookWithoutHeadersFromExerciseAndNumericRows() {
        val result = NotebookFormatDetector.detect(
            lines(
                "T-Bar",
                "13 11 12",
                "52.5 52.5 50",
                "Rudern eng",
                "12 12 9",
                "70 60 60",
            )
        )

        assertEquals(NotebookFormatProfile.PREDEFINED_TABLE, result.profile)
        assertTrue(result.confidence.value >= 0.7)
    }

    @Test
    fun detectsCompactHandwrittenRows() {
        val result = NotebookFormatDetector.detect(
            lines(
                "Bench Press 80 kg x 8",
                "Bench Press 82.5 kg x 6",
            )
        )

        assertEquals(NotebookFormatProfile.HANDWRITTEN_COMPACT_ROWS, result.profile)
    }

    @Test
    fun detectsFreeformHandwrittenLog() {
        val result = NotebookFormatDetector.detect(
            lines(
                "Today felt heavy and shoulder was a bit sore",
                "Did some easy machines after bench",
            )
        )

        assertEquals(NotebookFormatProfile.HANDWRITTEN_FREEFORM_LOG, result.profile)
    }

    @Test
    fun unknownEmptyOcrStaysUnknown() {
        val result = NotebookFormatDetector.detect(emptyList())

        assertEquals(NotebookFormatProfile.MIXED_OR_UNKNOWN, result.profile)
        assertTrue(result.confidence.isLowConfidence)
    }

    private fun lines(vararg values: String): List<RecognizedNotebookLine> = values.mapIndexed { index, text ->
        RecognizedNotebookLine(
            id = "line-${index + 1}",
            pageId = "page-1",
            lineNumber = index + 1,
            text = text,
            confidence = RecognitionConfidence(0.90),
        )
    }
}
