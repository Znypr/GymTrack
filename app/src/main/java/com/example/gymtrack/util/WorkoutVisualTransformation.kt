package com.example.gymtrack.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp

class WorkoutVisualTransformation(private val times: List<String>) : VisualTransformation {
    private val timeRegex = "\\((?:\\d+'\\d{2}''|\\d+s)\\)".toRegex()
    private val secondsTrans = SmallSecondsVisualTransformation()

    override fun filter(text: AnnotatedString): TransformedText {
        val originalTrimmedLines = text.text.split('\n').map { it.trimStart() }
        val lines = text.text.split('\n').map { it.trimStart() }
        val pairs = lines.mapIndexed { idx, l -> l to times.getOrNull(idx).orEmpty() }
        val maxBase = pairs.filter { it.second.isNotBlank() }
            .map { it.first }
            .filter { it.isNotBlank() }
            .maxOfOrNull { it.length } ?: 0

        val transformed = buildString {
            var previousBlank = true
            originalTrimmedLines.forEachIndexed { i, lineContent ->
                val timeToDisplay = times.getOrNull(i).orEmpty()
                val isCurrentLineBlank = lineContent.isBlank()
                val shouldIndent = !isCurrentLineBlank && !previousBlank
                if (shouldIndent) append('\t')
                append(lineContent)
                if (timeToDisplay.isNotBlank()) {
                    val spacesToAppend = (maxBase - lineContent.length).coerceAtLeast(0)
                    repeat(spacesToAppend) { append(' ') }
                    append(' ')
                    append(timeToDisplay)
                }
                if (i != originalTrimmedLines.lastIndex) append('\n')
                previousBlank = isCurrentLineBlank
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                var remaining = offset
                var result = 0
                pairs.forEachIndexed { index, (line, time) ->
                    val lineLen = line.length
                    if (remaining <= lineLen) return result + remaining
                    result += lineLen
                    remaining -= lineLen
                    if (time.isNotBlank()) {
                        result += maxBase - line.length + 1 + time.length
                    }
                    if (index != pairs.lastIndex) {
                        if (remaining == 0) return result
                        result++
                        remaining--
                    }
                }
                return result
            }

            override fun transformedToOriginal(offset: Int): Int {
                var remaining = offset
                var result = 0
                pairs.forEachIndexed { index, (line, time) ->
                    val lineLen = line.length
                    if (remaining <= lineLen) return result + remaining
                    result += lineLen
                    remaining -= lineLen
                    if (time.isNotBlank()) {
                        val extra = maxBase - line.length + 1 + time.length
                        if (remaining <= extra) return result
                        remaining -= extra
                    }
                    if (index != pairs.lastIndex) {
                        if (remaining == 0) return result
                        result++
                        remaining--
                    }
                }
                return result
            }
        }

        val withSeconds = secondsTrans.filter(AnnotatedString(transformed)).text
        val builder = AnnotatedString.Builder(withSeconds)
        timeRegex.findAll(withSeconds.text).forEach { match ->
            builder.addStyle(
                SpanStyle(color = Color.LightGray),
                match.range.first,
                match.range.last + 1
            )
        }

        val alignedLines = transformed.split('\n')
        var index = 0
        var previousBlank = true
        alignedLines.forEach { line ->
            val end = index + line.length
            if (line.isNotBlank()) {
                if (previousBlank) {
                    builder.addStyle(
                        SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        index,
                        end
                    )
                } else {
                    builder.addStyle(
                        SpanStyle(fontSize = 13.sp),
                        index,
                        end
                    )
                }
            }
            previousBlank = line.isBlank()
            index = end + 1
        }

        return TransformedText(builder.toAnnotatedString(), offsetMapping)
    }
}
