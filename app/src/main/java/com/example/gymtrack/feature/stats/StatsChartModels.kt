package com.example.gymtrack.feature.stats

import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.util.WorkoutParser
import com.example.gymtrack.core.util.parseDurationSeconds
import com.example.gymtrack.core.util.parseNoteText

data class WeeklyWorkoutCount(
    val weekStart: Long,
    val count: Int,
)

enum class MetricImpact {
    POSITIVE,
    NEGATIVE,
    NEUTRAL,
}

data class TrainingMetricShift(
    val label: String,
    val baseline: Float,
    val recent: Float,
    val unit: String,
    val higherIsUsuallyGood: Boolean = true,
    val impact: MetricImpact = MetricImpact.NEUTRAL,
) {
    val absoluteChange: Float = recent - baseline
    val percentChange: Float? = if (baseline > 0f) ((recent - baseline) / baseline) * 100f else null
}

data class TrainingTrendPoint(
    val timestamp: Long,
    val strengthPercent: Float,
    val densityPercent: Float,
    val depthPercent: Float,
    val exerciseCountPercent: Float,
    val repsPercent: Float,
    val durationPercent: Float,
)

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
    val trendPoints: List<TrainingTrendPoint>,
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

private data class MetricDirectionContext(
    val strengthUp: Boolean,
    val strengthDown: Boolean,
    val densityUp: Boolean,
    val densityDown: Boolean,
    val depthUp: Boolean,
    val depthDown: Boolean,
    val exercisesUp: Boolean,
    val exercisesDown: Boolean,
    val repsUp: Boolean,
    val repsDown: Boolean,
    val durationUp: Boolean,
    val durationDown: Boolean,
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

    val rawStrength = TrainingMetricShift(
        label = "Strength",
        baseline = baseline.avgWorkingWeight,
        recent = recent.avgWorkingWeight,
        unit = weightUnitLabel,
    )
    val rawDensity = TrainingMetricShift(
        label = "Density",
        baseline = baseline.avgSetsPerMinute,
        recent = recent.avgSetsPerMinute,
        unit = "sets/min",
    )
    val rawSetDepth = TrainingMetricShift(
        label = "Depth",
        baseline = baseline.avgSetsPerExercise,
        recent = recent.avgSetsPerExercise,
        unit = "sets/ex",
    )
    val rawExerciseCount = TrainingMetricShift(
        label = "Exercises",
        baseline = baseline.avgExerciseCount,
        recent = recent.avgExerciseCount,
        unit = "ex/workout",
        higherIsUsuallyGood = false,
    )
    val rawReps = TrainingMetricShift(
        label = "Reps",
        baseline = baseline.avgReps,
        recent = recent.avgReps,
        unit = "reps/workout",
    )
    val rawDuration = TrainingMetricShift(
        label = "Time",
        baseline = baseline.avgDurationMinutes,
        recent = recent.avgDurationMinutes,
        unit = "min",
        higherIsUsuallyGood = false,
    )

    val context = MetricDirectionContext(
        strengthUp = rawStrength.isMeaningfullyUp(),
        strengthDown = rawStrength.isMeaningfullyDown(),
        densityUp = rawDensity.isMeaningfullyUp(),
        densityDown = rawDensity.isMeaningfullyDown(),
        depthUp = rawSetDepth.isMeaningfullyUp(),
        depthDown = rawSetDepth.isMeaningfullyDown(),
        exercisesUp = rawExerciseCount.isMeaningfullyUp(),
        exercisesDown = rawExerciseCount.isMeaningfullyDown(),
        repsUp = rawReps.isMeaningfullyUp(),
        repsDown = rawReps.isMeaningfullyDown(),
        durationUp = rawDuration.isMeaningfullyUp(),
        durationDown = rawDuration.isMeaningfullyDown(),
    )

    val strength = rawStrength.copy(impact = impactForStrength(context))
    val density = rawDensity.copy(impact = impactForDensity(context))
    val setDepth = rawSetDepth.copy(impact = impactForDepth(context))
    val exerciseCount = rawExerciseCount.copy(impact = impactForExerciseCount(context))
    val reps = rawReps.copy(impact = impactForReps(context))
    val duration = rawDuration.copy(impact = impactForDuration(context))

    return TrainingInsightRow(
        category = category,
        workoutCount = workouts.size,
        summary = summarizeDirection(context),
        strength = strength,
        density = density,
        setDepth = setDepth,
        exerciseCount = exerciseCount,
        reps = reps,
        duration = duration,
        trendPoints = buildTrendPoints(workouts),
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

private fun summarizeDirection(context: MetricDirectionContext): String {
    return when {
        context.strengthUp && (context.densityUp || context.durationDown) -> "Progressing with better output per minute."
        context.strengthUp -> "Strength is moving up; watch whether time cost rises too."
        context.strengthDown && context.densityUp -> "Faster sessions, but load may be dropping."
        context.densityDown && context.durationUp -> "Potential time sink: longer sessions without better density."
        context.depthUp && context.exercisesDown -> "More focused work: fewer exercises, more depth."
        context.exercisesUp && !context.depthUp -> "More spread out: more exercises without more depth."
        context.densityUp -> "Pacing improved; check if strength follows."
        else -> "Mostly stable strategy; inspect key exercises for specific progress."
    }
}

private fun impactForStrength(context: MetricDirectionContext): MetricImpact = when {
    context.strengthUp -> MetricImpact.POSITIVE
    context.strengthDown -> MetricImpact.NEGATIVE
    else -> MetricImpact.NEUTRAL
}

private fun impactForDensity(context: MetricDirectionContext): MetricImpact = when {
    context.densityUp -> MetricImpact.POSITIVE
    context.densityDown -> MetricImpact.NEGATIVE
    else -> MetricImpact.NEUTRAL
}

private fun impactForDepth(context: MetricDirectionContext): MetricImpact = when {
    context.depthUp -> MetricImpact.POSITIVE
    context.depthDown && (context.strengthUp || context.densityUp) -> MetricImpact.NEUTRAL
    context.depthDown -> MetricImpact.NEGATIVE
    else -> MetricImpact.NEUTRAL
}

private fun impactForExerciseCount(context: MetricDirectionContext): MetricImpact = when {
    context.exercisesDown && (context.strengthUp || context.densityUp || context.depthUp) -> MetricImpact.POSITIVE
    context.exercisesUp && !context.depthUp && !context.densityUp -> MetricImpact.NEGATIVE
    else -> MetricImpact.NEUTRAL
}

private fun impactForReps(context: MetricDirectionContext): MetricImpact = when {
    context.repsUp && !context.strengthDown -> MetricImpact.POSITIVE
    context.repsDown && context.strengthUp && (context.densityUp || context.durationDown || context.depthUp) -> MetricImpact.POSITIVE
    context.repsDown && context.strengthDown -> MetricImpact.NEGATIVE
    context.repsDown -> MetricImpact.NEUTRAL
    else -> MetricImpact.NEUTRAL
}

private fun impactForDuration(context: MetricDirectionContext): MetricImpact = when {
    context.durationDown && (context.strengthUp || context.densityUp || context.depthUp) -> MetricImpact.POSITIVE
    context.durationUp && (context.densityDown || context.strengthDown) -> MetricImpact.NEGATIVE
    context.durationUp -> MetricImpact.NEUTRAL
    else -> MetricImpact.NEUTRAL
}

private fun buildTrendPoints(workouts: List<WorkoutStrategyMetrics>): List<TrainingTrendPoint> {
    val strengthReference = workouts.firstPositiveOrNull { it.avgWorkingWeight }
    val densityReference = workouts.firstPositiveOrNull { it.setsPerMinute }
    val depthReference = workouts.firstPositiveOrNull { it.avgSetsPerExercise }
    val exerciseReference = workouts.firstPositiveOrNull { it.exerciseCount.toFloat() }
    val repsReference = workouts.firstPositiveOrNull { it.totalReps.toFloat() }
    val durationReference = workouts.firstPositiveOrNull { it.durationMinutes }

    return workouts.map { workout ->
        TrainingTrendPoint(
            timestamp = workout.timestamp,
            strengthPercent = percentChangeFrom(strengthReference, workout.avgWorkingWeight),
            densityPercent = percentChangeFrom(densityReference, workout.setsPerMinute),
            depthPercent = percentChangeFrom(depthReference, workout.avgSetsPerExercise),
            exerciseCountPercent = percentChangeFrom(exerciseReference, workout.exerciseCount.toFloat()),
            repsPercent = percentChangeFrom(repsReference, workout.totalReps.toFloat()),
            durationPercent = percentChangeFrom(durationReference, workout.durationMinutes),
        )
    }
}

private fun List<WorkoutStrategyMetrics>.firstPositiveOrNull(selector: (WorkoutStrategyMetrics) -> Float): Float? {
    return firstOrNull { selector(it) > 0f }?.let(selector)
}

private fun percentChangeFrom(reference: Float?, value: Float): Float {
    return if (reference != null && reference > 0f) ((value - reference) / reference) * 100f else 0f
}

private fun TrainingMetricShift.isMeaningfullyUp(): Boolean {
    val change = percentChange
    return if (change != null) change >= 5f else absoluteChange > 0f
}

private fun TrainingMetricShift.isMeaningfullyDown(): Boolean {
    val change = percentChange
    return if (change != null) change <= -5f else absoluteChange < 0f
}
