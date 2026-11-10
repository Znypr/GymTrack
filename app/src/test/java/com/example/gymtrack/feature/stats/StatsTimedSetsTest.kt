package com.example.gymtrack.feature.stats

import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.util.WorkoutParser
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #283: timed sets (`60s`, `45s 10`) count in Stats, in seconds, never as 0 kg × 0 reps. */
class StatsTimedSetsTest {
    private val parser = WorkoutParser()

    @Test
    fun timedSetsCountAsSetsWithTheirTime() {
        val day1 = note("Plank\n      60s\n      45s", day = 1)
        val day2 = note("Squat\n      5x 100\nPlank\n      75s", day = 3)
        val state = build(day1, day2)

        assertEquals(3, state.timedSets.setCount)
        assertEquals(180, state.timedSets.totalSeconds)
        // Avg Sets counts every set row: 2 + (1 + 1) sets over 3 exercises.
        assertEquals(4f / 3f, state.avgSets, 0.001f)
        assertTrue(state.topExercises.any { it.first.contains("Plank", ignoreCase = true) && it.second == 3 })
    }

    @Test
    fun bodyweightHoldIsChartedInSecondsNotKilograms() {
        val state = build(note("Plank\n      60s\n      40s", day = 1), note("Plank\n      90s", day = 3))
        val group = state.timedProgress.single()

        assertEquals(TimedProgressMetric.HOLD_SECONDS, group.metric)
        assertEquals("s", group.unitLabel)
        assertEquals(listOf(50f, 90f), group.series.single().points.map { it.value })
        assertTrue(group.series.single().points.none { it.value == 0f })
    }

    @Test
    fun weightedHoldIsChartedAsLoadTimesSecondsInTheDisplayUnit() {
        val kg = build(note("Plank\n      45s 10kg\n      30s 20kg", day = 1), displayUnit = WeightUnit.KG)
        val group = kg.timedProgress.single()
        assertEquals(TimedProgressMetric.LOAD_SECONDS, group.metric)
        assertEquals("kg·s", group.unitLabel)
        // (45 × 10 + 30 × 20) / 2 sets
        assertEquals(525f, group.series.single().points.single().value, 0.01f)

        val lb = build(note("Plank\n      45s 10kg", day = 1), displayUnit = WeightUnit.LB)
        assertEquals("lb·s", lb.timedProgress.single().unitLabel)
        assertEquals(45 * 10 / KG_PER_LB, lb.timedProgress.single().series.single().points.single().value.toDouble(), 0.1)
    }

    @Test
    fun mixedLoadedAndUnloadedHoldsFallBackToSeconds() {
        val state = build(note("Plank\n      60s\n      45s 10kg", day = 1))
        val group = state.timedProgress.single()
        // One honest unit for the whole chart: seconds, which every timed set has.
        assertEquals(TimedProgressMetric.HOLD_SECONDS, group.metric)
        assertEquals(52.5f, group.series.single().points.single().value, 0.01f)
    }

    @Test
    fun timedSetsDoNotChangeStrengthOrReps() {
        val a = note("Squat\n      5x 100\nPlank\n      60s 20kg", day = 1)
        val b = note("Squat\n      5x 110\nPlank\n      60s 20kg", day = 8)
        val row = buildTrainingInsights(listOf(a, b), parser, WeightUnit.KG, mapOf(
            a.timestamp to listOf(WeightUnit.KG, WeightUnit.KG),
            b.timestamp to listOf(WeightUnit.KG, WeightUnit.KG),
        )).first()
        // Strength is load per rep: only the squat sets, the hold is not a 0-rep set.
        assertEquals(100f, row.strength.baseline, 0.01f)
        assertEquals(110f, row.strength.recent, 0.01f)
        assertEquals(5f, row.reps.baseline, 0.01f)
        assertEquals(0, row.unknownUnitSets)
    }

    @Test
    fun formatsHoldTime() {
        assertEquals("1:35", formatHoldTime(95))
        assertEquals("1:02:05", formatHoldTime(3725))
    }

    private fun build(vararg notes: NoteLine, displayUnit: WeightUnit = WeightUnit.KG): StatsState {
        val facts = StatsWorkoutFacts(completedTimestamps = notes.map { it.timestamp }.toSet())
        return buildStatsState(notes.toList(), TimeRange.ALL_TIME, facts, displayUnit, notes.maxOf { it.timestamp } + 1, parser)
    }

    private fun note(text: String, day: Int): NoteLine = NoteLine(
        title = "Workout",
        text = text,
        timestamp = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.MARCH, day, 18, 0)
        }.timeInMillis,
        categoryName = "Core",
    )
}
