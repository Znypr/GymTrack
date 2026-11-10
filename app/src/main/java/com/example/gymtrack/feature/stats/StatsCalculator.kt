package com.example.gymtrack.feature.stats

import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.util.WorkoutParser
import com.example.gymtrack.core.util.parseDurationSeconds
import com.example.gymtrack.core.util.parseNoteText
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Builds the Stats screen state (rules in docs/STATS_RULES.md).
 *
 * Only finished workouts ([StatsWorkoutFacts.isFinished]) are counted: Workouts, Weekly Avg,
 * Avg Sets, Favorite, insights, weekly consistency, duration trend and the time-of-day heat map
 * all use the same finished set. Unfinished workouts in the range are reported in
 * [StatsState.unfinishedCount] so they stay visible instead of silently vanishing.
 */
fun buildStatsState(
    allNotes: List<NoteLine>,
    range: TimeRange,
    facts: StatsWorkoutFacts,
    displayUnit: WeightUnit,
    nowMillis: Long,
    parser: WorkoutParser = WorkoutParser(),
): StatsState {
    val cutoff = if (range.days > 0) nowMillis - TimeUnit.DAYS.toMillis(range.days.toLong()) else 0L
    val inRange = allNotes.filter { it.timestamp >= cutoff }
    val (notes, unfinished) = inRange.partition { facts.isFinished(it.timestamp) }

    if (notes.isEmpty()) {
        return StatsState(
            currentRange = range,
            filteredNotes = emptyList(),
            unfinishedCount = unfinished.size,
            displayUnit = displayUnit,
        )
    }

    val (mainCount, subCount) = notes.fold(0 to 0) { acc, note ->
        val lines = parseNoteText(note.text, note.rowMetadata).first
        var main = 0
        var sub = 0
        for (l in lines) {
            if (l.isBlank()) continue
            if (l.startsWith("    ")) sub++ else main++
        }
        (acc.first + main) to (acc.second + sub)
    }
    val avgSets = if (mainCount > 0) subCount.toFloat() / mainCount else 0f

    val minTime = notes.minOf { it.timestamp }
    val maxTime = notes.maxOf { it.timestamp }
    val durationWeeks = if (range == TimeRange.ALL_TIME) {
        val diff = (maxTime - minTime).coerceAtLeast(1L)
        diff.toFloat() / TimeUnit.DAYS.toMillis(7)
    } else {
        range.days / 7f
    }
    val avgWeekly = if (durationWeeks > 0) notes.size / durationWeeks else 0f

    val counts = notes.groupingBy { it.categoryName ?: "Other" }.eachCount()

    val durationAvgs = notes.groupBy { it.categoryName ?: "Other" }
        .mapValues { entry ->
            val durations = entry.value.mapNotNull { note ->
                parseNoteText(note.text, note.rowMetadata).second.mapNotNull {
                    if (it.isBlank()) null else parseDurationSeconds(it)
                }.maxOrNull()
            }
            if (durations.isEmpty()) 0f else durations.average().toFloat() / 60f
        }

    val heatmap = Array(7) { IntArray(24) }
    notes.forEach { n ->
        val c = Calendar.getInstance().apply { timeInMillis = n.timestamp }
        val day = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val hour = c.get(Calendar.HOUR_OF_DAY)
        heatmap[day][hour]++
    }

    val exerciseCounts = notes.flatMap { note ->
        parser.parseWorkout(note.text, rowMetadata = note.rowMetadata).map { it.exerciseName }
    }
        .groupingBy { it }
        .eachCount()
        .toList()
        .sortedByDescending { it.second }
        .take(5)

    return StatsState(
        totalNotes = notes.size,
        avgWorkoutsPerWeek = avgWeekly,
        avgSets = avgSets,
        categoryCounts = counts,
        averageDurations = durationAvgs,
        heatmapData = heatmap,
        topExercises = exerciseCounts,
        weeklyWorkoutCounts = buildWeeklyWorkoutCounts(notes),
        trainingInsights = buildTrainingInsights(
            notes = notes,
            parser = parser,
            displayUnit = displayUnit,
            storedSetUnits = facts.storedSetUnits,
        ),
        currentRange = range,
        filteredNotes = notes,
        unfinishedCount = unfinished.size,
        displayUnit = displayUnit,
    )
}
