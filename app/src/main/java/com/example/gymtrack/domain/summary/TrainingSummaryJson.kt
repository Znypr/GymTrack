package com.example.gymtrack.domain.summary

import java.util.Locale

object TrainingSummaryJson {
    fun encode(summary: TrainingSummary): String = buildString {
        append('{')
        appendField("schema_version", summary.schemaVersion)
        appendField("workout_id", summary.workoutId)
        appendField("date", summary.date)
        appendField("started_at", summary.startedAt)
        appendNullableField("ended_at", summary.endedAt)
        appendNullableField("focus", summary.focus)
        appendField("status", summary.status.name.lowercase(Locale.ROOT))
        appendNullableField("duration_min", summary.durationMinutes)
        appendField("exercise_count", summary.exerciseCount)
        appendField("set_count", summary.setCount)
        appendStringArrayField("top_lifts", summary.topLifts)
        appendField(
            "performance_signal",
            summary.performanceSignal.name.lowercase(Locale.ROOT),
        )
        appendNullableField("energy", summary.energy)
        appendNullableField("recovery_note", summary.recoveryNote)
        appendField("source", summary.source)
        appendLastField("source_updated_at", summary.sourceUpdatedAt)
        append('}')
    }

    private fun StringBuilder.appendField(name: String, value: String) {
        appendQuoted(name)
        append(':')
        appendQuoted(value)
        append(',')
    }

    private fun StringBuilder.appendField(name: String, value: Int) {
        appendQuoted(name)
        append(':')
        append(value)
        append(',')
    }

    private fun StringBuilder.appendNullableField(name: String, value: String?) {
        appendQuoted(name)
        append(':')
        if (value == null) append("null") else appendQuoted(value)
        append(',')
    }

    private fun StringBuilder.appendNullableField(name: String, value: Int?) {
        appendQuoted(name)
        append(':')
        if (value == null) append("null") else append(value)
        append(',')
    }

    private fun StringBuilder.appendStringArrayField(name: String, values: List<String>) {
        appendQuoted(name)
        append(':')
        append('[')
        values.forEachIndexed { index, value ->
            if (index > 0) append(',')
            appendQuoted(value)
        }
        append(']')
        append(',')
    }

    private fun StringBuilder.appendLastField(name: String, value: String) {
        appendQuoted(name)
        append(':')
        appendQuoted(value)
    }

    private fun StringBuilder.appendQuoted(value: String) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (character.code < 0x20) {
                        append("\\u")
                        append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        append(character)
                    }
                }
            }
        }
        append('"')
    }
}
