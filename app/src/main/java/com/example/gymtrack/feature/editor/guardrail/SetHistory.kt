package com.example.gymtrack.feature.editor.guardrail

import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.util.ParsedSetDTO
import com.example.gymtrack.core.util.WorkoutParser
import com.example.gymtrack.core.util.buildNoteRowMetadata
import com.example.gymtrack.core.util.parseNoteText

/** 1 lb in kg. Weights are compared in kg so kg and lb sessions of one exercise mix correctly. */
internal const val KG_PER_LB = 0.45359237

internal fun toKg(weight: Double, unit: String): Double = if (unit.equals("LB", ignoreCase = true)) weight * KG_PER_LB else weight

/** The relative time a set row stores at its end: `      8x 80 (1'30'')`. */
private val SET_REST_SUFFIX = Regex("""\((\d+)'(\d{2})''\)\s*$""")

/**
 * The history key of an exercise row: the strict identity of a set under it, so the editor and the
 * history resolve names (aliases, brands, L/R) exactly like the parser and Stats do.
 */
internal fun exerciseKey(parser: WorkoutParser, exerciseName: String, flag: ExerciseFlag, defaultUnit: String): String? {
    val text = "${exerciseName.trim()}\n      1x 1"
    val metadata = buildNoteRowMetadata(listOf("", ""), listOf(flag, flag))
    return runCatching { parser.parseWorkout(text, defaultUnit, metadata).firstOrNull()?.exerciseIdentity?.strictComparisonKey }.getOrNull()
}

/** One logged set of an exercise in an earlier workout. */
data class HistorySet(
    val weight: Double,
    val unit: String,
    val reps: Int,
    val isTimed: Boolean,
) {
    val weightKg: Double get() = toKg(weight, unit)
}

/**
 * One earlier workout's sets of one exercise, in logged order, plus the rests between them (the
 * relative times of every set after the first; the first one also holds the walk to the station).
 */
data class HistorySession(val workoutTimestamp: Long, val sets: List<HistorySet>, val restSeconds: List<Int> = emptyList())

/**
 * The user's own history of one exercise (strict identity key, so a dumbbell and a machine
 * variant never share a reference), newest session first. Built once per editor open.
 */
data class ExerciseHistory(val sessions: List<HistorySession>) {
    val sessionCount: Int get() = sessions.size

    private val recent: List<HistorySession> get() = sessions.take(GuardrailThresholds.RECENT_SESSIONS)

    /**
     * Heaviest set of the recent sessions, ignoring a session max that is itself an outlier (an
     * old `825` the user never fixed must not lift the reference and hide the next slip).
     */
    val recentMaxKg: Double by lazy {
        val maxes = recent.mapNotNull { s -> s.sets.filter { !it.isTimed && it.weight > 0 }.maxOfOrNull { it.weightKg } }
        if (maxes.isEmpty()) return@lazy 0.0
        val median = maxes.sorted()[maxes.size / 2]
        maxes.filter { it <= median * GuardrailThresholds.OUTLIER_FACTOR }.maxOrNull() ?: median
    }

    /** The last weighted set of the newest session, for "(last 80×8)". */
    val lastWeightedSet: HistorySet? by lazy {
        sessions.firstNotNullOfOrNull { s -> s.sets.lastOrNull { !it.isTimed && it.weight > 0 } }
    }

    val maxReps: Int by lazy { sessions.flatMap { it.sets }.filter { !it.isTimed }.maxOfOrNull { it.reps } ?: 0 }

    val repRange: IntRange? by lazy {
        val reps = recent.flatMap { it.sets }.filter { !it.isTimed && it.reps > 0 }.map { it.reps }
        if (reps.isEmpty()) null else reps.min()..reps.max()
    }

    /** The usual rest between sets: median of the recent rests, rounded to 15 s. Null if too few. */
    val usualRestSeconds: Int? by lazy {
        val rests = recent.flatMap { it.restSeconds }.filter { it in GuardrailThresholds.REST_RANGE_SECONDS }
        if (rests.size < GuardrailThresholds.MIN_REST_SAMPLES) return@lazy null
        val median = rests.sorted()[rests.size / 2]
        ((median + 7) / 15) * 15
    }
}

/** Per-exercise history keyed by [com.example.gymtrack.core.util.ExerciseIdentity.strictComparisonKey]. */
class SetHistory internal constructor(private val byKey: Map<String, ExerciseHistory>) {
    fun forKey(key: String): ExerciseHistory? = byKey[key]

    val exerciseCount: Int get() = byKey.size

    companion object {
        val EMPTY = SetHistory(emptyMap())

        /**
         * Parses every stored workout except [excludeTimestamp] (the one being edited: an edited
         * old workout must not be checked against its own sets). Heavy: call off the main thread.
         */
        fun build(
            notes: List<NoteLine>,
            excludeTimestamp: Long?,
            defaultUnit: String,
            parser: WorkoutParser = WorkoutParser(),
        ): SetHistory {
            val sessions = mutableMapOf<String, MutableList<HistorySession>>()
            val keyCache = HashMap<Pair<String, ExerciseFlag>, String?>()
            notes.asSequence()
                .filter { it.timestamp != excludeTimestamp && it.text.isNotBlank() }
                .sortedByDescending { it.timestamp }
                .forEach { note ->
                    val sets = runCatching { parser.parseWorkout(note.text, defaultUnit, note.rowMetadata) }.getOrNull() ?: return@forEach
                    val rests = restsByKey(note, parser, defaultUnit, keyCache)
                    sets.groupBy { it.exerciseIdentity.strictComparisonKey }.forEach { (key, group) ->
                        sessions.getOrPut(key) { mutableListOf() } +=
                            HistorySession(note.timestamp, group.map(::toHistorySet), rests[key].orEmpty())
                    }
                }
            return SetHistory(sessions.mapValues { ExerciseHistory(it.value) })
        }

        /**
         * The parser keeps one time per exercise, so rests are read from the rows themselves: per
         * exercise block (a name row and the set rows under it, up to a blank row), every set
         * after the first.
         */
        private fun restsByKey(
            note: NoteLine,
            parser: WorkoutParser,
            defaultUnit: String,
            keyCache: HashMap<Pair<String, ExerciseFlag>, String?>,
        ): Map<String, List<Int>> {
            val (lines, _, flags) = runCatching { parseNoteText(note.text, note.rowMetadata) }.getOrNull() ?: return emptyMap()
            val out = mutableMapOf<String, MutableList<Int>>()
            var i = 0
            while (i < lines.size) {
                if (lines[i].isBlank()) {
                    i++
                    continue
                }
                val name = lines[i]
                val flag = flags.getOrNull(i) ?: ExerciseFlag.BILATERAL
                var j = i + 1
                val rests = mutableListOf<Int>()
                while (j < lines.size && lines[j].isNotBlank()) {
                    SET_REST_SUFFIX.find(lines[j])?.let { rests += it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
                    j++
                }
                if (rests.size > 1) {
                    keyCache.getOrPut(name.trim() to flag) { exerciseKey(parser, name, flag, defaultUnit) }?.let { key ->
                        out.getOrPut(key) { mutableListOf() } += rests.drop(1)
                    }
                }
                i = j
            }
            return out
        }

        private fun toHistorySet(set: ParsedSetDTO) = HistorySet(
            weight = set.weight.toDouble(),
            unit = set.weightUnit,
            reps = set.reps,
            isTimed = set.durationSeconds != null,
        )
    }
}
