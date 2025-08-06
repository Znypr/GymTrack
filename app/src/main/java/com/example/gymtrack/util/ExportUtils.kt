package com.example.gymtrack.util

import android.content.Context
import android.os.Environment
import com.example.gymtrack.data.NoteLine
import com.example.gymtrack.data.Settings
import com.example.gymtrack.data.ExerciseFlag
import java.io.File

data class SubEntry(val parent: Int, val text: String, val time: String, val flag: ExerciseFlag)

private fun csvEscape(value: String): String {
    var v = value.replace("\"", "\"\"")
    if (v.contains(',') || v.contains('"') || v.contains('\n')) {
        v = "\"$v\""
    }
    return v
}

private fun ExerciseFlag.toCsvString(): String = when (this) {
    ExerciseFlag.BILATERAL -> "bi"
    ExerciseFlag.UNILATERAL -> "uni"
    ExerciseFlag.SUPERSET -> "SS"
}

private fun ExerciseFlag.toSubEntryString(): String = when (this) {
    ExerciseFlag.BILATERAL -> "1x"
    ExerciseFlag.UNILATERAL -> "2x"
    ExerciseFlag.SUPERSET -> "2x"
}

private fun parseFlag(value: String?): ExerciseFlag = when (value?.lowercase()) {
    "uni" -> ExerciseFlag.UNILATERAL
    "ss" -> ExerciseFlag.SUPERSET
    "2x" -> ExerciseFlag.UNILATERAL
    "1x" -> ExerciseFlag.BILATERAL
    else -> ExerciseFlag.BILATERAL
}

fun exportNote(context: Context, note: NoteLine, settings: Settings): File {
    val parsed = parseNoteText(note.text)

    val main = mutableListOf<Triple<String, String, ExerciseFlag>>()
    val subs = mutableListOf<SubEntry>()

    var currentMain = -1
    parsed.first.forEachIndexed { idx, line ->
        val time = parsed.second.getOrNull(idx).orEmpty()
        val flag = parsed.third.getOrNull(idx) ?: ExerciseFlag.BILATERAL
        if (line.isBlank()) return@forEachIndexed
        if (line.startsWith("    ")) {
            if (currentMain >= 0) {
                val parentFlag = main.getOrNull(currentMain)?.third ?: ExerciseFlag.BILATERAL
                subs += SubEntry(currentMain, line.trim(), time, parentFlag)
            }
        } else {
            currentMain = main.size
            main += Triple(line.trim(), time, flag)
        }
    }

    val builder = StringBuilder()
    builder.append("Title,Category,Timestamp,Learnings\n")
    builder.append(csvEscape(note.title)).append(',')
        .append(csvEscape(note.categoryName ?: "")).append(',')
        .append(csvEscape(formatFullDateTime(note.timestamp, settings))).append(',')
        .append(csvEscape(note.learnings)).append('\n')

    builder.append("Main Index,Main Entry,Time,Flag\n")
    main.forEachIndexed { index, (text, time, flag) ->
        builder.append(index).append(',')
            .append(csvEscape(text)).append(',')
            .append(csvEscape(time)).append(',')
            .append(flag.toCsvString()).append('\n')
    }

    builder.append("Main Index,Sub Entry,Time,Flag\n")
    subs.forEach { (index, text, time, flag) ->
        builder.append(index).append(',')
            .append(csvEscape(text)).append(',')
            .append(csvEscape(time)).append(',')
            .append(flag.toSubEntryString()).append('\n')
    }

    val dir = File(context.filesDir, "csv").apply { mkdirs() }
    val safeTitle = note.title
        .trim()
        .replace(" ", "_")
        .replace(Regex("[^A-Za-z0-9_-]"), "")
        .ifEmpty { "note" }
    val date = java.text.SimpleDateFormat("dd-MM-yy", java.util.Locale.getDefault())
        .format(java.util.Date(note.timestamp))
    val time = java.text.SimpleDateFormat("HH-mm", java.util.Locale.getDefault())
        .format(java.util.Date(note.timestamp))
    val file = File(dir, "${safeTitle}-$date-$time.csv")
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

    val main = mutableListOf<Triple<String, String, ExerciseFlag>>()

    if (lines[idx].trimStart().startsWith("Main Entry")) {
        val hasFlag = parseCsvRow(lines[idx]).any { it.trim().equals("Flag", true) || it.trim().equals("Uni", true) }
        idx++
        while (idx < lines.size && !lines[idx].trimStart().startsWith("Main Index")) {
            val row = parseCsvRow(lines[idx])
            val text = row.getOrNull(0).orEmpty()
            val time = row.getOrNull(1).orEmpty()
            val flag = if (hasFlag) parseFlag(row.getOrNull(2)) else ExerciseFlag.BILATERAL
            if (text.isNotBlank() || time.isNotBlank()) main += Triple(text, time, flag)
            idx++
        }
    } else if (lines[idx].trimStart().startsWith("Main Index")) {
        val header = parseCsvRow(lines[idx])
        val secondCol = header.getOrNull(1)?.trim()?.lowercase()
        if (secondCol != "main entry") return null
        val hasFlag = header.any { it.trim().equals("Flag", true) || it.trim().equals("Uni", true) }
        idx++
        while (idx < lines.size) {
            if (lines[idx].trimStart().startsWith("Main Index") &&
                parseCsvRow(lines[idx]).getOrNull(1)?.trim()?.equals("Sub Entry", true) == true) break
            val row = parseCsvRow(lines[idx])
            val text = row.getOrNull(1).orEmpty()
            val time = row.getOrNull(2).orEmpty()
            val flag = if (hasFlag) parseFlag(row.getOrNull(3)) else ExerciseFlag.BILATERAL
            if (text.isNotBlank() || time.isNotBlank()) main += Triple(text, time, flag)
            idx++
        }
    } else return null

    val subs = mutableListOf<SubEntry>()
    if (idx < lines.size && lines[idx].trimStart().startsWith("Main Index")) {
        val header = parseCsvRow(lines[idx])
        val hasFlag = header.any { it.trim().equals("Flag", true) || it.trim().equals("Uni", true) }
        idx++
        while (idx < lines.size) {
            val row = parseCsvRow(lines[idx])
            val parent = row.getOrNull(0)?.toIntOrNull() ?: -1
            val text = row.getOrNull(1).orEmpty()
            val time = row.getOrNull(2).orEmpty()
            val flag = if (hasFlag) parseFlag(row.getOrNull(3)) else ExerciseFlag.BILATERAL
            if (parent >= 0) subs += SubEntry(parent, text, time, flag)
            idx++
        }
    }

    val bodyLines = mutableListOf<String>()
    val times = mutableListOf<String>()
    val flags = mutableListOf<ExerciseFlag>()
    main.forEachIndexed { i, (text, time, flag) ->
        if (i > 0) {
            bodyLines += ""
            times += ""
            flags += ExerciseFlag.BILATERAL
        }
        bodyLines += text
        times += time
        flags += flag
        subs.filter { it.parent == i }.forEach { (_, sText, sTime, _) ->
            bodyLines += "    $sText"
            times += sTime
            flags += flag
        }
    }

    val fullText = combineTextAndTimes(bodyLines.joinToString("\n"), times, flags)
    val time = parseFullDateTime(timestampStr)
    return NoteLine(title, fullText, time, category, null, learnings)
}

