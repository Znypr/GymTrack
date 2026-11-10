package com.example.gymtrack.simulation

import com.example.gymtrack.core.data.CanonicalWorkoutEntity
import com.example.gymtrack.core.data.CanonicalWorkoutExerciseEntity
import com.example.gymtrack.core.data.CanonicalWorkoutSetEntity
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.util.ParsedSetDTO
import com.example.gymtrack.core.util.WorkoutParser
import com.example.gymtrack.core.util.parseDurationSeconds
import com.example.gymtrack.core.util.parseNoteText
import com.example.gymtrack.feature.stats.buildTrainingInsights
import com.example.gymtrack.feature.stats.buildWeeklyWorkoutCounts
import com.example.gymtrack.feature.stats.countUnreasonableWorkoutDurations
import com.example.gymtrack.feature.stats.isoWeekStart
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.IsoFields
import kotlin.math.abs

private const val MAX_PLAUSIBLE_WORKOUT_MILLIS = 4L * 60L * 60L * 1_000L
private const val KG_PER_LB = 0.45359237

/** Canonical rows of one workout, from the projector (JVM) or from Room (device). */
data class CanonicalRows(
    val workout: CanonicalWorkoutEntity?,
    val exercises: List<CanonicalWorkoutExerciseEntity>,
    val sets: List<CanonicalWorkoutSetEntity>,
)

/**
 * Invariants a user silently relies on, checked against generator truth after every simulated
 * day (per workout) and every simulated week (statistics). Violations become findings; nothing
 * here throws, so one run lists every problem with the day it first appeared.
 */
class HistoryInvariants(
    private val history: SimHistory,
    private val findings: SimulationFindings,
) {
    private val idsByKey = mutableMapOf<String, MutableMap<String, String>>() // key -> id -> first typed name
    private val keysById = mutableMapOf<String, MutableSet<String>>()

    fun checkFinishedWorkout(
        session: EditorSession,
        parsed: List<ParsedSetDTO>,
        canonical: CanonicalRows,
    ) {
        val w = session.workout
        val truth = session.truth
        fun report(id: String, severity: Severity, title: String, example: String, issue: String? = null) =
            findings.report(id, severity, title, history, w.day, w.index, example, issue)

        // Lost / duplicated / phantom sets.
        if (parsed.size != truth.size) {
            val kind = if (parsed.size > truth.size) "PHANTOM-SET" else "LOST-SET"
            val extra = if (EdgeTag.KILLED_MID_SET in w.tags) " (half-typed set at app kill)" else ""
            report(
                kind,
                if (parsed.size > truth.size) Severity.MEDIUM else Severity.HIGH,
                if (parsed.size > truth.size) "Saved workout has more sets than the user did$extra" else "A set the user entered is missing after save",
                "workout ${w.index} ${w.date}: user did ${truth.size} sets, app saved ${parsed.size}: " +
                    parsed.joinToString { "${it.reps}x${it.weight}" },
            )
        }

        truth.zip(parsed).forEachIndexed { i, (t, p) ->
            if (t.reps != p.reps || abs(t.weight - p.weight) > 0.001 || t.durationSeconds != p.durationSeconds) {
                report(
                    "SET-VALUE-MISMATCH", Severity.HIGH, "Saved reps/weight/duration differ from what was typed",
                    "workout ${w.index} set $i: typed ${t.reps}x${t.weight} (${t.durationSeconds}s), saved ${p.reps}x${p.weight} (${p.durationSeconds}s)",
                )
            }
            if (p.weightUnit != w.unit.storageValue && t.weight > 0.0) {
                report(
                    "SET-UNIT-MISMATCH", Severity.HIGH, "Set saved with a different unit than the default in use",
                    "workout ${w.index} set $i: default ${w.unit}, saved ${p.weightUnit}",
                )
            }
            val typed = w.exercises.first { it.key == t.exerciseKey }.typedName
            if (t.unilateral && !p.isUnilateral) {
                report(
                    "UNILATERAL-LOST", Severity.MEDIUM,
                    "Exercise flagged Uni, but its sets are saved bilateral (flag tapped after the exercise's Enter)",
                    "workout ${w.index} ${w.date} '$typed' set $i saved isUnilateral=false",
                )
            }
            if (!t.unilateral && p.isUnilateral) {
                report(
                    "UNILATERAL-INHERITED", Severity.MEDIUM,
                    "Sets saved unilateral because the new exercise row inherited Uni from the previous exercise",
                    "workout ${w.index} ${w.date} '$typed' (${w.exercises.first { it.key == t.exerciseKey }.flag}) set $i saved isUnilateral=true",
                )
            }
            // Exercise identity as the repository stores it (parser autocorrect + resolver).
            trackIdentity(t.exerciseKey, typed, p.exerciseIdentity.baseComparisonKey, w)
        }

        // Canonical tables agree with the note.
        val workout = canonical.workout
        if (workout == null) {
            report("CANONICAL-MISSING", Severity.HIGH, "Finished workout has no canonical row", "workout ${w.index} ${w.date}")
            return
        }
        if (workout.legacyMigrationStatus == "NEEDS_REVIEW") {
            report(
                "CANONICAL-NEEDS-REVIEW", Severity.LOW, "Normal workout lands as NEEDS_REVIEW in canonical tables",
                "workout ${w.index}: ${workout.legacyMigrationMessage}",
            )
        }
        if (canonical.sets.size != parsed.size) {
            report(
                "CANONICAL-SET-COUNT", Severity.HIGH, "Canonical set count differs from parsed legacy sets",
                "workout ${w.index}: canonical ${canonical.sets.size}, legacy ${parsed.size}",
            )
        }
        val offsets = canonical.exercises.sortedBy { it.position }.flatMap { ex ->
            canonical.sets.filter { it.workoutExerciseId == ex.id }.sortedBy { it.position }
        }.map { it.performedAtOffsetSeconds }
        if (offsets.zipWithNext().any { (a, b) -> a != null && b != null && b < a }) {
            report("OFFSETS-NOT-MONOTONIC", Severity.MEDIUM, "Set times go backwards within a workout", "workout ${w.index}: $offsets")
        }
        truth.zip(offsets).firstOrNull { (t, offset) ->
            offset != null && abs(workout.startedAt + offset * 1_000L - t.enteredAtMillis) > 5_000L
        }?.let { (t, offset) ->
            val drift = (workout.startedAt + offset!! * 1_000L - t.enteredAtMillis) / 1_000L
            report(
                "SET-TIME-SHIFTED", Severity.MEDIUM,
                "Canonical set time (started_at + offset) differs from when the set was entered",
                "workout ${w.index} ${w.date} tags=${w.tags}: shifted by ${drift}s",
            )
        }
        val ended = workout.endedAt
        if (ended != null) {
            val duration = ended - workout.startedAt
            if (duration <= 0L || duration > MAX_PLAUSIBLE_WORKOUT_MILLIS) {
                report(
                    "IMPLAUSIBLE-DURATION", Severity.MEDIUM, "Workout duration is negative or longer than 4 h",
                    "workout ${w.index} ${w.date} tags=${w.tags}: ${duration / 60_000L} min",
                )
            }
            val finished = session.finishedAtMillis
            // A workout resumed after a long pause (lateSet) ends before the pause by design
            // (docs/WORKOUT_DURATION.md), so its Finish next morning is not the end.
            if (finished != null && w.lateSet == null && abs(finished - ended) > 4L * 60_000L && duration <= MAX_PLAUSIBLE_WORKOUT_MILLIS) {
                report(
                    "DURATION-ENDS-AT-LAST-SET", Severity.LOW,
                    "Saved duration ends at the last set, not at Finish (card shows less than the timer)",
                    "workout ${w.index}: ${(finished - ended) / 60_000L} min between last set and Finish",
                )
            }
        }
        val explicitUnits = canonical.sets.filter { (it.weight ?: 0.0) > 0.0 }.map { it.weightUnit }.distinct()
        if (explicitUnits.any { it == null }) {
            report(
                "CANONICAL-UNIT-NULL", Severity.MEDIUM,
                "Keypad sets store weight_unit = NULL in workout_sets (docs/WEIGHT_UNITS.md requires the unit used)",
                "workout ${w.index} (${w.unit}): units ${explicitUnits}",
            )
        }
    }

    private fun trackIdentity(key: String, typed: String, id: String, w: SimWorkout) {
        val ids = idsByKey.getOrPut(key) { linkedMapOf() }
        if (id !in ids) {
            ids[id] = typed
            if (ids.size > 1) {
                findings.report(
                    "ALIAS-SPLIT-$key", Severity.MEDIUM,
                    "Aliases of one exercise land in separate progress groups",
                    history, w.day, w.index,
                    "'${typed}' -> $id, but earlier ${ids.entries.first().let { "'${it.value}' -> ${it.key}" }}",
                )
            }
        }
        val keys = keysById.getOrPut(id) { linkedSetOf() }
        if (keys.add(key) && keys.size > 1) {
            findings.report(
                "ALIAS-MERGE-$id", Severity.MEDIUM, "Different exercises merged into one identity",
                history, w.day, w.index, "id $id covers $keys (latest typed '$typed')",
            )
        }
    }

    /** Weekly statistics invariants over everything the app holds at [day]. */
    fun checkStats(day: Int, notes: List<NoteLine>, sessions: List<EditorSession>) {
        if (notes.isEmpty()) return
        fun report(id: String, severity: Severity, title: String, example: String, issue: String? = null) =
            findings.report(id, severity, title, history, day, null, example, issue)

        val unfinished = sessions.count { !it.isFinished }
        val weekly = buildWeeklyWorkoutCounts(notes)
        if (weekly.sumOf { it.count } != notes.size) {
            report("WEEKLY-COUNT-SUM", Severity.HIGH, "Weekly workout counts do not add up to the workout count", "${weekly.sumOf { it.count }} vs ${notes.size}")
        }
        if (unfinished > 0) {
            report(
                "STATS-COUNTS-UNFINISHED", Severity.LOW,
                "Stats 'Workouts' counts workouts that were never finished",
                "$unfinished unfinished of ${notes.size}",
            )
        }

        // Week attribution in the zone where the workout happened vs the phone's current zone.
        sessions.forEach { s ->
            val localWeek = Instant.ofEpochMilli(s.workout.startMillis).atZone(s.workout.zoneId).let {
                it.get(IsoFields.WEEK_BASED_YEAR) * 100 + it.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            }
            val appWeek = Instant.ofEpochMilli(isoWeekStart(s.workout.startMillis)).atZone(ZoneId.systemDefault()).let {
                it.get(IsoFields.WEEK_BASED_YEAR) * 100 + it.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            }
            if (localWeek != appWeek) {
                report(
                    "WEEK-SHIFTED-BY-TIMEZONE", Severity.LOW, "Workout counted in a different week than the one it was done in",
                    "workout ${s.workout.index} ${s.workout.date} in ${s.workout.zoneId}: local week $localWeek, Stats week $appWeek",
                )
            }
        }

        val unreasonable = countUnreasonableWorkoutDurations(notes)
        if (unreasonable > 0) {
            report(
                "STATS-DROPS-LONG-WORKOUTS", Severity.LOW,
                "Stats silently drops workouts whose saved duration looks unreasonable",
                "$unreasonable workout(s) excluded from duration charts",
            )
        }

        // Volume and progress across the kg -> lb switch.
        val parser = WorkoutParser()
        val truthKg = sessions.flatMap { s -> s.truth.map { t -> t.weight * t.reps * if (s.workout.unit == WeightUnit.LB) KG_PER_LB else 1.0 } }.sum()
        val appMixed = sessions.flatMap { s -> parser.parseWorkout(s.finalNote.text, s.workout.unit.storageValue, s.finalNote.rowMetadata) }
            .sumOf { (it.weight * it.reps).toDouble() }
        if (sessions.any { it.workout.unit == WeightUnit.LB } && abs(appMixed - truthKg) / truthKg > 0.02) {
            report(
                "VOLUME-MIXES-UNITS", Severity.MEDIUM,
                "Volume and progress averages add lb and kg numbers as if they were the same unit",
                "after the switch: summed ${appMixed.toLong()} vs ${truthKg.toLong()} kg real; progress query AVG(weight*reps)/reps ignores sets.weightUnit",
            )
            val insights = buildTrainingInsights(notes, parser)
            report(
                "INSIGHTS-LABEL-KG", Severity.LOW, "Training insights always label weights 'kg', also after switching to lb",
                "StatsViewModel calls buildTrainingInsights(notes, parser) with the default 'kg' label; ${insights.size} rows",
            )
        }

        // PRs per exercise group: heaviest set should be in one unit.
        sessions.groupBy { it.workout.unit }.takeIf { it.size > 1 }?.let {
            val bench = sessions.flatMap { s -> s.truth.filter { t -> t.exerciseKey == "bench_press" }.map { t -> s.workout.unit to t.weight } }
            val maxKg = bench.filter { it.first == WeightUnit.KG }.maxOfOrNull { it.second }
            val maxLbRaw = bench.filter { it.first == WeightUnit.LB }.maxOfOrNull { it.second }
            if (maxKg != null && maxLbRaw != null) {
                report(
                    "PR-JUMP-AT-UNIT-SWITCH", Severity.MEDIUM,
                    "Best-weight/progress for an exercise jumps ×2.2 at the kg → lb switch",
                    "bench best ${maxKg} kg before, raw ${maxLbRaw} after (= ${(maxLbRaw * KG_PER_LB).toInt()} kg)",
                )
            }
        }
    }

    companion object {
        /** Grey elapsed column maximum in minutes, as the Home card shows it. */
        fun cardMinutes(note: NoteLine): Int =
            (parseNoteText(note.text, note.rowMetadata).second.filter(String::isNotBlank).maxOfOrNull(::parseDurationSeconds) ?: 0) / 60
    }
}
