package com.example.gymtrack.feature.today

import com.example.gymtrack.core.data.NoteLine
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/*
 * Today tab logic. Pure and cheap (README rules 7 and 9): one pass over the workout list, no
 * parsing, no database queries.
 */

/** The default rotation, used when history has no answer yet. */
val DEFAULT_ROTATION = listOf("Push", "Pull", "Legs")

/** How many recent workouts the rotation guess learns from. */
private const val ROTATION_WINDOW = 30

/**
 * The category to suggest next. Deterministic:
 * 1. take the category of the latest workout that has one;
 * 2. among the last [ROTATION_WINDOW] workouts, count what the user did right after that
 *    category, and pick the most frequent follower;
 * 3. ties go to the default rotation order Push → Pull → Legs (the follower of the last one
 *    first), then alphabetically;
 * 4. with no follower in history, the default rotation's next category; a custom category with
 *    no follower is followed by the first other category in [categories].
 *
 * Returns null when no workout has a category (then Today offers "Start blank" as the primary).
 */
fun guessNextCategory(notes: List<NoteLine>, categories: List<String>): String? {
    val sequence = notes
        .sortedBy { it.timestamp }
        .mapNotNull { note -> note.categoryName?.trim()?.takeIf(String::isNotEmpty) }
    val last = sequence.lastOrNull() ?: return null
    val window = sequence.takeLast(ROTATION_WINDOW)
    val followers = window.zipWithNext()
        .filter { (previous, _) -> previous.equals(last, ignoreCase = true) }
        .groupingBy { (_, next) -> next }
        .eachCount()

    val defaultNext = defaultSuccessor(last)
    if (followers.isEmpty()) {
        return defaultNext
            ?: categories.firstOrNull { !it.equals(last, ignoreCase = true) }
            ?: last
    }
    val best = followers.values.max()
    return followers.filterValues { it == best }.keys
        .sortedWith(compareBy<String>({ rotationRank(it, after = last) }, { it.lowercase() }))
        .first()
}

private fun defaultSuccessor(category: String): String? {
    val index = DEFAULT_ROTATION.indexOfFirst { it.equals(category, ignoreCase = true) }
    if (index < 0) return null
    return DEFAULT_ROTATION[(index + 1) % DEFAULT_ROTATION.size]
}

/** Distance from [after] in the default rotation (1 = its successor); custom categories last. */
private fun rotationRank(category: String, after: String): Int {
    val index = DEFAULT_ROTATION.indexOfFirst { it.equals(category, ignoreCase = true) }
    if (index < 0) return Int.MAX_VALUE
    val afterIndex = DEFAULT_ROTATION.indexOfFirst { it.equals(after, ignoreCase = true) }
    if (afterIndex < 0) return index
    return (index - afterIndex + DEFAULT_ROTATION.size - 1) % DEFAULT_ROTATION.size
}

/** The "Next up" line: "Pull · usually Tue/Fri · last 5 days ago". */
data class NextUp(
    val category: String,
    val usualDays: List<DayOfWeek>,
    /** Days since the last workout of [category]; null when never done. */
    val lastDaysAgo: Long?,
)

fun nextUp(
    notes: List<NoteLine>,
    categories: List<String>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): NextUp? {
    val category = guessNextCategory(notes, categories) ?: return null
    val ofCategory = notes.filter { it.categoryName?.trim().equals(category, ignoreCase = true) }
    val dates = ofCategory.map { it.localDate(zone) }
    val lastDaysAgo = dates.maxOrNull()?.let { ChronoUnit.DAYS.between(it, today).coerceAtLeast(0L) }
    val since = today.minusWeeks(8)
    val usualDays = dates
        .filter { it.isAfter(since) && !it.isAfter(today) }
        .groupingBy { it.dayOfWeek }
        .eachCount()
        .filterValues { it >= 2 }
        .entries
        .sortedWith(compareByDescending<Map.Entry<DayOfWeek, Int>> { it.value }.thenBy { it.key })
        .take(2)
        .map { it.key }
        .sorted()
    return NextUp(category, usualDays, lastDaysAgo)
}

enum class DayStatus { DONE, PLANNED, REST }

data class WeekDay(val date: LocalDate, val status: DayStatus, val isToday: Boolean)

/**
 * This week, Monday first. DONE = a workout was logged that day. PLANNED = today or later, not
 * done yet, on a weekday the user trained on in at least 2 of the 4 weeks before this one.
 * REST = anything else.
 */
fun weekStrip(
    notes: List<NoteLine>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): List<WeekDay> {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val dates = notes.map { it.localDate(zone) }.toSet()
    val lookbackStart = monday.minusWeeks(4)
    val usualWeekdays = dates
        .filter { !it.isBefore(lookbackStart) && it.isBefore(monday) }
        .groupingBy { it.dayOfWeek }
        .eachCount()
        .filterValues { it >= 2 }
        .keys
    return (0L until 7L).map { offset ->
        val date = monday.plusDays(offset)
        val status = when {
            date in dates -> DayStatus.DONE
            !date.isBefore(today) && date.dayOfWeek in usualWeekdays -> DayStatus.PLANNED
            else -> DayStatus.REST
        }
        WeekDay(date, status, isToday = date == today)
    }
}

/** The last [count] workouts, newest first. */
fun recentWorkouts(notes: List<NoteLine>, count: Int = 3): List<NoteLine> =
    notes.sortedByDescending { it.timestamp }.take(count)

internal fun NoteLine.localDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
