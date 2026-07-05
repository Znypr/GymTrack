package com.example.gymtrack.core.backup

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

object BackupArchive {
    const val CURRENT_FORMAT_VERSION = 1
    internal const val MANIFEST_NAME = "manifest.json"
    internal const val PAYLOAD_NAME = "data.json"

    fun write(
        output: OutputStream,
        payload: GymTrackBackupPayload,
        appVersion: String,
        databaseSchemaVersion: Int,
        createdAtEpochMillis: Long = System.currentTimeMillis(),
    ): BackupManifest = BackupArchiveWriter.write(
        output = output,
        payload = payload,
        appVersion = appVersion,
        databaseSchemaVersion = databaseSchemaVersion,
        createdAtEpochMillis = createdAtEpochMillis,
    )

    fun read(input: InputStream): BackupArchiveContents = BackupArchiveReader.read(input)

    internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}
