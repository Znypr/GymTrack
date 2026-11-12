package com.example.gymtrack.core.data

/**
 * A saved workout plan (#314, planner v1 "templates first"): a name, the category it starts
 * and the exercise names in order. Starting it prefills the editor with those names only; no
 * set values are ever copied, so a template can never log a set by itself.
 */
data class WorkoutTemplate(
    val name: String,
    val categoryName: String,
    val exercises: List<String>,
)

/**
 * Template list rules and persistence, pure so it is unit-tested.
 *
 * Stored as one string: templates separated by a newline, fields by `|`, exercises by `;`, with
 * `%`, `|`, `;` and newlines percent-escaped. Unreadable records are skipped, never fatal.
 */
internal object TemplatePrefs {
    const val MAX_TEMPLATES = 20
    private const val RECORD = '\n'
    private const val FIELD = '|'
    private const val ITEM = ';'

    fun encode(templates: List<WorkoutTemplate>): String =
        templates.joinToString(RECORD.toString()) { t ->
            listOf(escape(t.name), escape(t.categoryName), t.exercises.joinToString(ITEM.toString(), transform = ::escape))
                .joinToString(FIELD.toString())
        }

    fun decode(stored: String?): List<WorkoutTemplate> {
        if (stored.isNullOrEmpty()) return emptyList()
        return stored.split(RECORD).mapNotNull { record ->
            val fields = record.split(FIELD)
            if (fields.size != 3) return@mapNotNull null
            val exercises = fields[2].split(ITEM).map(::unescape).map(String::trim).filter(String::isNotEmpty)
            val name = unescape(fields[0]).trim()
            if (name.isEmpty() || exercises.isEmpty()) null else WorkoutTemplate(name, unescape(fields[1]).trim(), exercises)
        }.distinctBy { it.name.lowercase() }.take(MAX_TEMPLATES)
    }

    private fun escape(text: String): String = buildString {
        for (ch in text) {
            when (ch) {
                '%' -> append("%25")
                FIELD -> append("%7C")
                ITEM -> append("%3B")
                RECORD -> append("%0A")
                '\r' -> append("%0D")
                else -> append(ch)
            }
        }
    }

    private fun unescape(text: String): String = text
        .replace("%7C", FIELD.toString()).replace("%3B", ITEM.toString())
        .replace("%0A", RECORD.toString()).replace("%0D", "\r").replace("%25", "%")
}

/** "Push", or "Push 2", "Push 3"… when that name is taken (case-insensitive). */
fun uniqueTemplateName(base: String, existing: List<WorkoutTemplate>): String {
    val clean = base.trim().ifEmpty { "Workout" }
    val taken = existing.map { it.name.lowercase() }.toSet()
    if (clean.lowercase() !in taken) return clean
    var n = 2
    while ("$clean $n".lowercase() in taken) n++
    return "$clean $n"
}

/** Adds [template] at the end, or replaces the one with the same name. Null when the list is full. */
fun List<WorkoutTemplate>.withSaved(template: WorkoutTemplate): List<WorkoutTemplate>? {
    val index = indexOfFirst { it.name.equals(template.name, ignoreCase = true) }
    if (index >= 0) return toMutableList().also { it[index] = template }
    if (size >= TemplatePrefs.MAX_TEMPLATES) return null
    return this + template
}
