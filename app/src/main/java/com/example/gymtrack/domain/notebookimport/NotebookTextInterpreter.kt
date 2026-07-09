package com.example.gymtrack.domain.notebookimport

import com.example.gymtrack.domain.model.ExerciseMode
import com.example.gymtrack.domain.model.WeightUnit
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Deterministic first-pass interpreter from recognized notebook lines to reviewable draft rows.
 *
 * This is intentionally conservative. It recognizes simple fixture-friendly rows and the printed
 * GymTrack notebook table style where exercise names and set values can be recognized as separate
 * OCR lines. It still leaves every value unconfirmed and never writes canonical workout history.
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
    private val europeanDateLine = Regex("""^(\d{1,2})\.(\d{1,2})\.?(?:\s*(\d{2,4}))?(?:\s+(.+))?$""")
    private val titleDateLine = Regex(
        """^(push|pull|beine|legs|upper|lower|full\s*body)\s+(\d{1,2}\.\d{1,2}\.?(?:\s*\d{2,4})?)$""",
        RegexOption.IGNORE_CASE,
    )
    private val standaloneTitleLine = Regex(
        """^(push|pull|beine|legs|upper|lower|full\s*body)$""",
        RegexOption.IGNORE_CASE,
    )
    private val titledLine = Regex("""^(?:workout|title)\s*:\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val setLine = Regex(
        """^(.+?)\s+([0-9]+(?:[.,][0-9]+)?|\?)\s*(kg|kgs|kilogram|kilograms|lb|lbs|pound|pounds)?\s*[x×]\s*([0-9]+|\?)$""",
        RegexOption.IGNORE_CASE,
    )
    private val numberToken = Regex("""\?|\d+(?:[.,]\d+)?""")
    private val letterToken = Regex("""[A-Za-zÄÖÜäöüß]""")
    private val boilerplate = setOf(
        "exercise",
        "sets",
        "notes",
        "notebook",
        "gymbook",
        "calories",
        "dauer",
        "duration",
        "1",
        "2",
        "3",
        "4",
        "5",
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
        val tableCandidates = mutableListOf<RecognizedNotebookLine>()
        val consumedLineIds = mutableSetOf<String>()

        page.lines.forEach { line ->
            val text = line.text.trim()
            when {
                parseTitleDateLine(text, line)?.also { parsed ->
                    title = field(parsed.title, line)
                    startedAt = parsed.date
                    consumedLineIds += line.id
                } != null -> Unit
                isoDateLine.matches(text) -> {
                    val match = isoDateLine.matchEntire(text) ?: error("Date match disappeared")
                    startedAt = parseIsoDateField(match, line)
                    match.groupValues.getOrNull(4)
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { parsedTitle -> title = field(parsedTitle, line) }
                    consumedLineIds += line.id
                }
                europeanDateLine.matches(text) -> {
                    val match = europeanDateLine.matchEntire(text) ?: error("Date match disappeared")
                    startedAt = parseEuropeanDateField(match, line)
                    match.groupValues.getOrNull(4)
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { parsedTitle -> title = field(parsedTitle, line) }
                    consumedLineIds += line.id
                }
                standaloneTitleLine.matches(text) -> {
                    title = field(text.replaceFirstChar { it.uppercase() }, line)
                    consumedLineIds += line.id
                }
                titledLine.matches(text) -> {
                    val parsedTitle = titledLine.matchEntire(text)?.groupValues?.get(1)?.trim()
                    if (parsedTitle.isNullOrBlank()) {
                        warnings += warning(line, "Title line was empty")
                    } else {
                        title = field(parsedTitle, line)
                    }
                    consumedLineIds += line.id
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
                    consumedLineIds += line.id
                }
                isBoilerplate(text) -> consumedLineIds += line.id
                else -> tableCandidates += line
            }
        }

        val tableResult = interpretTableRows(page, tableCandidates, exercises.size)
        consumedLineIds += tableResult.consumedLineIds
        exercises += tableResult.exercises

        tableCandidates
            .filterNot { it.id in consumedLineIds }
            .filterNot { isBoilerplate(it.text.trim()) }
            .forEach { warnings += warning(it, "Line was not interpreted into a workout draft row") }

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

    private fun parseTitleDateLine(
        text: String,
        line: RecognizedNotebookLine,
    ): ParsedTitleDate? {
        val match = titleDateLine.matchEntire(text) ?: return null
        val title = match.groupValues[1].trim().replaceFirstChar { it.uppercase() }
        val dateText = match.groupValues[2].trim()
        val dateMatch = europeanDateLine.matchEntire(dateText) ?: return null
        return ParsedTitleDate(
            title = title,
            date = parseEuropeanDateField(dateMatch, line),
        )
    }

    private fun parseIsoDateField(
        match: MatchResult,
        line: RecognizedNotebookLine,
    ): RecognizedField<Long> {
        val year = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        val day = match.groupValues[3].toInt()
        return dateField(year, month, day, line, hasExplicitYear = true)
    }

    private fun parseEuropeanDateField(
        match: MatchResult,
        line: RecognizedNotebookLine,
    ): RecognizedField<Long> {
        val day = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        val rawYear = match.groupValues.getOrNull(3).orEmpty().trim()
        if (rawYear.isBlank()) {
            return RecognizedField(
                value = null,
                confidence = RecognitionConfidence(0.0),
                reviewState = ReviewState.NEEDS_REVIEW,
                provenance = line.provenance,
            )
        }
        val year = rawYear.toInt().let { if (it < 100) 2000 + it else it }
        return dateField(year, month, day, line, hasExplicitYear = true)
    }

    private fun dateField(
        year: Int,
        month: Int,
        day: Int,
        line: RecognizedNotebookLine,
        hasExplicitYear: Boolean,
    ): RecognizedField<Long> {
        val millis = if (hasExplicitYear) runCatching { utcStartOfDayMillis(year, month, day) }.getOrNull() else null
        return RecognizedField(
            value = millis,
            confidence = if (millis == null) RecognitionConfidence(0.0) else line.confidence,
            reviewState = ReviewState.NEEDS_REVIEW,
            provenance = line.provenance,
        )
    }

    private fun interpretTableRows(
        page: RecognizedNotebookPage,
        lines: List<RecognizedNotebookLine>,
        startPosition: Int,
    ): TableInterpretationResult {
        val exercises = mutableListOf<ExerciseBuilder>()
        val consumed = mutableSetOf<String>()
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            val text = line.text.trim()
            val mixed = parseMixedNameAndNumbers(text)
            val name = when {
                mixed?.name?.isNotBlank() == true -> mixed.name
                isExerciseNameCandidate(text) -> text.cleanedExerciseName()
                else -> null
            }

            if (name == null) {
                index += 1
                continue
            }

            val numericRows = mutableListOf<NumericRow>()
            mixed?.values?.takeIf { it.isNotEmpty() }?.let { values ->
                numericRows += NumericRow(values = values, provenanceLine = line)
            }
            consumed += line.id

            var lookahead = index + 1
            while (lookahead < lines.size) {
                val next = lines[lookahead]
                val nextText = next.text.trim()
                if (parseMixedNameAndNumbers(nextText)?.name?.isNotBlank() == true || isExerciseNameCandidate(nextText)) {
                    break
                }
                val values = parseNumberSequence(nextText)
                if (values.isNotEmpty()) {
                    numericRows += NumericRow(values = values, provenanceLine = next)
                    consumed += next.id
                    lookahead += 1
                } else if (isBoilerplate(nextText)) {
                    consumed += next.id
                    lookahead += 1
                } else {
                    break
                }
            }

            val builder = ExerciseBuilder(
                id = "exercise-${page.pageId}-${startPosition + exercises.size + 1}",
                position = startPosition + exercises.size,
                name = name,
                provenance = line.provenance,
                confidence = line.confidence,
            )
            builder.sets += tableSets(
                pageId = page.pageId,
                builder = builder,
                rows = numericRows,
            )
            if (builder.sets.isNotEmpty()) {
                exercises += builder
            }
            index = lookahead
        }

        return TableInterpretationResult(
            exercises = exercises,
            consumedLineIds = consumed,
        )
    }

    private fun tableSets(
        pageId: String,
        builder: ExerciseBuilder,
        rows: List<NumericRow>,
    ): List<NotebookSetDraft> {
        if (rows.isEmpty()) return emptyList()
        val first = rows.first()
        val second = rows.getOrNull(1)

        val pair = when {
            second != null -> RepWeightRows(reps = first, weights = second)
            looksLikeCombinedRepWeightRow(first.values) -> {
                val split = first.values.size / 2
                RepWeightRows(
                    reps = first.copy(values = first.values.take(split)),
                    weights = first.copy(values = first.values.drop(split)),
                )
            }
            first.values.all { it.isUnknown || (it.value != null && it.value <= 35.0) } -> {
                RepWeightRows(reps = first, weights = null)
            }
            else -> RepWeightRows(reps = null, weights = first)
        }

        val count = maxOf(pair.reps?.values?.size ?: 0, pair.weights?.values?.size ?: 0)
        return (0 until count).map { position ->
            val repValue = pair.reps?.values?.getOrNull(position)
            val weightValue = pair.weights?.values?.getOrNull(position)
            val provenanceLine = repValue?.line ?: weightValue?.line ?: builder.provenanceLine(pageId)
            NotebookSetDraft(
                id = "set-$pageId-${builder.position + 1}-${position + 1}",
                position = position,
                repetitions = RecognizedField(
                    value = repValue?.value?.toWholeIntOrNull(),
                    confidence = confidenceFor(repValue, provenanceLine.confidence),
                    reviewState = ReviewState.NEEDS_REVIEW,
                    provenance = provenanceLine.provenance,
                ),
                weight = weightValue?.let {
                    RecognizedField(
                        value = it.value,
                        confidence = confidenceFor(it, provenanceLine.confidence),
                        reviewState = ReviewState.NEEDS_REVIEW,
                        provenance = it.line.provenance,
                    )
                },
                weightUnit = weightValue?.let {
                    RecognizedField(
                        value = if (it.value == null) null else WeightUnit.UNKNOWN,
                        confidence = RecognitionConfidence(0.0),
                        reviewState = ReviewState.NEEDS_REVIEW,
                        provenance = it.line.provenance,
                    )
                },
                reviewState = ReviewState.NEEDS_REVIEW,
            )
        }
    }

    private fun confidenceFor(
        value: NumericValue?,
        fallback: RecognitionConfidence,
    ): RecognitionConfidence = when {
        value == null || value.isUnknown || value.value == null -> RecognitionConfidence(0.0)
        else -> fallback
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

    private fun parseNumberSequence(text: String): List<NumericValue> = numberToken.findAll(text)
        .map { match ->
            val raw = match.value
            NumericValue(
                raw = raw,
                value = raw.toDoubleOrNullLenient(),
                line = textLinePlaceholder,
            )
        }
        .toList()

    private fun parseMixedNameAndNumbers(text: String): MixedNameNumbers? {
        val firstNumber = numberToken.find(text) ?: return null
        val name = text.substring(0, firstNumber.range.first).cleanedExerciseName()
        if (name.isBlank()) return null
        val values = numberToken.findAll(text.substring(firstNumber.range.first))
            .map { match -> NumericValue(raw = match.value, value = match.value.toDoubleOrNullLenient(), line = textLinePlaceholder) }
            .toList()
        return MixedNameNumbers(name = name, values = values)
    }

    private fun parseNumberSequence(
        line: RecognizedNotebookLine,
    ): List<NumericValue> = numberToken.findAll(line.text)
        .map { match ->
            NumericValue(
                raw = match.value,
                value = match.value.toDoubleOrNullLenient(),
                line = line,
            )
        }
        .toList()

    private fun parseMixedNameAndNumbers(
        line: RecognizedNotebookLine,
    ): MixedNameNumbers? {
        val firstNumber = numberToken.find(line.text) ?: return null
        val name = line.text.substring(0, firstNumber.range.first).cleanedExerciseName()
        if (name.isBlank()) return null
        val values = numberToken.findAll(line.text.substring(firstNumber.range.first))
            .map { match -> NumericValue(raw = match.value, value = match.value.toDoubleOrNullLenient(), line = line) }
            .toList()
        return MixedNameNumbers(name = name, values = values)
    }

    private fun parseNumberSequence(text: String, line: RecognizedNotebookLine): List<NumericValue> = numberToken.findAll(text)
        .map { match -> NumericValue(raw = match.value, value = match.value.toDoubleOrNullLenient(), line = line) }
        .toList()

    private fun parseMixedNameAndNumbers(text: String, line: RecognizedNotebookLine): MixedNameNumbers? {
        val firstNumber = numberToken.find(text) ?: return null
        val name = text.substring(0, firstNumber.range.first).cleanedExerciseName()
        if (name.isBlank()) return null
        val values = parseNumberSequence(text.substring(firstNumber.range.first), line)
        return MixedNameNumbers(name = name, values = values)
    }

    private fun isExerciseNameCandidate(text: String): Boolean {
        val cleaned = text.cleanedExerciseName()
        if (cleaned.isBlank() || isBoilerplate(cleaned)) return false
        if (!letterToken.containsMatchIn(cleaned)) return false
        val withoutUnits = cleaned.replace(Regex("""\b(kg|kgs|lb|lbs)\b""", RegexOption.IGNORE_CASE), "")
        return numberToken.findAll(withoutUnits).count() <= 1
    }

    private fun isBoilerplate(text: String): Boolean {
        val normalized = text.lowercase()
            .replace(Regex("""[^a-zäöüß0-9]+"""), " ")
            .trim()
        if (normalized in boilerplate) return true
        if (normalized.isBlank()) return true
        return normalized.split(" ").all { it in boilerplate }
    }

    private fun looksLikeCombinedRepWeightRow(values: List<NumericValue>): Boolean {
        if (values.size < 4 || values.size % 2 != 0) return false
        val split = values.size / 2
        val reps = values.take(split).mapNotNull { it.value }
        val weights = values.drop(split).mapNotNull { it.value }
        if (reps.size != split || weights.size != split) return false
        return reps.all { it in 1.0..35.0 } && weights.any { it > 35.0 || it % 1.0 != 0.0 }
    }

    private fun utcStartOfDayMillis(year: Int, monthOneBased: Int, day: Int): Long {
        require(monthOneBased in 1..12) { "Month out of range" }
        require(day in 1..31) { "Day out of range" }
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.clear()
        calendar.isLenient = false
        calendar.set(year, monthOneBased - 1, day, 0, 0, 0)
        return calendar.timeInMillis
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

    private fun Double.toWholeIntOrNull(): Int? =
        takeIf { it >= 0.0 && it == roundToInt().toDouble() }?.roundToInt()

    private fun String.cleanedExerciseName(): String = trim()
        .replace(Regex("""^[\-–—:=•\s]+"""), "")
        .replace(Regex("""[\-–—:=•\s]+$"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()

    private val textLinePlaceholder = RecognizedNotebookLine(
        id = "placeholder",
        pageId = "placeholder",
        lineNumber = 1,
        text = "placeholder",
        confidence = RecognitionConfidence(0.0),
    )

    private data class ParsedTitleDate(
        val title: String,
        val date: RecognizedField<Long>,
    )

    private data class MixedNameNumbers(
        val name: String,
        val values: List<NumericValue>,
    )

    private data class NumericValue(
        val raw: String,
        val value: Double?,
        val line: RecognizedNotebookLine,
    ) {
        val isUnknown: Boolean
            get() = raw == "?"
    }

    private data class NumericRow(
        val values: List<NumericValue>,
        val provenanceLine: RecognizedNotebookLine,
    )

    private data class RepWeightRows(
        val reps: NumericRow?,
        val weights: NumericRow?,
    )

    private data class TableInterpretationResult(
        val exercises: List<ExerciseBuilder>,
        val consumedLineIds: Set<String>,
    )

    private data class ExerciseBuilder(
        val id: String,
        val position: Int,
        val name: String,
        val provenance: NotebookLineProvenance,
        val confidence: RecognitionConfidence,
        val sets: MutableList<NotebookSetDraft> = mutableListOf(),
    ) {
        fun provenanceLine(pageId: String): RecognizedNotebookLine = RecognizedNotebookLine(
            id = "$id-provenance",
            pageId = pageId,
            lineNumber = provenance.lineNumber ?: 1,
            text = provenance.sourceText ?: name,
            confidence = confidence,
        )

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
