package com.example.gymtrack.core.data

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.IsoFields

/**
 * Pure builders for the per-set fields and derived rows of one workout (#306, #307). The
 * repository calls them inside its transactions; tests call them directly.
 */
object DerivedIndexWriter {
    /** Bump when a derived row's meaning changes; the next reader rebuilds lazily. */
    const val VERSION = 1
    const val INDEX_NAME = "workout_derived"

    /** Same weight in kilograms, for comparing loads of sets typed in different units. */
    fun loadKg(weight: Float, unit: String): Double {
        val kg = if (WeightUnit.fromStorage(unit) == WeightUnit.LB) weight * KG_PER_LB else weight.toDouble()
        return kg
    }

    /** Exercise block of a stored row: consecutive rows under one exercise header. */
    fun blockKey(row: SetEntity): Any = BlockKey(row.exerciseId, row.modifier, row.brand, row.absoluteTime, row.relativeTime)

    private data class BlockKey(
        val exerciseId: Long,
        val modifier: String?,
        val brand: String?,
        val headerTime: String?,
        val headerRelativeTime: String?,
    )

    /**
     * [rows] (one workout, logged order) with set type, source and rest filled in.
     *
     * @param offsets logging time of each row in seconds after start, aligned with [rows]; null
     *   when unknown (rest stays null).
     * @param timedRows rows that are timed sets (no reps-based load), aligned with [rows].
     * @param overrides the user's choices, aligned with [rows]; null entries are inferred.
     */
    fun classify(
        rows: List<SetEntity>,
        offsets: List<Int?>? = null,
        timedRows: List<Boolean>? = null,
        overrides: List<SetType?>? = null,
    ): List<SetEntity> {
        val results = SetTypeInference.classify(
            rows.mapIndexed { index, row ->
                SetTypeInput(
                    blockKey = blockKey(row),
                    loadKg = loadKg(row.weight, row.weightUnit),
                    reps = row.reps,
                    isTimed = timedRows?.getOrNull(index) ?: false,
                    offsetSeconds = offsets?.getOrNull(index),
                    override = overrides?.getOrNull(index),
                )
            },
        )
        return rows.zip(results) { row, result ->
            row.copy(
                setType = result.type.storageValue,
                setTypeSource = result.source.storageValue,
                restSeconds = result.restSeconds,
            )
        }
    }

    /** `exercise_recent` rows for one workout's classified [rows]. */
    fun recentRows(workoutId: Long, rows: List<SetEntity>): List<ExerciseRecentEntity> =
        rows.filter { it.exerciseId > 0 }
            .groupBy { it.exerciseId }
            .mapNotNull { (exerciseId, exerciseRows) ->
                val working = exerciseRows.filter { SetType.fromStorage(it.setType) == SetType.WORKING }
                if (working.isEmpty()) return@mapNotNull null
                // The first set at the top load gives the top reps.
                val topLoad = working.maxOf { loadKg(it.weight, it.weightUnit) }
                val firstTop = working.first { loadKg(it.weight, it.weightUnit) == topLoad }
                ExerciseRecentEntity(
                    workoutId = workoutId,
                    exerciseId = exerciseId,
                    workingSets = working.size,
                    warmupSets = exerciseRows.size - working.size,
                    workingReps = working.sumOf { it.reps },
                    topWeight = firstTop.weight,
                    topWeightUnit = WeightUnit.fromStorage(firstTop.weightUnit).storageValue,
                    topReps = firstTop.reps,
                    volumeKg = working.sumOf { loadKg(it.weight, it.weightUnit) * it.reps }.toFloat(),
                    setsSummary = working.joinToString(" · ") { it.summaryText() },
                )
            }

    /** `muscle_week` rows for one workout's classified [rows] (stub muscle keys, see [muscleKeysFor]). */
    fun muscleWeekRows(
        workoutId: Long,
        rows: List<SetEntity>,
        muscleGroupOf: (Long) -> String?,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<MuscleWeekEntity> {
        val weekKey = weekKey(workoutId, zoneId)
        val totals = LinkedHashMap<String, Float>()
        rows.filter { it.exerciseId > 0 && SetType.fromStorage(it.setType) == SetType.WORKING }
            .forEach { row ->
                muscleKeysFor(row.exerciseId, muscleGroupOf(row.exerciseId)).forEach { (muscle, share) ->
                    totals[muscle] = (totals[muscle] ?: 0f) + share
                }
            }
        return totals.map { (muscle, sets) -> MuscleWeekEntity(workoutId, weekKey, muscle, sets) }
    }

    /**
     * Muscles one working set of an exercise counts for, with its share. Stub until the muscle
     * map lands: the stored `muscleGroup`, or the exercise itself.
     */
    fun muscleKeysFor(exerciseId: Long, muscleGroup: String?): List<Pair<String, Float>> {
        val group = muscleGroup?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        return listOf((group ?: "exercise:$exerciseId") to 1f)
    }

    fun weekKey(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
        val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val year = date.get(IsoFields.WEEK_BASED_YEAR)
        return "%d-W%02d".format(year, week)
    }

    private fun SetEntity.summaryText(): String {
        val load = if (weight > 0f) " ${formatWeight(weight)}" else ""
        val unit = if (weight > 0f && WeightUnit.fromStorage(weightUnit) == WeightUnit.LB) " lb" else ""
        return "${reps}x$load$unit"
    }

    private fun formatWeight(weight: Float): String =
        if (weight % 1f == 0f) weight.toLong().toString() else weight.toString()

    private const val KG_PER_LB = 0.45359237
}
