package com.example.gymtrack.feature.home

import com.example.gymtrack.core.data.NoteLine

private const val FINGERPRINT_SEPARATOR = "\u001F"
private const val TIMESTAMP_COLLISION_OFFSET_MILLIS = 1_000L

internal data class LegacyCsvImportCandidate(
    val displayName: String,
    val note: NoteLine,
    val fileSizeBytes: Long,
) {
    val fullFingerprint: String = legacyCsvFullFingerprint(note)
    val contentFingerprint: String = legacyCsvContentFingerprint(note)
    val completenessScore: Int = legacyCsvCompletenessScore(note, fileSizeBytes)
}

internal data class LegacyCsvCandidateSelection(
    val selected: List<LegacyCsvImportCandidate>,
    val exactDuplicates: Int,
    val supersededSnapshots: Int,
)

internal fun selectBestLegacyCsvCandidates(
    candidates: List<LegacyCsvImportCandidate>,
): LegacyCsvCandidateSelection {
    var exactDuplicates = 0
    var supersededSnapshots = 0
    val selected = candidates
        .groupBy { it.note.timestamp }
        .toSortedMap()
        .values
        .mapNotNull { timestampGroup ->
            val uniqueCandidates = timestampGroup
                .groupBy { it.fullFingerprint }
                .values
                .map { duplicateGroup ->
                    exactDuplicates += duplicateGroup.size - 1
                    duplicateGroup.maxWithOrNull(legacyCsvCandidateComparator) ?: duplicateGroup.first()
                }
            supersededSnapshots += (uniqueCandidates.size - 1).coerceAtLeast(0)
            uniqueCandidates.maxWithOrNull(legacyCsvCandidateComparator)
        }

    return LegacyCsvCandidateSelection(
        selected = selected,
        exactDuplicates = exactDuplicates,
        supersededSnapshots = supersededSnapshots,
    )
}

private val legacyCsvCandidateComparator = compareBy<LegacyCsvImportCandidate> { it.completenessScore }
    .thenBy { it.fileSizeBytes }
    .thenBy { it.displayName }

internal fun legacyCsvFullFingerprint(note: NoteLine): String = buildLegacyCsvFingerprint(
    note = note,
    includeTimestamp = true,
)

internal fun legacyCsvContentFingerprint(note: NoteLine): String = buildLegacyCsvFingerprint(
    note = note,
    includeTimestamp = false,
)

private fun buildLegacyCsvFingerprint(note: NoteLine, includeTimestamp: Boolean): String {
    return buildList {
        if (includeTimestamp) add(note.timestamp.toString())
        add(note.title)
        add(note.categoryName.orEmpty())
        add(note.categoryColor?.toString().orEmpty())
        add(note.learnings)
        add(note.text)
        add(note.rowMetadata)
    }.joinToString(FINGERPRINT_SEPARATOR)
}

private fun legacyCsvCompletenessScore(note: NoteLine, fileSizeBytes: Long): Int {
    val nonBlankTextLines = note.text.lineSequence().count { it.isNotBlank() }
    val setLikeLines = note.text.lineSequence().count { line ->
        line.trimStart().startsWith("1x") ||
            line.trimStart().startsWith("2x") ||
            line.trimStart().startsWith("3x") ||
            line.trimStart().startsWith("4x") ||
            line.trimStart().startsWith("5x") ||
            line.trimStart().startsWith("6x") ||
            line.trimStart().startsWith("7x") ||
            line.trimStart().startsWith("8x") ||
            line.trimStart().startsWith("9x") ||
            line.trimStart().startsWith("10x")
    }
    return nonBlankTextLines * 10_000 +
        setLikeLines * 1_000 +
        note.rowMetadata.length +
        fileSizeBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

internal data class LegacyCsvTimestampAllocation(
    val timestamp: Long,
    val adjusted: Boolean,
)

internal fun allocateLegacyCsvTimestamp(
    originalTimestamp: Long,
    usedTimestamps: MutableSet<Long>,
): LegacyCsvTimestampAllocation {
    var candidate = originalTimestamp
    var adjusted = false
    while (!usedTimestamps.add(candidate)) {
        candidate += TIMESTAMP_COLLISION_OFFSET_MILLIS
        adjusted = true
    }
    return LegacyCsvTimestampAllocation(
        timestamp = candidate,
        adjusted = adjusted,
    )
}
