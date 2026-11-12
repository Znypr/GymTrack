package com.example.gymtrack.feature.finish

import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.muscle.Muscle
import com.example.gymtrack.core.muscle.MuscleExerciseSets
import com.example.gymtrack.core.muscle.MuscleLevel
import com.example.gymtrack.core.muscle.MuscleLoadSource
import com.example.gymtrack.core.muscle.MuscleWeek
import com.example.gymtrack.core.muscle.UnmappedMuscleLoadSource
import com.example.gymtrack.feature.stats.ThisWeekText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Finish sheet and "This week" card wording for the body map (#311). */
class FinishMusclesTest {
    private val note = NoteLine(title = "Evening", text = "Squat\n      5x 100\n      5x 100", timestamp = 1_000L, categoryName = "Legs")

    private fun week(sets: Map<Muscle, Float>, unmapped: Set<Muscle> = emptySet()) = MuscleWeek(
        levels = Muscle.entries.associateWith { m ->
            if (m in unmapped) MuscleLevel.UNMAPPED else MuscleLevel.forWeeklySets(sets[m] ?: 0f)
        },
        sets = sets,
        exercises = sets.mapValues { (_, s) -> listOf(MuscleExerciseSets("Squat", s)) },
    )

    @Test
    fun theTemporarySourceMapsNothingAndSuggestsNothing() {
        val summary = buildFinishSummary(note, WeightUnit.KG, emptyList(), nowMillis = 2_000L)
        assertTrue(summary.muscles.isAllUnmapped)
        assertNull(summary.nextTime)
        assertTrue(summary.setsPerMuscle.isEmpty())
        assertTrue(runBlocking { UnmappedMuscleLoadSource.weekLevels("2026-W46") }.isAllUnmapped)
    }

    @Test
    fun setsPerMuscleAndNextTimeComeFromTheLoadedMuscles() {
        val muscles = week(mapOf(Muscle.QUADS to 2f, Muscle.GLUTE_MAX to 4f, Muscle.ADDUCTORS to 1f))
        val summary = buildFinishSummary(note, WeightUnit.KG, emptyList(), nowMillis = 2_000L, muscles = muscles)
        assertEquals(listOf(Muscle.GLUTE_MAX, Muscle.QUADS, Muscle.ADDUCTORS), summary.setsPerMuscle.map { it.first })
        // Untrained big movers come first on ties: hamstrings before anything else at 0 sets.
        assertEquals(Muscle.HAMSTRINGS, summary.nextTime)
    }

    @Test
    fun nextTimeUsesTheWeekWhenTheHostHasItAndNeverSuggestsUnmapped() {
        val all = Muscle.entries.associateWith { 6f }
        val w = week(all - Muscle.TIBIALIS + (Muscle.SOLEUS to 1f), unmapped = setOf(Muscle.TIBIALIS))
        assertEquals(Muscle.SOLEUS, nextMuscleSuggestion(w))
        val summary = buildFinishSummary(note, WeightUnit.KG, emptyList(), nowMillis = 2_000L, weekMuscles = w)
        assertEquals(Muscle.SOLEUS, summary.nextTime)
    }

    @Test
    fun thisWeekLinesAreHonest() {
        assertEquals(
            "Muscles aren't mapped for your exercises yet, so nothing is marked as trained.",
            ThisWeekText.notTrainedLine(MuscleWeek.unmapped),
        )
        val w = week(Muscle.entries.associateWith { 3f } - Muscle.GASTROCNEMIUS, unmapped = setOf(Muscle.TIBIALIS))
        assertEquals("Not trained yet: Calves", ThisWeekText.notTrainedLine(w))
        assertEquals("Quads · 3 sets", ThisWeekText.detailHeadline(w, Muscle.QUADS))
        assertEquals("Calves · no sets this week", ThisWeekText.detailHeadline(w, Muscle.GASTROCNEMIUS))
        assertEquals("Shins · not mapped yet", ThisWeekText.detailHeadline(w, Muscle.TIBIALIS))
        val everything = week(Muscle.entries.associateWith { 3f })
        assertEquals("Every mapped muscle trained this week.", ThisWeekText.notTrainedLine(everything))
    }

}
