package com.example.gymtrack.simulation

import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.data.WeightUnit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt
import kotlin.random.Random

/** Time and lifecycle situations the generator plants on specific days. */
enum class EdgeTag {
    DST_BACK,
    DST_FORWARD,
    YEAR_BOUNDARY,
    MIDNIGHT_SPAN,
    TIMEZONE_TRAVEL,
    LATE_FIRST_EXERCISE,
    FLAG_AFTER_ENTER,
    KILLED_MID_SET,
    BACK_THEN_FINISH_NEXT_MORNING,
    BACK_NEVER_FINISHED,
    NEXT_DAY_WHILE_OLD_ACTIVE,
    SAME_DAY_SECOND,
    DELOAD,
    POUNDS,
}

enum class SimEventType {
    UNIT_SWITCH_TO_LB,
    CATEGORY_ADD,
    CATEGORY_RENAME,
    CATEGORY_DELETE,
    BACKUP_RESTORE,
    EDIT_OLD_WORKOUT,
    RELAUNCH,
    RECREATE,
}

data class SimEvent(
    val day: Int,
    val type: SimEventType,
    val detail: String = "",
    /** For EDIT_OLD_WORKOUT: index of the workout that is edited. */
    val targetWorkout: Int? = null,
)

data class SimCategory(val name: String, val color: Long)

/** One set as the user taps it on the keypad, e.g. "8x80", "6+2x80", "10x32.5", "60s". */
data class SimSet(val keys: String, val restBeforeSeconds: Long) {
    val durationSeconds: Int? = Regex("""^(\d+)s$""").matchEntire(keys)?.groupValues?.get(1)?.toInt()
    val reps: Int = if (durationSeconds != null) 0 else keys.substringBefore('x').split('+').sumOf { it.toInt() }
    val weight: Double = if (durationSeconds != null) 0.0 else keys.substringAfter('x').toDouble()

    /** What the editor row shows before " (rest)": "8x 80", "60s ". */
    val editorText: String = keys.replace("x", "x ").replace("s", "s ")
}

data class SimExercise(
    /** Generator truth: the exercise the user means, whatever they typed. */
    val key: String,
    val typedName: String,
    val flag: ExerciseFlag,
    /** True when the user taps the flag only after pressing Enter on the exercise line. */
    val flagAfterEnter: Boolean,
    /** Seconds between the previous Enter (or the + tap) and typing this exercise. */
    val walkSeconds: Long,
    val sets: List<SimSet>,
)

enum class FinishMode { FINISH, BACK_THEN_FINISH_NEXT_MORNING, NEVER_FINISHED }

data class SimWorkout(
    val index: Int,
    val day: Int,
    val date: LocalDate,
    /** Epoch millis of the + tap, which is also the workout's identity (note timestamp). */
    val startMillis: Long,
    val zoneId: ZoneId,
    val category: SimCategory,
    val exercises: List<SimExercise>,
    val learnings: String,
    val unit: WeightUnit,
    val finishMode: FinishMode,
    /** Seconds between the last set and the Finish tap. */
    val finishDelaySeconds: Long,
    /** For KILLED_MID_SET: keys typed but not entered when the app was stopped. */
    val partialKeys: String? = null,
    /** For BACK_THEN_FINISH_NEXT_MORNING: the set logged after returning next morning. */
    val lateSet: SimSet? = null,
    val tags: Set<EdgeTag> = emptySet(),
) {
    val intendedSetCount: Int get() = exercises.sumOf { it.sets.size } + if (lateSet != null) 1 else 0
}

data class SimHistory(
    val seed: Long,
    val months: Int,
    val startDate: LocalDate,
    val homeZone: ZoneId,
    val workouts: List<SimWorkout>,
    val events: List<SimEvent>,
) {
    fun dateOf(day: Int): LocalDate = startDate.plusDays(day.toLong())

    /** Stable fingerprint, so a seed can be checked to reproduce the same history. */
    fun fingerprint(): Int = workouts.joinToString("|") { w ->
        "${w.startMillis}:${w.category.name}:" + w.exercises.joinToString(";") { e ->
            e.typedName + e.flag + e.sets.joinToString(",") { it.keys + "/" + it.restBeforeSeconds }
        }
    }.hashCode() * 31 + events.hashCode()
}

private data class ExerciseSpec(
    val key: String,
    val names: List<String>,
    val typos: List<String>,
    val baseKg: Double,
    val incrementKg: Double,
    val reps: IntRange,
    val sets: IntRange,
    val flag: ExerciseFlag = ExerciseFlag.BILATERAL,
    val timedSeconds: Int? = null,
    val introducedWeek: Int = 0,
)

/**
 * Deterministic months-long training history: push/pull/legs rotation, progressive overload,
 * deloads every sixth week, missed weeks, alias drift and typos, new exercises, unilateral and
 * superset work, timed sets, a kg → lb switch, category add/rename/delete, learnings text, and
 * planted time edges (DST both ways, year boundary, midnight span, travel, overnight timer,
 * killed mid-set, Back without Finish, same-day double session, late first exercise).
 *
 * Same seed → same history ([SimHistory.fingerprint]).
 */
class WorkoutHistoryGenerator(
    private val seed: Long,
    private val months: Int = 12,
    private val startDate: LocalDate = LocalDate.of(2025, 10, 6), // a Monday
    private val homeZone: ZoneId = ZoneId.of("Europe/Berlin"),
) {
    private val random = Random(seed)
    private val weeks = months * 52 / 12
    private val travelZone = ZoneId.of("America/New_York")

    private val push = SimCategory("Push", 0xFFFF3B30)
    private val pull = SimCategory("Pull", 0xFFAF52DE)
    private val legs = SimCategory("Legs", 0xFF34C759)
    private val upper = SimCategory("Upper", 0xFF007AFF)

    private val program: Map<String, List<ExerciseSpec>> = mapOf(
        "Push" to listOf(
            ExerciseSpec("bench_press", listOf("Bench Press", "bench", "BP", "Bench press"), listOf("Bench Pres", "Bnech Press"), 70.0, 2.5, 5..8, 3..4),
            ExerciseSpec("incline_db_press", listOf("Incline DB Press", "incline db press", "Incline Dumbbell Press"), listOf("Inclne DB Press"), 26.0, 2.0, 8..12, 3..3),
            ExerciseSpec("lateral_raise", listOf("Lateral Raise", "lat raise", "Side Raise"), listOf("Lateral Rasie"), 8.0, 1.0, 12..15, 2..3, ExerciseFlag.UNILATERAL),
            ExerciseSpec("cable_fly", listOf("Cable Fly", "cable flys", "Cable Flye"), listOf("Cabel Fly"), 15.0, 1.25, 12..15, 2..2, ExerciseFlag.SUPERSET),
            ExerciseSpec("triceps_pushdown", listOf("Triceps Pushdown", "tricep pushdown", "Pushdown"), listOf("Tricpes Pushdown"), 25.0, 1.25, 10..12, 2..2, ExerciseFlag.SUPERSET),
        ),
        "Pull" to listOf(
            ExerciseSpec("lat_pulldown", listOf("Lat Pulldown", "latpull", "Lat pull"), listOf("Lat Pulldwon"), 55.0, 2.5, 8..10, 3..4),
            ExerciseSpec("barbell_row", listOf("Barbell Row", "BB Row", "Bent Over Row"), listOf("Barbel Row"), 60.0, 2.5, 6..10, 3..3),
            ExerciseSpec("biceps_curl", listOf("Biceps Curl", "bicep curl", "Curls"), listOf("Bicpes Curl"), 12.0, 1.0, 10..12, 3..3, ExerciseFlag.UNILATERAL),
            ExerciseSpec("face_pull", listOf("Face Pull", "face pulls"), emptyList(), 20.0, 1.25, 12..15, 2..3, introducedWeek = 8),
            ExerciseSpec("dead_hang", listOf("Dead Hang", "hang"), emptyList(), 0.0, 0.0, 0..0, 1..2, timedSeconds = 45),
        ),
        "Legs" to listOf(
            ExerciseSpec("squat", listOf("Squat", "Back Squat", "squats"), listOf("Sqaut"), 80.0, 2.5, 5..8, 3..4),
            ExerciseSpec("romanian_deadlift", listOf("Romanian Deadlift", "RDL", "romanian dl"), listOf("Romanain Deadlift"), 70.0, 2.5, 8..10, 3..3),
            ExerciseSpec("leg_press", listOf("Leg Press", "legpress"), listOf("Leg Pres"), 140.0, 5.0, 10..12, 3..3),
            ExerciseSpec("bulgarian_split_squat", listOf("Bulgarian Split Squat", "BSS", "split squat"), emptyList(), 16.0, 2.0, 8..10, 2..3, ExerciseFlag.UNILATERAL, introducedWeek = 12),
            ExerciseSpec("plank", listOf("Plank"), emptyList(), 0.0, 0.0, 0..0, 1..1, timedSeconds = 60),
        ),
    )

    fun generate(): SimHistory {
        val events = mutableListOf<SimEvent>()
        val workouts = mutableListOf<SimWorkout>()
        val categoryNames = mutableMapOf("Legs" to legs)
        var hasUpper = false
        var unit = WeightUnit.KG
        val unitSwitchWeek = (weeks / 2).coerceAtLeast(1)
        val missedWeeks = setOf(9, 27, random.nextInt(14, weeks.coerceAtLeast(15)))
        var rotation = 0
        var dayIndexOfLastWorkout = -1

        fun day(date: LocalDate) = (date.toEpochDay() - startDate.toEpochDay()).toInt()

        for (week in 0 until weeks) {
            val weekStart = startDate.plusWeeks(week.toLong())
            when (week) {
                13 -> { events += SimEvent(day(weekStart), SimEventType.CATEGORY_ADD, "Upper"); hasUpper = true }
                22 -> {
                    events += SimEvent(day(weekStart), SimEventType.CATEGORY_RENAME, "Legs->Leg Day")
                    categoryNames["Legs"] = SimCategory("Leg Day", legs.color)
                }
                39 -> { events += SimEvent(day(weekStart), SimEventType.CATEGORY_DELETE, "Upper"); hasUpper = false }
                17, 43 -> events += SimEvent(day(weekStart) + 2, SimEventType.BACKUP_RESTORE)
            }
            if (week == unitSwitchWeek) {
                unit = WeightUnit.LB
                events += SimEvent(day(weekStart), SimEventType.UNIT_SWITCH_TO_LB)
            }
            if (week % 5 == 4) events += SimEvent(day(weekStart) + 3, SimEventType.RECREATE)
            events += SimEvent(day(weekStart), SimEventType.RELAUNCH)
            if (week == 30) {
                events += SimEvent(day(weekStart) + 1, SimEventType.EDIT_OLD_WORKOUT, "first set +5", targetWorkout = 10)
            }

            val forcedDays = forcedEdgeDates().filter { (date, _) -> !date.isBefore(weekStart) && date.isBefore(weekStart.plusWeeks(1)) }
            if (week in missedWeeks && forcedDays.isEmpty()) continue

            val sessions = if (week in missedWeeks) 0 else random.nextInt(4, 7)
            val pattern = listOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.WEDNESDAY,
            ).take(sessions)
            val plannedDates = (pattern.map { weekStart.with(java.time.temporal.TemporalAdjusters.nextOrSame(it)) } +
                forcedDays.map { it.first }).distinct().sorted()

            for (date in plannedDates) {
                val forcedTags = forcedDays.filter { it.first == date }.flatMap { it.second }.toSet()
                val travelling = date.isTravelWeek()
                val zone = if (travelling) travelZone else homeZone
                val time = forcedTime(forcedTags) ?: randomStartTime()
                val categoryBase = listOf(push, pull, legs)[rotation % 3]
                rotation++
                val category = categoryNames[categoryBase.name] ?: categoryBase
                val index = workouts.size
                val tags = forcedTags.toMutableSet()
                if (travelling) tags += EdgeTag.TIMEZONE_TRAVEL
                if (week % 6 == 5) tags += EdgeTag.DELOAD
                if (unit == WeightUnit.LB) tags += EdgeTag.POUNDS
                val late = if (random.nextInt(10) == 0) { tags += EdgeTag.LATE_FIRST_EXERCISE; random.nextLong(12, 85) * 5 } else 0L

                val exercises = buildExercises(categoryBase.name, week, unit, EdgeTag.DELOAD in tags, late, tags)
                val finishMode = when {
                    index == 35 -> FinishMode.BACK_THEN_FINISH_NEXT_MORNING.also { tags += EdgeTag.BACK_THEN_FINISH_NEXT_MORNING }
                    index == 60 -> FinishMode.NEVER_FINISHED.also { tags += EdgeTag.BACK_NEVER_FINISHED }
                    else -> FinishMode.FINISH
                }
                if (index == 61) tags += EdgeTag.NEXT_DAY_WHILE_OLD_ACTIVE
                val partial = if (index == 20) { tags += EdgeTag.KILLED_MID_SET; "8x8" } else null
                val lateSet = if (finishMode == FinishMode.BACK_THEN_FINISH_NEXT_MORNING) {
                    SimSet(exercises.last().sets.last().keys, restBeforeSeconds = 13L * 3600L)
                } else null

                workouts += SimWorkout(
                    index = index,
                    day = day(date),
                    date = date,
                    startMillis = ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli(),
                    zoneId = zone,
                    category = category,
                    exercises = exercises,
                    learnings = if (random.nextInt(4) == 0) LEARNINGS[random.nextInt(LEARNINGS.size)] else "",
                    unit = unit,
                    finishMode = finishMode,
                    finishDelaySeconds = random.nextLong(6, 61) * 5,
                    partialKeys = partial,
                    lateSet = lateSet,
                    tags = tags,
                )
                dayIndexOfLastWorkout = day(date)

                if (index == 75 || (EdgeTag.SAME_DAY_SECOND in forcedTags)) {
                    // Short arm session on the same evening.
                    val second = workouts.size
                    val secondCategory = if (hasUpper) upper else push
                    val armTime = LocalTime.of(21, 30)
                    workouts += SimWorkout(
                        index = second,
                        day = day(date),
                        date = date,
                        startMillis = ZonedDateTime.of(date, armTime, zone).toInstant().toEpochMilli(),
                        zoneId = zone,
                        category = secondCategory,
                        exercises = listOf(
                            SimExercise("biceps_curl", "Biceps Curl", ExerciseFlag.BILATERAL, false, 0, listOf(SimSet("12x${fmt(weightFor(12.0, 1.0, week, unit, false))}", 60), SimSet("10x${fmt(weightFor(12.0, 1.0, week, unit, false))}", 90))),
                            SimExercise("triceps_pushdown", "Triceps Pushdown", ExerciseFlag.BILATERAL, false, 90, listOf(SimSet("12x${fmt(weightFor(25.0, 1.25, week, unit, false))}", 60))),
                        ),
                        learnings = "",
                        unit = unit,
                        finishMode = FinishMode.FINISH,
                        finishDelaySeconds = 30,
                        tags = setOf(EdgeTag.SAME_DAY_SECOND) + if (unit == WeightUnit.LB) setOf(EdgeTag.POUNDS) else emptySet(),
                    )
                }
            }
        }
        check(dayIndexOfLastWorkout > 0) { "Seed $seed produced no workouts" }
        return SimHistory(seed, months, startDate, homeZone, workouts, events.sortedBy { it.day })
    }

    private fun buildExercises(
        category: String,
        week: Int,
        unit: WeightUnit,
        deload: Boolean,
        lateStartSeconds: Long,
        tags: MutableSet<EdgeTag>,
    ): List<SimExercise> {
        val specs = program.getValue(category).filter { week >= it.introducedWeek }
        return specs.mapIndexed { position, spec ->
            val name = when (random.nextInt(100)) {
                in 0 until 70 -> spec.names.first()
                in 70 until 92 -> spec.names[random.nextInt(spec.names.size)]
                else -> (spec.typos.ifEmpty { spec.names })[random.nextInt(spec.typos.ifEmpty { spec.names }.size)]
            }
            val flagAfterEnter = spec.flag != ExerciseFlag.BILATERAL && random.nextInt(10) < 3
            if (flagAfterEnter) tags += EdgeTag.FLAG_AFTER_ENTER
            val setCount = random.nextInt(spec.sets.first, spec.sets.last + 1)
            val sets = (0 until setCount).map { setIndex ->
                val rest = when {
                    setIndex == 0 -> random.nextLong(6, 25) * 5
                    spec.flag == ExerciseFlag.SUPERSET -> random.nextLong(12, 25) * 5
                    else -> random.nextLong(18, 37) * 5 // 90-180 s
                }
                val keys = if (spec.timedSeconds != null) {
                    "${spec.timedSeconds + 15 * random.nextInt(0, 2)}s"
                } else {
                    val reps = random.nextInt(spec.reps.first, spec.reps.last + 1)
                    val weight = fmt(weightFor(spec.baseKg, spec.incrementKg, week, unit, deload))
                    if (setIndex == setCount - 1 && random.nextInt(8) == 0) "$reps+${random.nextInt(2, 4)}x$weight" else "${reps}x$weight"
                }
                SimSet(keys, rest)
            }
            val walk = if (position == 0) lateStartSeconds else random.nextLong(12, 37) * 5
            SimExercise(spec.key, name, spec.flag, flagAfterEnter, walk, sets)
        }
    }

    /** Progressive overload: + increment every two weeks, 10 % deload, lb typed after the switch. */
    private fun weightFor(baseKg: Double, incrementKg: Double, week: Int, unit: WeightUnit, deload: Boolean): Double {
        var kg = baseKg + incrementKg * (week / 2)
        if (deload) kg *= 0.9
        return if (unit == WeightUnit.LB) {
            (kg * 2.20462 / 5.0).roundToInt() * 5.0
        } else {
            (kg / 2.5).roundToInt() * 2.5 + if (incrementKg < 2.0 && kg < 20) 0.5 else 0.0
        }
    }

    private fun randomStartTime(): LocalTime = if (random.nextInt(10) < 7) {
        LocalTime.of(17, 30).plusMinutes(random.nextLong(0, 25) * 5)
    } else {
        LocalTime.of(6, 30).plusMinutes(random.nextLong(0, 19) * 5)
    }

    private fun forcedEdgeDates(): List<Pair<LocalDate, Set<EdgeTag>>> = listOf(
        LocalDate.of(2025, 10, 26) to setOf(EdgeTag.DST_BACK),
        LocalDate.of(2025, 12, 31) to setOf(EdgeTag.YEAR_BOUNDARY, EdgeTag.MIDNIGHT_SPAN),
        LocalDate.of(2026, 3, 29) to setOf(EdgeTag.DST_FORWARD),
        LocalDate.of(2026, 2, 14) to setOf(EdgeTag.SAME_DAY_SECOND),
    ).filter { (date, _) -> date.isBefore(startDate.plusWeeks(weeks.toLong())) && !date.isBefore(startDate) }

    private fun forcedTime(tags: Set<EdgeTag>): LocalTime? = when {
        EdgeTag.DST_BACK in tags -> LocalTime.of(1, 45)
        EdgeTag.DST_FORWARD in tags -> LocalTime.of(1, 35)
        EdgeTag.MIDNIGHT_SPAN in tags -> LocalTime.of(23, 25)
        else -> null
    }

    private fun LocalDate.isTravelWeek(): Boolean =
        !isBefore(LocalDate.of(2026, 5, 11)) && !isAfter(LocalDate.of(2026, 5, 17))

    companion object {
        val LEARNINGS = listOf(
            "Shoulder felt tight, go lighter next time",
            "Felt strong 💪",
            "PR: \"easy\" triple, keep the bar path, then add 2.5",
            "Schulter zwickt – nächstes Mal leichter",
            "Gym crowded, swapped order; rests longer",
            "Sleep 5h.\nEnergy low, cut last set",
        )

        fun fmt(weight: Double): String =
            if (weight % 1.0 == 0.0) weight.toLong().toString() else weight.toString()
    }
}
