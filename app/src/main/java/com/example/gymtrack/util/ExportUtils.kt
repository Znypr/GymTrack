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

