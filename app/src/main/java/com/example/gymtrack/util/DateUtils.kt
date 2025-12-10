package com.example.gymtrack.util

import com.example.gymtrack.data.Settings
import java.text.SimpleDateFormat
import java.util.*

// --- EXISTING FORMATTERS ---

fun parseDurationSeconds(str: String): Int {
    val primeRegex = "(\\d+)'(\\d{2})''".toRegex()
    primeRegex.matchEntire(str)?.let {
        val minutes = it.groupValues[1].toInt()
        val seconds = it.groupValues[2].toInt()
        return minutes * 60 + seconds
    }

    val hmsRegex = "(\\d{1,2}):(\\d{2}):(\\d{2})".toRegex()
    hmsRegex.matchEntire(str)?.let {
        val h = it.groupValues[1].toInt()
        val m = it.groupValues[2].toInt()
        val s = it.groupValues[3].toInt()
        return h * 3600 + m * 60 + s
    }

    val hmRegex = "(\\d{1,2}):(\\d{2})".toRegex()
    hmRegex.matchEntire(str)?.let {
        val h = it.groupValues[1].toInt()
        val m = it.groupValues[2].toInt()
        return h * 3600 + m * 60
    }

    return str.removeSuffix("s").toIntOrNull() ?: 0
}

fun formatWeekRelativeTime(timestamp: Long, settings: Settings): String {
    val nowCal = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        minimalDaysInFirstWeek = 4
    }
    val dateCal = Calendar.getInstance().apply {
        timeInMillis = timestamp
        firstDayOfWeek = Calendar.MONDAY
        minimalDaysInFirstWeek = 4
    }

    val timeFormat = SimpleDateFormat(
        if (settings.is24Hour) "HH:mm" else "hh:mm a",
        Locale.getDefault()
    )
    val dayNameFormat = SimpleDateFormat("EEEE", Locale.getDefault())
    val fullFormat = SimpleDateFormat(
        if (settings.is24Hour) "dd/MM/yyyy HH:mm" else "dd/MM/yyyy hh:mm a",
        Locale.getDefault()
    )

    val sameDay = nowCal.get(Calendar.YEAR) == dateCal.get(Calendar.YEAR) &&
            nowCal.get(Calendar.DAY_OF_YEAR) == dateCal.get(Calendar.DAY_OF_YEAR)

    val yesterdayCal = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = yesterdayCal.get(Calendar.YEAR) == dateCal.get(Calendar.YEAR) &&
            yesterdayCal.get(Calendar.DAY_OF_YEAR) == dateCal.get(Calendar.DAY_OF_YEAR)

    // “Last 7 days” window (exclusive of Today/Yesterday checks above)
    val sevenDaysAgo = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }
    val withinLast7Days = dateCal.after(sevenDaysAgo) && !sameDay && !isYesterday

    val sameWeek = nowCal.get(Calendar.YEAR) == dateCal.get(Calendar.YEAR) &&
            nowCal.get(Calendar.WEEK_OF_YEAR) == dateCal.get(Calendar.WEEK_OF_YEAR)

    return when {
        sameDay -> "Today ${timeFormat.format(dateCal.time)}"
        isYesterday -> "Yesterday ${timeFormat.format(dateCal.time)}"
        withinLast7Days -> "${dayNameFormat.format(dateCal.time)} ${timeFormat.format(dateCal.time)}"
        sameWeek -> "${dayNameFormat.format(dateCal.time)} ${timeFormat.format(dateCal.time)}"
        else -> fullFormat.format(dateCal.time)
    }
}

fun formatFullDateTime(timestamp: Long, settings: Settings): String {
    val pattern = if (settings.is24Hour) "dd/MM/yyyy HH:mm" else "dd/MM/yyyy hh:mm a"
    val format = SimpleDateFormat(pattern, Locale.getDefault())
    return format.format(Date(timestamp))
}

fun parseFullDateTime(value: String): Long {
    val patterns = listOf("dd/MM/yyyy HH:mm", "dd/MM/yyyy hh:mm a")
    for (p in patterns) {
        try {
            val format = SimpleDateFormat(p, Locale.getDefault())
            val date = format.parse(value)
            if (date != null) return date.time
        } catch (_: Exception) {
        }
    }
    return 0L
}


fun formatRelativeTime(timestamp: Long, settings: Settings): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val date = Date(timestamp)
    val timeFormat =
        SimpleDateFormat(if (settings.is24Hour) "HH:mm" else "hh:mm a", Locale.getDefault())
    val fullFormat = SimpleDateFormat(
        if (settings.is24Hour) "dd MMM HH:mm" else "dd MMM hh:mm a",
        Locale.getDefault(),
    )

    return when {
        diff < 60_000 -> "Just now"
        diff < 86_400_000 -> "Today ${timeFormat.format(date)}"
        diff < 172_800_000 -> "Yesterday ${timeFormat.format(date)}"
        else -> fullFormat.format(date)
    }
}

fun formatRoundedTime(timestamp: Long, settings: Settings): String {
    val rounding = settings.roundingSeconds.coerceAtLeast(1) * 1000L
    val rounded = ((timestamp + rounding / 2) / rounding) * rounding
    val pattern = if (settings.is24Hour) "HH:mm:ss" else "hh:mm:ss a"
    val format = SimpleDateFormat(pattern, Locale.getDefault())
    return format.format(Date(rounded))
}

fun formatElapsedMinutesSeconds(
    start: Long,
    now: Long,
    settings: Settings,
): String {
    val rounding = settings.roundingSeconds.coerceAtLeast(1) * 1000L
    var diff = now - start
    diff = ((diff + rounding / 2) / rounding) * rounding
    val totalSeconds = diff / 1000
    return formatSecondsToMinutesSeconds(totalSeconds)
}

fun formatTime(timestamp: Long, settings: Settings): String {
    val pattern = if (settings.is24Hour) "HH:mm" else "hh:mm a"
    val format = SimpleDateFormat(pattern, Locale.getDefault())
    return format.format(Date(timestamp))
}

fun formatDate(timestamp: Long, settings: Settings): String {
    val pattern = "dd/MM/yyyy"
    val format = SimpleDateFormat(pattern, Locale.getDefault())
    return format.format(Date(timestamp))
}


fun formatSecondsToMinutesSeconds(seconds: Long): String {
    val minutes = seconds / 60
    val sec = seconds % 60
    return "${minutes}'${sec.toString().padStart(2, '0')}''"
}

// --- NEW HELPER FUNCTIONS FOR RELATIVE TIME LOGIC ---

/**
 * Parses a time string (HH:mm:ss, HH:mm, or mm'ss'') into milliseconds.
 * Returns null if parsing fails or string is empty.
 */
fun parseTimeOrDuration(timeStr: String): Long? {
    if (timeStr.isBlank()) return null

    try {
        // Case A: Relative duration format "mm'ss''"
        if (timeStr.contains("'")) {
            val parts = timeStr.replace("''", "").split("'")
            val min = parts.getOrNull(0)?.toLongOrNull() ?: 0L
            val sec = parts.getOrNull(1)?.toLongOrNull() ?: 0L
            // Return duration in millis
            return (min * 60 + sec) * 1000L
        }

        // Case B: Absolute wall clock "HH:mm:ss"
        // Strip potential AM/PM or extra dates
        val cleanTime = timeStr.trim().split(" ")[0]
        val parts = cleanTime.split(":")

        val hours = parts.getOrNull(0)?.toLongOrNull() ?: 0L
        val minutes = parts.getOrNull(1)?.toLongOrNull() ?: 0L
        val seconds = parts.getOrNull(2)?.toLongOrNull() ?: 0L

        // Return millis into the day
        return (hours * 3600 + minutes * 60 + seconds) * 1000L

    } catch (e: Exception) {
        return null
    }
}

/**
 * Calculates the difference between a previous time string and the current system time.
 * Returns the formatted string "(m'ss'')" to be inserted into the note.
 */
fun getRelativeTimeDiffString(prevTimeStr: String, currentTimeMillis: Long): String? {
    val prevMillis = parseTimeOrDuration(prevTimeStr) ?: return null

    // Convert current time to "millis into the day" to match the parsed format
    val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
    val currentMillisIntoDay = (cal.get(Calendar.HOUR_OF_DAY) * 3600 +
            cal.get(Calendar.MINUTE) * 60 +
            cal.get(Calendar.SECOND)) * 1000L

    var diff = currentMillisIntoDay - prevMillis

    // Handle midnight rollover
    if (diff < 0) {
        diff += 24 * 3600 * 1000L
    }

    // Convert diff to seconds
    val totalSeconds = diff / 1000
    if (totalSeconds < 0) return null

    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    // Format as (m'ss'')
    return String.format("(%d'%02d'')", minutes, seconds)
}