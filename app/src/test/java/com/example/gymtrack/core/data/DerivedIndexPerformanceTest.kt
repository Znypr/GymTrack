package com.example.gymtrack.core.data

import com.example.gymtrack.core.util.WorkoutParser
import com.example.gymtrack.simulation.EditorTranscript
import com.example.gymtrack.simulation.WorkoutHistoryGenerator
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #307 budget check on the seeded 1,000-workout history: the per-workout work Finish adds
 * (classify + derived rows) and the CPU side of the lazy rebuild. Room I/O is not measured here
 * (no JVM Room in this project); the Macrobenchmark gate is a follow-up issue.
 */
class DerivedIndexPerformanceTest {

    @Test
    fun finalizeAndRebuildStayWithinBudget() {
        val history = WorkoutHistoryGenerator(seed = 11L, months = 72).generate()
        val notes = history.workouts.take(1_000).map { EditorTranscript.build(it).finalNote }
        val parser = WorkoutParser()
        val exerciseIds = HashMap<String, Long>()

        // Warm the JIT the way an app session would have, then measure.
        repeat(2) { derive(notes.take(100), parser, exerciseIds) }

        val start = System.nanoTime()
        val totals = derive(notes, parser, exerciseIds)
        val totalMs = (System.nanoTime() - start) / 1_000_000.0
        val perWorkoutMs = totalMs / notes.size

        println(
            "DERIVED-BENCH ${notes.size} workouts, ${totals.sets} sets: rebuild CPU %.0f ms, %.3f ms per workout; ".format(totalMs, perWorkoutMs) +
                "warm-ups ${totals.warmUps} (%.1f %%), recent rows ${totals.recentRows}, muscle rows ${totals.muscleRows}"
                    .format(100.0 * totals.warmUps / totals.sets.coerceAtLeast(1)),
        )
        // Finish adds one workout's worth: budget +50 ms on a device; the JVM figure must be far below.
        assertTrue("per workout $perWorkoutMs ms", perWorkoutMs < 5.0)
        assertTrue("rebuild $totalMs ms", totalMs < 5_000.0)
    }

    private data class Totals(val sets: Int, val warmUps: Int, val recentRows: Int, val muscleRows: Int)

    private fun derive(
        notes: List<com.example.gymtrack.core.data.NoteEntity>,
        parser: WorkoutParser,
        exerciseIds: MutableMap<String, Long>,
    ): Totals {
        var sets = 0
        var warmUps = 0
        var recentRows = 0
        var muscleRows = 0
        for (note in notes) {
            val parsed = parser.parseWorkout(note.text, rowMetadata = note.rowMetadata)
            val rows = parsed.map { set ->
                SetEntity(
                    workoutId = note.timestamp,
                    exerciseId = exerciseIds.getOrPut(set.exerciseIdentity.canonicalName) { exerciseIds.size + 1L },
                    weight = set.weight,
                    reps = set.reps,
                    isUnilateral = set.isUnilateral,
                    modifier = set.modifier,
                    brand = set.brand,
                    relativeTime = set.relativeTime,
                    absoluteTime = set.absoluteTime,
                    weightUnit = set.weightUnit,
                )
            }
            val classified = DerivedIndexWriter.classify(rows, parsed.map { it.rowOffsetSeconds })
            sets += classified.size
            warmUps += classified.count { it.setType == SetType.WARMUP.storageValue }
            recentRows += DerivedIndexWriter.recentRows(note.timestamp, classified).size
            muscleRows += DerivedIndexWriter.muscleWeekRows(note.timestamp, classified, { null }).size
        }
        return Totals(sets, warmUps, recentRows, muscleRows)
    }
}
