package com.example.gymtrack.util

import android.content.Context
import android.os.Environment
import com.example.gymtrack.data.NoteLine
import com.example.gymtrack.data.Settings
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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

    val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    val baseName = if (note.title.isBlank()) "note" else note.title
    val safe = baseName.replace(Regex("[^A-Za-z0-9_-]"), "_")
    val fileName = "${safe}_${sdf.format(Date(note.timestamp))}.csv"
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
    val file = File(dir, fileName)
    file.writeText(builder.toString())
    return file
}

