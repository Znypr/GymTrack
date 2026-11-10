package com.example.gymtrack.feature.stats

import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.util.WorkoutParser
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Builds the Stats screen state (rules in docs/STATS_RULES.md).
 *
 * Only finished workouts ([StatsWorkoutFacts.isFinished]) are counted: Workouts, Weekly Avg,
 * Avg Sets, Favorite, insights, weekly consistency, duration trend and the time-of-day heat map
 * all use the same finished set. Unfinished workouts in the range are reported in
 * [StatsState.unfinishedCount] so they stay visible instead of silently vanishing.
 *
 * Each note is parsed at most once ([digests] memoizes it across loads, #303).
 */
fun buildStatsState(
    allNotes: List<NoteLine>,
    range: TimeRange,
    facts: StatsWorkoutFacts,
    displayUnit: WeightUnit,
    nowMillis: Long,
    parser: WorkoutParser = WorkoutParser(),
    digests: StatsDigestCache = StatsDigestCache(),
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

    val noteDigests = digests.digestAll(notes, facts.storedSetUnits) { parser }
    val mainCount = noteDigests.sumOf { it.mainLineCount }
    val subCount = noteDigests.sumOf { it.subLineCount }
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

    val durationAvgs = notes.indices.groupBy { notes[it].categoryName ?: "Other" }
        .mapValues { (_, indexes) ->
            val durations = indexes.mapNotNull { noteDigests[it].maxElapsedSeconds }
            if (durations.isEmpty()) 0f else durations.average().toFloat() / 60f
        }

    val heatmap = Array(7) { IntArray(24) }
    notes.forEach { n ->
        val c = Calendar.getInstance().apply { timeInMillis = n.timestamp }
        val day = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val hour = c.get(Calendar.HOUR_OF_DAY)
        heatmap[day][hour]++
    }

    val exerciseCounts = noteDigests.asSequence()
        .flatMap { digest -> digest.sets.asSequence().map { it.set.exerciseName } }
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
        trainingInsights = buildTrainingInsightsFromDigests(
            workouts = notes.zip(noteDigests),
            displayUnit = displayUnit,
        ),
        weeklyDurations = buildWeeklyAverageWorkoutDurationsFromDigests(noteDigests),
        unreasonableDurationCount = countUnreasonableWorkoutDurationsFromDigests(noteDigests),
        timedSets = buildTimedSetSummary(noteDigests),
        timedProgress = buildTimedProgress(noteDigests, displayUnit),
        currentRange = range,
        filteredNotes = notes,
        unfinishedCount = unfinished.size,
        displayUnit = displayUnit,
    )
}
