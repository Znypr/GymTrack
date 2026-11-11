package com.example.gymtrack.core.data

/** What a logged set was for (#306). Stats, PRs, volume and muscle load count [WORKING] only. */
enum class SetType {
    WARMUP,
    WORKING,
    ;

    val storageValue: String get() = name

    companion object {
        fun fromStorage(value: String?): SetType =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: WORKING
    }
}

/** Who decided a set's [SetType]: the inference below, or the user (long-press override, #306). */
enum class SetTypeSource {
    INFERRED,
    USER,
    ;

    val storageValue: String get() = name

    companion object {
        fun fromStorage(value: String?): SetTypeSource =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: INFERRED
    }
}

/**
 * One set, as the inference sees it. Sets must be given in logged order.
 *
 * @param blockKey sets with the same key that follow each other are one exercise block (one
 *   exercise header in the note). Any value with a stable `equals`.
 * @param loadKg load in kilograms; null or 0 for bodyweight, unknown or timed sets.
 * @param offsetSeconds when the set row was logged, in seconds after the workout start.
 * @param override the user's choice; wins over the inference.
 */
data class SetTypeInput(
    val blockKey: Any,
    val loadKg: Double?,
    val reps: Int,
    val isTimed: Boolean = false,
    val offsetSeconds: Int? = null,
    val override: SetType? = null,
)

data class SetTypeResult(
    val type: SetType,
    val source: SetTypeSource,
    /** Seconds since the previous set of the same block; null for the first set or unknown times. */
    val restSeconds: Int?,
)

/**
 * Infers warm-up vs working sets and rest (#306). Pure; runs at Finish/save and in the lazy
 * derived-index rebuild, never while typing.
 *
 * The rule follows how warm-ups are prescribed in strength training (NSCA *Essentials of Strength
 * Training and Conditioning*; ramp protocols of ~40-60% → 70-85% of the work load for few reps
 * before the first work set): warm-up sets are a **ramp of lighter sets before the first work
 * set**, never sets after it.
 *
 * Per exercise block, with `top` = the heaviest load and `topReps` = reps of the first set at
 * that load, the leading sets are warm-ups while each one is
 *  - lighter than [LIGHT_RATIO] (50%) of `top`, whatever the reps (empty bar × 10), or
 *  - lighter than [RAMP_RATIO] (80%) of `top` with no more reps than `topReps` (60×5 before 100×5), or
 *  - up to about 90% of `top` ([PRIMER_RATIO]) with at most [PRIMER_MAX_REPS] reps and fewer reps
 *    than `topReps` (80×3 or 90×1 before 100×5: the last ramp sets before the work load),
 *  and the ramp does not go down (each warm-up is at least as heavy as the one before).
 *
 * The first set that fails this starts the working sets; every later set is WORKING, so a light
 * back-off set after the work sets is never a warm-up. An ascending pyramid with more reps on the
 * lighter sets (60×10, 70×8, 80×6) stays all WORKING. Blocks without two known loads (bodyweight,
 * timed, unknown unit) stay WORKING: when unsure, the set counts, so no real work is lost from
 * stats.
 *
 * Rest is the gap between two consecutive set rows of the same block (logging time, so it also
 * includes the set itself). The gap before the first set of a block and gaps across exercise
 * changes are not rest and are null; gaps over [MAX_REST_SECONDS] are a break, also null.
 */
object SetTypeInference {
    const val LIGHT_RATIO = 0.50
    const val RAMP_RATIO = 0.80
    const val PRIMER_RATIO = 0.92
    const val PRIMER_MAX_REPS = 3
    const val MAX_REST_SECONDS = 30 * 60

    fun classify(sets: List<SetTypeInput>): List<SetTypeResult> {
        if (sets.isEmpty()) return emptyList()
        val types = arrayOfNulls<SetType>(sets.size)
        val rests = arrayOfNulls<Int>(sets.size)

        var start = 0
        while (start < sets.size) {
            var end = start + 1
            while (end < sets.size && sets[end].blockKey == sets[start].blockKey) end++
            classifyBlock(sets, start, end, types)
            for (index in start + 1 until end) {
                val previous = sets[index - 1].offsetSeconds
                val current = sets[index].offsetSeconds
                if (previous != null && current != null) {
                    val gap = current - previous
                    if (gap in 0..MAX_REST_SECONDS) rests[index] = gap
                }
            }
            start = end
        }

        return sets.mapIndexed { index, input ->
            if (input.override != null) {
                SetTypeResult(input.override, SetTypeSource.USER, rests[index])
            } else {
                SetTypeResult(types[index] ?: SetType.WORKING, SetTypeSource.INFERRED, rests[index])
            }
        }
    }

    private fun classifyBlock(
        sets: List<SetTypeInput>,
        start: Int,
        end: Int,
        types: Array<SetType?>,
    ) {
        for (index in start until end) types[index] = SetType.WORKING

        var topIndex = -1
        var top = 0.0
        var loadedSets = 0
        for (index in start until end) {
            val load = sets[index].loadedKg() ?: continue
            loadedSets++
            if (load > top) {
                top = load
                topIndex = index
            }
        }
        if (loadedSets < 2 || topIndex < 0) return
        val topReps = sets[topIndex].reps

        var previousWarmUpLoad = 0.0
        for (index in start until topIndex) {
            val set = sets[index]
            val load = set.loadedKg() ?: break
            if (load < previousWarmUpLoad) break
            if (!isWarmUp(load / top, set.reps, topReps)) break
            types[index] = SetType.WARMUP
            previousWarmUpLoad = load
        }
    }

    private fun isWarmUp(ratio: Double, reps: Int, topReps: Int): Boolean = when {
        ratio < LIGHT_RATIO -> true
        ratio < RAMP_RATIO -> reps <= topReps
        ratio < PRIMER_RATIO -> reps in 1..PRIMER_MAX_REPS && reps < topReps
        else -> false
    }

    private fun SetTypeInput.loadedKg(): Double? =
        if (isTimed || reps <= 0) null else loadKg?.takeIf { it > 0.0 }
}
