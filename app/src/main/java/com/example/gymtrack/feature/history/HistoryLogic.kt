package com.example.gymtrack.feature.history

import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.util.ParsedSetDTO
import com.example.gymtrack.core.util.noteWorkoutDuration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/*
 * History tab logic. Pure (README rule 9): search, category filter, sort, month sections and the
 * row facts (duration, top set).
 */

data class HistoryFilter(
    val query: String = "",
    /** Category name to show, or null for all. */
    val category: String? = null,
    val newestFirst: Boolean = true,
)

/**
 * Workouts matching [filter]. The search matches exercise names and note text: every word of
 * the query must appear (case-insensitive) in the workout's title, category, rows or learnings.
 */
fun filterHistory(notes: List<NoteLine>, filter: HistoryFilter): List<NoteLine> {
    val words = filter.query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotEmpty)
    val matching = notes.filter { note ->
        val categoryMatches = filter.category == null ||
            note.categoryName?.trim().equals(filter.category.trim(), ignoreCase = true)
        categoryMatches && (words.isEmpty() || note.searchText().let { text -> words.all { it in text } })
    }
    return if (filter.newestFirst) matching.sortedByDescending { it.timestamp } else matching.sortedBy { it.timestamp }
}

private fun NoteLine.searchText(): String =
    listOf(title, categoryName.orEmpty(), text, learnings).joinToString("\n").lowercase()

/** Month sections in the list order of [sorted] (call with the output of [filterHistory]). */
fun groupByMonth(sorted: List<NoteLine>, zone: ZoneId = ZoneId.systemDefault()): List<Pair<YearMonth, List<NoteLine>>> {
    val sections = LinkedHashMap<YearMonth, MutableList<NoteLine>>()
    sorted.forEach { note ->
        val month = YearMonth.from(Instant.ofEpochMilli(note.timestamp).atZone(zone))
        sections.getOrPut(month) { mutableListOf() } += note
    }
    return sections.map { (month, notes) -> month to notes }
}

/**
 * Minutes shown on a row: start to Finish when the workout was finished, else start to the last
 * logged row, ending early at a pause of more than 3 h (docs/WORKOUT_DURATION.md).
 */
fun workoutCardMinutes(note: NoteLine, finishedAtMillis: Long?): Int =
    (noteWorkoutDuration(note.text, note.rowMetadata, note.timestamp, finishedAtMillis)?.seconds ?: 0) / 60

/** The heaviest set of a workout (ties: more reps, then the earlier set). */
data class TopSet(val exercise: String, val reps: Int, val weight: Float, val unit: WeightUnit)

fun topSet(sets: List<ParsedSetDTO>): TopSet? = sets
    .filter { it.reps > 0 && it.weight > 0f && it.durationSeconds == null }
    .withIndex()
    .maxWithOrNull(compareBy<IndexedValue<ParsedSetDTO>>({ it.value.weight }, { it.value.reps }, { -it.index }))
    ?.value
    ?.let { TopSet(it.exerciseName.trim(), it.reps, it.weight, WeightUnit.fromStorage(it.weightUnit)) }

/**
 * "Bench 8 × 80": REPS × WEIGHT (README rule 1). The unit is only shown when it differs from
 * the default (README rule 3).
 */
fun formatTopSet(top: TopSet, defaultUnit: WeightUnit): String {
    val unit = if (top.unit != defaultUnit) " ${top.unit.displayLabel}" else ""
    val name = top.exercise.takeIf(String::isNotEmpty)?.let { "$it " }.orEmpty()
    return "$name${top.reps} × ${formatWeight(top.weight)}$unit"
}

internal fun formatWeight(weight: Float): String {
    val rounded = Math.round(weight * 100f) / 100f
    return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString().trimEnd('0').trimEnd('.')
}

/** Sort control label: it always says the current order (#297). */
fun sortLabel(newestFirst: Boolean): String = if (newestFirst) "Newest first" else "Oldest first"

/** "1 workout", "3 workouts" (#297: never "notes"). */
fun workoutCount(count: Int): String = if (count == 1) "1 workout" else "$count workouts"
