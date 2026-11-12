package com.example.gymtrack.feature.home

import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.WorkoutTemplate
import com.example.gymtrack.core.data.uniqueTemplateName
import com.example.gymtrack.core.util.parseNoteText

/*
 * "Start from last <category>" (#305). Pure logic, no Android imports (README rule 9).
 *
 * A prefilled workout copies the exercise NAMES of the last workout of a category, in that
 * workout's order, and nothing else: no set values, no times, no flags. Each exercise gets the
 * editor's own "+ set" row; the user logs every set themselves.
 */

/** Trailing relative time an old exercise row may carry: "Bench (1'05'')". */
private val TRAILING_TIME = Regex("""\s*\(\d+'\d{2}''\)$""")

/** What one tap on "Start from last <category>" creates. */
data class WorkoutPrefill(
    val category: Category,
    /** Exercise names in the source workout's order, each once. Never empty. */
    val exercises: List<String>,
    /** The workout the names came from. */
    val sourceTimestamp: Long,
) {
    /** The editor text: one exercise block per name, no set rows (the editor adds them). */
    val text: String get() = prefillText(exercises)
}

/**
 * Exercise names of [note] in order: the first row and every row after a blank row (the
 * storage rule, EDITOR_BEHAVIOUR §1.2). Set rows are skipped, so no set value is ever copied.
 * Repeats of the same exercise (case-insensitive) are kept once, at their first position.
 */
fun exerciseNames(note: NoteLine): List<String> {
    val lines = parseNoteText(note.text, note.rowMetadata).first
    val seen = HashSet<String>()
    return lines.indices
        .filter { i -> lines[i].isNotBlank() && (i == 0 || lines[i - 1].isBlank()) }
        .map { i -> lines[i].trim().replace(TRAILING_TIME, "").trim() }
        .filter { name -> name.isNotEmpty() && seen.add(name.lowercase()) }
}

/** Editor text for [exercises]: exercise blocks separated by one blank row. */
fun prefillText(exercises: List<String>): String = exercises.joinToString("\n\n")

/**
 * The prefill for [categoryName]: the newest workout of that category (by timestamp) that has
 * at least one exercise. The category (name and colour) comes from [categories] when it is
 * still there, else from the source workout. Null when no such workout exists.
 */
fun prefillFromLast(
    notes: List<NoteLine>,
    categoryName: String,
    categories: List<Category>,
): WorkoutPrefill? {
    val wanted = categoryName.trim()
    if (wanted.isEmpty()) return null
    var best: Pair<NoteLine, List<String>>? = null
    for (note in notes.sortedByDescending { it.timestamp }) {
        if (!note.categoryName?.trim().equals(wanted, ignoreCase = true)) continue
        val names = exerciseNames(note)
        if (names.isNotEmpty()) {
            best = note to names
            break
        }
    }
    val (source, names) = best ?: return null
    val category = categories.firstOrNull { it.name.equals(wanted, ignoreCase = true) }
        ?: Category(source.categoryName!!.trim(), source.categoryColor ?: FALLBACK_CATEGORY_COLOR)
    return WorkoutPrefill(category, names, source.timestamp)
}

/**
 * [prefillFromLast] for every category in [categories] in one newest-first pass, keyed by
 * lower-case category name. Categories without a usable workout are left out.
 */
fun prefillsFromLast(notes: List<NoteLine>, categories: List<Category>): Map<String, WorkoutPrefill> {
    val wanted = categories.associateBy { it.name.trim().lowercase() }.filterKeys(String::isNotEmpty)
    val result = HashMap<String, WorkoutPrefill>()
    for (note in notes.sortedByDescending { it.timestamp }) {
        if (result.size == wanted.size) break
        val key = note.categoryName?.trim()?.lowercase() ?: continue
        val category = wanted[key] ?: continue
        if (key in result) continue
        val names = exerciseNames(note)
        if (names.isNotEmpty()) result[key] = WorkoutPrefill(category, names, note.timestamp)
    }
    return result
}

/** "Bench · Row · Curl +2": a short preview of what the prefill brings. */
fun prefillPreview(exercises: List<String>, shown: Int = 3): String {
    val head = exercises.take(shown).joinToString(" · ")
    val rest = exercises.size - shown
    return if (rest > 0) "$head +$rest" else head
}

/**
 * True once the workout has a set row with text: any non-blank row right below another
 * non-blank row. Exercise names alone (an untouched "Start from last") are not a logged set.
 */
fun NoteLine.hasLoggedSet(): Boolean {
    val lines = parseNoteText(text, rowMetadata).first
    return (1 until lines.size).any { i -> lines[i].isNotBlank() && lines[i - 1].isNotBlank() }
}

private const val FALLBACK_CATEGORY_COLOR = 0xFF808080

/**
 * A template from [note] (#314): its exercise names in order, named after its category
 * ("Push", "Push 2" when taken). Null when the workout has no exercise.
 */
fun templateFromNote(note: NoteLine, existing: List<WorkoutTemplate>): WorkoutTemplate? {
    val names = exerciseNames(note)
    if (names.isEmpty()) return null
    val category = note.categoryName?.trim().orEmpty()
    return WorkoutTemplate(uniqueTemplateName(category.ifEmpty { note.title.trim() }, existing), category, names)
}

/** The category a template starts: the user's category of that name, else one rebuilt from it, else none. */
fun WorkoutTemplate.startCategory(categories: List<Category>): Category? {
    if (categoryName.isBlank()) return null
    return categories.firstOrNull { it.name.equals(categoryName, ignoreCase = true) }
        ?: Category(categoryName, 0xFF808080)
}
