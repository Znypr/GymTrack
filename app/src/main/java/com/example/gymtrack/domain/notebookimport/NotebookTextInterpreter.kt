package com.example.gymtrack.domain.notebookimport

import com.example.gymtrack.domain.model.ExerciseMode
import com.example.gymtrack.domain.model.WeightUnit
import java.util.Calendar
import java.util.TimeZone

/**
 * Deterministic first-pass interpreter from recognized notebook lines to reviewable draft rows.
 *
 * This is intentionally conservative. It recognizes only simple fixture-friendly date/title/set
 * patterns and leaves everything else as warnings or unresolved fields. It does not match canonical
 * exercises, confirm values, or write canonical workout history.
 */
data class NotebookTextInterpretationResult(
    val batch: NotebookImportBatchDraft,
    val warnings: List<String> = emptyList(),
) {
    init {
        require(warnings.none { it.isBlank() }) { "Interpretation warnings must not be blank" }
    }

    val requiresReview: Boolean
        get() = warnings.isNotEmpty() || batch.hasUnresolvedFields
}

object NotebookTextInterpreter {
    private val isoDateLine = Regex("""^(\d{4})-(\d{2})-(\d{2})(?:\s+(.+))?$""")
    private val titledLine = Regex("""^(?:workout|title)\s*:\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val setLine = Regex(
        """^(.+?)\s+([0-9]+(?:[.,][0-9]+)?|\?)\s*(kg|kgs|kilogram|kilograms|lb|lbs|pound|pounds)?\s*[x×]\s*([0-9]+|\?)$""",
        RegexOption.IGNORE_CASE,
    )

    fun interpret(
        request: NotebookRecognitionRequest,
        output: NotebookRecognitionOutput,
    ): NotebookTextInterpretationResult {
        require(output.recognizedPages.map { it.pageId }.toSet().all { pageId ->
            request.batch.pages.any { it.id == pageId }
        }) { "Recognition output can only reference pages from the interpretation request" }

        val warnings = mutableListOf<String>()
        val workouts = output.recognizedPages.mapNotNull { page ->
            interpretPage(page, warnings)
        }

        return NotebookTextInterpretationResult(
            batch = request.batch.copy(workouts = workouts),
            warnings = warnings,
        )
    }

    private fun interpretPage(
        page: RecognizedNotebookPage,
        warnings: MutableList<String>,
    ): NotebookWorkoutDraft? {
        var startedAt: RecognizedField<Long>? = null
        var title: RecognizedField<String>? = null
        val exercises = mutableListOf<ExerciseBuilder>()

        page.lines.forEach { line ->
            val text = line.text.trim()
            when {
                isoDateLine.matches(text) -> {
                    val match = isoDateLine.matchEntire(text) ?: error("Date match disappeared")
                    startedAt = parseDateField(match, line)
                    match.groupValues.getOrNull(4)
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { parsedTitle -> title = field(parsedTitle, line) }
                }
                titledLine.matches(text) -> {
                    val parsedTitle = titledLine.matchEntire(text)?.groupValues?.get(1)?.trim()
                    if (parsedTitle.isNullOrBlank()) {
                        warnings += warning(line, "Title line was empty")
                    } else {
                        title = field(parsedTitle, line)
                    }
                }
                setLine.matches(text) -> {
                    val match = setLine.matchEntire(text) ?: error("Set match disappeared")
                    val exerciseName = match.groupValues[1].trim()
                    if (exerciseName.isBlank()) {
                        warnings += warning(line, "Set line did not contain an exercise name")
                    } else {
                        val builder = exercises.lastOrNull()?.takeIf { it.name == exerciseName }
                            ?: ExerciseBuilder(
                                id = "exercise-${page.pageId}-${exercises.size + 1}",
                                position = exercises.size,
                                name = exerciseName,
                                provenance = line.provenance,
                                confidence = line.confidence,
                            ).also { exercises += it }
                        builder.sets += setDraft(
                            id = "set-${page.pageId}-${builder.sets.size + 1}",
                            position = builder.sets.size,
                            match = match,
                            line = line,
                        )
                    }
                }
                else -> warnings += warning(line, "Line was not interpreted into a workout draft row")
            }
        }

        if (exercises.isEmpty()) {
            warnings += "No importable exercise rows found on page ${page.pageId}"
            return null
        }

        return NotebookWorkoutDraft(
            id = "workout-${page.pageId}",
            sourcePageIds = setOf(page.pageId),
            startedAtEpochMillis = startedAt ?: unresolvedField(NotebookLineProvenance(pageId = page.pageId)),
            title = title,
            exercises = exercises.map { it.toDraft() },
            reviewState = ReviewState.NEEDS_REVIEW,
        )
    }

    private fun parseDateField(
        match: MatchResult,
        line: RecognizedNotebookLine,
    ): RecognizedField<Long> {
        val year = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        val day = match.groupValues[3].toInt()
        val millis = runCatching { utcStartOfDayMillis(year, month, day) }.getOrNull()
        return RecognizedField(
            value = millis,
            confidence = if (millis == null) RecognitionConfidence(0.0) else line.confidence,
            reviewState = ReviewState.NEEDS_REVIEW,
            provenance = line.provenance,
        )
    }

    private fun utcStartOfDayMillis(year: Int, monthOneBased: Int, day: Int): Long {
        require(monthOneBased in 1..12) { "Month out of range" }
        require(day in 1..31) { "Day out of range" }
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.clear()
        calendar.setLenient(false)
        calendar.set(year, monthOneBased - 1, day, 0, 0, 0)
        return calendar.timeInMillis
    }

    private fun setDraft(
        id: String,
        position: Int,
        match: MatchResult,
        line: RecognizedNotebookLine,
    ): NotebookSetDraft {
        val rawWeight = match.groupValues[2]
        val rawUnit = match.groupValues[3]
        val rawReps = match.groupValues[4]
        val weight = rawWeight.toDoubleOrNullLenient()
        val reps = rawReps.toIntOrNull()
        val unit = when (rawUnit.lowercase()) {
            "kg", "kgs", "kilogram", "kilograms" -> WeightUnit.KILOGRAM
            "lb", "lbs", "pound", "pounds" -> WeightUnit.POUND
            "" -> if (weight == null) null else WeightUnit.UNKNOWN
            else -> WeightUnit.UNKNOWN
        }

        return NotebookSetDraft(
            id = id,
            position = position,
            repetitions = RecognizedField(
                value = reps,
                confidence = if (reps == null) RecognitionConfidence(0.0) else line.confidence,
                reviewState = ReviewState.NEEDS_REVIEW,
                provenance = line.provenance,
            ),
            weight = RecognizedField(
                value = weight,
                confidence = if (weight == null) RecognitionConfidence(0.0) else line.confidence,
                reviewState = ReviewState.NEEDS_REVIEW,
                provenance = line.provenance,
            ),
            weightUnit = unit?.let {
                RecognizedField(
                    value = it,
                    confidence = if (it == WeightUnit.UNKNOWN) RecognitionConfidence(0.0) else line.confidence,
                    reviewState = ReviewState.NEEDS_REVIEW,
                    provenance = line.provenance,
                )
            },
            reviewState = ReviewState.NEEDS_REVIEW,
        )
    }

    private fun <T> field(value: T, line: RecognizedNotebookLine): RecognizedField<T> = RecognizedField(
        value = value,
        confidence = line.confidence,
        reviewState = ReviewState.NEEDS_REVIEW,
        provenance = line.provenance,
    )

    private fun unresolvedField(provenance: NotebookLineProvenance): RecognizedField<Long> = RecognizedField(
        value = null,
        confidence = RecognitionConfidence(0.0),
        reviewState = ReviewState.NEEDS_REVIEW,
        provenance = provenance,
    )

    private fun warning(line: RecognizedNotebookLine, message: String): String =
        "$message at ${line.pageId}:${line.lineNumber}"

    private fun String.toDoubleOrNullLenient(): Double? =
        takeUnless { it == "?" }?.replace(',', '.')?.toDoubleOrNull()

    private data class ExerciseBuilder(
        val id: String,
        val position: Int,
        val name: String,
        val provenance: NotebookLineProvenance,
        val confidence: RecognitionConfidence,
        val sets: MutableList<NotebookSetDraft> = mutableListOf(),
    ) {
        fun toDraft(): NotebookExerciseDraft = NotebookExerciseDraft(
            id = id,
            position = position,
            recognizedName = RecognizedField(
                value = name,
                confidence = confidence,
                reviewState = ReviewState.NEEDS_REVIEW,
                provenance = provenance,
            ),
            recognizedMode = RecognizedField(
                value = null,
                confidence = RecognitionConfidence(0.0),
                reviewState = ReviewState.NEEDS_REVIEW,
                provenance = provenance,
            ),
            exerciseResolution = ExerciseResolution(),
            sets = sets,
            reviewState = ReviewState.NEEDS_REVIEW,
        )
    }
}
