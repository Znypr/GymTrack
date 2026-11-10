package com.example.gymtrack.feature.stats

import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.util.WorkoutParser
import com.example.gymtrack.core.util.buildNoteRowMetadata
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #326 (units) and #322 (finished-only counts), rules in docs/STATS_RULES.md. */
class StatsUnitsAndCompletionTest {
    private val parser = WorkoutParser()

    @Test
    fun convertWeightUsesTheExactPound() {
        assertEquals(45.359237, convertWeight(100.0, WeightUnit.LB, WeightUnit.KG), 1e-9)
        assertEquals(220.462, convertWeight(100.0, WeightUnit.KG, WeightUnit.LB), 1e-3)
        assertEquals(80.0, convertWeight(80.0, WeightUnit.KG, WeightUnit.KG), 0.0)
        assertEquals(225f, kilogramsToDisplay(102.0582f, WeightUnit.LB), 0.01f)
    }

    @Test
    fun storedUnitsGiveKeypadSetsTheirRealUnit() {
        val lbDay = note("Bench Press\n    8x 225\n    8x 225", day = 8)
        val resolved = resolveSetUnits(lbDay, parser, listOf(WeightUnit.LB, WeightUnit.LB))
        assertEquals(listOf(WeightUnit.LB, WeightUnit.LB), resolved.map { it.unit })
    }

    @Test
    fun withoutStoredUnitsOnlyExplicitUnitsAreKnown() {
        val resolved = resolveSetUnits(note("Bench Press\n    8x 100\n    5x 60kg"), parser, storedUnits = null)
        assertNull(resolved[0].unit)
        assertEquals(WeightUnit.KG, resolved[1].unit)
    }

    @Test
    fun mixedUnitVolumeIsConvertedBeforeComparing() {
        // 100 kg before the switch, 225 lb (= 102.06 kg) after: a +2 % change, not +125 %.
        val kgDay = note("Bench Press\n    8x 100\n    8x 100", day = 1)
        val lbDay = note("Bench Press\n    8x 225\n    8x 225", day = 8)
        val stored = mapOf(
            kgDay.timestamp to listOf(WeightUnit.KG, WeightUnit.KG),
            lbDay.timestamp to listOf(WeightUnit.LB, WeightUnit.LB),
        )

        val inKg = buildTrainingInsights(listOf(kgDay, lbDay), parser, WeightUnit.KG, stored).first().strength
        assertEquals(100f, inKg.baseline, 0.01f)
        assertEquals(102.06f, inKg.recent, 0.01f)
        assertEquals("kg", inKg.unit)

        val inLb = buildTrainingInsights(listOf(kgDay, lbDay), parser, WeightUnit.LB, stored).first().strength
        assertEquals(220.46f, inLb.baseline, 0.01f)
        assertEquals(225f, inLb.recent, 0.01f)
        assertEquals("lb", inLb.unit)
    }

    @Test
    fun setsWithUnknownUnitAreLeftOutAndCounted() {
        val known = note("Bench Press\n    8x 100kg", day = 1)
        val unknown = note("Bench Press\n    8x 225", day = 8)
        val row = buildTrainingInsights(listOf(known, unknown), parser, WeightUnit.KG).first()
        assertEquals(1, row.unknownUnitSets)
        // The unknown workout says nothing about load: no fake drop to 0 kg.
        assertEquals(0f, row.strength.recent, 0f)
        assertTrue(row.strength.baseline > 0f)
    }

    @Test
    fun onlyFinishedWorkoutsAreCountedAndUnfinishedStayVisible() {
        val finishedA = note("Squat\n    5x 100", day = 1)
        val finishedB = note("Squat\n    5x 105", day = 8)
        val draft = note("Squat\n    5x 110", day = 9)
        val facts = StatsWorkoutFacts(
            completedTimestamps = setOf(finishedB.timestamp),
            storedSetUnits = mapOf(finishedA.timestamp to listOf(WeightUnit.KG)),
        )

        val state = buildStatsState(
            listOf(finishedA, finishedB, draft), TimeRange.ALL_TIME, facts, WeightUnit.KG,
            nowMillis = draft.timestamp + 1, parser = parser,
        )

        assertEquals(2, state.totalNotes)
        assertEquals(1, state.unfinishedCount)
        assertEquals(2, state.weeklyWorkoutCounts.sumOf { it.count })
        assertEquals(listOf(finishedA.timestamp, finishedB.timestamp), state.filteredNotes.map { it.timestamp })
        assertEquals(2, state.categoryCounts.values.sum())
        assertEquals(2, state.heatmapData.sumOf { it.sum() })
        assertEquals(2 / (7f / 7f), state.avgWorkoutsPerWeek, 0.01f)
    }

    @Test
    fun allUnfinishedGivesZeroWorkoutsButReportsThem() {
        val draft = note("Squat\n    5x 110", day = 9)
        val state = buildStatsState(listOf(draft), TimeRange.ALL_TIME, StatsWorkoutFacts(), WeightUnit.LB, draft.timestamp + 1, parser)
        assertEquals(0, state.totalNotes)
        assertEquals(1, state.unfinishedCount)
        assertEquals(WeightUnit.LB, state.displayUnit)
    }

    private fun note(text: String, day: Int = 1): NoteLine {
        val lines = text.lines()
        return NoteLine(
            title = "Workout",
            text = text,
            timestamp = Calendar.getInstance().apply {
                clear()
                set(2026, Calendar.MARCH, day, 18, 0)
            }.timeInMillis,
            categoryName = "Legs",
            rowMetadata = buildNoteRowMetadata(
                times = List(lines.size) { if (it == 0) "0'00''" else "${it * 2}'00''" },
                flags = List(lines.size) { ExerciseFlag.BILATERAL },
            ),
        )
    }
}
