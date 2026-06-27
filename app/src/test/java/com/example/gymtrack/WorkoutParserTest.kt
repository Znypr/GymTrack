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
        assertEquals("Bench press", results[0].exerciseName)
        assertEquals(100f, results[0].weight, 0.01f)
        assertEquals(5, results[0].reps)
    }

    @Test
    fun `handles shortcut entry with inherited weight`() {
        val rawText = """
            Bench Press
                5x 100kg
                5x
        """.trimIndent()

        val results = parser.parseWorkout(rawText)

        assertEquals(2, results.size)
        assertEquals(100f, results[0].weight, 0.01f)
        assertEquals(5, results[0].reps)
        assertEquals(100f, results[1].weight, 0.01f)
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
        assertEquals("Bench press", results[0].exerciseName)
        assertEquals(100f, results[0].weight, 0.01f)
    }
}
