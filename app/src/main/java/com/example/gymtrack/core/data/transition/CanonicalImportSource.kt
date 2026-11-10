package com.example.gymtrack.core.data.transition

import com.example.gymtrack.core.data.CanonicalWorkoutEntity
import com.example.gymtrack.core.data.NoteEntity
import com.example.gymtrack.domain.model.WorkoutStatus
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Change detection for the legacy-to-canonical import.
 *
 * Every projected workout carries a source stamp (`canonical-import-v1 src=<hash>`) at the start of
 * `legacy_migration_message`. The hash covers the import version and every note field the projector
 * reads, so an unchanged note is recognised from the workout row alone: no projection, no child-row
 * reads and no writes. Bumping [CANONICAL_IMPORT_VERSION] invalidates every stamp.
 */
internal object CanonicalImportSource {
    private const val HASH_LENGTH = 16
    private val stampPattern = Regex(
        "^${Regex.escape(CANONICAL_IMPORT_VERSION)} src=([0-9a-f]{$HASH_LENGTH})(?::|$)",
    )

    fun hash(note: NoteEntity): String {
        val fields = listOf(
            CANONICAL_IMPORT_VERSION,
            note.timestamp.toString(),
            note.title,
            note.text,
            note.rowMetadata,
            note.learnings,
            note.categoryName?.trim()?.takeIf(String::isNotEmpty)?.let(CanonicalKeys::normalize),
            note.categoryColor?.toString(),
        )
        // Length-prefixed fields keep the encoding unambiguous; null and "" differ.
        val encoded = fields.joinToString(separator = "") { field ->
            if (field == null) "-;" else "${field.length}:$field;"
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(encoded.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }.take(HASH_LENGTH)
    }

    fun stamp(note: NoteEntity): String = "$CANONICAL_IMPORT_VERSION src=${hash(note)}"

    /** The source hash stored on [message], or null for rows written before stamping existed. */
    fun storedHash(message: String?): String? =
        message?.let { stampPattern.find(it)?.groupValues?.get(1) }

    fun isCurrent(existing: CanonicalWorkoutEntity?, note: NoteEntity): Boolean =
        existing != null && storedHash(existing.legacyMigrationMessage) == hash(note)

    /**
     * The import rule for a row that already exists: content comes from the new projection, but the
     * import never lowers the status (a `COMPLETED` row written by the completion path stays
     * `COMPLETED`), never moves `createdAt` later and never moves `updatedAt` back in time.
     */
    fun merge(existing: CanonicalWorkoutEntity?, projected: CanonicalWorkoutEntity): CanonicalWorkoutEntity {
        if (existing == null) return projected
        return projected.copy(
            status = higherStatus(existing.status, projected.status),
            createdAt = minOf(existing.createdAt, projected.createdAt),
            updatedAt = maxOf(existing.updatedAt, projected.updatedAt),
        )
    }

    private fun higherStatus(existing: String, projected: String): String {
        val existingRank = statusRank(existing) ?: return existing
        val projectedRank = statusRank(projected) ?: return existing
        return if (projectedRank > existingRank) projected else existing
    }

    private fun statusRank(status: String): Int? = when (status) {
        WorkoutStatus.DRAFT.name -> 0
        WorkoutStatus.PARTIAL.name -> 1
        WorkoutStatus.COMPLETED.name -> 2
        else -> null
    }
}
