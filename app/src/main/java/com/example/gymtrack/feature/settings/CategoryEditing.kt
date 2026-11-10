package com.example.gymtrack.feature.settings

import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.DEFAULT_CATEGORIES

/**
 * Category list edits for Settings (#296). Pure: the screen calls these and saves the result.
 * Saved workouts keep the category name they were saved with; nothing here touches workouts.
 */
object CategoryEditing {
    const val MAX_NAME_LENGTH = 24

    /** Why a name can't be used, or null when it is fine. [except] = the category being renamed. */
    fun nameProblem(list: List<Category>, name: String, except: Category? = null): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> "Type a name."
            trimmed.length > MAX_NAME_LENGTH -> "Keep it under $MAX_NAME_LENGTH characters."
            list.any { it != except && it.name.equals(trimmed, ignoreCase = true) } -> "\"$trimmed\" already exists."
            else -> null
        }
    }

    fun add(list: List<Category>, name: String, color: Long): List<Category> =
        if (nameProblem(list, name) != null) list else list + Category(name.trim(), color)

    /** Replaces [old] in place (keeps the order). */
    fun update(list: List<Category>, old: Category, name: String, color: Long): List<Category> =
        if (nameProblem(list, name, except = old) != null) list
        else list.map { if (it == old) Category(name.trim(), color) else it }

    /** The last category can't be removed (the editor needs one to pick). */
    fun canRemove(list: List<Category>): Boolean = list.size > 1

    fun remove(list: List<Category>, category: Category): List<Category> =
        if (canRemove(list)) list - category else list

    /** Defaults whose name is not in the list (removed or renamed). */
    fun missingDefaults(list: List<Category>): List<Category> =
        DEFAULT_CATEGORIES.filter { d -> list.none { it.name.equals(d.name, ignoreCase = true) } }

    fun restoreDefaults(list: List<Category>): List<Category> = list + missingDefaults(list)
}
