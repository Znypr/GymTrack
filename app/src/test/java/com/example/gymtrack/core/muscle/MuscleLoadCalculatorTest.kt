package com.example.gymtrack.core.muscle

import com.example.gymtrack.core.data.DerivedIndexWriter
import com.example.gymtrack.core.data.SetEntity
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #309: primary 1.0, secondary 0.5, working sets only (Pelland et al. 2024). */
class MuscleLoadCalculatorTest {
    private val exercises = listOf(
        MuscleExerciseRef(1, "Bench Press", null),
        MuscleExerciseRef(2, "Biceps Curl", null),
        MuscleExerciseRef(3, "Bulgarian Split Squat", null),
        MuscleExerciseRef(4, "Hanging Leg Raise", null),
        MuscleExerciseRef(5, "Dead Hang", null),
        MuscleExerciseRef(6, "Lat Pulldown", null),
    ).associateBy { it.id }
    private val resolver = MuscleResolver(exercises, emptyMap())

    private fun working(id: Long, modifier: String? = null) = MuscleSetInput(id, modifier, isWorking = true)
    private fun warmup(id: Long) = MuscleSetInput(id, null, isWorking = false)

    @Test
    fun primaryCountsOneAndSecondaryHalf() {
        val load = MuscleLoadCalculator.calculate(List(3) { working(1) }, resolver::resolve)
        assertEquals(3f, load.setsByMuscle.getValue(Muscle.CHEST_LOWER))
        assertEquals(3f, load.setsByMuscle.getValue(Muscle.CHEST_UPPER))
        assertEquals(1.5f, load.setsByMuscle.getValue(Muscle.TRICEPS))
        assertEquals(1.5f, load.setsByMuscle.getValue(Muscle.DELT_FRONT))
        assertTrue(Muscle.LATS !in load.setsByMuscle)
    }

    @Test
    fun warmUpsAreExcluded() {
        val load = MuscleLoadCalculator.calculate(listOf(warmup(1), warmup(1), working(1)), resolver::resolve)
        assertEquals(1f, load.setsByMuscle.getValue(Muscle.CHEST_LOWER))
    }

    @Test
    fun aUnilateralSetCountsOnceNotPerSide() {
        // Bulgarian split squat logged per leg: each row is one set (the doc: do not double).
        val load = MuscleLoadCalculator.calculate(List(2) { working(3) }, resolver::resolve)
        assertEquals(2f, load.setsByMuscle.getValue(Muscle.QUADS))
        assertEquals(1f, load.setsByMuscle.getValue(Muscle.GLUTE_MED))
    }

    @Test
    fun supersetRowsCountForTheirOwnExercise() {
        // Bench and lat pulldown alternated: no cross-credit, biceps only from the pulldown.
        val sets = listOf(working(1), working(6), working(1), working(6))
        val load = MuscleLoadCalculator.calculate(sets, resolver::resolve)
        assertEquals(2f, load.setsByMuscle.getValue(Muscle.LATS))
        assertEquals(2f, load.setsByMuscle.getValue(Muscle.CHEST_LOWER))
        assertEquals(1f, load.setsByMuscle.getValue(Muscle.BICEPS))
        assertEquals(mapOf(6L to 1f), load.setsByMuscleAndExercise.getValue(Muscle.BICEPS))
    }

    @Test
    fun aMuscleListedInTwoExercisesAddsUp() {
        val load = MuscleLoadCalculator.calculate(listOf(working(2), working(6)), resolver::resolve)
        // Curl primary 1.0 + pulldown secondary 0.5.
        assertEquals(1.5f, load.setsByMuscle.getValue(Muscle.BICEPS))
        assertEquals(mapOf(2L to 1f, 6L to 0.5f), load.setsByMuscleAndExercise.getValue(Muscle.BICEPS))
    }

    @Test
    fun unmappedSetsAreReportedNotZeroed() {
        val load = MuscleLoadCalculator.calculate(listOf(working(5), working(5), warmup(5), working(1)), resolver::resolve)
        assertEquals(mapOf(5L to 2), load.unmappedSetsByExercise)
        assertEquals(1f, load.setsByMuscle.getValue(Muscle.CHEST_LOWER))
    }

    private val workoutId = LocalDateTime.of(2026, 11, 10, 18, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun row(exerciseId: Long, weight: Float, reps: Int, unit: String = "KG", unilateral: Boolean = false, type: String = "WORKING") =
        SetEntity(workoutId = workoutId, exerciseId = exerciseId, weight = weight, reps = reps, isUnilateral = unilateral, weightUnit = unit, setType = type)

    @Test
    fun kgAndLbSetsCountTheSame() {
        val rows = listOf(row(1, 100f, 5, "KG"), row(1, 225f, 5, "LB"))
        val muscles = DerivedIndexWriter.muscleWeekRows(workoutId, rows, resolver::resolve, ZoneOffset.UTC)
        assertEquals(2f, muscles.single { it.muscle == Muscle.CHEST_LOWER.id }.effectiveSets)
    }

    @Test
    fun timedSetsCountOneEach() {
        // 3 hanging leg raise holds stored as 0 kg / 0 reps rows are still 3 working sets.
        val rows = List(3) { row(4, 0f, 0) }
        val muscles = DerivedIndexWriter.muscleWeekRows(workoutId, rows, resolver::resolve, ZoneOffset.UTC)
        assertEquals(3f, muscles.single { it.muscle == Muscle.ABS.id }.effectiveSets)
        assertEquals(1.5f, muscles.single { it.muscle == Muscle.OBLIQUES.id }.effectiveSets)
    }

    @Test
    fun unilateralRowsAreNotDoubledInTheDerivedRows() {
        val rows = listOf(row(2, 12f, 10, unilateral = true), row(2, 12f, 10, unilateral = true))
        val muscles = DerivedIndexWriter.muscleWeekRows(workoutId, rows, resolver::resolve, ZoneOffset.UTC)
        assertEquals(2f, muscles.single { it.muscle == Muscle.BICEPS.id }.effectiveSets)
    }

    @Test
    fun aWorkoutWhoseCategoryWasDeletedStillCounts() {
        // Muscle rows read only sets and exercises, never the note's category: deleting the
        // category ("Push" gone) changes nothing.
        val rows = listOf(row(1, 100f, 5), row(1, 100f, 5, type = "WARMUP"))
        val muscles = DerivedIndexWriter.muscleWeekRows(workoutId, rows, resolver::resolve, ZoneOffset.UTC)
        assertEquals(1f, muscles.single { it.muscle == Muscle.CHEST_LOWER.id }.effectiveSets)
        assertTrue(muscles.all { it.weekKey == "2026-W46" && it.exerciseId == 1L })
    }

    @Test
    fun unmappedExercisesGetAnUnmappedRow() {
        val rows = listOf(row(5, 0f, 0), row(5, 0f, 0))
        val muscles = DerivedIndexWriter.muscleWeekRows(workoutId, rows, resolver::resolve, ZoneOffset.UTC)
        val unmapped = muscles.single()
        assertEquals(WeeklyMuscleSummary.UNMAPPED_KEY, unmapped.muscle)
        assertEquals(5L, unmapped.exerciseId)
        assertEquals(2f, unmapped.effectiveSets)
    }
}
