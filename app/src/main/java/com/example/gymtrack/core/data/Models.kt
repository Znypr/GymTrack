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

enum class HomeCardMetric(val storageValue: String, val displayLabel: String) {
    TOTAL_SETS("TOTAL_SETS", "Sets"),
    SETS_PER_MINUTE("SETS_PER_MINUTE", "Sets/min"),
    EXERCISES("EXERCISES", "Exercises"),
    AVG_SETS_PER_EXERCISE("AVG_SETS_PER_EXERCISE", "Sets/exercise");

    companion object {
        fun fromStorage(value: String?): HomeCardMetric = values().firstOrNull {
            it.storageValue == value?.trim()?.uppercase()
        } ?: SETS_PER_MINUTE
    }
}

enum class WorkoutIntensityFormula(val storageValue: String, val displayLabel: String, val description: String) {
    SET_DENSITY(
        storageValue = "SET_DENSITY",
        displayLabel = "Density",
        description = "Flames compare sets per minute against recent workouts in the same category.",
    ),
    SET_VOLUME(
        storageValue = "SET_VOLUME",
        displayLabel = "Volume",
        description = "Flames compare total set count against recent workouts in the same category.",
    ),
    AVG_SETS_PER_EXERCISE(
        storageValue = "AVG_SETS_PER_EXERCISE",
        displayLabel = "Depth",
        description = "Flames compare average sets per exercise against recent workouts in the same category.",
    );

    companion object {
        fun fromStorage(value: String?): WorkoutIntensityFormula = values().firstOrNull {
            it.storageValue == value?.trim()?.uppercase()
        } ?: SET_DENSITY
    }
}

enum class HomeOverviewWidget(val storageValue: String, val displayLabel: String) {
    LAST_WORKOUT("LAST_WORKOUT", "Last workout"),
    RECENT_INTENSITY("RECENT_INTENSITY", "Recent intensity"),
    QUICK_START("QUICK_START", "Quick start");

    companion object {
        fun fromStorage(value: String?): HomeOverviewWidget = values().firstOrNull {
            it.storageValue == value?.trim()?.uppercase()
        } ?: LAST_WORKOUT
    }
}

data class Settings(
    val is24Hour: Boolean = true,
    val roundingSeconds: Int = 5,
    val darkMode: Boolean = true,
    val categories: List<Category> = DEFAULT_CATEGORIES,
    val defaultWeightUnit: WeightUnit = WeightUnit.KG,
    val homeCardMetric: HomeCardMetric = HomeCardMetric.SETS_PER_MINUTE,
    val workoutIntensityFormula: WorkoutIntensityFormula = WorkoutIntensityFormula.SET_DENSITY,
    val homeOverviewWidget: HomeOverviewWidget = HomeOverviewWidget.LAST_WORKOUT,
)

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
