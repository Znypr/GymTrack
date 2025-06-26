package com.example.gymtrack.util

val timeValueRegex = "(?:\\d+'\\d{2}''|\\d{1,2}:\\d{2}:\\d{2}(?:\\s[AP]M)?|\\d{1,2}:\\d{2})$".toRegex()
private val relativeTimeRegex = "\\s*\\((?:\\d+'\\d{2}''|\\d+s)\\)$".toRegex()
private val ABS_TIME = Regex("""^\d+'?\d{2}"?$""")      // 0'05"  or  12'34
private const val SEP = '\u200B'   // invisible separator

fun parseNoteText(text: String): Pair<List<String>, List<String>> {
    if (text.isEmpty()) return emptyList<String>() to emptyList()

    val body   = mutableListOf<String>()
    val absCol = mutableListOf<String>()

    for (l in text.split('\n')) {
        val cut = l.lastIndexOf(SEP)
        if (cut == -1) {                 // no ABS time stored
            body   += l
            absCol += ""
        } else {
            body   += l.substring(0, cut)   // keeps relative "(0'07")"
            absCol += l.substring(cut + 1)  // absolute "0'05""
        }
    }
    return body to absCol
}


fun combineTextAndTimes(text: String, times: List<String>): String {
    val lines = if (text.isEmpty()) emptyList() else text.split('\n')
    return lines.mapIndexed { idx, line ->
        val ts = times.getOrNull(idx).orEmpty()
        // append ABS time only once, and only after the invisible SEP
        if (ts.isBlank() || line.endsWith("$SEP$ts")) line
        else "$line$SEP$ts"
    }.joinToString("\n")
}
