package com.example.gymtrack.feature.home

import com.example.gymtrack.core.data.NoteLine

private const val FINGERPRINT_SEPARATOR = "\u001F"
private const val TIMESTAMP_COLLISION_OFFSET_MILLIS = 1_000L

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
