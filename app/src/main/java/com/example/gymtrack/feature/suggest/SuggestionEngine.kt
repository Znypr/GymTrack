package com.example.gymtrack.feature.suggest

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

/*
 * Suggestion engine (#312, #308). Pure Kotlin, no Android imports, no clock, no I/O: history +
 * goal in, suggestion out. Rules and citations: docs/TRAINING_POLICY.md §4.
 */

enum class LoadUnit(val label: String) {
    KG("kg"),
    LB("lb"),
    ;

    companion object {
        fun fromLabel(value: String?): LoadUnit? = when (value?.trim()?.lowercase()) {
            "kg", "kgs" -> KG
            "lb", "lbs" -> LB
            else -> null
        }
    }
}

/** Where a suggested value comes from: what the user logged, or the goal's guideline table. */
enum class SuggestionSource { HISTORY, POLICY }

/** One working set of an earlier session. [load] 0 = bodyweight / no load. */
data class PastSet(val reps: Int, val load: Double, val unit: LoadUnit)

/** One finished session of one exercise: its working sets in logged order. */
data class PastSession(val workoutId: Long, val sets: List<PastSet>)

/**
 * A set already typed today in the same exercise block. Null fields = unknown (the set is then
 * low confidence and no suggestion is made).
 */
data class TodaySet(val reps: Int?, val load: Double?, val unit: LoadUnit?)

enum class SetSuggestionKind {
    /** Last session's matching set, as it was. */
    REPEAT,

    /** Every set reached the top of the range: the same set + one load step (double progression). */
    PROGRESS,
}

/** The next set to offer in the pill. Always [SuggestionSource.HISTORY]. */
data class SetSuggestion(
    val reps: Int,
    val load: Double,
    val unit: LoadUnit,
    val kind: SetSuggestionKind,
    /** Last session's matching set this is based on. */
    val basis: PastSet,
    /** 1-based working-set number today. */
    val setNumber: Int,
    val step: Double,
    /** Top of the user's own recent rep range. */
    val repTop: Int,
) {
    val source: SuggestionSource get() = SuggestionSource.HISTORY

    /** Editor grammar (`8x 82.5`), with the unit only when it is not [defaultUnit]. */
    fun entry(defaultUnit: LoadUnit): String =
        "${reps}x ${formatLoad(load)}" + if (unit != defaultUnit) " ${unit.label}" else ""

    /** Display grammar, like a set row: `8 × 82.5`. */
    fun label(defaultUnit: LoadUnit): String =
        "$reps × ${formatLoad(load)}" + if (unit != defaultUnit) " ${unit.label}" else ""
}

/** One warm-up set of the ramp (expert practice, POLICY; only with a goal). */
data class WarmUpSet(val reps: Int, val load: Double, val unit: LoadUnit, val percent: Int)

/** One "why" line with its source, so the UI can label history and policy separately. */
data class SuggestionLine(val text: String, val source: SuggestionSource)

/** Everything the engine says about one exercise right now. */
data class ExerciseSuggestion(
    val next: SetSuggestion?,
    /** HISTORY: working sets in the last session. */
    val lastSetCount: Int?,
    /** HISTORY: the user's usual rest for this exercise, seconds. */
    val usualRestSeconds: Int?,
    /** POLICY: null when no goal is set. */
    val policy: GoalPolicy?,
    val kind: ExerciseKind,
    /** POLICY, expert practice: empty without a goal or a loaded working set. */
    val warmUp: List<WarmUpSet>,
) {
    val policyReps: IntRange? get() = policy?.reps(kind)
    val policyRir: IntRange? get() = policy?.rir(kind)
    val policyRestSeconds: IntRange? get() = policy?.restSeconds(kind)
    val policySets: IntRange? get() = policy?.setsPerExercise

    /** History lines first, then the goal's lines and the disclaimer (only with a goal). */
    fun whyLines(defaultUnit: LoadUnit): List<SuggestionLine> = buildList {
        next?.let { s ->
            val basis = "${s.basis.reps} × ${formatLoad(s.basis.load)}" + if (s.unit != defaultUnit) " ${s.unit.label}" else ""
            add(SuggestionLine("Last time set ${s.setNumber}: $basis.", SuggestionSource.HISTORY))
            val reason = when (s.kind) {
                SetSuggestionKind.PROGRESS ->
                    "Every set reached ${s.repTop} reps → +${formatLoad(s.step)} ${s.unit.label} (your smallest step)."
                SetSuggestionKind.REPEAT -> "Not every set reached ${s.repTop} reps yet → same load."
            }
            add(SuggestionLine(reason, SuggestionSource.HISTORY))
        }
        val habits = listOfNotNull(
            lastSetCount?.let { "$it working sets last time" },
            usualRestSeconds?.let { "usual rest ${formatRest(it)}" },
        )
        if (habits.isNotEmpty()) add(SuggestionLine(habits.joinToString(", ").replaceFirstChar(Char::uppercase) + ".", SuggestionSource.HISTORY))
        val p = policy ?: return@buildList
        add(
            SuggestionLine(
                "${p.goal.label}: ${range(p.reps(kind))} reps, ${range(p.rir(kind))} in reserve, " +
                    "rest ${restRange(p.restSeconds(kind))}, ${range(p.setsPerExercise)} sets.",
                SuggestionSource.POLICY,
            ),
        )
        if (warmUp.isNotEmpty()) {
            val ramp = warmUp.joinToString(" · ") { "${it.reps} × ${formatLoad(it.load)}" }
            add(SuggestionLine("Warm-up idea (expert practice): $ramp.", SuggestionSource.POLICY))
        }
        add(SuggestionLine(COACHING_DISCLAIMER, SuggestionSource.POLICY))
    }
}

object SuggestionEngine {
    /** Today's sets lighter than this share of last session's top load are warm-ups... */
    const val WARMUP_RATIO = 0.80

    /** ...and so are low-rep primers up to this share (80 % × 3 before 100 % × 8), like #306. */
    const val PRIMER_RATIO = 0.92
    const val PRIMER_MAX_REPS = 3

    /** A matched load above this × the median top of the other recent sessions is a kept typo. */
    const val OUTLIER_FACTOR = 1.5

    /** Sessions that make up the user's own rep range. */
    const val RANGE_SESSIONS = 3

    /** After a load step, reps restart at most this far below the top of the range. */
    const val MAX_REP_DROP = 4

    private val KG_STEPS = listOf(0.5, 1.0, 1.25, 2.0, 2.5, 5.0)
    private val LB_STEPS = listOf(1.0, 2.5, 5.0, 10.0)
    private val WARMUP_RAMP = listOf(40 to 8, 60 to 5, 80 to 3)

    fun maxStep(unit: LoadUnit): Double = if (unit == LoadUnit.KG) KG_STEPS.last() else LB_STEPS.last()

    fun defaultStep(unit: LoadUnit): Double = if (unit == LoadUnit.KG) 2.5 else 5.0

    /**
     * The smallest load increment the user has used on this exercise in [unit]: the smallest gap
     * between two different loads, snapped down to a real increment. Never above [maxStep].
     */
    fun loadStep(history: List<PastSession>, unit: LoadUnit): Double {
        val steps = if (unit == LoadUnit.KG) KG_STEPS else LB_STEPS
        val loads = history.asSequence()
            .flatMap { it.sets }
            .filter { it.unit == unit && it.load > 0 }
            .map { round(it.load * 100) / 100 }
            .distinct()
            .sorted()
            .toList()
        val gap = loads.zipWithNext { a, b -> round((b - a) * 100) / 100 }
            .filter { it >= steps.first() - 1e-9 }
            .minOrNull()
            ?: return defaultStep(unit)
        return steps.lastOrNull { it <= gap + 1e-9 } ?: steps.first()
    }

    /**
     * The next set for today's working set number `today.working + 1`, from the last session.
     * Null when there is nothing safe to suggest (see TRAINING_POLICY.md §4.6).
     */
    fun nextSet(history: List<PastSession>, today: List<TodaySet>): SetSuggestion? {
        val last = history.firstOrNull()?.sets?.takeIf { it.isNotEmpty() } ?: return null
        val unit = last.first().unit
        if (last.any { it.unit != unit }) return null
        if (today.any { it.reps == null }) return null
        // A kept typo (`825`) must not turn today's real sets into "warm-ups".
        val lastTopKg = last.filterNot { isOutlier(it, history) }.maxOfOrNull { toKg(it.load, it.unit) } ?: 0.0

        val workingToday = today.count { set ->
            val load = set.load ?: 0.0
            if (lastTopKg <= 0.0 || load <= 0.0) return@count true
            val ratio = toKg(load, set.unit ?: unit) / lastTopKg
            !(ratio < WARMUP_RATIO || (ratio < PRIMER_RATIO && (set.reps ?: 0) <= PRIMER_MAX_REPS))
        }
        val basis = last.getOrNull(workingToday) ?: return null
        if (basis.reps <= 0 || basis.load <= 0.0) return null
        if (isOutlier(basis, history)) return null

        val recent = history.take(RANGE_SESSIONS).flatMap { it.sets }.filter { it.reps > 0 }
        val repTop = recent.maxOf { it.reps }
        val repBottom = recent.minOf { it.reps }
        val oneLoad = last.all { abs(it.load - last.first().load) < 1e-6 }
        val allHitTop = last.all { it.reps >= repTop }
        val step = loadStep(history, unit).coerceAtMost(maxStep(unit))

        return if (oneLoad && allHitTop) {
            SetSuggestion(
                reps = repBottom.coerceAtLeast(repTop - MAX_REP_DROP).coerceAtLeast(1),
                load = basis.load + step,
                unit = unit,
                kind = SetSuggestionKind.PROGRESS,
                basis = basis,
                setNumber = workingToday + 1,
                step = step,
                repTop = repTop,
            )
        } else {
            SetSuggestion(basis.reps, basis.load, unit, SetSuggestionKind.REPEAT, basis, workingToday + 1, step, repTop)
        }
    }

    /** Warm-up ramp ~40/60/80 % of [workingLoad], rounded down to [step]; empty sets dropped. */
    fun warmUpRamp(workingLoad: Double, step: Double, unit: LoadUnit): List<WarmUpSet> {
        if (workingLoad <= 0.0 || step <= 0.0) return emptyList()
        return WARMUP_RAMP.mapNotNull { (percent, reps) ->
            val load = floor(workingLoad * percent / 100.0 / step + 1e-9) * step
            if (load <= 0.0 || load >= workingLoad) null else WarmUpSet(reps, round(load * 100) / 100, unit, percent)
        }.distinctBy { it.load }
    }

    /**
     * The whole suggestion for one exercise. [goal] NONE = history only. [usualRestSeconds] comes
     * from the rest history (#349) and is passed through as HISTORY.
     */
    fun suggest(
        history: List<PastSession>,
        today: List<TodaySet>,
        goal: TrainingGoal,
        kind: ExerciseKind,
        usualRestSeconds: Int? = null,
    ): ExerciseSuggestion {
        val next = nextSet(history, today)
        val policy = GoalPolicy.of(goal)
        val firstWorking = next?.takeIf { it.setNumber == 1 }
        val warmUp = if (policy != null && firstWorking != null) {
            warmUpRamp(firstWorking.load, firstWorking.step, firstWorking.unit)
        } else {
            emptyList()
        }
        return ExerciseSuggestion(
            next = next,
            lastSetCount = history.firstOrNull()?.sets?.size?.takeIf { it > 0 },
            usualRestSeconds = usualRestSeconds,
            policy = policy,
            kind = kind,
            warmUp = warmUp,
        )
    }

    private fun isOutlier(basis: PastSet, history: List<PastSession>): Boolean {
        val otherTops = history.drop(1).mapNotNull { s -> s.sets.filter { it.load > 0 }.maxOfOrNull { toKg(it.load, it.unit) } }
        if (otherTops.size < 2) return false
        val median = otherTops.sorted()[otherTops.size / 2]
        return toKg(basis.load, basis.unit) > median * OUTLIER_FACTOR
    }

    private const val KG_PER_LB = 0.45359237

    internal fun toKg(load: Double, unit: LoadUnit): Double = if (unit == LoadUnit.LB) load * KG_PER_LB else load
}

/** `80`, `82.5`, `81.25`. */
fun formatLoad(load: Double): String {
    val rounded = round(load * 100) / 100
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString().trimEnd('0').trimEnd('.')
}

private fun range(r: IntRange): String = if (r.first == r.last) "${r.first}" else "${r.first}–${r.last}"

private fun formatRest(seconds: Int): String = "${seconds / 60}'${(seconds % 60).toString().padStart(2, '0')}"

private fun restRange(r: IntRange): String {
    fun one(s: Int) = if (s % 60 == 0) "${s / 60}" else formatRest(s)
    return if (r.first % 60 == 0 && r.last % 60 == 0) "${one(r.first)}–${one(r.last)} min" else "${formatRest(r.first)}–${formatRest(r.last)}"
}
