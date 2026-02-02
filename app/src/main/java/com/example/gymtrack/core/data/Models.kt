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

data class Settings(
    val is24Hour: Boolean = true,
    val roundingSeconds: Int = 5,
    val darkMode: Boolean = true,
    val categories: List<Category> = DEFAULT_CATEGORIES
)

data class NoteLine(
    val title: String,
    val text: String,
    val timestamp: Long,
    val categoryName: String? = null,
    val categoryColor: Long? = null,
    val learnings: String = ""
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