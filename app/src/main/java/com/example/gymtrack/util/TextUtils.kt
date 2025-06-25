package com.example.gymtrack.util

val timeValueRegex = "(?:\\d+'\\d{2}''|\\d{1,2}:\\d{2}:\\d{2}(?:\\s[AP]M)?|\\d{1,2}:\\d{2})$".toRegex()
private val relativeTimeRegex = "\\s*\\((?:\\d+'\\d{2}''|\\d+s)\\)$".toRegex()

fun parseNoteText(text: String): Pair<List<String>, List<String>> {
    if (text.isEmpty()) return Pair(emptyList(), emptyList())
    val lines = text.split('\n', ignoreCase = false, limit = -1)
    val base = mutableListOf<String>()
    val times = mutableListOf<String>()
    lines.forEach { line ->
        val trimmed = line.trimEnd()
        val timeMatch = timeValueRegex.find(trimmed)
        val withoutTime = if (timeMatch != null) trimmed.substring(0, timeMatch.range.first).trimEnd() else trimmed
        val baseText = relativeTimeRegex.replace(withoutTime, "").trimEnd()
        base.add(baseText)
        times.add(timeMatch?.value.orEmpty())
    }
    return Pair(base, times)
}

fun combineTextAndTimes(text: String, times: List<String>): String {
    val lines = if (text.isEmpty()) emptyList() else text.split('\n', ignoreCase = false, limit = -1)
    return lines.mapIndexed { index, l ->
        val ts = if (index < times.size) times[index] else ""
        if (ts.isBlank()) l else "$l $ts"
    }.joinToString("\n")
}
