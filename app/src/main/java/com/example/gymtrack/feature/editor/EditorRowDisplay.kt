package com.example.gymtrack.feature.editor

import com.example.gymtrack.core.data.WeightUnit

/*
 * Editor v2 display rules (docs/design/EDITOR_V2.md §1–§2). Pure string work on one stored row:
 * no WorkoutParser, no history, so it is safe on the composition path. The stored text is never
 * changed by any of this; it only decides what a row looks like.
 */

private val REST_SUFFIX = Regex("""\s*\((\d+)'(\d{2})''\)\s*$""")
private val REPS_SET = Regex("""^(\d+(?:\+\d+)*)\s*[xX]\s*(?:(\d+(?:[.,]\d+)?)\s*(kgs?|lbs?)?)?$""", RegexOption.IGNORE_CASE)
private val TIMED_SET = Regex("""^(\d+)\s*[sS]\s*(?:(\d+(?:[.,]\d+)?)\s*(kgs?|lbs?)?)?$""", RegexOption.IGNORE_CASE)

/** A stored set row split into the parts the user may and may not edit. */
data class SetRowParts(
    /** Leading spaces (the structural 6-space set indent, or none for a draft). */
    val indent: String,
    /** What the user typed: `8x 80`, `10+3+2x 45`, `60s`. */
    val entry: String,
    /** The automatic relative time including its leading space, ` (1'38'')`, or "". */
    val restSuffix: String,
) {
    /** Rebuilds the stored text with a new entry; indent and rest time are kept as they were. */
    fun withEntry(newEntry: String): String = indent + newEntry.trim() + restSuffix
}

fun splitSetRow(text: String): SetRowParts {
    val indent = text.takeWhile { it == ' ' }
    val body = text.substring(indent.length)
    val rest = REST_SUFFIX.find(body)
    return if (rest != null) {
        SetRowParts(indent, body.substring(0, rest.range.first).trimEnd(), body.substring(rest.range.first))
    } else {
        SetRowParts(indent, body.trimEnd(), "")
    }
}

/** `(1'38'')` → `1'38`; null when the row has no relative time. */
fun restLabel(text: String): String? =
    REST_SUFFIX.find(text)?.let { "${it.groupValues[1]}'${it.groupValues[2]}" }

/** `2'44''` (absolute column) → `2'44`. */
fun shortTimeLabel(stored: String): String = stored.trim().removeSuffix("''")

data class SetRowDisplay(
    /** `8`, `10+3+2`; null for a timed set or free text. */
    val reps: String?,
    /** `60` for a timed set. */
    val seconds: String?,
    /** The weight to show, typed or carried; null for bodyweight. */
    val weight: String?,
    /** True when [weight] is inherited from an earlier set (`8x`): drawn muted. */
    val weightIsCarried: Boolean,
    /** Unit label, only when it differs from the default unit. */
    val unit: String?,
    /** Myo-rep total (`10+3+2` → 15); null for a plain set. */
    val myoTotal: Int?,
    /** An entry that is not a recognised set, shown as typed (without indent or time). */
    val freeText: String?,
    /** Rest before this set, `1'38`. */
    val rest: String?,
)

private data class TypedWeight(val weight: String, val unit: String?)

private fun parseWeightOf(entry: String): TypedWeight? {
    val m = REPS_SET.matchEntire(entry.trim()) ?: return null
    val weight = m.groupValues[2].takeIf(String::isNotEmpty) ?: return null
    return TypedWeight(weight, m.groupValues[3].takeIf(String::isNotEmpty))
}

/**
 * The weight a reps-only set inherits: the last weight typed on a reps set earlier in the same
 * exercise. Timed sets neither use nor update it (mirrors WorkoutParser's carry-over rule).
 * [earlierEntries] are the exercise's earlier set texts, oldest first.
 */
private fun carriedWeight(earlierEntries: List<String>): TypedWeight? {
    for (i in earlierEntries.indices.reversed()) {
        val weight = parseWeightOf(splitSetRow(earlierEntries[i]).entry)
        if (weight != null) return weight
    }
    return null
}

private fun unitLabelIfNotDefault(unit: String?, defaultUnit: WeightUnit): String? {
    if (unit.isNullOrBlank()) return null
    val parsed = WeightUnit.fromStorage(unit)
    return if (parsed == defaultUnit) null else parsed.displayLabel
}

/**
 * What a committed or draft set row shows. [earlierSetTexts] are the stored texts of the same
 * exercise's set rows above it (oldest first), used only for the muted carried weight.
 */
fun setRowDisplay(
    text: String,
    earlierSetTexts: List<String>,
    defaultUnit: WeightUnit,
): SetRowDisplay {
    val parts = splitSetRow(text)
    val rest = restLabel(text)
    val entry = parts.entry.trim()

    REPS_SET.matchEntire(entry)?.let { m ->
        val reps = m.groupValues[1]
        val typed = m.groupValues[2].takeIf(String::isNotEmpty)
        val carried = if (typed == null) carriedWeight(earlierSetTexts) else null
        val unit = if (typed != null) m.groupValues[3].takeIf(String::isNotEmpty) else carried?.unit
        val myoTotal = if ('+' in reps) reps.split('+').sumOf { it.toIntOrNull() ?: 0 } else null
        return SetRowDisplay(
            reps = reps,
            seconds = null,
            weight = typed ?: carried?.weight,
            weightIsCarried = typed == null && carried != null,
            unit = unitLabelIfNotDefault(unit, defaultUnit),
            myoTotal = myoTotal,
            freeText = null,
            rest = rest,
        )
    }
    TIMED_SET.matchEntire(entry)?.let { m ->
        return SetRowDisplay(
            reps = null,
            seconds = m.groupValues[1],
            weight = m.groupValues[2].takeIf(String::isNotEmpty),
            weightIsCarried = false,
            unit = unitLabelIfNotDefault(m.groupValues[3].takeIf(String::isNotEmpty), defaultUnit),
            myoTotal = null,
            freeText = null,
            rest = rest,
        )
    }
    return SetRowDisplay(null, null, null, false, null, null, freeText = entry, rest = rest)
}

/** The set being typed on the keypad, drawn in display grammar: `8x 8` → `8 × 8`, `60s` → `60 s`. */
fun prettyKeypadEntry(entry: String): String = entry
    .replace(Regex("""\s*[xX]\s*"""), " × ")
    .replace(Regex("""(\d)\s*[sS]\s+(?=\d)"""), "$1 s × ")
    .replace(Regex("""(\d)\s*[sS](?![a-zA-Z])\s*"""), "$1 s ")
    .trimStart()
