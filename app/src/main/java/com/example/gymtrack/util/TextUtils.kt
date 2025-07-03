package com.example.gymtrack.util

val timeValueRegex = "(?:\\d+'\\d{2}''|\\d{1,2}:\\d{2}:\\d{2}(?:\\s[AP]M)?|\\d{1,2}:\\d{2})$".toRegex()
private val relativeTimeRegex = "\\s*\\((?:\\d+'\\d{2}''|\\d+s)\\)$".toRegex()
private val ABS_TIME = Regex("""^\d+'?\d{2}"?$""")      // 0'05"  or  12'34
private const val SEP = '\u200B'   // invisible separator for ABS time
private const val UNI_SEP = '\u200C' // separator for uni/bi flag

fun parseNoteText(text: String): Triple<List<String>, List<String>, List<Boolean>> {
    if (text.isEmpty()) return Triple(emptyList(), emptyList(), emptyList())

    val body   = mutableListOf<String>()
    val absCol = mutableListOf<String>()
    val uniCol = mutableListOf<Boolean>()

    for (l in text.split('\n')) {
        var line = l
        var uni = false

        val uniCut = line.lastIndexOf(UNI_SEP)
        if (uniCut != -1) {
            uni = line.substring(uniCut + 1) == "u"
            line = line.substring(0, uniCut)
        }

        val cut = line.lastIndexOf(SEP)
        if (cut == -1) {                 // no ABS time stored
            body   += line
            absCol += ""
        } else {
            body   += line.substring(0, cut)   // keeps relative "(0'07")"
            absCol += line.substring(cut + 1)  // absolute "0'05""
        }
        uniCol += uni
    }
    return Triple(body, absCol, uniCol)
}


fun combineTextAndTimes(text: String, times: List<String>, unis: List<Boolean>): String {
    val lines = if (text.isEmpty()) emptyList() else text.split('\n')
    return lines.mapIndexed { idx, line ->
        var result = line
        val ts = times.getOrNull(idx).orEmpty()
        // append ABS time only once, and only after the invisible SEP
        if (ts.isNotBlank() && !result.endsWith("$SEP$ts")) {
            result += "$SEP$ts"
        }
        val uni = unis.getOrNull(idx) ?: false
        result += "$UNI_SEP" + if (uni) "u" else "b"
        result
    }.joinToString("\n")
}
