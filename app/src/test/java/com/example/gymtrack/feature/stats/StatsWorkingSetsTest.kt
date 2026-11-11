package com.example.gymtrack.feature.stats

import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.SetType
import com.example.gymtrack.core.data.StoredSetUnitRow
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.util.WorkoutParser
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #306: Stats count WORKING sets only; the user's choices win over the inference. */
class StatsWorkingSetsTest {
    private val parser = WorkoutParser()
    private val day = TimeUnit.DAYS.toMillis(1)
    private val start = 1_700_000_000_000L

    private fun note(text: String, dayIndex: Int) =
        NoteLine("W$dayIndex", text, start + dayIndex * day, "Push", 0L, "", "")

    // Same working sets both days; day 2 adds three warm-ups.
    private val plain = note("Bench Press\n    5x 100\n    5x 100\n    5x 100", 0)
    private val withWarmUps = note("Bench Press\n    10x 20\n    5x 60\n    3x 80\n    5x 100\n    5x 100\n    5x 100", 7)
    private val notes = listOf(plain, withWarmUps)
    private val kg = mapOf(
        plain.timestamp to List(3) { WeightUnit.KG },
        withWarmUps.timestamp to List(6) { WeightUnit.KG },
    )

    @Test
    fun digestMarksTheRampAsWarmUp() {
        val digest = StatsDigestCache().digest(withWarmUps, kg[withWarmUps.timestamp], parser)
        assertEquals(
            listOf(SetType.WARMUP, SetType.WARMUP, SetType.WARMUP, SetType.WORKING, SetType.WORKING, SetType.WORKING),
            digest.sets.map { it.type },
        )
        assertEquals(3, digest.workingSets.size)
    }

    @Test
    fun warmUpsDoNotChangeStrengthRepsOrSetCounts() {
        val row = buildTrainingInsights(notes, parser, WeightUnit.KG, kg).first()
        assertEquals(100f, row.strength.baseline, 0.01f)
        assertEquals(100f, row.strength.recent, 0.01f)
        assertEquals(row.reps.baseline, row.reps.recent, 0.01f)
        assertEquals(row.setDepth.baseline, row.setDepth.recent, 0.01f)
    }

    @Test
    fun topExercisesCountWorkingSets() {
        val facts = StatsWorkoutFacts(completedTimestamps = notes.map { it.timestamp }.toSet(), storedSetUnits = kg)
        val state = buildStatsState(notes, TimeRange.ALL_TIME, facts, WeightUnit.KG, start + 30 * day)
        assertEquals(6, state.topExercises.single().second)
    }

    @Test
    fun userChoiceOverridesTheInference() {
        val rows = (0 until 6).map { index ->
            StoredSetUnitRow(
                workoutId = withWarmUps.timestamp,
                weightUnit = "KG",
                // The user says the 80 × 3 was a real (working) set.
                setType = if (index == 2) "WORKING" else "WARMUP",
                setTypeSource = if (index == 2) "USER" else "INFERRED",
            )
        } + List(3) { StoredSetUnitRow(plain.timestamp, "KG") }
        val facts = StatsWorkoutFacts.fromStoredRows(notes.map { it.timestamp }, rows)
        assertEquals(listOf(null, null, SetType.WORKING, null, null, null), facts.userSetTypes[withWarmUps.timestamp])
        assertTrue(plain.timestamp !in facts.userSetTypes)

        val digests = StatsDigestCache().digestAll(listOf(withWarmUps), facts)
        assertEquals(4, digests.single().workingSets.size)

        // A user choice changes the cache key, so a stale Stats state is never served.
        val inferredOnly = facts.copy(userSetTypes = emptyMap())
        assertNotEquals(inferredOnly.signature(), facts.signature())
    }
}
