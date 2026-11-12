package com.example.gymtrack.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.gymtrack.core.data.ExerciseMuscleEntity
import com.example.gymtrack.core.data.NoteDatabase
import com.example.gymtrack.core.data.SettingsStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** #310: the user's muscle edits survive a backup round trip; old backups without them still load. */
@RunWith(AndroidJUnit4::class)
class ExerciseMusclesBackupTest {
    private val edits = listOf(
        ExerciseMuscleEntity(1L, "chestLower", "PRIMARY"),
        ExerciseMuscleEntity(1L, "triceps", "SECONDARY"),
    )

    @Test
    fun archiveRoundTripKeepsMuscleEdits() {
        val payload = BackupFixtures.payload().copy(exerciseMuscles = edits)
        val bytes = ByteArrayOutputStream().also { BackupArchive.write(it, payload, "test", 15) }.toByteArray()
        val contents = BackupArchive.read(ByteArrayInputStream(bytes))
        assertEquals(edits, contents.payload.exerciseMuscles)
        assertEquals(2, contents.manifest.counts.exerciseMuscles)
    }

    @Test
    fun payloadAndManifestWithoutTheFieldDecode() {
        val encoded = JSONObject(String(BackupJsonCodec.encodePayload(BackupFixtures.payload())))
        encoded.remove("exerciseMuscles")
        val decoded = BackupJsonCodec.decodePayload(encoded.toString().toByteArray())
        assertTrue(decoded.exerciseMuscles.isEmpty())

        val manifest = JSONObject(String(BackupJsonCodec.encodeManifest(BackupManifest(1, 0L, "old", 14, "x", BackupCounts.from(decoded)))))
        manifest.getJSONObject("counts").remove("exerciseMuscles")
        assertEquals(0, BackupJsonCodec.decodeManifest(manifest.toString().toByteArray()).counts.exerciseMuscles)
    }

    @Test
    fun restoreWritesMuscleEditsIntoTheDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val oldSettings = SettingsStore.load(context)
        val database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java).allowMainThreadQueries().build()
        try {
            val payload = BackupFixtures.payload().copy(exerciseMuscles = edits)
            val file = File(context.cacheDir, "muscles-${System.nanoTime()}.gymtrack-backup")
            file.outputStream().use { BackupArchive.write(it, payload, "test", 15) }
            val repository = BackupRepository(database)
            val result = repository.restoreBackup(context, context.contentResolver, Uri.fromFile(file))
            assertEquals(edits, database.exerciseMuscleDao().getAll().sortedBy { it.muscle })
            assertEquals(edits.sortedBy { it.muscle }, repository.snapshot(result.settings).exerciseMuscles.sortedBy { it.muscle })
        } finally {
            database.close()
            SettingsStore.save(context, oldSettings)
        }
    }
}
