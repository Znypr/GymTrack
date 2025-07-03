package com.example.gymtrack.util

import android.content.Context
import android.os.Environment
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

    val main = mutableListOf<Pair<String, String>>()
    val subs = mutableListOf<Triple<Int, String, String>>()

    var currentMain = -1
    parsed.first.forEachIndexed { idx, line ->
        val time = parsed.second.getOrNull(idx).orEmpty()
        if (line.isBlank()) return@forEachIndexed
        if (line.startsWith("    ")) {
            if (currentMain >= 0) {
                subs += Triple(currentMain, line.trim(), time)
            }
        } else {
            currentMain = main.size
            main += line.trim() to time
        }
    }

    val builder = StringBuilder()
    builder.append("Title,Category,Timestamp,Learnings\n")
    builder.append(csvEscape(note.title)).append(',')
        .append(csvEscape(note.categoryName ?: "")).append(',')
        .append(csvEscape(formatFullDateTime(note.timestamp, settings))).append(',')
        .append(csvEscape(note.learnings)).append('\n')

    builder.append("Main Index,Main Entry,Time\n")
    main.forEachIndexed { index, (text, time) ->
        builder.append(index).append(',')
            .append(csvEscape(text)).append(',')
            .append(csvEscape(time)).append('\n')
    }

    builder.append("Main Index,Sub Entry,Time\n")
    subs.forEach { (index, text, time) ->
        builder.append(index).append(',')
            .append(csvEscape(text)).append(',')
            .append(csvEscape(time)).append('\n')
    }

    val dir = File(context.filesDir, "csv").apply { mkdirs() }
    val safeTitle = note.title
        .trim()
        .replace(" ", "_")
        .replace(Regex("[^A-Za-z0-9_-]"), "")
        .ifEmpty { "note" }
    val date = java.text.SimpleDateFormat("dd-MM-yy", java.util.Locale.getDefault())
        .format(java.util.Date(note.timestamp))
    val file = File(dir, "${safeTitle}-$date.csv")
    file.writeText(builder.toString())

    // Also copy to public Downloads directory if writable
    try {
        val downloads =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloads?.exists() == false) downloads.mkdirs()
        if (downloads != null && downloads.canWrite()) {
            file.copyTo(File(downloads, file.name), overwrite = true)
        }
    } catch (_: Exception) {
        // ignore failures copying to public storage
    }

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

    val metaRow = parseCsvRow(lines[1])
    val title = metaRow.getOrNull(0) ?: ""
    val category = metaRow.getOrNull(1).takeUnless { it.isNullOrEmpty() }
    val timestampStr = metaRow.getOrNull(2) ?: return null
    val learnings = metaRow.getOrNull(3) ?: ""

    var idx = 2
    while (idx < lines.size && lines[idx].isBlank()) idx++
    if (idx >= lines.size) return null

    val main = mutableListOf<Pair<String, String>>()

    if (lines[idx].startsWith("Main Entry")) {
        idx++
        while (idx < lines.size && !lines[idx].startsWith("Main Index")) {
            val row = parseCsvRow(lines[idx])
            val text = row.getOrNull(0).orEmpty()
            val time = row.getOrNull(1).orEmpty()
            if (text.isNotBlank() || time.isNotBlank()) main += text to time
            idx++
        }
    } else if (lines[idx].startsWith("Main Index")) {
        val header = parseCsvRow(lines[idx])
        if (header.getOrNull(1) != "Main Entry") return null
        idx++
        while (idx < lines.size) {
            if (lines[idx].startsWith("Main Index") && parseCsvRow(lines[idx]).getOrNull(1) == "Sub Entry") break
            val row = parseCsvRow(lines[idx])
            val text = row.getOrNull(1).orEmpty()
            val time = row.getOrNull(2).orEmpty()
            if (text.isNotBlank() || time.isNotBlank()) main += text to time
            idx++
        }
    } else return null

    val subs = mutableListOf<Triple<Int, String, String>>()
    if (idx < lines.size && lines[idx].startsWith("Main Index")) {
        idx++
        while (idx < lines.size) {
            val row = parseCsvRow(lines[idx])
            val parent = row.getOrNull(0)?.toIntOrNull() ?: -1
            val text = row.getOrNull(1).orEmpty()
            val time = row.getOrNull(2).orEmpty()
            if (parent >= 0) subs += Triple(parent, text, time)
            idx++
        }
    }

    val bodyLines = mutableListOf<String>()
    val times = mutableListOf<String>()
    main.forEachIndexed { i, (text, time) ->
        if (i > 0) {
            bodyLines += ""
            times += ""
        }
        bodyLines += text
        times += time
        subs.filter { it.first == i }.forEach { (_, sText, sTime) ->
            bodyLines += "    $sText"
            times += sTime
        }
    }

    val fullText = combineTextAndTimes(bodyLines.joinToString("\n"), times)
    val time = parseFullDateTime(timestampStr)
    return NoteLine(title, fullText, time, category, null, learnings)
}

