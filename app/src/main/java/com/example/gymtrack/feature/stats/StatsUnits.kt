package com.example.gymtrack.feature.stats

import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.util.ParsedSetDTO
import com.example.gymtrack.core.util.WorkoutParser

/** Exact international pound. */
const val KG_PER_LB: Double = 0.45359237

/** Converts [value] from [from] to [to]. Same unit returns the value untouched. */
fun convertWeight(value: Double, from: WeightUnit, to: WeightUnit): Double = when {
    from == to -> value
    from == WeightUnit.LB -> value * KG_PER_LB
    else -> value / KG_PER_LB
}

/** Progress SQL returns kilograms (see `SetDao`); this turns them into the display unit. */
fun kilogramsToDisplay(kilograms: Float, display: WeightUnit): Float =
    convertWeight(kilograms.toDouble(), WeightUnit.KG, display).toFloat()

/**
 * What Stats needs to know about saved workouts beyond the note text (docs/STATS_RULES.md).
 *
 * - [completedTimestamps]: workouts whose canonical row is `COMPLETED` (finished with Finish).
 * - [storedSetUnits]: per workout, the unit of every legacy `sets` row in insertion order. These
 *   rows are written only when a workout is finished (or by the legacy repair for old databases),
 *   with the default unit that was in force at that moment, so they are the record of which unit a
 *   keypad set (no unit in the text) was typed in.
 */
data class StatsWorkoutFacts(
    val completedTimestamps: Set<Long> = emptySet(),
    val storedSetUnits: Map<Long, List<WeightUnit>> = emptyMap(),
) {
    /** A workout counts as finished when it was completed or has derived set rows. */
    fun isFinished(timestamp: Long): Boolean =
        timestamp in completedTimestamps || timestamp in storedSetUnits

    /**
     * Content signature for the Stats cache key. It must be identical in every process: enum
     * `hashCode()` is an identity hash that changes on each app start, so units are hashed by
     * [WeightUnit.ordinal] (#303: the persisted cache missed after every restart).
     */
    fun signature(): Long {
        var result = completedTimestamps.size.toLong()
        completedTimestamps.sorted().forEach { result = 31 * result + it }
        storedSetUnits.toSortedMap().forEach { (timestamp, units) ->
            result = 31 * result + timestamp
            result = 31 * result + units.size
            units.forEach { unit -> result = 31 * result + unit.ordinal + 1 }
        }
        return result
    }

    companion object {
        fun fromRows(
            completedTimestamps: List<Long>,
            storedUnits: List<Pair<Long, String>>,
        ): StatsWorkoutFacts = StatsWorkoutFacts(
            completedTimestamps = completedTimestamps.toSet(),
            storedSetUnits = storedUnits
                .groupBy({ it.first }, { WeightUnit.fromStorage(it.second) }),
        )
    }
}

/** A parsed set with the unit it was really typed in, or `null` when that cannot be known. */
data class UnitResolvedSet(val set: ParsedSetDTO, val unit: WeightUnit?)

/**
 * Parses [note] and gives every set the unit it was entered in:
 *
 * 1. Stored rows line up with the parsed sets: use the stored unit of each row.
 * 2. Otherwise, if all stored rows share one unit, parse with that unit as the default, so
 *    explicit units in the text still win.
 * 3. Otherwise (no stored rows, or mixed rows that do not line up): units written in the text are
 *    known; sets without one are `null` (unknown) and must not be converted or summed.
 */
fun resolveSetUnits(
    note: NoteLine,
    parser: WorkoutParser,
    storedUnits: List<WeightUnit>?,
): List<UnitResolvedSet> {
    val singleStored = storedUnits?.distinct()?.singleOrNull()
    val primary = parser.parseWorkout(
        note.text,
        (singleStored ?: WeightUnit.KG).storageValue,
        note.rowMetadata,
    )
    if (storedUnits != null && storedUnits.size == primary.size) {
        return primary.zip(storedUnits) { set, unit -> UnitResolvedSet(set, unit) }
    }
    if (singleStored != null) {
        return primary.map { UnitResolvedSet(it, WeightUnit.fromStorage(it.weightUnit)) }
    }
    val alternate = parser.parseWorkout(note.text, WeightUnit.LB.storageValue, note.rowMetadata)
    return primary.mapIndexed { index, set ->
        val other = alternate.getOrNull(index)
        val explicit = other != null && other.weightUnit == set.weightUnit && alternate.size == primary.size
        UnitResolvedSet(set, if (explicit) WeightUnit.fromStorage(set.weightUnit) else null)
    }
}
