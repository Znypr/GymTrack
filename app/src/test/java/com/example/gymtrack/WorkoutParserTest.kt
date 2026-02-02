package com.example.gymtrack

import com.example.gymtrack.core.util.WorkoutParser
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutParserTest {

    private val parser = WorkoutParser()

    @Test
    fun `parses standard entry 5x 100kg correctly`() {
        val rawText = """
            Bench Press
                5x 100kg
                5x 100kg
        """.trimIndent()

        val results = parser.parseWorkout(rawText)

        assertEquals(2, results.size)
        // First set
        assertEquals("Bench Press", results[0].exerciseName)
        assertEquals(100f, results[0].weight, 0.01f)
        assertEquals(5, results[0].reps)
    }

    @Test
    fun `handles shortcut entry (missing weight carries over)`() {
        // THIS is the scenario that was failing before because my test data was wrong.
        // It now uses your actual format:
        // Set 1: "5x 100kg" -> defines weight as 100
        // Set 2: "5x"       -> should inherit 100
        val rawText = """
            Bench Press
                5x 100kg
                5x
        """.trimIndent()

        val results = parser.parseWorkout(rawText)

        assertEquals(2, results.size)

        // Set 1: Explicit weight
        assertEquals(100f, results[0].weight, 0.01f)
        assertEquals(5, results[0].reps)

        // Set 2: Inherited weight
        assertEquals("Should carry over weight from previous set", 100f, results[1].weight, 0.01f)
        assertEquals(5, results[1].reps)
    }

    @Test
    fun `resets weight when exercise changes`() {
        val rawText = """
            Bench Press
                5x 100kg
            
            Squat
                5x
        """.trimIndent()

        val results = parser.parseWorkout(rawText)

        assertEquals(1, results.size)
        // Should ONLY parse Bench Press.
        // Squat has "5x" but no weight defined yet, so it should be skipped (or 0),
        // because we don't carry over weight across different exercises.
        assertEquals("Bench Press", results[0].exerciseName)
        assertEquals(100f, results[0].weight, 0.01f)
    }
}