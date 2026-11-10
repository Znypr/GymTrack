package com.example.gymtrack.feature.stats

import com.example.gymtrack.core.data.GraphPoint
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.util.noteWorkoutDuration
import java.util.Calendar

const val MAX_REASONABLE_WORKOUT_DURATION_MINUTES = 300f
private const val MIN_EXERCISE_POINTS_FOR_ANOMALIES = 8
private const val MIN_IQR_FOR_ANOMALIES = 2f
private const val IQR_ANOMALY_MULTIPLIER = 2.5f

data class WorkoutDurationSample(
    val timestamp: Long,
    val minutes: Float,
)

fun calculateExerciseOutliers(data: List<GraphPoint>): List<GraphPoint> {
    if (data.size < MIN_EXERCISE_POINTS_FOR_ANOMALIES) return emptyList()

    val values = data.map { it.avgVal }.sorted()
    val q1 = percentile(values, 0.25f)
    val q3 = percentile(values, 0.75f)
    val iqr = q3 - q1
    if (iqr < MIN_IQR_FOR_ANOMALIES) return emptyList()

    val lower = q1 - IQR_ANOMALY_MULTIPLIER * iqr
    val upper = q3 + IQR_ANOMALY_MULTIPLIER * iqr
    return data.filter { it.avgVal < lower || it.avgVal > upper }
}

fun extractReasonableWorkoutDurationMinutes(note: NoteLine): WorkoutDurationSample? {
    val minutes = extractWorkoutDurationMinutes(note) ?: return null
    if (!isReasonableWorkoutDuration(minutes)) return null
    return WorkoutDurationSample(timestamp = note.timestamp, minutes = minutes)
}

fun countUnreasonableWorkoutDurations(notes: List<NoteLine>): Int {
    return notes.count { note ->
        val minutes = extractWorkoutDurationMinutes(note)
        minutes != null && !isReasonableWorkoutDuration(minutes)
    }
}

fun buildWeeklyAverageWorkoutDurations(notes: List<NoteLine>): List<Pair<Long, Float>> {
    return notes
        .mapNotNull(::extractReasonableWorkoutDurationMinutes)
        .groupBy { isoWeekStart(it.timestamp) }
        .toSortedMap()
        .map { (weekStart, samples) ->
            weekStart to samples.map { it.minutes }.average().toFloat()
        }
}

/** [countUnreasonableWorkoutDurations] for digested notes. */
fun countUnreasonableWorkoutDurationsFromDigests(digests: List<StatsNoteDigest>): Int =
    digests.count { digest ->
        val minutes = digest.workoutDurationMinutes
        minutes != null && !isReasonableWorkoutDuration(minutes)
    }

/** [buildWeeklyAverageWorkoutDurations] for digested notes (no re-parse on the UI thread, #303). */
fun buildWeeklyAverageWorkoutDurationsFromDigests(digests: List<StatsNoteDigest>): List<Pair<Long, Float>> =
    digests
        .mapNotNull { digest ->
            digest.workoutDurationMinutes
                ?.takeIf(::isReasonableWorkoutDuration)
                ?.let { WorkoutDurationSample(digest.timestamp, it) }
        }
        .groupBy { isoWeekStart(it.timestamp) }
        .toSortedMap()
        .map { (weekStart, samples) -> weekStart to samples.map { it.minutes }.average().toFloat() }

fun isoWeekStart(timestamp: Long): Long {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = timestamp
        firstDayOfWeek = Calendar.MONDAY
        minimalDaysInFirstWeek = 4
    }
    val dayOffsetFromMonday = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    calendar.add(Calendar.DAY_OF_YEAR, -dayOffsetFromMonday)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

/** Same duration rule as the Home card (docs/WORKOUT_DURATION.md): ends before a pause > 3 h. */
private fun extractWorkoutDurationMinutes(note: NoteLine): Float? {
    return noteWorkoutDuration(note.text, note.rowMetadata, note.timestamp)
        ?.seconds
        ?.div(60f)
}

private fun isReasonableWorkoutDuration(minutes: Float): Boolean {
    return minutes > 0f && minutes <= MAX_REASONABLE_WORKOUT_DURATION_MINUTES
}

private fun percentile(sortedValues: List<Float>, percentile: Float): Float {
    if (sortedValues.isEmpty()) return 0f
    val clamped = percentile.coerceIn(0f, 1f)
    val index = ((sortedValues.lastIndex) * clamped).toInt()
    return sortedValues[index]
}
