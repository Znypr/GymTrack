package com.example.gymtrack.feature.stats

import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.util.ExerciseIdentity
import com.example.gymtrack.core.util.ExerciseIdentityResolver
import com.example.gymtrack.core.util.ParsedSetDTO

/** One chart value at one workout. */
data class StatsPoint(val timestamp: Long, val value: Float)

/** What a timed-exercise chart shows (docs/STATS_RULES.md, "Timed sets"). */
enum class TimedProgressMetric {
    /** Average hold per timed set, in seconds. */
    HOLD_SECONDS,

    /** Average load × seconds per timed set, in display-unit·s. Only when every timed set has a known load. */
    LOAD_SECONDS,
}

data class TimedProgressSeries(
    /** Same key as the progress chart's variant ([ExerciseIdentity.progressComparisonKey]). */
    val key: String,
    val points: List<StatsPoint>,
)

/** Timed sets of one exercise (all its variants), charted in one honest unit. */
data class TimedProgressGroup(
    /** [ExerciseIdentity.baseComparisonKey]. */
    val baseKey: String,
    val name: String,
    val metric: TimedProgressMetric,
    /** Axis unit: `s`, or `kg·s` / `lb·s` for [TimedProgressMetric.LOAD_SECONDS]. */
    val unitLabel: String,
    val series: List<TimedProgressSeries>,
    val timedSetCount: Int,
) {
    val axisTitle: String
        get() = when (metric) {
            TimedProgressMetric.HOLD_SECONDS -> "Avg hold per timed set · s"
            TimedProgressMetric.LOAD_SECONDS -> "Avg load × time per timed set · $unitLabel"
        }
}

/** Timed sets in the counted workouts. */
data class TimedSetSummary(
    val setCount: Int = 0,
    val totalSeconds: Int = 0,
)

val ParsedSetDTO.isTimed: Boolean get() = (durationSeconds ?: 0) > 0

fun buildTimedSetSummary(digests: List<StatsNoteDigest>): TimedSetSummary {
    var count = 0
    var seconds = 0
    digests.forEach { digest ->
        digest.sets.forEach { resolved ->
            val duration = resolved.set.durationSeconds
            if (duration != null && duration > 0) {
                count++
                seconds += duration
            }
        }
    }
    return TimedSetSummary(count, seconds)
}

private data class IdentityInput(
    val name: String,
    val modifier: String?,
    val brand: String?,
    val isUnilateral: Boolean,
)

private class TimedObservation(
    val timestamp: Long,
    val identity: ExerciseIdentity,
    val seconds: Int,
    /** Load × seconds in the display unit; null when the set has no load or its unit is unknown. */
    val loadSeconds: Double?,
)

/**
 * Progress series for timed sets. Exercise identity is resolved the way the progress chart
 * resolves its stored rows (`WorkoutRepository.identityFor`), so the series keys line up with
 * the chart's variants.
 */
fun buildTimedProgress(
    digests: List<StatsNoteDigest>,
    displayUnit: WeightUnit,
): List<TimedProgressGroup> {
    val identities = HashMap<IdentityInput, ExerciseIdentity>()
    val observations = ArrayList<TimedObservation>()
    digests.forEach { digest ->
        digest.sets.forEach { resolved ->
            val set = resolved.set
            val seconds = set.durationSeconds ?: return@forEach
            if (seconds <= 0) return@forEach
            val storedName = set.exerciseIdentity.canonicalName.trim().ifBlank { set.exerciseName.trim() }
            val input = IdentityInput(storedName, set.modifier, set.brand, set.isUnilateral)
            val identity = identities.getOrPut(input) {
                ExerciseIdentityResolver.resolve(
                    rawName = storedName,
                    parsedName = storedName,
                    modifier = set.modifier,
                    brand = set.brand,
                    isUnilateral = set.isUnilateral,
                )
            }
            val load = resolved.unit
                ?.takeIf { set.weight > 0f }
                ?.let { unit -> convertWeight(set.weight.toDouble(), unit, displayUnit) * seconds }
            observations += TimedObservation(digest.timestamp, identity, seconds, load)
        }
    }

    return observations
        .groupBy { it.identity.baseComparisonKey }
        .map { (baseKey, group) ->
            // One unit per chart: load × time only when every timed set has a known load.
            val metric = if (group.all { it.loadSeconds != null }) {
                TimedProgressMetric.LOAD_SECONDS
            } else {
                TimedProgressMetric.HOLD_SECONDS
            }
            val series = group
                .groupBy { it.identity.progressComparisonKey }
                .map { (key, variantObservations) ->
                    TimedProgressSeries(
                        key = key,
                        points = variantObservations
                            .groupBy { it.timestamp }
                            .map { (timestamp, sets) ->
                                val value = when (metric) {
                                    TimedProgressMetric.HOLD_SECONDS -> sets.map { it.seconds.toDouble() }.average()
                                    TimedProgressMetric.LOAD_SECONDS -> sets.map { it.loadSeconds!! }.average()
                                }
                                StatsPoint(timestamp, value.toFloat())
                            }
                            .sortedBy { it.timestamp },
                    )
                }
                .sortedBy { it.key }
            TimedProgressGroup(
                baseKey = baseKey,
                name = group.groupingBy { it.identity.canonicalName }.eachCount().maxBy { it.value }.key,
                metric = metric,
                unitLabel = when (metric) {
                    TimedProgressMetric.HOLD_SECONDS -> "s"
                    TimedProgressMetric.LOAD_SECONDS -> "${displayUnit.displayLabel}·s"
                },
                series = series,
                timedSetCount = group.size,
            )
        }
        .sortedWith(compareByDescending<TimedProgressGroup> { it.timedSetCount }.thenBy { it.name })
}
