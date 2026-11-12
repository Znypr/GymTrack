package com.example.gymtrack.core.muscle

/** One exercise's share of a muscle's weekly sets. */
data class MuscleExerciseShare(val exerciseId: Long, val name: String, val effectiveSets: Float)

/** A muscle's week: fractional sets, level and the exercises behind them, most first. */
data class MuscleWeekStat(
    val muscle: Muscle,
    val effectiveSets: Float,
    val level: MuscleLevel,
    val exercises: List<MuscleExerciseShare>,
)

/** An exercise trained this week that has no mapping (shown, never counted as zero). */
data class UnmappedExercise(val exerciseId: Long, val name: String, val workingSets: Int)

/** One stored `muscle_week` total for a week: muscle key, exercise, effective sets. */
data class MuscleWeekRow(val muscle: String, val exerciseId: Long, val exerciseName: String?, val effectiveSets: Float)

/**
 * The week of every one of the 26 regions (#309), for the body map (#311) and the Progress tab.
 *
 * Level rule: a muscle with sets gets its band from [MuscleLevel.forWeeklySets] (a lower bound when
 * unmapped exercises exist). A muscle with 0 mapped sets is [MuscleLevel.NONE] only when every
 * trained exercise is mapped; otherwise it is [MuscleLevel.UNMAPPED], because an unmapped exercise
 * may have trained it and the app cannot honestly say zero.
 */
data class WeeklyMuscleSummary(
    val weekKey: String,
    val muscles: Map<Muscle, MuscleWeekStat>,
    val unmapped: List<UnmappedExercise>,
) {
    val levels: Map<Muscle, MuscleLevel> get() = muscles.mapValues { it.value.level }

    companion object {
        /** `muscle_week.muscle` key of the working sets of an unmapped exercise. */
        const val UNMAPPED_KEY = "unmapped"

        fun from(weekKey: String, rows: List<MuscleWeekRow>): WeeklyMuscleSummary {
            val unmapped = rows.filter { it.muscle == UNMAPPED_KEY }
                .groupBy { it.exerciseId }
                .map { (id, exerciseRows) ->
                    UnmappedExercise(id, exerciseRows.first().displayName(), exerciseRows.sumOf { it.effectiveSets.toDouble() }.toInt())
                }
                .sortedByDescending { it.workingSets }
            val byMuscle = rows.mapNotNull { row -> Muscle.fromId(row.muscle)?.let { it to row } }
                .groupBy({ it.first }, { it.second })
            val muscles = Muscle.entries.associateWith { muscle ->
                val shares = byMuscle[muscle].orEmpty()
                    .groupBy { it.exerciseId }
                    .map { (id, exerciseRows) ->
                        MuscleExerciseShare(id, exerciseRows.first().displayName(), exerciseRows.sumOf { it.effectiveSets.toDouble() }.toFloat())
                    }
                    .sortedByDescending { it.effectiveSets }
                val sets = shares.sumOf { it.effectiveSets.toDouble() }.toFloat()
                val level = when {
                    sets > 0f -> MuscleLevel.forWeeklySets(sets)
                    unmapped.isNotEmpty() -> MuscleLevel.UNMAPPED
                    else -> MuscleLevel.NONE
                }
                MuscleWeekStat(muscle, sets, level, shares)
            }
            return WeeklyMuscleSummary(weekKey, muscles, unmapped)
        }

        private fun MuscleWeekRow.displayName(): String = exerciseName?.takeIf(String::isNotBlank) ?: "Exercise $exerciseId"
    }
}
