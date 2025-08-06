package com.example.gymtrack.util

import com.example.gymtrack.data.ExerciseFlag

val timeValueRegex = "(?:\\d+'\\d{2}''|\\d{1,2}:\\d{2}:\\d{2}(?:\\s[AP]M)?|\\d{1,2}:\\d{2})$".toRegex()
private val relativeTimeRegex = "\\s*\\((?:\\d+'\\d{2}''|\\d+s)\\)$".toRegex()
private val ABS_TIME = Regex("""^\d+'?\d{2}"?$""")      // 0'05"  or  12'34
private const val SEP = '\u200B'   // invisible separator for ABS time
private const val FLAG_SEP = '\u200C' // separator for exercise flag

fun parseNoteText(text: String): Triple<List<String>, List<String>, List<ExerciseFlag>> {
    if (text.isEmpty()) return Triple(emptyList(), emptyList(), emptyList())

    val body   = mutableListOf<String>()
    val absCol = mutableListOf<String>()
    val flagCol = mutableListOf<ExerciseFlag>()

    for (l in text.split('\n')) {
        var line = l
        var flag = ExerciseFlag.BILATERAL

        val flagCut = line.lastIndexOf(FLAG_SEP)
        if (flagCut != -1) {
            flag = when (line.substring(flagCut + 1)) {
                "u" -> ExerciseFlag.UNILATERAL
                "s" -> ExerciseFlag.SUPERSET
                else -> ExerciseFlag.BILATERAL
            }
            line = line.substring(0, flagCut)
        }

        val cut = line.lastIndexOf(SEP)
        if (cut == -1) {                 // no ABS time stored
            body   += line
            absCol += ""
        } else {
            body   += line.substring(0, cut)   // keeps relative "(0'07")"
            absCol += line.substring(cut + 1)  // absolute "0'05""
        }
        flagCol += flag
    }
    return Triple(body, absCol, flagCol)
}

fun combineTextAndTimes(text: String, times: List<String>, flags: List<ExerciseFlag>): String {
    val lines = if (text.isEmpty()) emptyList() else text.split('\n')
    return lines.mapIndexed { idx, line ->
        var result = line
        val ts = times.getOrNull(idx).orEmpty()
        // append ABS time only once, and only after the invisible SEP
        if (ts.isNotBlank() && !result.endsWith("$SEP$ts")) {
            result += "$SEP$ts"
        }
        val flag = flags.getOrNull(idx) ?: ExerciseFlag.BILATERAL
        val code = when (flag) {
            ExerciseFlag.UNILATERAL -> "u"
            ExerciseFlag.SUPERSET -> "s"
            ExerciseFlag.BILATERAL -> "b"
        }
        result += "$FLAG_SEP$code"
        result
    }.joinToString("\n")
}
