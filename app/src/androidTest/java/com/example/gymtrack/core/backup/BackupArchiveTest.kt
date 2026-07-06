package com.example.gymtrack.core.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupArchiveTest {
    @Test
    fun archiveRoundTripPreservesPayload() {
        val payload = BackupFixtures.payload()
        val output = ByteArrayOutputStream()
        val manifest = BackupArchive.write(output, payload, "1.8", 9, 1234L)
        val restored = BackupArchive.read(ByteArrayInputStream(output.toByteArray()))

        assertEquals(payload, restored.payload)
        assertEquals(manifest, restored.manifest)
        assertEquals(9, restored.manifest.counts.totalRecords)
    }

    @Test
    fun changedPayloadIsRejected() {
        val entries = archiveEntries(createArchive()).toMutableMap()
        val payload = entries.getValue("data.json").copyOf()
        payload[payload.lastIndex] = (payload.last().toInt() xor 1).toByte()
        entries["data.json"] = payload

        assertThrows(InvalidBackupException::class.java) {
            BackupArchive.read(ByteArrayInputStream(writeArchive(entries)))
        }
    }

    @Test
    fun unsupportedFormatVersionIsRejected() {
        val entries = archiveEntries(createArchive()).toMutableMap()
        val manifest = BackupJsonCodec.decodeManifest(entries.getValue("manifest.json"))
        entries["manifest.json"] = BackupJsonCodec.encodeManifest(manifest.copy(formatVersion = 99))

        assertThrows(InvalidBackupException::class.java) {
            BackupArchive.read(ByteArrayInputStream(writeArchive(entries)))
        }
    }

    @Test
    fun missingRelationshipIsRejected() {
        val payload = BackupFixtures.payload()
        val invalid = payload.copy(
            canonicalWorkoutSets = listOf(
                payload.canonicalWorkoutSets.single().copy(workoutExerciseId = "missing"),
            ),
        )
        assertThrows(InvalidBackupException::class.java) {
            BackupValidator.check(invalid)
        }
    }

    private fun createArchive(): ByteArray = ByteArrayOutputStream().also { output ->
        BackupArchive.write(output, BackupFixtures.payload(), "1.8", 9, 1234L)
    }.toByteArray()

    private fun archiveEntries(archive: ByteArray): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun writeArchive(entries: Map<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }.toByteArray()
}
