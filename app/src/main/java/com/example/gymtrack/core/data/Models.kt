package com.example.gymtrack.core.data

// --- EXISTING LEGACY MODELS (Kept for UI compatibility) ---
data class Category(
    val name: String,
    val color: Long
)

val DEFAULT_CATEGORIES = listOf(
    Category("Push", 0xFFFF3B30),
    Category("Pull", 0xFFAF52DE),
    Category("Legs", 0xFF34C759),
)

val DEFAULT_CATEGORY_NAMES = DEFAULT_CATEGORIES.map { it.name }.toSet()

enum class WeightUnit(val storageValue: String, val displayLabel: String) {
    KG("KG", "kg"),
    LB("LB", "lb");

    companion object {
        fun fromStorage(value: String?): WeightUnit = when (value?.trim()?.uppercase()) {
            "LB", "LBS" -> LB
            "KG", "KGS" -> KG
            else -> KG
        }
    }
}

/**
 * The app's look (design language v2, #333). Normal is crisp (dark OLED or light); Notebook is
 * hand-drawn paper and pencil. SYSTEM = Normal following the system light/dark setting.
 */
enum class ThemeChoice(val storageValue: String, val label: String) {
    NORMAL_DARK("NORMAL_DARK", "Normal · dark"),
    NORMAL_LIGHT("NORMAL_LIGHT", "Normal · light"),
    NOTEBOOK("NOTEBOOK", "Notebook"),
    SYSTEM("SYSTEM", "Follow system");

    /** True when this choice draws light text on a dark page, given the system setting. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        NORMAL_DARK -> true
        NORMAL_LIGHT, NOTEBOOK -> false
        SYSTEM -> systemDark
    }

    companion object {
        /**
         * Reads a stored choice. When none is stored (installs and backups from before v2),
         * the old dark-mode switch decides: on → NORMAL_DARK, off → NORMAL_LIGHT.
         * Unknown values (a newer app's choice) fall back the same way.
         */
        fun fromStorage(value: String?, legacyDarkMode: Boolean?): ThemeChoice =
            entries.firstOrNull { it.storageValue == value?.trim()?.uppercase() }
                ?: when (legacyDarkMode) {
                    true -> NORMAL_DARK
                    false -> NORMAL_LIGHT
                    null -> Settings.DEFAULT_THEME
                }
    }
}

data class Settings(
    val is24Hour: Boolean = true,
    val roundingSeconds: Int = 5,
    val themeChoice: ThemeChoice = DEFAULT_THEME,
    val categories: List<Category> = DEFAULT_CATEGORIES,
    val defaultWeightUnit: WeightUnit = WeightUnit.KG,
    /** Opt-in coaching (#312): NONE (the default) = history suggestions only, no policy. */
    val trainingGoal: com.example.gymtrack.feature.suggest.TrainingGoal = com.example.gymtrack.feature.suggest.TrainingGoal.NONE,
    /** Planner v1 (#314): saved workout templates, in the user's order. */
    val templates: List<WorkoutTemplate> = emptyList(),
) {
    /**
     * Legacy flag kept for code that still reads it (the old editor's status-bar colour).
     * SYSTEM counts as dark here because Settings cannot see the system setting; new code
     * reads `GymTrackTheme.tokens.isDark` instead.
     */
    @Deprecated("Read GymTrackTheme.tokens.isDark", ReplaceWith("themeChoice.isDark(systemDark)"))
    val darkMode: Boolean get() = themeChoice.isDark(systemDark = true)

    companion object {
        /** Single source of truth for the theme default; [SettingsStore] falls back to it too. */
        /** New installs open in Notebook (user decision 2026-11-12). Stored choices are kept. */
        val DEFAULT_THEME = ThemeChoice.NOTEBOOK
    }
}

data class NoteLine(
    val title: String,
    val text: String,
    val timestamp: Long,
    val categoryName: String? = null,
    val categoryColor: Long? = null,
    val learnings: String = "",
    val rowMetadata: String = "",
)

/**
 * Represents a raw workout note entry.
 * Required by StatsOverview and SetsDistributionChart.
 */
data class Note(
    val id: Int = 0,
    val text: String,        // Used by WorkoutParser in your charts
    val date: Long,
    val categoryName: String? = null
)

// --- NEW OBJECT-ORIENTED MODELS ---

/**
 * Represents a distinct exercise type (e.g., "Bench Press").
 * @param parentId Used for grouping variations (e.g., "Incline Bench" -> parent "Bench Press").
 * @param muscleGroup Primary target (e.g., "Chest", "Back").
 */
data class Exercise(
    val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val muscleGroup: String? = null,
    val aliases: List<String> = emptyList() // Helps map "bp", "bench" -> "Bench Press"
)

/**
 * Represents a single performed set.
 * The core atom of your tracking.
 */
data class WorkoutSet(
    val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long, // Links to Exercise.id
    val weight: Float,
    val reps: Int,
    val rpe: Float? = null, // Rate of Perceived Exertion (optional)
    val isUnilateral: Boolean = false,
    val timestamp: Long // When this specific set happened
)

/**
 * A summarized view of a workout for a specific exercise.
 * Useful for your future graph cards (e.g., "Avg Sets per Exercise").
 */
data class ExerciseSessionSummary(
    val exerciseName: String,
    val totalSets: Int,
    val avgWeight: Float,
    val maxWeight: Float,
    val totalVolume: Float // sets * reps * weight
)
