package com.example.gymtrack.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.gymtrack.core.data.NoteDatabase
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.SettingsStore
import com.example.gymtrack.core.util.exportNote
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CrossVersionBackupFixtureTest {
    @Test
    fun schema8FixtureRestoresIntoCurrentSchemaAndKeepsVisibleDataUsable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val oldSettings = SettingsStore.load(context)
        val database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = BackupRepository(database)
            val fixture = archiveFromFixture(context, "backup-fixtures/schema8-v1")
            val contents = fixture.inputStream().use { BackupArchive.read(it) }

            assertEquals(1, contents.manifest.formatVersion)
            assertEquals("0.9.0-schema8-fixture", contents.manifest.appVersion)
            assertEquals(8, contents.manifest.databaseSchemaVersion)
            assertEquals(expectedSchema8Counts(), contents.manifest.counts)

            val result = repository.restoreBackup(context, context.contentResolver, Uri.fromFile(fixture))
            val restored = repository.snapshot(result.settings)
            assertEquals(contents.payload, restored)
            assertEquals(contents.manifest, result.manifest)

            val restoredNotes = database.noteDao().getAllForBackup()
            assertEquals(2, restoredNotes.size)
            val pushNote = database.noteDao().getById(1_720_000_000_000L) ?: error("Push fixture note missing")
            assertEquals("Schema 8 Push Day", pushNote.title)
            assertTrue(pushNote.text.contains("Bench Press 100 kg x5"))
            assertEquals("Keep elbows tucked.", pushNote.learnings)

            val canonicalSets = database.backupDao().getCanonicalWorkoutSets()
            assertEquals(4, canonicalSets.size)
            assertEquals("LB", canonicalSets.single { it.id == "cset-incline-1" }.weightUnit)
            assertEquals("KG", canonicalSets.single { it.id == "cset-bench-1" }.weightUnit)

            val benchHistory = database.setDao().getAverageWeightHistory(201L).first().single()
            assertEquals(1_720_000_000_000L, benchHistory.originTimestamp)
            assertEquals(((100f * 5f) + (90f * 8f)) / 13f, benchHistory.avgVal, 0.001f)

            val exportedCsv = exportNote(
                context = context,
                note = NoteLine(
                    title = pushNote.title,
                    text = pushNote.text,
                    timestamp = pushNote.timestamp,
                    categoryName = pushNote.categoryName,
                    categoryColor = pushNote.categoryColor,
                    learnings = pushNote.learnings.orEmpty(),
                ),
                settings = result.settings,
            )
            assertNotEquals("export_failed.log", exportedCsv.name)
            assertTrue(exportedCsv.readText().contains("Schema 8 Push Day"))
            assertTrue(exportedCsv.readText().contains("Bench Press 100 kg x5"))
        } finally {
            database.close()
            SettingsStore.save(context, oldSettings)
        }
    }

    @Test
    fun unsupportedFixtureFormatVersionFailsSafely() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixture = archiveFromFixture(context, "backup-fixtures/schema8-format99")

        val error = assertThrows(InvalidBackupException::class.java) {
            fixture.inputStream().use { BackupArchive.read(it) }
        }

        assertEquals("Unsupported backup format version 99", error.message)
    }

    private fun expectedSchema8Counts() = BackupCounts(
        legacyNotes = 2,
        legacyExercises = 2,
        legacySets = 3,
        canonicalCategories = 2,
        canonicalExercises = 3,
        canonicalExerciseAliases = 3,
        canonicalWorkouts = 2,
        canonicalWorkoutExercises = 3,
        canonicalWorkoutSets = 4,
    )

    private fun archiveFromFixture(context: Context, fixtureDirectory: String): File {
        val file = File(context.cacheDir, "${fixtureDirectory.substringAfterLast('/')}-${System.nanoTime()}.gymtrack-backup")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.writeAssetEntry(fixtureDirectory, BackupArchive.MANIFEST_NAME)
            zip.writeAssetEntry(fixtureDirectory, BackupArchive.PAYLOAD_NAME)
        }
        return file
    }

    private fun ZipOutputStream.writeAssetEntry(fixtureDirectory: String, name: String) {
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        putNextEntry(ZipEntry(name))
        testAssets.open("$fixtureDirectory/$name").use { input ->
            input.copyTo(this)
        }
        closeEntry()
    }
}
