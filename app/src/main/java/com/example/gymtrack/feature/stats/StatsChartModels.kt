package com.example.gymtrack.feature.stats

import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.util.WorkoutParser

data class WeeklyWorkoutCount(
    val weekStart: Long,
    val count: Int,
)

data class CategoryVolume(
    val category: String,
    val totalVolume: Float,
    val setCount: Int,
)

private data class CategoryVolumeAccumulator(
    var totalVolume: Float = 0f,
    var setCount: Int = 0,
)

fun buildWeeklyWorkoutCounts(notes: List<NoteLine>): List<WeeklyWorkoutCount> {
    return notes
        .groupBy { isoWeekStart(it.timestamp) }
        .toSortedMap()
        .map { (weekStart, weekNotes) ->
            WeeklyWorkoutCount(weekStart = weekStart, count = weekNotes.size)
        }
}

fun buildVolumeByCategory(
    notes: List<NoteLine>,
    parser: WorkoutParser = WorkoutParser(),
    maxCategories: Int = 6,
): List<CategoryVolume> {
    val categoryVolumes = mutableMapOf<String, CategoryVolumeAccumulator>()

    notes.forEach { note ->
        val category = note.categoryName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "Other"

        parser.parseWorkout(note.text, rowMetadata = note.rowMetadata)
            .filter { it.weight > 0f && it.reps > 0 }
            .forEach { set ->
                val accumulator = categoryVolumes.getOrPut(category) { CategoryVolumeAccumulator() }
                accumulator.totalVolume += set.weight * set.reps
                accumulator.setCount += 1
            }
    }

    return categoryVolumes
        .map { (category, volume) ->
            CategoryVolume(
                category = category,
                totalVolume = volume.totalVolume,
                setCount = volume.setCount,
            )
        }
        .filter { it.totalVolume > 0f }
        .sortedWith(compareByDescending<CategoryVolume> { it.totalVolume }.thenBy { it.category })
        .take(maxCategories)
}
