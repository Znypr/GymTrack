package com.example.gymtrack.domain.notebookimport

import com.example.gymtrack.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookTextInterpreterTest {

    @Test
    fun interpretsSimpleRecognizedLinesIntoReviewableWorkoutDraft() {
        val output = output(
            lines = listOf(
                "2026-07-09 Push",
                "Bench Press 80 kg x 8",
                "Bench Press 82.5 kg x 6",
                "Curl 12 x 10",
            )
        )

        val result = NotebookTextInterpreter.interpret(request(), output)

        assertEquals(
            NotebookFormatProfile.HANDWRITTEN_COMPACT_ROWS,
            result.formatProfilesByPageId.getValue("page-1").profile,
        )
        val workout = result.batch.workouts.single()
        assertEquals("Push", workout.title?.value)
        assertTrue(workout.startedAtEpochMillis.value != null)
        assertEquals(ReviewState.NEEDS_REVIEW, workout.reviewState)
        assertEquals(2, workout.exercises.size)
        assertEquals("Bench Press", workout.exercises[0].recognizedName.value)
        assertNull(workout.exercises[0].recognizedMode.value)
        assertEquals(2, workout.exercises[0].sets.size)
        assertEquals(80.0, workout.exercises[0].sets[0].weight?.value ?: -1.0, 0.001)
        assertEquals(8, workout.exercises[0].sets[0].repetitions?.value)
        assertEquals(WeightUnit.KILOGRAM, workout.exercises[0].sets[0].weightUnit?.value)
        assertEquals(WeightUnit.UNKNOWN, workout.exercises[1].sets.single().weightUnit?.value)
        assertFalse(result.batch.canWriteCanonicalHistory)
        assertTrue(result.requiresReview)
    }

    @Test
    fun interpretsNotebookTableExerciseRepsAndWeightRows() {
        val result = NotebookTextInterpreter.interpret(
            request(),
            output(
                lines = listOf(
                    "Push",
                    "23.06.",
                    "Schulter",
                    "14 12 10",
                    "11 11 15",
                    "Trizeps",
                    "8 9 10",
                    "26,25 22,5 20",
                )
            ),
        )

        assertEquals(
            NotebookFormatProfile.PREDEFINED_TABLE,
            result.formatProfilesByPageId.getValue("page-1").profile,
        )
        val workout = result.batch.workouts.single()
        assertEquals("Push", workout.title?.value)
        assertNull(workout.startedAtEpochMillis.value)
        assertTrue(workout.startedAtEpochMillis.confidence.isLowConfidence)
        assertEquals(2, workout.exercises.size)

        val shoulder = workout.exercises[0]
        assertEquals("Schulter", shoulder.recognizedName.value)
        assertEquals(3, shoulder.sets.size)
        assertEquals(14, shoulder.sets[0].repetitions?.value)
        assertEquals(11.0, shoulder.sets[0].weight?.value ?: -1.0, 0.001)
        assertEquals(WeightUnit.UNKNOWN, shoulder.sets[0].weightUnit?.value)

        val triceps = workout.exercises[1]
        assertEquals("Trizeps", triceps.recognizedName.value)
        assertEquals(3, triceps.sets.size)
        assertEquals(8, triceps.sets[0].repetitions?.value)
        assertEquals(26.25, triceps.sets[0].weight?.value ?: -1.0, 0.001)
        assertTrue(triceps.sets[0].weightUnit?.confidence?.isLowConfidence == true)
    }

    @Test
    fun interpretsNotebookTableMixedExerciseAndRepRow() {
        val result = NotebookTextInterpreter.interpret(
            request(),
            output(
                lines = listOf(
                    "Pull 18.06.",
                    "T-Bar 13 11 12",
                    "52.5 52.5 50",
                    "Rudern eng 12 12 9",
                    "70 60 60",
                )
            ),
        )

        assertEquals(
            NotebookFormatProfile.PREDEFINED_TABLE,
            result.formatProfilesByPageId.getValue("page-1").profile,
        )
        val workout = result.batch.workouts.single()
        assertEquals("Pull", workout.title?.value)
        assertNull(workout.startedAtEpochMillis.value)
        assertEquals(2, workout.exercises.size)
        assertEquals("T-Bar", workout.exercises[0].recognizedName.value)
        assertEquals(13, workout.exercises[0].sets[0].repetitions?.value)
        assertEquals(52.5, workout.exercises[0].sets[0].weight?.value ?: -1.0, 0.001)
        assertEquals("Rudern eng", workout.exercises[1].recognizedName.value)
        assertEquals(60.0, workout.exercises[1].sets[1].weight?.value ?: -1.0, 0.001)
    }

    @Test
    fun interpretsCombinedRepAndWeightValuesOnOneRecognizedLine() {
        val result = NotebookTextInterpreter.interpret(
            request(),
            output(lines = listOf("Dips 15 13 10 12.5 12.5 12.5")),
        )

        val exercise = result.batch.workouts.single().exercises.single()
        assertEquals("Dips", exercise.recognizedName.value)
        assertEquals(3, exercise.sets.size)
        assertEquals(15, exercise.sets[0].repetitions?.value)
        assertEquals(12.5, exercise.sets[0].weight?.value ?: -1.0, 0.001)
        assertEquals(10, exercise.sets[2].repetitions?.value)
        assertEquals(12.5, exercise.sets[2].weight?.value ?: -1.0, 0.001)
    }

    @Test
    fun missingDateStaysUnresolvedInsteadOfGuessing() {
        val result = NotebookTextInterpreter.interpret(
            request(),
            output(lines = listOf("Bench Press 80 kg x 8")),
        )

        val workout = result.batch.workouts.single()
        assertNull(workout.startedAtEpochMillis.value)
        assertEquals(ReviewState.NEEDS_REVIEW, workout.startedAtEpochMillis.reviewState)
        assertTrue(workout.startedAtEpochMillis.confidence.isLowConfidence)
    }

    @Test
    fun ambiguousSetValuesStayUnresolved() {
        val result = NotebookTextInterpreter.interpret(
            request(),
            output(
                lines = listOf(
                    "2026-07-09 Push",
                    "Bench Press ? x 8",
                    "Squat 100 kg x ?",
                )
            ),
        )

        val bench = result.batch.workouts.single().exercises[0].sets.single()
        val squat = result.batch.workouts.single().exercises[1].sets.single()

        assertNull(bench.weight?.value)
        assertEquals(8, bench.repetitions?.value)
        assertTrue(bench.weight?.confidence?.isLowConfidence == true)
        assertEquals(100.0, squat.weight?.value ?: -1.0, 0.001)
        assertNull(squat.repetitions?.value)
        assertTrue(squat.repetitions?.confidence?.isLowConfidence == true)
    }

    @Test
    fun uninterpretedLinesCreateWarningsWithoutRawNotebookText() {
        val result = NotebookTextInterpreter.interpret(
            request(),
            output(
                lines = listOf(
                    "2026-07-09 Push",
                    "felt strong today",
                    "Bench Press 80 kg x 8",
                )
            ),
        )

        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings.single().contains("page-1:2"))
        assertFalse(result.warnings.single().contains("felt strong today"))
    }

    @Test
    fun noExerciseRowsReturnNoWorkoutAndWarning() {
        val result = NotebookTextInterpreter.interpret(
            request(),
            output(lines = listOf("2026-07-09 Push", "Title: Upper")),
        )

        assertTrue(result.batch.workouts.isEmpty())
        assertTrue(result.warnings.any { it.contains("No importable exercise rows") })
    }

    @Test
    fun recognitionOutputCannotReferencePagesOutsideRequestBatch() {
        assertThrows(IllegalArgumentException::class.java) {
            NotebookTextInterpreter.interpret(
                request(),
                output(pageId = "page-2", lines = listOf("Bench Press 80 kg x 8")),
            )
        }
    }

    @Test
    fun invalidDateIsRetainedAsUnresolvedDateField() {
        val result = NotebookTextInterpreter.interpret(
            request(),
            output(
                lines = listOf(
                    "2026-99-99 Push",
                    "Bench Press 80 kg x 8",
                )
            ),
        )

        val workout = result.batch.workouts.single()
        assertNull(workout.startedAtEpochMillis.value)
        assertEquals("Push", workout.title?.value)
        assertTrue(workout.startedAtEpochMillis.confidence.isLowConfidence)
    }

    private fun request(): NotebookRecognitionRequest = NotebookRecognitionRequest(
        batch = NotebookImportBatchDraft(
            id = "batch-1",
            pages = listOf(
                NotebookPageDraft(
                    id = "page-1",
                    position = 0,
                    sourceFingerprintSha256 = "fingerprint-page-1",
                )
            ),
        )
    )

    private fun output(
        pageId: String = "page-1",
        lines: List<String>,
    ): NotebookRecognitionOutput = NotebookRecognitionOutput(
        provider = NotebookRecognitionProviderDescriptor(
            id = "fixture-lines",
            displayName = "Fixture lines",
            processingLocation = ProcessingLocation.ON_DEVICE,
        ),
        recognizedPages = listOf(
            RecognizedNotebookPage(
                pageId = pageId,
                lines = lines.mapIndexed { index, text ->
                    RecognizedNotebookLine(
                        id = "$pageId-line-${index + 1}",
                        pageId = pageId,
                        lineNumber = index + 1,
                        text = text,
                        confidence = RecognitionConfidence(0.95),
                    )
                },
                confidence = RecognitionConfidence(0.95),
            )
        ),
    )
}
