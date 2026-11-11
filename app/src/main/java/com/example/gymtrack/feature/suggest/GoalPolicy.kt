package com.example.gymtrack.feature.suggest

/*
 * Goal presets (#312). Pure Kotlin, no Android imports: the literature table of
 * docs/TRAINING_POLICY.md §2 as data. Change the doc and this file together.
 */

/** The user's training goal. Opt-in: [NONE] (the default) switches all coaching off. */
enum class TrainingGoal(val storageValue: String, val label: String) {
    NONE("NONE", "No goal"),
    BUILD_MUSCLE("BUILD_MUSCLE", "Build muscle"),
    LOSE_FAT("LOSE_FAT", "Lose fat"),
    GET_STRONGER("GET_STRONGER", "Get stronger"),
    ;

    companion object {
        /** Unknown or missing values read as [NONE], so old settings and backups stay valid. */
        fun fromStorage(value: String?): TrainingGoal =
            entries.firstOrNull { it.storageValue.equals(value?.trim(), ignoreCase = true) } ?: NONE
    }
}

/** Shown on every coaching surface (a goal is set). */
const val COACHING_DISCLAIMER = "Not medical advice; stop if pain."

/** Compound (multi-joint) or isolation exercise; [UNKNOWN] shows the union of both ranges. */
enum class ExerciseKind {
    COMPOUND,
    ISOLATION,
    UNKNOWN,
    ;

    companion object {
        private val ISOLATION_WORDS = listOf(
            "curl", "raise", "fly", "flye", "extension", "pushdown", "push-down", "kickback",
            "calf", "shrug", "crunch", "pullover", "pec deck", "adduct", "abduct", "wrist",
            "face pull", "leg ext", "leg curl", "crossover",
        )
        private val COMPOUND_WORDS = listOf(
            "squat", "deadlift", "press", "bench", "row", "pull-up", "pullup", "pull up", "chin",
            "dip", "lunge", "split", "thrust", "clean", "snatch", "jerk", "pulldown", "pull-down",
            "lat pull", "hack", "step-up", "step up", "good morning", "rdl",
        )

        /** A name heuristic until the muscle map (#309) can tell. Isolation words win ("leg press" is compound, "leg extension" is not). */
        fun guess(name: String): ExerciseKind {
            val n = name.lowercase()
            return when {
                ISOLATION_WORDS.any { it in n } -> ISOLATION
                COMPOUND_WORDS.any { it in n } -> COMPOUND
                else -> UNKNOWN
            }
        }
    }
}

/**
 * The guideline ranges for one goal. Policy only: it never reads history, and every value it
 * gives is labelled [SuggestionSource.POLICY]. Rest is in seconds.
 */
data class GoalPolicy(
    val goal: TrainingGoal,
    val weeklySetsPerMuscle: IntRange,
    val compoundReps: IntRange,
    val isolationReps: IntRange,
    val compoundRir: IntRange,
    val isolationRir: IntRange,
    val compoundRestSeconds: IntRange,
    val isolationRestSeconds: IntRange,
    /** Per exercise per session: expert practice (see TRAINING_POLICY.md §2, note 2). */
    val setsPerExercise: IntRange,
) {
    fun reps(kind: ExerciseKind): IntRange = pick(kind, compoundReps, isolationReps)
    fun rir(kind: ExerciseKind): IntRange = pick(kind, compoundRir, isolationRir)
    fun restSeconds(kind: ExerciseKind): IntRange = pick(kind, compoundRestSeconds, isolationRestSeconds)

    private fun pick(kind: ExerciseKind, compound: IntRange, isolation: IntRange): IntRange = when (kind) {
        ExerciseKind.COMPOUND -> compound
        ExerciseKind.ISOLATION -> isolation
        ExerciseKind.UNKNOWN -> minOf(compound.first, isolation.first)..maxOf(compound.last, isolation.last)
    }

    companion object {
        val BUILD_MUSCLE = GoalPolicy(
            goal = TrainingGoal.BUILD_MUSCLE,
            weeklySetsPerMuscle = 10..20,
            compoundReps = 6..15,
            isolationReps = 10..20,
            compoundRir = 0..3,
            isolationRir = 0..2,
            compoundRestSeconds = 120..180,
            isolationRestSeconds = 90..120,
            setsPerExercise = 2..4,
        )

        /** Same reps and rest as [BUILD_MUSCLE] (shorter rest is unsupported); keep volume ≥ 8–10. */
        val LOSE_FAT = BUILD_MUSCLE.copy(
            goal = TrainingGoal.LOSE_FAT,
            weeklySetsPerMuscle = 8..20,
            compoundRir = 1..3,
            isolationRir = 1..3,
        )

        /** Main lifts 1–6 reps, 3–5 min; accessories hypertrophy-style (expert practice). */
        val GET_STRONGER = GoalPolicy(
            goal = TrainingGoal.GET_STRONGER,
            weeklySetsPerMuscle = 4..10,
            compoundReps = 1..6,
            isolationReps = 6..15,
            compoundRir = 1..3,
            isolationRir = 1..3,
            compoundRestSeconds = 180..300,
            isolationRestSeconds = 120..180,
            setsPerExercise = 3..5,
        )

        /** The policy of [goal]; null for [TrainingGoal.NONE] (coaching off). */
        fun of(goal: TrainingGoal): GoalPolicy? = when (goal) {
            TrainingGoal.NONE -> null
            TrainingGoal.BUILD_MUSCLE -> BUILD_MUSCLE
            TrainingGoal.LOSE_FAT -> LOSE_FAT
            TrainingGoal.GET_STRONGER -> GET_STRONGER
        }
    }
}
