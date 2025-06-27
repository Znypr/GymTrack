package com.example.gymtrack.util

import android.content.Context
import android.os.Environment
import com.example.gymtrack.data.NoteLine
import com.example.gymtrack.data.Settings
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

fun exportNote(context: Context, note: NoteLine, settings: Settings) {
    val workbook = XSSFWorkbook()
    val sheet = workbook.createSheet("Note")

    var rowIndex = 0
    sheet.createRow(rowIndex++).apply {
        createCell(0).setCellValue("Title")
        createCell(1).setCellValue(note.title)
    }
    sheet.createRow(rowIndex++).apply {
        createCell(0).setCellValue("Category")
        createCell(1).setCellValue(note.categoryName ?: "")
    }
    sheet.createRow(rowIndex++).apply {
        createCell(0).setCellValue("Timestamp")
        createCell(1).setCellValue(formatFullDateTime(note.timestamp, settings))
    }
    sheet.createRow(rowIndex++).apply {
        createCell(0).setCellValue("Learnings")
        createCell(1).setCellValue(note.learnings)
    }

    rowIndex++
    sheet.createRow(rowIndex++).apply {
        createCell(0).setCellValue("Text")
        createCell(1).setCellValue("Absolute Time")
    }

    val parsed = parseNoteText(note.text)
    parsed.first.forEachIndexed { idx, line ->
        sheet.createRow(rowIndex++).apply {
            createCell(0).setCellValue(line)
            createCell(1).setCellValue(parsed.second.getOrNull(idx).orEmpty())
        }
    }

    val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    val baseName = if (note.title.isBlank()) "note" else note.title
    val safe = baseName.replace(Regex("[^A-Za-z0-9_-]"), "_")
    val fileName = "${safe}_${sdf.format(Date(note.timestamp))}.xlsx"
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
    val file = File(dir, fileName)
    FileOutputStream(file).use { fos ->
        workbook.write(fos)
    }
    workbook.close()
}
