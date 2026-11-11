package com.example.gymtrack.feature.editor.guardrail

import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.util.parseNoteText
import com.example.gymtrack.feature.editor.splitSetRow

/** How many sets of a history would have shown a hint, per rule (#304 false-positive check). */
data class GuardrailReplayReport(val sets: Int, val hints: Map<GuardrailRule, Int>, val examples: List<String>) {
    val hinted: Int get() = hints.values.sum()
    val rate: Double get() = if (sets == 0) 0.0 else hinted.toDouble() / sets
}

/**
 * Replays stored workouts oldest first, each set checked at "Enter" against only the workouts
 * before it, exactly as the editor would have. Run it on a real database export to measure the
 * false-positive rate (acceptance: under 2 %). Debug/test tool: it parses the history once per
 * workout, so it is O(workouts²).
 */
fun replayGuardrails(notes: List<NoteLine>, defaultUnit: String = "KG", maxExamples: Int = 20): GuardrailReplayReport {
    val ordered = notes.filter { it.text.isNotBlank() }.sortedBy { it.timestamp }
    var sets = 0
    val hints = mutableMapOf<GuardrailRule, Int>()
    val examples = mutableListOf<String>()
    ordered.forEachIndexed { k, note ->
        val guard = SetGuardrails(SetHistory.build(ordered.subList(0, k), null, defaultUnit), defaultUnit)
        val (lines, _, flags) = parseNoteText(note.text, note.rowMetadata)
        var i = 0
        while (i < lines.size) {
            if (lines[i].isBlank()) {
                i++
                continue
            }
            val name = lines[i]
            val flag = flags.getOrNull(i) ?: ExerciseFlag.BILATERAL
            val earlier = mutableListOf<String>()
            var j = i + 1
            while (j < lines.size && lines[j].isNotBlank()) {
                val parts = splitSetRow(lines[j])
                val rest = Regex("""(\d+)'(\d{2})""").find(parts.restSuffix)?.let { it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
                sets++
                guard.check(GuardrailBlock(name, flag, earlier.toList(), parts.entry, rest))?.let { hint ->
                    hints[hint.rule] = (hints[hint.rule] ?: 0) + 1
                    if (examples.size < maxExamples) examples += "$name | ${parts.entry} → ${hint.message}"
                }
                earlier += parts.entry
                j++
            }
            i = j
        }
    }
    return GuardrailReplayReport(sets, hints, examples)
}
