package com.example.gymtrack.core.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.gymtrack.core.data.ExerciseFlag
import java.util.Locale

// Regex to detect the legacy broken format: ends with time (e.g. 0'00") followed by a flag code (b/u/s).
// Captures: Group 1 (Body), Group 2 (Time), Group 3 (Flag)
private val DIRTY_TAIL_REGEX = Regex("""^(.*?)(\d+'\d{2}(?:''|")?)([bus])$""")

private const val SEP = '\u200B' // Legacy invisible separator for ABS time.
private const val FLAG_SEP = '\u200C' // Legacy invisible separator for exercise flag.
private const val ROW_METADATA_SEPARATOR = '\t'

private fun ExerciseFlag.toStorageCode(): String = when (this) {
    ExerciseFlag.UNILATERAL -> "u"
    ExerciseFlag.SUPERSET -> "s"
    ExerciseFlag.BILATERAL -> "b"
}

private fun flagFromStorageCode(value: String?): ExerciseFlag = when (value?.trim()?.lowercase()) {
    "u" -> ExerciseFlag.UNILATERAL
    "s" -> ExerciseFlag.SUPERSET
    else -> ExerciseFlag.BILATERAL
}

data class NoteRowMetadata(
    val absoluteTime: String = "",
    val flag: ExerciseFlag = ExerciseFlag.BILATERAL,
)

fun parseNoteText(
    text: String,
    rowMetadata: String? = null,
): Triple<List<String>, List<String>, List<ExerciseFlag>> {
    if (!rowMetadata.isNullOrBlank()) {
        val visibleLines = if (text.isEmpty()) emptyList() else text.split('\n')
        val metadataRows = parseNoteRowMetadata(rowMetadata)
        return Triple(
            visibleLines,
            visibleLines.mapIndexed { index, _ -> metadataRows.getOrNull(index)?.absoluteTime.orEmpty() },
            visibleLines.mapIndexed { index, _ -> metadataRows.getOrNull(index)?.flag ?: ExerciseFlag.BILATERAL },
        )
    }

    return parseLegacyNoteText(text)
}

private fun parseLegacyNoteText(text: String): Triple<List<String>, List<String>, List<ExerciseFlag>> {
    if (text.isEmpty()) return Triple(emptyList(), emptyList(), emptyList())

    val body = mutableListOf<String>()
    val absCol = mutableListOf<String>()
    val flagCol = mutableListOf<ExerciseFlag>()

    for (l in text.split('\n')) {
        var line = l.trimEnd()

        // Skip lines that are just a flag code (ghost lines from the legacy bug).
        if (line == "b" || line == "u" || line == "s") continue

        var flag = ExerciseFlag.BILATERAL
        var absTime = ""
        var cleanBody = line

        // 1. Try legacy standard parsing with invisible separators.
        val flagCut = line.lastIndexOf(FLAG_SEP)
        if (flagCut != -1) {
            flag = flagFromStorageCode(line.substring(flagCut + 1))
            cleanBody = line.substring(0, flagCut)

            val cut = cleanBody.lastIndexOf(SEP)
            if (cut != -1) {
                absTime = cleanBody.substring(cut + 1)
                cleanBody = cleanBody.substring(0, cut)
            }
        } else {
            // 2. Fallback: parse previously visible dirty metadata.
            val match = DIRTY_TAIL_REGEX.find(line)
            if (match != null) {
                cleanBody = match.groupValues[1]
                absTime = match.groupValues[2]
                flag = flagFromStorageCode(match.groupValues[3])
            }
        }

        body += cleanBody
        absCol += absTime
        flagCol += flag
    }
    return Triple(body, absCol, flagCol)
}

fun buildNoteRowMetadata(times: List<String>, flags: List<ExerciseFlag>): String {
    val count = maxOf(times.size, flags.size)
    return (0 until count).joinToString("\n") { index ->
        val absoluteTime = times.getOrNull(index).orEmpty()
        val flag = flags.getOrNull(index) ?: ExerciseFlag.BILATERAL
        absoluteTime + ROW_METADATA_SEPARATOR + flag.toStorageCode()
    }
}

fun parseNoteRowMetadata(rowMetadata: String?): List<NoteRowMetadata> {
    if (rowMetadata.isNullOrEmpty()) return emptyList()
    return rowMetadata.split('\n').map { row ->
        val parts = row.split(ROW_METADATA_SEPARATOR, limit = 2)
        NoteRowMetadata(
            absoluteTime = parts.getOrNull(0).orEmpty(),
            flag = flagFromStorageCode(parts.getOrNull(1)),
        )
    }
}

/**
 * Legacy encoder retained only for compatibility tests and old import/export readers.
 * New editor saves must persist visible note text and `rowMetadata` separately.
 */
fun combineTextAndTimes(text: String, times: List<String>, flags: List<ExerciseFlag>): String {
    val lines = if (text.isEmpty()) emptyList() else text.split('\n')
    return lines.mapIndexed { idx, line ->
        var result = line
        val ts = times.getOrNull(idx).orEmpty()
        if (ts.isNotBlank() && !result.endsWith("$SEP$ts")) {
            result += "$SEP$ts"
        }
        result += "$FLAG_SEP${(flags.getOrNull(idx) ?: ExerciseFlag.BILATERAL).toStorageCode()}"
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
