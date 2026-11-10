package com.example.gymtrack.core.util

/**
 * Full result of [WorkoutParser.parse]: the typed sets plus everything the parser could not
 * take at face value. Nothing is dropped or renamed silently; each case appears in [diagnostics].
 */
data class WorkoutParseResult(
    val sets: List<ParsedSetDTO>,
    val diagnostics: List<ParseDiagnostic>,
) {
    /** Diagnostics for lines whose set was not saved. */
    val droppedLines: List<ParseDiagnostic> get() = diagnostics.filter { it.reason.dropsSet }
}

/**
 * One parser note about a line.
 *
 * @property lineIndex index of the row in the parsed note (equals the editor row index for notes
 *   stored with `rowMetadata`; legacy inline-metadata notes skip ghost flag lines first).
 * @property line the raw row text, unchanged.
 * @property original / [replacement] the token before and after an autocorrection
 *   ([ParseDiagnosticReason.NAME_AUTOCORRECTED] only). Raw text is never rewritten, so the
 *   correction can always be undone by changing the vocabulary or the typed name.
 */
data class ParseDiagnostic(
    val lineIndex: Int,
    val line: String,
    val reason: ParseDiagnosticReason,
    val original: String? = null,
    val replacement: String? = null,
)

enum class ParseDiagnosticReason(val dropsSet: Boolean, val message: String) {
    /** Reps were found but there is no weight on the line and none to carry from an earlier set. */
    MISSING_WEIGHT(true, "Set has reps but no weight, and no earlier weight in this exercise to reuse."),

    /** The line sits where a set belongs but no reps, duration or weight could be read from it. */
    UNRECOGNISED_SET(true, "Line looks like a set but its reps could not be read."),

    /** A lone number on a non-cardio exercise: it could be reps or weight. */
    AMBIGUOUS_NUMBER(true, "Lone number without x: unclear whether it is reps or weight."),

    /** A timed set of 0 seconds. */
    ZERO_DURATION(true, "Timed set has a duration of 0 seconds."),

    /** An exercise-name token was corrected to a vocabulary word (spelling fix only). */
    NAME_AUTOCORRECTED(false, "Exercise name word was spell-corrected."),
}
