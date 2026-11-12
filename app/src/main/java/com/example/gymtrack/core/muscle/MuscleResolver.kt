package com.example.gymtrack.core.muscle

/** Primary (1.0 set) or secondary (0.5 set) credit of one muscle in one exercise (#309). */
enum class MuscleRole(val storageValue: String) {
    PRIMARY("PRIMARY"),
    SECONDARY("SECONDARY"),
    ;

    companion object {
        /** Null for an unknown stored value (never guessed). */
        fun fromStorage(value: String?): MuscleRole? = entries.firstOrNull { it.storageValue == value?.trim()?.uppercase() }
    }
}

/** Where a resolved mapping came from, in resolver order. */
enum class MuscleMappingOrigin {
    /** The user's own edit (`exercise_muscles`, #310). */
    USER,

    /** The built-in seed ([MuscleMappingSeed]). */
    SEED,

    /** Inherited from a parent exercise (user override or seed of the parent). */
    PARENT,
}

/** The muscles one working set of an exercise counts for. */
data class MuscleMapping(
    val primary: Set<Muscle>,
    val secondary: Set<Muscle>,
    val origin: MuscleMappingOrigin,
    /** The seed row behind it (also for [MuscleMappingOrigin.PARENT] when the parent used the seed). */
    val seed: MuscleSeedEntry? = null,
    /** The exercise the mapping was taken from; differs from the asked one for [MuscleMappingOrigin.PARENT]. */
    val fromExerciseId: Long? = null,
) {
    /** Share of one working set per muscle: primary wins when a muscle is listed twice. */
    val shares: Map<Muscle, Float>
        get() = buildMap {
            secondary.forEach { put(it, MuscleLoadCalculator.SECONDARY_SHARE) }
            primary.forEach { put(it, MuscleLoadCalculator.PRIMARY_SHARE) }
        }

    companion object {
        fun fromSeed(entry: MuscleSeedEntry, origin: MuscleMappingOrigin = MuscleMappingOrigin.SEED, fromExerciseId: Long? = null) =
            MuscleMapping(entry.primary, entry.secondary, origin, entry, fromExerciseId)
    }
}

/** One stored user override row (`exercise_muscles`). */
data class MuscleOverride(val muscle: Muscle, val role: MuscleRole)

/** What the resolver needs to know about one exercise (`exercises` row). */
data class MuscleExerciseRef(val id: Long, val name: String, val parentId: Long?)

/**
 * Resolves the muscles of an exercise (#309). Order: user override → seed → parent walk →
 * unmapped (null). The parent walk is cycle-safe and applies the same order to each parent.
 * An exercise with override rows uses exactly those rows; the seed is not merged in.
 *
 * Results are cached per (exercise, modifier); build a new resolver after an override changes.
 */
class MuscleResolver(
    private val exercises: Map<Long, MuscleExerciseRef>,
    private val overrides: Map<Long, List<MuscleOverride>>,
    private val seed: (name: String, modifier: String?) -> MuscleSeedEntry? = MuscleMappingSeed::lookup,
) {
    private val cache = HashMap<Pair<Long, String?>, MuscleMapping?>()

    /** The mapping, or null when the exercise is unmapped (shown as such, never as zero). */
    fun resolve(exerciseId: Long, modifier: String? = null): MuscleMapping? {
        val key = exerciseId to modifier?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
        return cache.getOrPut(key) { walk(exerciseId, key.second) }
    }

    private fun walk(exerciseId: Long, modifier: String?): MuscleMapping? {
        val visited = HashSet<Long>()
        var currentId: Long? = exerciseId
        while (currentId != null && visited.add(currentId)) {
            val isSelf = currentId == exerciseId
            ownMapping(currentId, modifier)?.let { mapping ->
                return if (isSelf) mapping else mapping.copy(origin = MuscleMappingOrigin.PARENT, fromExerciseId = currentId)
            }
            currentId = exercises[currentId]?.parentId
        }
        return null
    }

    private fun ownMapping(exerciseId: Long, modifier: String?): MuscleMapping? {
        overrides[exerciseId]?.takeIf { rows -> rows.any { it.role == MuscleRole.PRIMARY } }?.let { rows ->
            val primary = rows.filter { it.role == MuscleRole.PRIMARY }.mapTo(linkedSetOf()) { it.muscle }
            val secondary = rows.filter { it.role == MuscleRole.SECONDARY }.mapTo(linkedSetOf()) { it.muscle } - primary
            return MuscleMapping(primary, secondary, MuscleMappingOrigin.USER, fromExerciseId = exerciseId)
        }
        val exercise = exercises[exerciseId] ?: return null
        return seed(exercise.name, modifier)?.let { MuscleMapping.fromSeed(it, fromExerciseId = exerciseId) }
    }
}
