package com.example.gymtrack.feature.stats

import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.util.WorkoutParser
import com.example.gymtrack.core.util.parseDurationSeconds
import com.example.gymtrack.core.util.parseNoteText

data class WeeklyWorkoutCount(
    val weekStart: Long,
    val count: Int,
)

data class TrainingMetricShift(
    val label: String,
    val baseline: Float,
    val recent: Float,
    val unit: String,
    val higherIsUsuallyGood: Boolean = true,
) {
    val absoluteChange: Float = recent - baseline
    val percentChange: Float? = if (baseline > 0f) ((recent - baseline) / baseline) * 100f else null
}

data class TrainingInsightRow(
    val category: String,
    val workoutCount: Int,
    val summary: String,
    val strength: TrainingMetricShift,
    val density: TrainingMetricShift,
    val setDepth: TrainingMetricShift,
    val exerciseCount: TrainingMetricShift,
    val reps: TrainingMetricShift,
    val duration: TrainingMetricShift,
)

private data class WorkoutStrategyMetrics(
    val timestamp: Long,
    val category: String,
    val durationMinutes: Float,
    val exerciseCount: Int,
    val setCount: Int,
    val totalReps: Int,
    val loadedReps: Int,
    val totalLoad: Float,
) {
    val avgWorkingWeight: Float = if (loadedReps > 0) totalLoad / loadedReps else 0f
    val setsPerMinute: Float = if (durationMinutes > 0f) setCount / durationMinutes else 0f
    val avgSetsPerExercise: Float = if (exerciseCount > 0) setCount.toFloat() / exerciseCount else 0f
}

private data class WorkoutStrategySummary(
    val avgDurationMinutes: Float,
    val avgExerciseCount: Float,
    val avgSetCount: Float,
    val avgReps: Float,
    val avgWorkingWeight: Float,
    val avgSetsPerMinute: Float,
    val avgSetsPerExercise: Float,
)

private val setLineStartRegex = Regex("(?i)^[\\d+]+\\s*x.*")
private val setLinePrefixRegex = Regex("(?i)^x\\s*[\\d+]+.*")
private val repsRegex = Regex("(?i)(?:x\\s*([0-9+]+)|([0-9+]+)\\s*x|([0-9+]+)\\s*reps?)")

fun buildWeeklyWorkoutCounts(notes: List<NoteLine>): List<WeeklyWorkoutCount> {
    return notes
        .groupBy { isoWeekStart(it.timestamp) }
        .toSortedMap()
        .map { (weekStart, weekNotes) ->
            WeeklyWorkoutCount(weekStart = weekStart, count = weekNotes.size)
        }
}

fun buildTrainingInsights(
    notes: List<NoteLine>,
    parser: WorkoutParser = WorkoutParser(),
    weightUnitLabel: String = "kg",
    maxCategories: Int = 5,
): List<TrainingInsightRow> {
    val workouts = notes
        .mapNotNull { note -> note.toWorkoutStrategyMetrics(parser) }
        .sortedBy { it.timestamp }

    if (workouts.size < 2) return emptyList()

    val allWorkouts = buildTrainingInsightRow(
        category = "All workouts",
        workouts = workouts,
        weightUnitLabel = weightUnitLabel,
    )

    val categoryRows = workouts
        .groupBy { it.category }
        .toList()
        .sortedWith(compareByDescending<Pair<String, List<WorkoutStrategyMetrics>>> { it.second.size }.thenBy { it.first })
        .mapNotNull { (category, categoryWorkouts) ->
            buildTrainingInsightRow(
                category = category,
                workouts = categoryWorkouts.sortedBy { it.timestamp },
                weightUnitLabel = weightUnitLabel,
            )
        }
        .take(maxCategories)

    return listOfNotNull(allWorkouts) + categoryRows
}

private fun NoteLine.toWorkoutStrategyMetrics(parser: WorkoutParser): WorkoutStrategyMetrics? {
    val parsedText = parseNoteText(text, rowMetadata)
    val visibleLines = parsedText.first
    val absoluteTimes = parsedText.second
    val durationMinutes = absoluteTimes
        .mapNotNull { parseDurationSeconds(it) }
        .maxOrNull()
        ?.div(60f)
        ?: 0f

    val parsedSets = parser.parseWorkout(text, rowMetadata = rowMetadata)
    val exerciseCount = visibleLines.count { line ->
        val clean = line.trim()
        clean.isNotBlank() && !looksLikeSetLine(line)
    }
    val setCount = parsedSets.size
    if (setCount == 0 && exerciseCount == 0) return null

    val totalReps = parsedSets.sumOf { it.reps }
    val loadedSets = parsedSets.filter { it.weight > 0f && it.reps > 0 }
    val totalLoad = loadedSets.sumOf { (it.weight * it.reps).toDouble() }.toFloat()
    val loadedReps = loadedSets.sumOf { it.reps }

    return WorkoutStrategyMetrics(
        timestamp = timestamp,
        category = categoryName?.trim()?.takeIf { it.isNotEmpty() } ?: "Other",
        durationMinutes = durationMinutes,
        exerciseCount = exerciseCount,
        setCount = setCount,
        totalReps = totalReps,
        loadedReps = loadedReps,
        totalLoad = totalLoad,
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

private fun buildTrainingInsightRow(
    category: String,
    workouts: List<WorkoutStrategyMetrics>,
    weightUnitLabel: String,
): TrainingInsightRow? {
    if (workouts.size < 2) return null

    val splitIndex = (workouts.size / 2).coerceAtLeast(1)
    val baseline = summarizeStrategy(workouts.take(splitIndex))
    val recent = summarizeStrategy(workouts.drop(splitIndex).ifEmpty { workouts.takeLast(1) })

    val strength = TrainingMetricShift(
        label = "Strength",
        baseline = baseline.avgWorkingWeight,
        recent = recent.avgWorkingWeight,
        unit = weightUnitLabel,
    )
    val density = TrainingMetricShift(
        label = "Density",
        baseline = baseline.avgSetsPerMinute,
        recent = recent.avgSetsPerMinute,
        unit = "sets/min",
    )
    val setDepth = TrainingMetricShift(
        label = "Depth",
        baseline = baseline.avgSetsPerExercise,
        recent = recent.avgSetsPerExercise,
        unit = "sets/ex",
    )
    val exerciseCount = TrainingMetricShift(
        label = "Exercises",
        baseline = baseline.avgExerciseCount,
        recent = recent.avgExerciseCount,
        unit = "ex/workout",
        higherIsUsuallyGood = false,
    )
    val reps = TrainingMetricShift(
        label = "Reps",
        baseline = baseline.avgReps,
        recent = recent.avgReps,
        unit = "reps/workout",
    )
    val duration = TrainingMetricShift(
        label = "Time",
        baseline = baseline.avgDurationMinutes,
        recent = recent.avgDurationMinutes,
        unit = "min",
        higherIsUsuallyGood = false,
    )

    return TrainingInsightRow(
        category = category,
        workoutCount = workouts.size,
        summary = summarizeDirection(strength, density, setDepth, exerciseCount, duration),
        strength = strength,
        density = density,
        setDepth = setDepth,
        exerciseCount = exerciseCount,
        reps = reps,
        duration = duration,
    )
}

private fun summarizeStrategy(workouts: List<WorkoutStrategyMetrics>): WorkoutStrategySummary {
    fun averageOf(selector: (WorkoutStrategyMetrics) -> Float): Float {
        return if (workouts.isEmpty()) 0f else workouts.map(selector).average().toFloat()
    }

    return WorkoutStrategySummary(
        avgDurationMinutes = averageOf { it.durationMinutes },
        avgExerciseCount = averageOf { it.exerciseCount.toFloat() },
        avgSetCount = averageOf { it.setCount.toFloat() },
        avgReps = averageOf { it.totalReps.toFloat() },
        avgWorkingWeight = averageOf { it.avgWorkingWeight },
        avgSetsPerMinute = averageOf { it.setsPerMinute },
        avgSetsPerExercise = averageOf { it.avgSetsPerExercise },
    )
}

private fun summarizeDirection(
    strength: TrainingMetricShift,
    density: TrainingMetricShift,
    setDepth: TrainingMetricShift,
    exerciseCount: TrainingMetricShift,
    duration: TrainingMetricShift,
): String {
    val strengthUp = strength.isMeaningfullyUp()
    val strengthDown = strength.isMeaningfullyDown()
    val densityUp = density.isMeaningfullyUp()
    val densityDown = density.isMeaningfullyDown()
    val depthUp = setDepth.isMeaningfullyUp()
    val exercisesDown = exerciseCount.isMeaningfullyDown()
    val exercisesUp = exerciseCount.isMeaningfullyUp()
    val durationUp = duration.isMeaningfullyUp()
    val durationDown = duration.isMeaningfullyDown()

    return when {
        strengthUp && (densityUp || durationDown) -> "Progressing with better output per minute."
        strengthUp -> "Strength is moving up; watch whether time cost rises too."
        strengthDown && densityUp -> "Faster sessions, but load may be dropping."
        densityDown && durationUp -> "Potential time sink: longer sessions without better density."
        depthUp && exercisesDown -> "More focused work: fewer exercises, more depth."
        exercisesUp && !depthUp -> "More spread out: more exercises without more depth."
        densityUp -> "Pacing improved; check if strength follows."
        else -> "Mostly stable strategy; inspect key exercises for specific progress."
    }
}

private fun TrainingMetricShift.isMeaningfullyUp(): Boolean {
    val change = percentChange
    return if (change != null) change >= 5f else absoluteChange > 0f
}

private fun TrainingMetricShift.isMeaningfullyDown(): Boolean {
    val change = percentChange
    return if (change != null) change <= -5f else absoluteChange < 0f
}
