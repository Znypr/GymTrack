package com.example.gymtrack.feature.finish

import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.WeightUnit
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinishSummaryTest {
    private val zone = ZoneOffset.UTC

    /** Mon 9 Nov 2026 18:00 UTC. */
    private val monday = LocalDateTime.of(2026, 11, 9, 18, 0).toInstant(zone).toEpochMilli()
    private val minute = 60_000L
    private val day = 24 * 60 * minute

    private fun workout(text: String, category: String? = "Push", start: Long = monday) =
        NoteLine(title = "Evening", text = text, timestamp = start, categoryName = category)

    private fun build(
        note: NoteLine,
        unit: WeightUnit = WeightUnit.KG,
        finished: Collection<Long> = emptyList(),
        now: Long = note.timestamp + 64 * minute,
        finishedAt: Long? = note.timestamp + 64 * minute,
    ) = buildFinishSummary(note, unit, finished, now, finishedAt, zone)

    @Test
    fun headlineCountsExercisesSetsAndMinutes() {
        val note = workout(
            "Bench Press\n      8x 80\n      8x 82.5\n      6x 85\n" +
                "Incline Dumbbell Press\n      10x 30\n      10x 30\n" +
                "Triceps Pushdown\n      12x 25\n      12x 25\n" +
                "Lateral Raise\n      15x 10\n      15x 10\n      15x 10",
        )
        val summary = build(note)

        assertEquals(4, summary.exerciseCount)
        assertEquals(10, summary.setCount)
        assertEquals(64, summary.durationMinutes)
        assertEquals("Push · 64 min · 4 exercises · 10 sets", summary.headline)
    }

    @Test
    fun topSetIsHeaviestThenMostRepsInRepsTimesWeight() {
        val summary = build(workout("Bench Press\n      8x 80\n      6x 85\n      8x 85\n      10x 60"))
        assertEquals("8 × 85", summary.topSets.single().display)
    }

    @Test
    fun topSetShowsUnitOnlyWhenItDiffersFromTheDefault() {
        val summary = build(workout("Bench Press\n      8x 80\n      5x 185lb"), unit = WeightUnit.KG)
        // 185 lb = 83.9 kg > 80 kg; typed in lb, default kg → unit shown.
        assertEquals("5 × 185 lb", summary.topSets.single().display)

        val inLb = build(workout("Bench Press\n      5x 185"), unit = WeightUnit.LB)
        assertEquals("5 × 185", inLb.topSets.single().display)
    }

    @Test
    fun timedSetsAreNeverShownAsWeightTimesReps() {
        val summary = build(workout("Plank\n      60s\n      45s 10", category = "Core"))
        val top = summary.topSets.single()
        assertEquals("60s", top.display)
        assertEquals(2, summary.setCount)
        assertEquals("Core", summary.title)
    }

    @Test
    fun unloadedRepSetsShowReps() {
        val summary = build(workout("Pull Up\n      8x\n      10x", category = "Pull"))
        assertEquals("10 reps", summary.topSets.single().display)
    }

    @Test
    fun thisWeekCountsFinishedWorkoutsOfTheCurrentMondayWeekIncludingThisOne() {
        val note = workout("Squat\n      5x 100", category = "Legs")
        val lastSunday = monday - day // Sun 8 Nov: previous week
        val laterThisWeek = monday + 2 * day // Wed 11 Nov (clock skew / imported)
        val summary = build(note, finished = listOf(lastSunday, laterThisWeek, note.timestamp))

        // Mon (this one) + Wed; Sunday belongs to last week; this workout is not counted twice.
        assertEquals(2, summary.workoutsThisWeek)
        assertEquals("This week: 2 workouts", summary.weekLine)

        assertEquals("This week: 1 workout", build(note).weekLine)
    }

    @Test
    fun unknownDurationIsLeftOutAndTitleFallsBack() {
        val note = workout("Squat\n      5x 100", category = null).copy(title = "")
        val summary = buildFinishSummary(note, WeightUnit.KG, emptyList(), note.timestamp, finishedAtMillis = null, zone = zone)
        assertNull(summary.durationMinutes)
        assertEquals("Workout · 1 exercise · 1 set", summary.headline)
    }
}
