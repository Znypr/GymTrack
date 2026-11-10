package com.example.gymtrack.feature.stats

import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.util.WorkoutParser
import com.example.gymtrack.core.util.noteWorkoutDuration
import com.example.gymtrack.core.util.parseDurationSeconds
import com.example.gymtrack.core.util.parseNoteText

/**
 * Everything Stats needs from one note, read in a single pass (#303): the text is split once
 * and the workout parser runs once (twice only for a note without stored units, see
 * [resolveSetUnits]). Every Stats card reads digests instead of re-parsing the note.
 */
data class StatsNoteDigest(
    val timestamp: Long,
    /** Non-blank exercise rows (not indented). */
    val mainLineCount: Int,
    /** Non-blank set rows (indented with 4+ spaces). Timed sets are set rows too. */
    val subLineCount: Int,
    /** Longest grey elapsed time of any non-blank row, in seconds; null when no row has one. */
    val maxElapsedSeconds: Int?,
    /** Rows that are exercises, as the training insights count them. */
    val insightExerciseCount: Int,
    /** Every parsed set with the unit it was typed in. Timed sets have reps = 0 and a duration. */
    val sets: List<UnitResolvedSet>,
    /** Workout duration by docs/WORKOUT_DURATION.md, in minutes; null when unknown. */
    val workoutDurationMinutes: Float?,
)

/** Identity of a note's content for the digest memo: same text, metadata and units = same digest. */
private data class DigestKey(
    val timestamp: Long,
    val text: String,
    val rowMetadata: String,
    val storedUnits: List<WeightUnit>?,
)

/** Makes a [StatsNoteDigest]; tests swap it to count parses. */
fun interface StatsNoteDigester {
    fun digest(note: NoteLine, storedUnits: List<WeightUnit>?, parser: WorkoutParser): StatsNoteDigest
}

private val digitsOnlyRegex = Regex("^\\d+$")
private val setLineStartRegex = Regex("(?i)^[\\d+]+\\s*x.*")
private val setLinePrefixRegex = Regex("(?i)^x\\s*[\\d+]+.*")
private val repsRegex = Regex("(?i)(?:x\\s*([0-9+]+)|([0-9+]+)\\s*x|([0-9+]+)\\s*reps?)")

internal fun looksLikeSetLine(line: String): Boolean {
    val clean = line.trim()
    return setLineStartRegex.matches(clean) ||
        setLinePrefixRegex.matches(clean) ||
        repsRegex.containsMatchIn(clean) ||
        digitsOnlyRegex.matches(clean) ||
        line.startsWith("    ")
}

val DefaultStatsNoteDigester = StatsNoteDigester { note, storedUnits, parser ->
    val (visibleLines, absoluteTimes) = parseNoteText(note.text, note.rowMetadata)
    var main = 0
    var sub = 0
    var exercises = 0
    for (line in visibleLines) {
        if (line.isBlank()) continue
        if (line.startsWith("    ")) sub++ else main++
        if (!looksLikeSetLine(line)) exercises++
    }
    val maxElapsed = absoluteTimes.asSequence()
        .filter(String::isNotBlank)
        .map(::parseDurationSeconds)
        .maxOrNull()
    StatsNoteDigest(
        timestamp = note.timestamp,
        mainLineCount = main,
        subLineCount = sub,
        maxElapsedSeconds = maxElapsed,
        insightExerciseCount = exercises,
        sets = resolveSetUnits(note, parser, storedUnits),
        workoutDurationMinutes = noteWorkoutDuration(note.text, note.rowMetadata, note.timestamp)
            ?.seconds
            ?.div(60f),
    )
}

/**
 * Memo of digests by (timestamp, text, metadata, stored units), so a note is parsed at most once
 * per load and not again on the next load while it is unchanged. Bounded LRU; thread-safe.
 */
class StatsDigestCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val digester: StatsNoteDigester = DefaultStatsNoteDigester,
) {
    private val entries = object : LinkedHashMap<DigestKey, StatsNoteDigest>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<DigestKey, StatsNoteDigest>?): Boolean =
            size > maxEntries
    }

    /** Notes digested (parsed) since this cache was made; for tests and benchmarks. */
    @Volatile
    var parseCount: Int = 0
        private set

    fun digest(
        note: NoteLine,
        storedUnits: List<WeightUnit>?,
        parser: WorkoutParser,
    ): StatsNoteDigest {
        val key = DigestKey(note.timestamp, note.text, note.rowMetadata, storedUnits)
        synchronized(entries) { entries[key]?.let { return it } }
        val digest = digester.digest(note, storedUnits, parser)
        synchronized(entries) {
            parseCount++
            entries[key] = digest
        }
        return digest
    }

    /** Digests for [notes], in order; only notes not in the memo are parsed (one parser per call). */
    fun digestAll(
        notes: List<NoteLine>,
        storedSetUnits: Map<Long, List<WeightUnit>>,
        parserFactory: () -> WorkoutParser = ::WorkoutParser,
    ): List<StatsNoteDigest> {
        val parser = parserFactory()
        return notes.map { digest(it, storedSetUnits[it.timestamp], parser) }
    }

    companion object {
        /** Several years of history; one digest is a few hundred bytes plus its parsed sets. */
        const val DEFAULT_MAX_ENTRIES = 4_096
    }
}
