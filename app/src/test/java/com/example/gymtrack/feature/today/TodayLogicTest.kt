package com.example.gymtrack.feature.today

import com.example.gymtrack.core.data.NoteLine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodayLogicTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val categories = listOf("Push", "Pull", "Legs")

    /** Tue 10 Nov 2026 (the design docs call it Mon; the calendar says Tuesday). */
    private val today = LocalDate.of(2026, 11, 10)

    private fun workout(date: LocalDate, category: String?, hour: Int = 18) = NoteLine(
        title = "",
        text = "Bench\n      8x 80",
        timestamp = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
        categoryName = category,
    )

    private fun sequence(vararg cats: String): List<NoteLine> =
        cats.mapIndexed { index, cat -> workout(today.minusDays((cats.size - index).toLong() * 2), cat) }

    @Test
    fun `no categorised history gives no guess`() {
        assertNull(guessNextCategory(emptyList(), categories))
        assertNull(guessNextCategory(listOf(workout(today, null)), categories))
    }

    @Test
    fun `design example - Push last, next is Pull`() {
        val notes = listOf(
            workout(LocalDate.of(2026, 11, 3), "Pull"),
            workout(LocalDate.of(2026, 11, 5), "Legs"),
            workout(LocalDate.of(2026, 11, 7), "Push"),
        )
        assertEquals("Pull", guessNextCategory(notes, categories))
    }

    @Test
    fun `default rotation fills in when history has no follower yet`() {
        assertEquals("Pull", guessNextCategory(sequence("Push"), categories))
        assertEquals("Legs", guessNextCategory(sequence("Pull"), categories))
        assertEquals("Push", guessNextCategory(sequence("Legs"), categories))
    }

    @Test
    fun `learned order beats the default rotation`() {
        // This user does Push → Legs → Pull.
        val notes = sequence("Push", "Legs", "Pull", "Push", "Legs", "Pull", "Push")
        assertEquals("Legs", guessNextCategory(notes, categories))
    }

    @Test
    fun `ties go to the default rotation order`() {
        // After Push: once Pull, once Legs → Pull (Push's default successor).
        val notes = sequence("Push", "Legs", "Push", "Pull", "Push")
        assertEquals("Pull", guessNextCategory(notes, categories))
    }

    @Test
    fun `custom category without follower falls back to the first other category`() {
        assertEquals("Push", guessNextCategory(sequence("Cardio"), categories + "Cardio"))
    }

    @Test
    fun `guess is case-insensitive and deterministic`() {
        val notes = sequence("push", "Pull", "PUSH")
        assertEquals("Pull", guessNextCategory(notes, categories))
        assertEquals(guessNextCategory(notes, categories), guessNextCategory(notes.shuffled(), categories))
    }

    @Test
    fun `next up line lists usual days and how long ago`() {
        val notes = listOf(
            workout(LocalDate.of(2026, 10, 20), "Pull"), // Tue
            workout(LocalDate.of(2026, 10, 27), "Pull"), // Tue
            workout(LocalDate.of(2026, 10, 30), "Pull"), // Fri
            workout(LocalDate.of(2026, 11, 6), "Pull"), // Fri
            workout(LocalDate.of(2026, 11, 7), "Push"),
        )
        val next = nextUp(notes, categories, today, zone)!!
        assertEquals("Pull", next.category)
        assertEquals(listOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY), next.usualDays)
        assertEquals(4L, next.lastDaysAgo)
        assertEquals("Next up: Pull · usually Tue/Fri · last 4 days ago", nextUpLine(next, Locale.ENGLISH))
    }

    @Test
    fun `week strip marks done, planned and rest`() {
        // Usual days in the 4 weeks before this week: Monday and Wednesday (twice each).
        val notes = listOf(
            workout(LocalDate.of(2026, 10, 19), "Push"), // Mon
            workout(LocalDate.of(2026, 10, 26), "Push"), // Mon
            workout(LocalDate.of(2026, 10, 21), "Legs"), // Wed
            workout(LocalDate.of(2026, 11, 4), "Legs"), // Wed
            workout(LocalDate.of(2026, 11, 10), "Pull"), // today (Tue 10 Nov)
        )
        val week = weekStrip(notes, today, zone)
        assertEquals(7, week.size)
        assertEquals(LocalDate.of(2026, 11, 9), week.first().date) // Monday start
        assertEquals(
            listOf(DayStatus.REST, DayStatus.DONE, DayStatus.PLANNED, DayStatus.REST, DayStatus.REST, DayStatus.REST, DayStatus.REST),
            week.map { it.status },
        )
        assertEquals(listOf(false, true, false, false, false, false, false), week.map { it.isToday })
    }

    @Test
    fun `recent is the last three, newest first`() {
        val notes = sequence("Push", "Pull", "Legs", "Push")
        assertEquals(notes.takeLast(3).reversed(), recentWorkouts(notes))
    }
}
