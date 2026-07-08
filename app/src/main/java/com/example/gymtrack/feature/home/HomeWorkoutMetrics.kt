package com.example.gymtrack.feature.home

import com.example.gymtrack.core.data.HomeCardMetric
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.data.WorkoutIntensityFormula
import com.example.gymtrack.core.util.WorkoutParser
import com.example.gymtrack.core.util.parseDurationSeconds
import com.example.gymtrack.core.util.parseNoteText
import kotlin.math.roundToInt

internal data class HomeWorkoutStats(
    val durationMinutes: Int,
    val exerciseCount: Int,
    val setCount: Int,
    val totalReps: Int,
    val avgSetsPerExercise: Float,
    val setsPerMinute: Float,
) {
    val hasUsefulTrainingData: Boolean = setCount > 0 && durationMinutes > 0
}

private val setLineStartRegex = Regex("(?i)^[\\d+]+\\s*x.*")
private val setLinePrefixRegex = Regex("(?i)^x\\s*[\\d+]+.*")
private val repsRegex = Regex("(?i)(?:x\\s*([0-9+]+)|([0-9+]+)\\s*x|([0-9+]+)\\s*reps?)")

internal fun analyzeWorkoutForHome(note: NoteLine, settings: Settings): HomeWorkoutStats {
    val parsedText = parseNoteText(note.text, note.rowMetadata)
    val visibleLines = parsedText.first
    val absoluteTimes = parsedText.second
    val durationMinutes = absoluteTimes.mapNotNull { parseDurationSeconds(it) }.maxOrNull()?.div(60) ?: 0
    val setRows = WorkoutParser().parseWorkout(
        rawText = note.text,
        defaultWeightUnit = settings.defaultWeightUnit.storageValue,
        rowMetadata = note.rowMetadata,
    )
    val exerciseCount = visibleLines.count { line ->
        val clean = line.trim()
        clean.isNotBlank() && !looksLikeSetLine(line)
    }
    val setCount = setRows.size
    val totalReps = setRows.sumOf { it.reps }
    val avgSetsPerExercise = if (exerciseCount > 0) setCount.toFloat() / exerciseCount else 0f
    val setsPerMinute = if (durationMinutes > 0) setCount.toFloat() / durationMinutes else 0f

    return HomeWorkoutStats(
        durationMinutes = durationMinutes,
        exerciseCount = exerciseCount,
        setCount = setCount,
        totalReps = totalReps,
        avgSetsPerExercise = avgSetsPerExercise,
        setsPerMinute = setsPerMinute,
    )
}

private fun looksLikeSetLine(line: String): Boolean {
    val clean = line.trim()
    return setLineStartRegex.matches(clean) ||
        setLinePrefixRegex.matches(clean) ||
        repsRegex.containsMatchIn(clean) ||
        clean.matches(Regex("^\\d+$")) ||
        line.startsWith("    ")
}

internal fun HomeWorkoutStats.cardMetricLabel(metric: HomeCardMetric): String = when (metric) {
    HomeCardMetric.TOTAL_SETS -> if (setCount == 1) "1 set" else "$setCount sets"
    HomeCardMetric.SETS_PER_MINUTE -> if (setsPerMinute > 0f) "${setsPerMinute.format1()} sets/min" else "No density"
    HomeCardMetric.EXERCISES -> if (exerciseCount == 1) "1 exercise" else "$exerciseCount exercises"
    HomeCardMetric.AVG_SETS_PER_EXERCISE -> if (avgSetsPerExercise > 0f) "${avgSetsPerExercise.format1()} sets/ex" else "No sets/ex"
}

internal fun HomeWorkoutStats.intensityScore(formula: WorkoutIntensityFormula): Float = when (formula) {
    WorkoutIntensityFormula.SET_DENSITY -> setsPerMinute
    WorkoutIntensityFormula.SET_VOLUME -> setCount.toFloat()
    WorkoutIntensityFormula.AVG_SETS_PER_EXERCISE -> avgSetsPerExercise
}

internal fun intensityFlames(
    current: HomeWorkoutStats,
    peerStats: List<HomeWorkoutStats>,
    formula: WorkoutIntensityFormula,
): Int {
    val currentScore = current.intensityScore(formula)
    if (currentScore <= 0f) return 0

    val peers = peerStats
        .map { it.intensityScore(formula) }
        .filter { it > 0f }

    if (peers.size < 2) {
        return fallbackFlames(current, formula)
    }

    val peerAverage = peers.average().toFloat().takeIf { it > 0f } ?: return fallbackFlames(current, formula)
    val ratio = currentScore / peerAverage
    return when {
        ratio >= 1.15f -> 3
        ratio >= 0.85f -> 2
        else -> 1
    }
}

private fun fallbackFlames(current: HomeWorkoutStats, formula: WorkoutIntensityFormula): Int = when (formula) {
    WorkoutIntensityFormula.SET_DENSITY -> when {
        current.setsPerMinute >= 0.24f -> 3
        current.setsPerMinute >= 0.15f -> 2
        current.setsPerMinute > 0f -> 1
        else -> 0
    }
    WorkoutIntensityFormula.SET_VOLUME -> when {
        current.setCount >= 24 -> 3
        current.setCount >= 14 -> 2
        current.setCount > 0 -> 1
        else -> 0
    }
    WorkoutIntensityFormula.AVG_SETS_PER_EXERCISE -> when {
        current.avgSetsPerExercise >= 3.5f -> 3
        current.avgSetsPerExercise >= 2.3f -> 2
        current.avgSetsPerExercise > 0f -> 1
        else -> 0
    }
}

internal fun flameText(flames: Int): String = when (flames.coerceIn(0, 3)) {
    3 -> "🔥🔥🔥"
    2 -> "🔥🔥"
    1 -> "🔥"
    else -> ""
}

private fun Float.format1(): String {
    val rounded = (this * 10f).roundToInt() / 10f
    return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
}
