package com.example.gymtrack.util

val timeValueRegex = "\\d{2}:\\d{2}:\\d{2}(?:\\s[AP]M)?$".toRegex()

fun parseNoteText(text: String): Pair<List<String>, List<String>> {
    if (text.isBlank()) return Pair(emptyList(), emptyList())
    val lines = text.trimEnd('\n').split('\n')
    val base = mutableListOf<String>()
    val times = mutableListOf<String>()
    lines.forEach { line ->
        val match = timeValueRegex.find(line.trimEnd())
        if (match != null) {
            base.add(line.substring(0, match.range.first).trimEnd())
            times.add(match.value)
        } else {
            base.add(line)
            times.add("")
        }
    }
    return Pair(base, times)
}

fun combineTextAndTimes(text: String, times: List<String>): String {
    val lines = if (text.isEmpty()) emptyList() else text.split('\n')
    return lines.mapIndexed { index, l ->
        val ts = times.getOrNull(index).orEmpty()
        if (ts.isBlank()) l else "$l $ts"
    }.joinToString("\n")
}
