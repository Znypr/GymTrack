package com.example.gymtrack.domain.notebookimport

import java.security.MessageDigest

/**
 * Pure page-intake helper for capture/upload sources.
 *
 * Android picker/camera code should read bytes outside the domain layer, then pass those bytes here
 * to create page draft records with deterministic exact-byte fingerprints. Higher-level perceptual
 * duplicate detection can be added later, but exact fingerprinting is the first safety gate.
 */
class NotebookPageSource(
    val id: String,
    sourceBytes: ByteArray,
    val sourceUri: String? = null,
    val capturedAtEpochMillis: Long? = null,
) {
    val sourceBytes: ByteArray = sourceBytes.copyOf()

    init {
        require(id.isNotBlank()) { "Notebook page source id must not be blank" }
        require(sourceBytes.isNotEmpty()) { "Notebook page source bytes must not be empty" }
        require(sourceUri == null || sourceUri.isNotBlank()) { "Source URI must not be blank" }
        require(capturedAtEpochMillis == null || capturedAtEpochMillis >= 0) {
            "Capture time must not be negative"
        }
    }
}

object NotebookPageIntake {

    fun sha256Hex(sourceBytes: ByteArray): String {
        require(sourceBytes.isNotEmpty()) { "Cannot fingerprint an empty notebook page source" }
        return MessageDigest
            .getInstance("SHA-256")
            .digest(sourceBytes)
            .toLowerHex()
    }

    fun createPageDrafts(sources: List<NotebookPageSource>): List<NotebookPageDraft> {
        require(sources.isNotEmpty()) { "Notebook page intake requires at least one source" }
        require(sources.map { it.id }.distinct().size == sources.size) {
            "Notebook page source ids must be unique"
        }

        val pages = sources.mapIndexed { index, source ->
            NotebookPageDraft(
                id = source.id,
                position = index,
                sourceFingerprintSha256 = sha256Hex(source.sourceBytes),
                sourceUri = source.sourceUri,
                capturedAtEpochMillis = source.capturedAtEpochMillis,
            )
        }

        require(pages.map { it.sourceFingerprintSha256 }.distinct().size == pages.size) {
            "Duplicate notebook page sources must be resolved before import"
        }

        return pages
    }
}

private val HEX_CHARS = "0123456789abcdef".toCharArray()

private fun ByteArray.toLowerHex(): String = buildString(size * 2) {
    for (byte in this@toLowerHex) {
        val value = byte.toInt() and 0xFF
        append(HEX_CHARS[value ushr 4])
        append(HEX_CHARS[value and 0x0F])
    }
}
