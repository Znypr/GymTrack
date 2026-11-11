package com.example.gymtrack.core.data

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #306/#307: per-set fields and derived rows written at Finish / edit / rebuild. */
class DerivedIndexWriterTest {
    private val workoutId = LocalDateTime.of(2026, 11, 10, 18, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun row(exerciseId: Long, weight: Float, reps: Int, unit: String = "KG", header: String? = "0'00") = SetEntity(
        workoutId = workoutId,
        exerciseId = exerciseId,
        weight = weight,
        reps = reps,
        isUnilateral = false,
        absoluteTime = header,
        weightUnit = unit,
    )

    private val bench = listOf(row(1, 20f, 10), row(1, 60f, 5), row(1, 100f, 5), row(1, 100f, 4))
    private val curl = listOf(row(2, 12f, 12), row(2, 12f, 10))

    @Test
    fun classifyFillsTypeSourceAndRest() {
        val classified = DerivedIndexWriter.classify(bench + curl, offsets = listOf(60, 150, 330, 480, 700, 760))
        assertEquals(
            listOf("WARMUP", "WARMUP", "WORKING", "WORKING", "WORKING", "WORKING"),
            classified.map { it.setType },
        )
        assertTrue(classified.all { it.setTypeSource == "INFERRED" })
        assertEquals(listOf(null, 90, 180, 150, null, 60), classified.map { it.restSeconds })
    }

    @Test
    fun loadsInPoundsAreComparedInKilograms() {
        // 135 lb ≈ 61 kg before 100 kg × 5: a warm-up.
        val classified = DerivedIndexWriter.classify(listOf(row(1, 135f, 5, unit = "LB"), row(1, 100f, 5)))
        assertEquals(listOf("WARMUP", "WORKING"), classified.map { it.setType })
    }

    @Test
    fun recentRowsSummariseWorkingSetsOnly() {
        val rows = DerivedIndexWriter.recentRows(workoutId, DerivedIndexWriter.classify(bench + curl))
            .associateBy { it.exerciseId }
        val benchRow = rows.getValue(1)
        assertEquals(2, benchRow.workingSets)
        assertEquals(2, benchRow.warmupSets)
        assertEquals(9, benchRow.workingReps)
        assertEquals(100f, benchRow.topWeight)
        assertEquals(5, benchRow.topReps)
        assertEquals(900f, benchRow.volumeKg, 0.01f)
        assertEquals("5x 100 · 4x 100", benchRow.setsSummary)
        assertEquals("12x 12 · 10x 12", rows.getValue(2).setsSummary)
    }

    @Test
    fun poundSessionsKeepTheirUnitAndVolumeIsKilograms() {
        val rows = DerivedIndexWriter.recentRows(
            workoutId,
            DerivedIndexWriter.classify(listOf(row(1, 225f, 5, unit = "LB"), row(1, 225f, 5, unit = "LB"))),
        )
        assertEquals("LB", rows.single().topWeightUnit)
        assertEquals("5x 225 lb · 5x 225 lb", rows.single().setsSummary)
        assertEquals(1020.6f, rows.single().volumeKg, 0.1f)
    }

    @Test
    fun muscleWeekCountsWorkingSetsPerStubMuscle() {
        val classified = DerivedIndexWriter.classify(bench + curl)
        val rows = DerivedIndexWriter.muscleWeekRows(
            workoutId,
            classified,
            muscleGroupOf = { id -> if (id == 2L) "Biceps" else null },
            zoneId = ZoneOffset.UTC,
        ).associateBy { it.muscle }
        assertEquals(2f, rows.getValue("exercise:1").effectiveSets)
        assertEquals(2f, rows.getValue("biceps").effectiveSets)
        assertTrue(rows.values.all { it.weekKey == "2026-W46" && it.workoutId == workoutId })
    }

    @Test
    fun weekKeyIsIsoWeek() {
        val newYearsEve = LocalDateTime.of(2024, 12, 31, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals("2025-W01", DerivedIndexWriter.weekKey(newYearsEve, ZoneOffset.UTC))
    }

    @Test
    fun userOverridesSurviveAReDeriveByExerciseAndPosition() {
        val stored = DerivedIndexWriter.classify(bench, overrides = listOf(null, SetType.WORKING, null, null))
        assertEquals("USER", stored[1].setTypeSource)
        val byPosition = WorkoutRepository.userOverridesByPosition(stored)
        assertEquals(mapOf((1L to 1) to SetType.WORKING), byPosition)

        // The edited note adds a curl block before the bench sets: positions are per exercise.
        val edited = curl + bench
        val overrides = WorkoutRepository.positionKeys(edited).map { byPosition[it] }
        val reclassified = DerivedIndexWriter.classify(edited, overrides = overrides)
        assertEquals("WORKING", reclassified[3].setType)
        assertEquals("USER", reclassified[3].setTypeSource)
        assertNull(overrides[2])
    }
}
