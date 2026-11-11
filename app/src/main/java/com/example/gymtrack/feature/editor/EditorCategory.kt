package com.example.gymtrack.feature.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.ui.components.CategoryKind

/**
 * The workout's category (#296), one rule for the whole editor:
 * - a new workout uses the category chosen on Today, else the first category in settings;
 * - an existing workout keeps exactly what is stored (name and colour), even if that category
 *   was removed from settings since. Opening, Back and autosave never rewrite it;
 * - only a tap in the category picker changes it.
 * There is no hard-coded fallback category.
 */
class EditorCategory {
    /** Stored name; null for a legacy workout without one (saved back as null). */
    var name by mutableStateOf<String?>(null)
        private set

    /** Stored colour, kept as-is so a save writes back what was loaded. */
    var color by mutableStateOf<Long?>(null)
        private set

    private var resolved = false

    /** An existing workout: take its stored values verbatim. */
    fun loadStored(storedName: String?, storedColor: Long?) {
        name = storedName
        color = storedColor
        resolved = true
    }

    /**
     * A new workout: apply [newWorkoutCategory] once. Later calls (recomposition, settings
     * reloading) do nothing, so a category the user picked is never replaced.
     */
    fun startNew(categories: List<Category>, chosenOnToday: String?) {
        if (resolved) return
        val category = newWorkoutCategory(categories, chosenOnToday) ?: return
        name = category.name
        color = category.color
        resolved = true
    }

    /** The user tapped a category in the picker. */
    fun choose(category: Category) {
        name = category.name
        color = category.color
        resolved = true
    }
}

/** A new workout's category: the one chosen on Today if it exists, else the first one. */
internal fun newWorkoutCategory(categories: List<Category>, chosenOnToday: String?): Category? =
    categories.firstOrNull { it.name == chosenOnToday } ?: categories.firstOrNull()

/** How the top line shows a category: its mark kind, a custom colour (or null = neutral ink). */
data class CategoryDisplay(val label: String, val kind: CategoryKind, val customColor: Long?)

/**
 * A category still in settings gets its own mark (Push ▲ / Pull ◆ / Legs ● or the user's colour).
 * One that is not (removed, renamed, or a legacy name) is shown as stored, with the neutral ■ mark,
 * never as Push.
 */
internal fun categoryDisplay(name: String?, categories: List<Category>): CategoryDisplay {
    if (name.isNullOrBlank()) return CategoryDisplay("Workout", CategoryKind.OTHER, null)
    val known = categories.firstOrNull { it.name == name }
        ?: return CategoryDisplay(name, CategoryKind.OTHER, null)
    val kind = CategoryKind.fromName(known.name)
    return CategoryDisplay(known.name, kind, if (kind == CategoryKind.OTHER) known.color else null)
}
