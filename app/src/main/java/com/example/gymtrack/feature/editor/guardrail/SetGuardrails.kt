package com.example.gymtrack.feature.editor.guardrail

import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.util.ParseDiagnosticReason
import com.example.gymtrack.core.util.WorkoutParser
import com.example.gymtrack.core.util.buildNoteRowMetadata
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Input guardrails (#304), all values in one place (docs/EDITOR_GUARDRAILS.md). Tuned for a low
 * false-positive rate: a hint the user has to dismiss on a correct set costs a tap mid-workout.
 */
object GuardrailThresholds {
    /** History rules need at least this many earlier workouts with the exercise. */
    const val MIN_SESSIONS = 3

    /** "Recent" = the newest this many sessions of the exercise. */
    const val RECENT_SESSIONS = 5

    /** A session max above median × this is an old outlier and is not a reference. */
    const val OUTLIER_FACTOR = 2.5

    /** A weight above recent max × this is flagged. */
    const val OVER_MAX_FACTOR = 2.0

    /** Decimal slip: weight ÷ 10 lands within this ratio of the reference. */
    val DECIMAL_NEAR = 0.5..1.5

    /** Unit slip: as typed it is outside [UNIT_AS_TYPED_OK], read in the other unit it is within [UNIT_NEAR]. */
    val UNIT_AS_TYPED_OK = 0.5..1.9
    val UNIT_NEAR = 0.8..1.25

    /** More reps than this on a set is flagged (unless the user's history has such sets). */
    const val MAX_REPS = 50

    /** The same entry as the set before within this many seconds looks like a double log. */
    const val DUPLICATE_MAX_GAP_SECONDS = 15

    /** Rest samples outside this range (a phone call, a drink) do not count for the usual rest. */
    val REST_RANGE_SECONDS = 20..600
    const val MIN_REST_SAMPLES = 3
}

enum class GuardrailRule { UNPARSED, DUPLICATE, DECIMAL_SLIP, UNIT_MISMATCH, OVER_RECENT_MAX, HIGH_REPS }

/** What the hint's first button does. Nothing changes without that tap. */
enum class GuardrailFix {
    /** Replace the set's entry with [GuardrailHint.fixEntry]. */
    REPLACE_ENTRY,

    /** Open the set on the keypad so the user types the right value. */
    EDIT,

    /** Delete the set (with the usual Undo). */
    REMOVE,
}

data class GuardrailHint(
    val rule: GuardrailRule,
    val message: String,
    val fix: GuardrailFix,
    val fixLabel: String,
    val fixEntry: String? = null,
)

/**
 * One exercise block as the editor holds it: the exercise row, the entries of its earlier sets
 * and the set being committed. [restSeconds] is the time since the previous Enter (null when an
 * old set is edited, which turns the duplicate rule off). [ignoredEarlier] are indexes into
 * [earlierEntries] that carry an open hint: they are not used as a reference.
 */
data class GuardrailBlock(
    val exerciseName: String,
    val flag: ExerciseFlag,
    val earlierEntries: List<String>,
    val entry: String,
    val restSeconds: Int? = null,
    val ignoredEarlier: Set<Int> = emptySet(),
)

private val ENTRY_WEIGHT = Regex("""^\s*([\d+]+)\s*[xX]\s*(\d+(?:[.,]\d+)?)(?:\s*(kg|kgs|lb|lbs))?\s*$""", RegexOption.IGNORE_CASE)
private val ENTRY_REPS = Regex("""^\s*([\d+]+)\s*[xX]""")

/**
 * Checks a set at Enter against the user's own [history] (#304). Pure and synchronous: one block
 * parse plus a map lookup, well under the 2 ms main-thread budget. Never changes anything itself.
 */
class SetGuardrails(
    private val history: SetHistory,
    private val defaultUnit: String = "KG",
    private val parser: WorkoutParser = WorkoutParser(),
) {
    private val keyCache = HashMap<Pair<String, ExerciseFlag>, String?>()

    fun check(block: GuardrailBlock): GuardrailHint? {
        val entry = block.entry.trim()
        if (entry.isEmpty() || block.exerciseName.isBlank()) return null
        val lines = buildList {
            add(block.exerciseName.trim())
            block.earlierEntries.forEach { add("      ${it.trim()}") }
            add("      $entry")
        }
        val metadata = buildNoteRowMetadata(List(lines.size) { "" }, List(lines.size) { block.flag })
        val result = runCatching { parser.parse(lines.joinToString("\n"), defaultUnit, metadata) }.getOrNull() ?: return null
        val lastLine = lines.lastIndex

        // 1. A line the parser drops never reaches stats: say why, no history needed.
        result.diagnostics.firstOrNull { it.lineIndex == lastLine && it.reason.dropsSet }?.let {
            return GuardrailHint(GuardrailRule.UNPARSED, unparsedMessage(it.reason), GuardrailFix.EDIT, "Edit")
        }
        val set = result.sets.lastOrNull() ?: return null

        // 2. The same set twice within seconds: a double tap on Next, not a straight set.
        val previous = block.earlierEntries.lastOrNull()?.trim()
        val gap = block.restSeconds
        if (previous != null && gap != null && gap < GuardrailThresholds.DUPLICATE_MAX_GAP_SECONDS &&
            normalise(previous) == normalise(entry)
        ) {
            return GuardrailHint(
                GuardrailRule.DUPLICATE,
                "Same as the set before, ${gap}s apart. Logged twice?",
                GuardrailFix.REMOVE,
                "Remove",
            )
        }

        val exercise = history.forKey(set.exerciseIdentity.strictComparisonKey) ?: return null
        if (exercise.sessionCount < GuardrailThresholds.MIN_SESSIONS) return null

        if (set.durationSeconds == null) {
            weightHint(block, entry, exercise, result.sets.dropLast(1).map { it.weight.toDouble() to it.weightUnit }, earlierSetLines(result, lastLine))
                ?.let { return it }
            repsHint(entry, set.reps, exercise)?.let { return it }
        }
        return null
    }

    /** The exercise's usual rest from history (rest timer default, #349), or null. */
    fun usualRestSeconds(exerciseName: String, flag: ExerciseFlag): Int? =
        keyFor(exerciseName, flag)?.let { history.forKey(it)?.usualRestSeconds }

    private fun keyFor(exerciseName: String, flag: ExerciseFlag): String? =
        keyCache.getOrPut(exerciseName.trim() to flag) { exerciseKey(parser, exerciseName, flag, defaultUnit) }

    /** Line index (in the block) of each parsed earlier set, in order, so open hints can be skipped. */
    private fun earlierSetLines(result: com.example.gymtrack.core.util.WorkoutParseResult, lastLine: Int): List<Int> {
        val dropped = result.diagnostics.filter { it.reason.dropsSet }.map { it.lineIndex }.toSet()
        return (1 until lastLine).filter { it !in dropped }
    }

    private fun weightHint(
        block: GuardrailBlock,
        entry: String,
        exercise: ExerciseHistory,
        earlierSets: List<Pair<Double, String>>,
        earlierLines: List<Int>,
    ): GuardrailHint? {
        // Only a weight typed on this set: a carried weight (`8x`) was checked when it was typed.
        val match = ENTRY_WEIGHT.matchEntire(entry) ?: return null
        val typed = match.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return null
        if (typed <= 0.0) return null
        val explicitUnit = match.groups[3]?.value?.let { if (it.startsWith("l", ignoreCase = true)) "LB" else "KG" }
        val unit = explicitUnit ?: defaultUnit.uppercase()
        val typedKg = toKg(typed, unit)

        val earlierMaxKg = earlierSets.withIndex()
            .filter { (i, _) -> earlierLines.getOrNull(i)?.let { it - 1 } !in block.ignoredEarlier }
            .maxOfOrNull { (_, s) -> toKg(s.first, s.second) } ?: 0.0
        val ref = maxOf(exercise.recentMaxKg, earlierMaxKg)
        if (ref <= 0.0) return null
        val last = lastLabel(exercise)

        // Decimal slip: `825` for `82.5`.
        if ('.' !in match.groupValues[2] && ',' !in match.groupValues[2] && typedKg > ref * GuardrailThresholds.OVER_MAX_FACTOR) {
            val candidate = typed / 10.0
            if (toKg(candidate, unit) / ref in GuardrailThresholds.DECIMAL_NEAR) {
                val shown = formatWeight(candidate)
                return GuardrailHint(
                    GuardrailRule.DECIMAL_SLIP,
                    "Meant $shown?$last",
                    GuardrailFix.REPLACE_ENTRY,
                    "Fix",
                    fixEntry = replaceWeight(entry, match, shown, explicitUnit),
                )
            }
        }

        // Unit slip: the number fits the other unit (`176` typed as kg is 80 kg in lb).
        val otherUnit = if (unit == "LB") "KG" else "LB"
        val asTyped = typedKg / ref
        val asOther = toKg(typed, otherUnit) / ref
        if (asTyped !in GuardrailThresholds.UNIT_AS_TYPED_OK && asOther in GuardrailThresholds.UNIT_NEAR) {
            val label = otherUnit.lowercase(Locale.ROOT)
            val unitText = if (otherUnit == defaultUnit.uppercase()) null else label
            return GuardrailHint(
                GuardrailRule.UNIT_MISMATCH,
                "${formatWeight(typed)} looks like $label.$last",
                GuardrailFix.REPLACE_ENTRY,
                "Use $label",
                fixEntry = replaceWeight(entry, match, formatWeight(typed), unitText, forceUnit = true),
            )
        }

        if (typedKg > ref * GuardrailThresholds.OVER_MAX_FACTOR) {
            return GuardrailHint(
                GuardrailRule.OVER_RECENT_MAX,
                "Over 2× your recent best (${formatWeight(inDefaultUnit(ref))} ${defaultUnit.lowercase(Locale.ROOT)}). Typo?",
                GuardrailFix.EDIT,
                "Edit",
            )
        }
        return null
    }

    private fun repsHint(entry: String, reps: Int, exercise: ExerciseHistory): GuardrailHint? {
        if (reps <= GuardrailThresholds.MAX_REPS || exercise.maxReps > GuardrailThresholds.MAX_REPS) return null
        val range = exercise.repRange
        val usual = range?.let { " (usual ${it.first}–${it.last})" }.orEmpty()
        // `60` for `6`, `88` for `8`: one key too many.
        val repsText = ENTRY_REPS.find(entry)?.groupValues?.get(1)
        val candidate = repsText?.takeIf { '+' !in it && it.length == 2 }?.substring(0, 1)?.toIntOrNull()
        return if (candidate != null && range != null && candidate in range) {
            GuardrailHint(
                GuardrailRule.HIGH_REPS,
                "Meant $candidate reps?$usual",
                GuardrailFix.REPLACE_ENTRY,
                "Fix",
                fixEntry = entry.replaceFirst(repsText, candidate.toString()),
            )
        } else {
            GuardrailHint(GuardrailRule.HIGH_REPS, "$reps reps?$usual", GuardrailFix.EDIT, "Edit")
        }
    }

    private fun inDefaultUnit(kg: Double): Double = if (defaultUnit.equals("LB", ignoreCase = true)) kg / KG_PER_LB else kg

    private fun lastLabel(exercise: ExerciseHistory): String {
        val set = exercise.lastWeightedSet ?: return ""
        val unit = if (set.unit.equals(defaultUnit, ignoreCase = true)) "" else " ${set.unit.lowercase(Locale.ROOT)}"
        return " (last ${formatWeight(set.weight)}$unit×${set.reps})"
    }

    private fun unparsedMessage(reason: ParseDiagnosticReason): String = when (reason) {
        ParseDiagnosticReason.MISSING_WEIGHT -> "No weight yet, so this set is not counted."
        ParseDiagnosticReason.AMBIGUOUS_NUMBER -> "Reps or weight? Add an x, e.g. 8x."
        ParseDiagnosticReason.ZERO_DURATION -> "A 0 s timed set is not counted."
        else -> "Can't read this set, so it is not counted."
    }

    private fun replaceWeight(entry: String, match: MatchResult, weight: String, unit: String?, forceUnit: Boolean = false): String {
        val reps = match.groupValues[1]
        val keepUnit = if (forceUnit) unit else (unit ?: match.groups[3]?.value)
        return "${reps}x $weight" + (keepUnit?.let { " $it" } ?: "")
    }

    private fun normalise(entry: String): String = entry.lowercase(Locale.ROOT).replace(" ", "").replace(',', '.')

    companion object {
        fun formatWeight(value: Double): String {
            val rounded = (value * 100).roundToInt() / 100.0
            return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString().trimEnd('0').trimEnd('.')
        }
    }
}
