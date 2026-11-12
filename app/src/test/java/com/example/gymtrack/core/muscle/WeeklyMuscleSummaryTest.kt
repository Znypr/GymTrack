package com.example.gymtrack.core.muscle

import org.junit.Assert.assertEquals
import org.junit.Test

/** #309: neutral levels none 0 / low 1–4 / moderate 5–9 / high 10+, unmapped never zero. */
class WeeklyMuscleSummaryTest {
    @Test
    fun levelsFollowTheBands() {
        assertEquals(MuscleLevel.NONE, MuscleLevel.forWeeklySets(0f))
        assertEquals(MuscleLevel.LOW, MuscleLevel.forWeeklySets(0.5f))
        assertEquals(MuscleLevel.LOW, MuscleLevel.forWeeklySets(4.5f))
        assertEquals(MuscleLevel.MODERATE, MuscleLevel.forWeeklySets(5f))
        assertEquals(MuscleLevel.MODERATE, MuscleLevel.forWeeklySets(9.5f))
        assertEquals(MuscleLevel.HIGH, MuscleLevel.forWeeklySets(10f))
    }

    @Test
    fun everyRegionIsPresentWithItsExercisesMostFirst() {
        val summary = WeeklyMuscleSummary.from(
            "2026-W46",
            listOf(
                MuscleWeekRow("biceps", 2, "Biceps Curl", 6f),
                MuscleWeekRow("biceps", 6, "Lat Pulldown", 2f),
                MuscleWeekRow("lats", 6, "Lat Pulldown", 4f),
            ),
        )
        assertEquals(26, summary.muscles.size)
        val biceps = summary.muscles.getValue(Muscle.BICEPS)
        assertEquals(8f, biceps.effectiveSets)
        assertEquals(MuscleLevel.MODERATE, biceps.level)
        assertEquals(listOf("Biceps Curl", "Lat Pulldown"), biceps.exercises.map { it.name })
        assertEquals(MuscleLevel.LOW, summary.levels.getValue(Muscle.LATS))
        assertEquals(MuscleLevel.NONE, summary.levels.getValue(Muscle.QUADS))
    }

    @Test
    fun withUnmappedExercisesUntrainedRegionsAreUnmappedNotNone() {
        val summary = WeeklyMuscleSummary.from(
            "2026-W46",
            listOf(
                MuscleWeekRow("lats", 6, "Lat Pulldown", 12f),
                MuscleWeekRow(WeeklyMuscleSummary.UNMAPPED_KEY, 5, "Dead Hang", 3f),
            ),
        )
        assertEquals(listOf(UnmappedExercise(5, "Dead Hang", 3)), summary.unmapped)
        assertEquals(MuscleLevel.HIGH, summary.levels.getValue(Muscle.LATS))
        assertEquals(MuscleLevel.UNMAPPED, summary.levels.getValue(Muscle.QUADS))
    }

    @Test
    fun unknownMuscleKeysFromANewerAppAreIgnored() {
        val summary = WeeklyMuscleSummary.from("2026-W46", listOf(MuscleWeekRow("neckFlexors", 1, "Neck curl", 3f)))
        assertEquals(MuscleLevel.NONE, summary.levels.getValue(Muscle.ABS))
    }
}
