package com.example.gymtrack.core.muscle

import com.example.gymtrack.core.data.NoteLine

/** One exercise's contribution to a muscle: its name as logged and its (fractional) working sets. */
data class MuscleExerciseSets(val exercise: String, val sets: Float)

/**
 * Muscle load for a week or one workout, as the body map shows it (#309, #311).
 *
 * @property levels every region's level; a missing key reads as [MuscleLevel.NONE] only when the
 *   source says the muscle is mapped, so sources should list all 26 (see [MuscleWeek.unmapped]).
 * @property sets fractional working sets per muscle (only muscles with sets > 0).
 * @property exercises the exercises behind [sets], most sets first.
 */
data class MuscleWeek(
    val levels: Map<Muscle, MuscleLevel>,
    val sets: Map<Muscle, Float> = emptyMap(),
    val exercises: Map<Muscle, List<MuscleExerciseSets>> = emptyMap(),
) {
    fun levelOf(muscle: Muscle): MuscleLevel = levels[muscle] ?: MuscleLevel.NONE
    fun setsOf(muscle: Muscle): Float = sets[muscle] ?: 0f

    /** True when no region is mapped (the map is all dotted outlines). */
    val isAllUnmapped: Boolean get() = Muscle.entries.all { levelOf(it) == MuscleLevel.UNMAPPED }

    companion object {
        /** Every region unmapped: the honest state until the exercise → muscle mapping exists. */
        val unmapped: MuscleWeek = MuscleWeek(Muscle.entries.associateWith { MuscleLevel.UNMAPPED })
    }
}

/**
 * Where the body map gets its numbers. The real implementation (`MuscleLoadCalculator` and
 * `weeklyMuscleLevels`, #309) lands separately; until it is connected, [UnmappedMuscleLoadSource]
 * keeps the UI honest by mapping nothing.
 */
interface MuscleLoadSource {
    /** Levels, sets and exercises for the ISO week [weekKey] (`2026-W45`, see `DerivedIndexWriter.weekKey`). */
    suspend fun weekLevels(weekKey: String): MuscleWeek

    /** Levels, sets and exercises for one workout, from its note as saved. Pure and fast. */
    fun workoutLevels(note: NoteLine): MuscleWeek
}

/** Temporary source: every region [MuscleLevel.UNMAPPED], no sets. Never pretends a muscle is untrained. */
object UnmappedMuscleLoadSource : MuscleLoadSource {
    override suspend fun weekLevels(weekKey: String): MuscleWeek = MuscleWeek.unmapped
    override fun workoutLevels(note: NoteLine): MuscleWeek = MuscleWeek.unmapped
}
