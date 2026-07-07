package com.example.gymtrack.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutParserWeightUnitTest {
    private val parser = WorkoutParser()

    @Test
    fun numericWeightUsesDefaultPoundsWhenUnitIsOmitted() {
        val set = parser.parseWorkout(
            rawText = "Bench Press\n    5x 100",
            defaultWeightUnit = "LB",
        ).single()

        assertEquals(100f, set.weight)
        assertEquals(5, set.reps)
        assertEquals("LB", set.weightUnit)
    }

    @Test
    fun explicitKilogramsOverrideDefaultPounds() {
        val set = parser.parseWorkout(
            rawText = "Bench Press\n    5x 100 kg",
            defaultWeightUnit = "LB",
        ).single()

        assertEquals(100f, set.weight)
        assertEquals(5, set.reps)
        assertEquals("KG", set.weightUnit)
    }

    @Test
    fun carriedWeightKeepsExplicitUnit() {
        val sets = parser.parseWorkout(
            rawText = "Bench Press\n    5x 100 lb\n    8x",
            defaultWeightUnit = "KG",
        )

        assertEquals(2, sets.size)
        assertEquals("LB", sets[0].weightUnit)
        assertEquals("LB", sets[1].weightUnit)
        assertEquals(100f, sets[1].weight)
        assertEquals(8, sets[1].reps)
    }
}
