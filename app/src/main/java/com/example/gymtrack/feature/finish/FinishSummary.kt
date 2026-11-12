package com.example.gymtrack.feature.finish

import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.muscle.Muscle
import com.example.gymtrack.core.muscle.MuscleLevel
import com.example.gymtrack.core.muscle.MuscleLoadSource
import com.example.gymtrack.core.muscle.MuscleWeek
import com.example.gymtrack.core.util.ParsedSetDTO
import com.example.gymtrack.core.util.WorkoutParser
import com.example.gymtrack.core.util.noteWorkoutDuration
import com.example.gymtrack.feature.stats.KG_PER_LB
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * What the Finish sheet shows. Built by [buildFinishSummary] from the saved workout; pure data,
 * platform-neutral (README rule 9).
 */
data class FinishSummary(
    /** Category name ("Push"), else the workout title, else "Workout". */
    val title: String,
    /** Workout duration by docs/WORKOUT_DURATION.md, whole minutes; null when unknown. */
    val durationMinutes: Int?,
    /** Exercises with at least one logged set. */
    val exerciseCount: Int,
    /** Every logged set row (rep sets and timed sets). The app has no warm-up flag. */
    val setCount: Int,
    /** Best set per exercise, in the order the exercises were logged. */
    val topSets: List<TopSet>,
    /** Finished workouts in the current week (Mon–Sun), this one included. */
    val workoutsThisWeek: Int,
    /** This workout's muscle load (#311); all unmapped until the mapping (#309) is connected. */
    val muscles: MuscleWeek = MuscleWeek.unmapped,
    /** The least-trained mapped muscle, for the "Next time: …" line; null when nothing is mapped. */
    val nextTime: Muscle? = null,
) {
    /** Muscles this workout trained, most sets first (ties in [Muscle] order). */
    val setsPerMuscle: List<Pair<Muscle, Float>>
        get() = muscles.sets.filterValues { it > 0f }.toList()
            .sortedWith(compareByDescending<Pair<Muscle, Float>> { it.second }.thenBy { it.first.ordinal })

    /** "Push · 64 min · 4 exercises · 10 sets"; unknown parts are left out. */
    val headline: String
        get() = listOfNotNull(
            title,
            durationMinutes?.let { "$it min" },
            plural(exerciseCount, "exercise"),
            plural(setCount, "set"),
        ).joinToString(" · ")

    /** "This week: 3 workouts". */
    val weekLine: String
        get() = "This week: ${plural(workoutsThisWeek, "workout")}"
}

/** One exercise's best set, already formatted ("8 × 80", "12 reps", "60s", "45s · 10"). */
data class TopSet(val exercise: String, val display: String)

/**
 * Builds the Finish sheet summary from the workout as it was saved.
 *
 * Honest numbers (docs/STATS_RULES.md):
 * - sets are parsed with [defaultUnit], the unit in force at Finish (docs/WEIGHT_UNITS.md);
 * - the top set is the heaviest rep set (loads compared in kg), ties broken by more reps; an
 *   exercise with only unloaded rep sets shows its most reps; timed sets are never shown as
 *   kg × reps: a timed-only exercise shows its longest hold (load breaks ties);
 * - a weight shows its unit only when it differs from [defaultUnit] (README rule 3);
 * - "this week" counts [finishedWorkoutStarts] (start timestamps of finished workouts) plus this
 *   one, in the Monday-start week that holds [nowMillis];
 * - [muscles] are loaded by the host from a [MuscleLoadSource] (unmapped when unknown); "Next time" is the
 *   least-trained mapped muscle of [weekMuscles] when the host has the week, else of this workout.
 */
fun buildFinishSummary(
    workout: NoteLine,
    defaultUnit: WeightUnit,
    finishedWorkoutStarts: Collection<Long>,
    nowMillis: Long,
    finishedAtMillis: Long? = null,
    zone: ZoneId = ZoneId.systemDefault(),
    parser: WorkoutParser = WorkoutParser(),
    muscles: MuscleWeek = MuscleWeek.unmapped,
    weekMuscles: MuscleWeek? = null,
): FinishSummary {
    val sets = parser.parseWorkout(workout.text, defaultUnit.storageValue, workout.rowMetadata)
    val byExercise = LinkedHashMap<String, MutableList<ParsedSetDTO>>()
    for (set in sets) {
        val name = set.exerciseName.trim().ifEmpty { "Exercise" }
        byExercise.getOrPut(name.lowercase(Locale.ROOT)) { mutableListOf() } += set
    }
    val topSets = byExercise.values.mapNotNull { group ->
        topSetOf(group, defaultUnit)?.let { TopSet(group.first().exerciseName.trim().ifEmpty { "Exercise" }, it) }
    }
    val minutes = noteWorkoutDuration(workout.text, workout.rowMetadata, workout.timestamp, finishedAtMillis)
        ?.seconds
        ?.let { (it + 30) / 60 }
        ?.takeIf { it > 0 }
    return FinishSummary(
        title = workout.categoryName?.trim()?.takeIf { it.isNotEmpty() }
            ?: workout.title.trim().takeIf { it.isNotEmpty() }
            ?: "Workout",
        durationMinutes = minutes,
        exerciseCount = byExercise.size,
        setCount = sets.size,
        topSets = topSets,
        workoutsThisWeek = countThisWeek(finishedWorkoutStarts + workout.timestamp, nowMillis, zone),
        muscles = muscles,
        nextTime = nextMuscleSuggestion(weekMuscles ?: muscles),
    )
}

/**
 * Tie-break for [nextMuscleSuggestion]: big movers first, so "Next time" names something worth a
 * session rather than the first id in the enum. Muscles not listed follow in [Muscle] order.
 */
internal val NEXT_TIME_PRIORITY: List<Muscle> = listOf(
    Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTE_MAX, Muscle.CHEST_LOWER, Muscle.LATS,
    Muscle.CHEST_UPPER, Muscle.RHOMBOIDS_TERES, Muscle.DELT_SIDE, Muscle.DELT_REAR, Muscle.TRICEPS,
    Muscle.BICEPS, Muscle.GASTROCNEMIUS, Muscle.ABS, Muscle.TRAPS_MID_LOWER, Muscle.ERECTORS,
    Muscle.DELT_FRONT, Muscle.ADDUCTORS, Muscle.GLUTE_MED, Muscle.TRAPS_UPPER, Muscle.OBLIQUES,
)

/**
 * The least-trained mapped muscle in [week] (fewest sets; ties by [NEXT_TIME_PRIORITY]).
 * Unmapped muscles are never suggested: we don't know they're untrained.
 */
fun nextMuscleSuggestion(week: MuscleWeek): Muscle? {
    val rank = { m: Muscle -> NEXT_TIME_PRIORITY.indexOf(m).let { if (it < 0) NEXT_TIME_PRIORITY.size + m.ordinal else it } }
    return Muscle.entries
        .filter { week.levelOf(it) != MuscleLevel.UNMAPPED }
        .minWithOrNull(compareBy<Muscle> { week.setsOf(it) }.thenBy(rank))
}

/** Distinct starts that fall in the Monday-start week holding [nowMillis]. */
internal fun countThisWeek(starts: Collection<Long>, nowMillis: Long, zone: ZoneId): Int {
    val weekStart = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .atStartOfDay(zone)
    val from = weekStart.toInstant().toEpochMilli()
    val until = weekStart.plusWeeks(1).toInstant().toEpochMilli()
    return starts.toSet().count { it in from until until }
}

private fun topSetOf(group: List<ParsedSetDTO>, defaultUnit: WeightUnit): String? {
    val repSets = group.filter { it.durationSeconds == null && it.reps > 0 }
    val loaded = repSets.filter { it.weight > 0f }
    if (loaded.isNotEmpty()) {
        val best = loaded.maxWith(compareBy<ParsedSetDTO> { kilograms(it) }.thenBy { it.reps })
        return "${best.reps} × ${formatWeight(best, defaultUnit)}"
    }
    if (repSets.isNotEmpty()) {
        val best = repSets.maxBy { it.reps }
        return "${best.reps} reps"
    }
    val timed = group.filter { (it.durationSeconds ?: 0) > 0 }
    if (timed.isNotEmpty()) {
        val best = timed.maxWith(compareBy<ParsedSetDTO> { it.durationSeconds ?: 0 }.thenBy { kilograms(it) })
        val load = if (best.weight > 0f) " · ${formatWeight(best, defaultUnit)}" else ""
        return "${best.durationSeconds}s$load"
    }
    return null
}

private fun kilograms(set: ParsedSetDTO): Double =
    if (WeightUnit.fromStorage(set.weightUnit) == WeightUnit.LB) set.weight * KG_PER_LB else set.weight.toDouble()

/** `80` / `82.5`, plus ` lb` / ` kg` only when the set's unit differs from the default. */
private fun formatWeight(set: ParsedSetDTO, defaultUnit: WeightUnit): String {
    val w = set.weight
    val number = if (w % 1f == 0f) w.toLong().toString() else String.format(Locale.ROOT, "%.2f", w).trimEnd('0').trimEnd('.')
    val unit = WeightUnit.fromStorage(set.weightUnit)
    return if (unit == defaultUnit) number else "$number ${unit.displayLabel}"
}

private fun plural(n: Int, word: String): String = if (n == 1) "1 $word" else "$n ${word}s"
