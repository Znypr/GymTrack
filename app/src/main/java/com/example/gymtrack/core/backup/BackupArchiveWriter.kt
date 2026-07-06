package com.example.gymtrack.core.backup

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object BackupArchiveWriter {
    fun write(
        output: OutputStream,
        payload: GymTrackBackupPayload,
        appVersion: String,
        databaseSchemaVersion: Int,
        createdAtEpochMillis: Long,
    ): BackupManifest {
        BackupValidator.check(payload)
        val payloadBytes = BackupJsonCodec.encodePayload(payload)
        val manifest = BackupManifest(
            formatVersion = BackupArchive.CURRENT_FORMAT_VERSION,
            createdAtEpochMillis = createdAtEpochMillis,
            appVersion = appVersion,
            databaseSchemaVersion = databaseSchemaVersion,
            payloadSha256 = BackupArchive.sha256(payloadBytes),
            counts = BackupCounts.from(payload),
        )
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(BackupArchive.MANIFEST_NAME))
            zip.write(BackupJsonCodec.encodeManifest(manifest))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(BackupArchive.PAYLOAD_NAME))
            zip.write(payloadBytes)
            zip.closeEntry()
        }
        return manifest
    }
}
