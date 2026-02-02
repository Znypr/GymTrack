package com.example.gymtrack.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.gymtrack.data.ExerciseFlag
import java.util.Locale

// Regex to detect the "broken" format: ends with time (e.g. 0'00") followed by a flag code (b/u/s)
// Captures: Group 1 (Body), Group 2 (Time), Group 3 (Flag)
private val DIRTY_TAIL_REGEX = Regex("""^(.*?)(\d+'\d{2}(?:''|")?)([bus])$""")

private const val SEP = '\u200B'   // invisible separator for ABS time
private const val FLAG_SEP = '\u200C' // separator for exercise flag

fun parseNoteText(text: String): Triple<List<String>, List<String>, List<ExerciseFlag>> {
    if (text.isEmpty()) return Triple(emptyList(), emptyList(), emptyList())

    val body   = mutableListOf<String>()
    val absCol = mutableListOf<String>()
    val flagCol = mutableListOf<ExerciseFlag>()

    for (l in text.split('\n')) {
        var line = l.trimEnd() // Remove trailing spaces

        // Skip lines that are just a flag code (ghost lines from the bug)
        if (line == "b" || line == "u" || line == "s") continue

        var flag = ExerciseFlag.BILATERAL
        var absTime = ""
        var cleanBody = line

        // 1. Try Standard Parsing (Invisible Separators)
        val flagCut = line.lastIndexOf(FLAG_SEP)
        if (flagCut != -1) {
            flag = when (line.substring(flagCut + 1)) {
                "u" -> ExerciseFlag.UNILATERAL
                "s" -> ExerciseFlag.SUPERSET
                else -> ExerciseFlag.BILATERAL
            }
            cleanBody = line.substring(0, flagCut)

            val cut = cleanBody.lastIndexOf(SEP)
            if (cut != -1) {
                absTime = cleanBody.substring(cut + 1)
                cleanBody = cleanBody.substring(0, cut)
            }
        }
        // 2. Fallback: Try "Dirty" Parsing (Visible Text)
        else {
            val match = DIRTY_TAIL_REGEX.find(line)
            if (match != null) {
                cleanBody = match.groupValues[1]
                absTime = match.groupValues[2]
                val code = match.groupValues[3]
                flag = when (code) {
                    "u" -> ExerciseFlag.UNILATERAL
                    "s" -> ExerciseFlag.SUPERSET
                    else -> ExerciseFlag.BILATERAL
                }
            }
        }

        body   += cleanBody
        absCol += absTime
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

class CapitalizeWordsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        val capitalized = original.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
        return TransformedText(AnnotatedString(capitalized), OffsetMapping.Identity)
    }
}