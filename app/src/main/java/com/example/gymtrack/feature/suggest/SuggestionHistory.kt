package com.example.gymtrack.feature.suggest

/*
 * The editor's in-memory history snapshot for suggestions (#308). Built once when the editor
 * opens, off the main thread; lookups are map reads (cached per typed name). Pure Kotlin: the
 * Room rows are converted by the caller through [parseSetsSummary].
 */

private val SUMMARY_SET = Regex("""^(\d+)x(?:\s+(\d+(?:\.\d+)?))?(?:\s+(kg|lb))?$""", RegexOption.IGNORE_CASE)

/**
 * `8x 80 · 8x 80 · 6x 80 lb` (the `exercise_recent.sets_summary` grammar) → working sets.
 * A set without a unit is in [defaultUnitOfRow] (the row's top-weight unit; the writer only
 * spells out `lb`). Any unreadable part makes the whole session empty: low confidence, no pill.
 */
fun parseSetsSummary(summary: String, defaultUnitOfRow: LoadUnit): List<PastSet> {
    if (summary.isBlank()) return emptyList()
    val parts = summary.split('·').map(String::trim).filter(String::isNotEmpty)
    val sets = parts.map { part ->
        val m = SUMMARY_SET.matchEntire(part) ?: return emptyList()
        PastSet(
            reps = m.groupValues[1].toInt(),
            load = m.groupValues[2].toDoubleOrNull() ?: 0.0,
            unit = LoadUnit.fromLabel(m.groupValues[3]) ?: if (m.groupValues[2].isEmpty()) defaultUnitOfRow else LoadUnit.KG,
        )
    }
    return sets
}

/**
 * Sessions per stored exercise name, newest first. [candidateNames] maps a typed name to the
 * stored names to try, in order (canonical name first, then the name as typed).
 */
class SuggestionHistory(
    private val byName: Map<String, List<PastSession>>,
    private val candidateNames: (String) -> List<String> = { listOf(it.trim()) },
) {
    private val cache = HashMap<String, List<PastSession>>()

    val exerciseCount: Int get() = byName.size

    /** Sessions of the exercise typed as [typedName]; empty when there is no history. */
    fun forExercise(typedName: String): List<PastSession> {
        val key = typedName.trim()
        if (key.isEmpty()) return emptyList()
        return cache.getOrPut(key) {
            runCatching { candidateNames(key) }.getOrDefault(listOf(key))
                .firstNotNullOfOrNull { name -> byName[name] ?: byName.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value }
                .orEmpty()
        }
    }

    companion object {
        val EMPTY = SuggestionHistory(emptyMap())
    }
}
