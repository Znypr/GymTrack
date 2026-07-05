package com.example.gymtrack.core.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

internal object BackupArchiveReader {
    private const val MAX_ENTRY_BYTES = 50 * 1024 * 1024

    fun read(input: InputStream): BackupArchiveContents {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && isSupportedEntry(entry.name)) {
                    if (entries.containsKey(entry.name)) {
                        throw InvalidBackupException("Backup contains duplicate ${entry.name}")
                    }
                    entries[entry.name] = zip.readEntryBytes()
                }
                zip.closeEntry()
            }
        }

        val manifestBytes = entries[BackupArchive.MANIFEST_NAME]
            ?: throw InvalidBackupException("Backup manifest is missing")
        val payloadBytes = entries[BackupArchive.PAYLOAD_NAME]
            ?: throw InvalidBackupException("Backup data is missing")
        val manifest = BackupJsonCodec.decodeManifest(manifestBytes)
        if (manifest.formatVersion != BackupArchive.CURRENT_FORMAT_VERSION) {
            throw InvalidBackupException("Unsupported backup format version ${manifest.formatVersion}")
        }
        if (!manifest.payloadSha256.equals(BackupArchive.sha256(payloadBytes), ignoreCase = true)) {
            throw InvalidBackupException("Backup checksum does not match")
        }

        val payload = BackupJsonCodec.decodePayload(payloadBytes)
        BackupValidator.check(payload)
        if (BackupCounts.from(payload) != manifest.counts) {
            throw InvalidBackupException("Backup record counts do not match the manifest")
        }
        return BackupArchiveContents(manifest, payload)
    }

    private fun isSupportedEntry(name: String): Boolean =
        name == BackupArchive.MANIFEST_NAME || name == BackupArchive.PAYLOAD_NAME

    private fun ZipInputStream.readEntryBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_ENTRY_BYTES) throw InvalidBackupException("Backup entry is too large")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
