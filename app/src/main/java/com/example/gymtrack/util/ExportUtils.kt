package com.example.gymtrack.util

import android.content.Context
import com.example.gymtrack.data.NoteLine
import com.example.gymtrack.data.Settings
import java.io.File

private fun csvEscape(value: String): String {
    var v = value.replace("\"", "\"\"")
    if (v.contains(',') || v.contains('"') || v.contains('\n')) {
        v = "\"$v\""
    }
    return v
}

fun exportNote(context: Context, note: NoteLine, settings: Settings): File {
    val parsed = parseNoteText(note.text)

    val builder = StringBuilder()
    builder.append("Title,Category,Timestamp,Learnings,Text,Absolute Time\n")
    parsed.first.forEachIndexed { idx, line ->
        builder.append(csvEscape(note.title)).append(',')
            .append(csvEscape(note.categoryName ?: "")).append(',')
            .append(csvEscape(formatFullDateTime(note.timestamp, settings))).append(',')
            .append(csvEscape(note.learnings)).append(',')
            .append(csvEscape(line)).append(',')
            .append(csvEscape(parsed.second.getOrNull(idx).orEmpty())).append('\n')
    }

    val dir = File(context.filesDir, "csv").apply { mkdirs() }
    val file = File(dir, "note_${note.timestamp}.csv")
    file.writeText(builder.toString())
    return file
}

fun getSavedCsvFiles(context: Context): List<File> {
    val dir = File(context.filesDir, "csv")
    return dir.listFiles()?.filter { it.extension == "csv" }?.sortedBy { it.name } ?: emptyList()
}

private fun parseCsvRow(row: String): List<String> {
    val result = mutableListOf<String>()
    var current = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < row.length) {
        val c = row[i]
        when (c) {
            '"' -> {
                if (inQuotes && i + 1 < row.length && row[i + 1] == '"') {
                    current.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            }
            ',' -> {
                if (inQuotes) current.append(c) else {
                    result += current.toString()
                    current = StringBuilder()
                }
            }
            else -> current.append(c)
        }
        i++
    }
    result += current.toString()
    return result
}

fun importNote(file: File, settings: Settings): NoteLine? {
    val lines = file.readLines()
    if (lines.size < 2) return null
    val rows = lines.drop(1).map { parseCsvRow(it) }
    if (rows.isEmpty()) return null

    val first = rows.first()
    val title = first.getOrNull(0) ?: ""
    val category = first.getOrNull(1).takeUnless { it.isNullOrEmpty() }
    val timestampStr = first.getOrNull(2) ?: return null
    val learnings = first.getOrNull(3) ?: ""
    val body = rows.map { it.getOrNull(4).orEmpty() }.joinToString("\n")
    val abs = rows.map { it.getOrNull(5).orEmpty() }

    val fullText = combineTextAndTimes(body, abs)
    val time = parseFullDateTime(timestampStr)
    return NoteLine(title, fullText, time, category, null, learnings)
}

