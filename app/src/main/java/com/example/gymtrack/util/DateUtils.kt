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
