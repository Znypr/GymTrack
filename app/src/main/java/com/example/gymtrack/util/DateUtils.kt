package com.example.gymtrack.util

import com.example.gymtrack.data.Settings
import java.text.SimpleDateFormat
import java.util.*

fun formatRelativeTime(timestamp: Long, settings: Settings): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val date = Date(timestamp)
    val timeFormat =
        SimpleDateFormat(if (settings.is24Hour) "HH:mm" else "hh:mm a", Locale.getDefault())
    val fullFormat = SimpleDateFormat(
        if (settings.is24Hour) "MMM dd HH:mm" else "MMM dd hh:mm a",
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

fun formatElapsedTime(
    start: Long,
    now: Long,
    settings: Settings,
    includeSeconds: Boolean,
): String {
    val rounding = settings.roundingSeconds.coerceAtLeast(1) * 1000L
    var diff = now - start
    diff = ((diff + rounding / 2) / rounding) * rounding
    val totalSeconds = diff / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (includeSeconds) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", hours, minutes)
    }
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

fun formatSecondsToMinutesSeconds(seconds: Long): String {
    val minutes = seconds / 60
    val sec = seconds % 60
    return "${minutes}'${sec.toString().padStart(2, '0')}''"
}

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

fun formatFullDateTime(timestamp: Long, settings: Settings): String {
    val pattern = if (settings.is24Hour) "yyyy-MM-dd HH:mm" else "yyyy-MM-dd hh:mm a"
    val format = SimpleDateFormat(pattern, Locale.getDefault())
    return format.format(Date(timestamp))
}

fun formatWeekRelativeTime(timestamp: Long, settings: Settings): String {
    val nowCal = Calendar.getInstance()
    val dateCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val timeFormat = SimpleDateFormat(if (settings.is24Hour) "HH:mm" else "hh:mm a", Locale.getDefault())
    val dayNameFormat = SimpleDateFormat("EEEE", Locale.getDefault())
    val fullFormat = SimpleDateFormat(if (settings.is24Hour) "yyyy-MM-dd HH:mm" else "yyyy-MM-dd hh:mm a", Locale.getDefault())

    val sameDay = nowCal.get(Calendar.YEAR) == dateCal.get(Calendar.YEAR) &&
            nowCal.get(Calendar.DAY_OF_YEAR) == dateCal.get(Calendar.DAY_OF_YEAR)

    val yesterdayCal = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = yesterdayCal.get(Calendar.YEAR) == dateCal.get(Calendar.YEAR) &&
            yesterdayCal.get(Calendar.DAY_OF_YEAR) == dateCal.get(Calendar.DAY_OF_YEAR)

    val sameWeek = nowCal.get(Calendar.YEAR) == dateCal.get(Calendar.YEAR) &&
            nowCal.get(Calendar.WEEK_OF_YEAR) == dateCal.get(Calendar.WEEK_OF_YEAR)

    return when {
        sameDay -> "Today ${timeFormat.format(dateCal.time)}"
        isYesterday -> "Yesterday ${timeFormat.format(dateCal.time)}"
        sameWeek -> "${dayNameFormat.format(dateCal.time)} ${timeFormat.format(dateCal.time)}"
        else -> fullFormat.format(dateCal.time)
    }
}
