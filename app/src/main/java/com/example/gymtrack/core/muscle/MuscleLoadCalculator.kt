package com.example.gymtrack.core.muscle

/**
 * One logged set as the muscle calculator sees it. Load, unit, reps and time do not matter for
 * set counting, so they are not here: a kg set and a lb set, or a 60 s hold, each count as one set.
 */
data class MuscleSetInput(
    val exerciseId: Long,
    /** The set row's modifier (`incline`, `seated` …); refines the seed. */
    val modifier: String?,
    /** Only WORKING sets count (#306); warm-ups are left out. */
    val isWorking: Boolean,
)

/** Fractional sets of one workout (or week), per muscle and per exercise. */
data class MuscleLoad(
    /** Effective sets per muscle (primary 1.0, secondary 0.5 per working set). */
    val setsByMuscle: Map<Muscle, Float>,
    /** The same, split by exercise: muscle → exercise id → effective sets. */
    val setsByMuscleAndExercise: Map<Muscle, Map<Long, Float>>,
    /** Working sets of exercises with no mapping: exercise id → set count. Never counted as zero. */
    val unmappedSetsByExercise: Map<Long, Int>,
)

/**
 * Pure fractional-set calculator (#309): each WORKING set adds 1.0 to its primary muscles and 0.5
 * to its secondary ones (Pelland et al. 2024, `docs/design/ANATOMY_MAPPING.md` §3).
 *
 * Rules: warm-ups excluded; a unilateral set counts once (not once per side); a timed set or hold
 * counts as one set; supersets need nothing special (each row counts for its own exercise); units
 * are irrelevant. Sets of unmapped exercises are reported in [MuscleLoad.unmappedSetsByExercise].
 */
object MuscleLoadCalculator {
    const val PRIMARY_SHARE = 1f
    const val SECONDARY_SHARE = 0.5f

    fun calculate(sets: List<MuscleSetInput>, mappingFor: (exerciseId: Long, modifier: String?) -> MuscleMapping?): MuscleLoad {
        val byMuscleAndExercise = LinkedHashMap<Muscle, LinkedHashMap<Long, Float>>()
        val unmapped = LinkedHashMap<Long, Int>()
        sets.filter { it.isWorking && it.exerciseId > 0 }.forEach { set ->
            val mapping = mappingFor(set.exerciseId, set.modifier)
            if (mapping == null) {
                unmapped[set.exerciseId] = (unmapped[set.exerciseId] ?: 0) + 1
                return@forEach
            }
            mapping.shares.forEach { (muscle, share) ->
                val perExercise = byMuscleAndExercise.getOrPut(muscle) { LinkedHashMap() }
                perExercise[set.exerciseId] = (perExercise[set.exerciseId] ?: 0f) + share
            }
        }
        return MuscleLoad(
            setsByMuscle = byMuscleAndExercise.mapValues { (_, perExercise) -> perExercise.values.sum() },
            setsByMuscleAndExercise = byMuscleAndExercise,
            unmappedSetsByExercise = unmapped,
        )
    }
}
