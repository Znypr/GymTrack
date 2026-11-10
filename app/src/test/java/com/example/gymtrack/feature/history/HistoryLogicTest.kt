package com.example.gymtrack.feature.history

import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.util.ParsedSetDTO
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryLogicTest {
    private val zone = ZoneId.of("Europe/Berlin")

    private fun workout(date: LocalDate, category: String?, text: String, learnings: String = "", title: String = "") = NoteLine(
        title = title,
        text = text,
        timestamp = date.atTime(18, 0).atZone(zone).toInstant().toEpochMilli(),
        categoryName = category,
        learnings = learnings,
    )

    private val push = workout(LocalDate.of(2026, 11, 7), "Push", "Bench press\n      8x 80\n\nLateral raise\n      12x 10")
    private val legs = workout(LocalDate.of(2026, 11, 5), "Legs", "Squat\n      5x 110", learnings = "knee felt fine")
    private val pull = workout(LocalDate.of(2026, 10, 30), "Pull", "Lat pulldown\n      10x 65")
    private val all = listOf(pull, push, legs)

    @Test
    fun `empty filter shows everything newest first`() {
        assertEquals(listOf(push, legs, pull), filterHistory(all, HistoryFilter()))
    }

    @Test
    fun `sort can be reversed`() {
        assertEquals(listOf(pull, legs, push), filterHistory(all, HistoryFilter(newestFirst = false)))
    }

    @Test
    fun `search matches exercise names case-insensitively`() {
        assertEquals(listOf(push), filterHistory(all, HistoryFilter(query = "BENCH")))
        assertEquals(listOf(pull), filterHistory(all, HistoryFilter(query = "pulldown")))
    }

    @Test
    fun `search matches learnings and needs every word`() {
        assertEquals(listOf(legs), filterHistory(all, HistoryFilter(query = "knee")))
        assertEquals(listOf(legs), filterHistory(all, HistoryFilter(query = "  squat   knee ")))
        assertEquals(emptyList<NoteLine>(), filterHistory(all, HistoryFilter(query = "squat bench")))
    }

    @Test
    fun `search matches category and title`() {
        val titled = workout(LocalDate.of(2026, 11, 1), null, "Run", title = "Morning cardio")
        assertEquals(listOf(titled), filterHistory(all + titled, HistoryFilter(query = "cardio")))
        assertEquals(listOf(legs), filterHistory(all, HistoryFilter(query = "legs")))
    }

    @Test
    fun `category chip filters and combines with search`() {
        assertEquals(listOf(push), filterHistory(all, HistoryFilter(category = "push")))
        assertEquals(emptyList<NoteLine>(), filterHistory(all, HistoryFilter(query = "squat", category = "Push")))
    }

    @Test
    fun `month sections keep list order`() {
        val sections = groupByMonth(filterHistory(all, HistoryFilter()), zone)
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10)), sections.map { it.first })
        assertEquals(listOf(push, legs), sections.first().second)
    }

    private fun set(name: String, reps: Int, weight: Float, unit: String = "KG", duration: Int? = null) = ParsedSetDTO(
        exerciseName = name,
        weight = weight,
        weightUnit = unit,
        reps = reps,
        durationSeconds = duration,
        isUnilateral = false,
        modifier = null,
        brand = null,
        relativeTime = null,
        absoluteTime = null,
    )

    @Test
    fun `top set is the heaviest, then most reps, then the first`() {
        val top = topSet(
            listOf(
                set("Bench", 8, 80f),
                set("Bench", 10, 80f),
                set("Fly", 12, 20f),
                set("Plank", 0, 0f, duration = 60),
            ),
        )
        assertEquals(TopSet("Bench", 10, 80f, WeightUnit.KG), top)
        assertNull(topSet(listOf(set("Plank", 1, 0f, duration = 60))))
    }

    @Test
    fun `top set reads REPS x WEIGHT and shows the unit only when not default`() {
        assertEquals("Bench 8 × 80", formatTopSet(TopSet("Bench", 8, 80f, WeightUnit.KG), WeightUnit.KG))
        assertEquals("Bench 8 × 82.5 lb", formatTopSet(TopSet("Bench", 8, 82.5f, WeightUnit.LB), WeightUnit.KG))
    }

    @Test
    fun `chips include stored categories missing from settings`() {
        val custom = workout(LocalDate.of(2026, 11, 2), "Cardio", "Run")
        val chips = chipCategories(listOf(Category("Push", 1L), Category("Pull", 2L)), all + custom)
        assertEquals(listOf("Push", "Pull", "Legs", "Cardio"), chips.map { it.name })
    }

    @Test
    fun `wording says workouts and the sort says its order`() {
        assertEquals("1 workout", workoutCount(1))
        assertEquals("3 workouts", workoutCount(3))
        assertEquals("Newest first", sortLabel(true))
        assertEquals("Oldest first", sortLabel(false))
    }
}
