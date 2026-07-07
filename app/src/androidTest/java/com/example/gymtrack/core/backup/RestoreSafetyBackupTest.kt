package com.example.gymtrack.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.gymtrack.core.data.NoteDatabase
import com.example.gymtrack.core.data.SettingsStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestoreSafetyBackupTest {
    @Test
    fun safetyBackupCreatedBeforeRestoreCanRecoverPreviousLocalData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val oldSettings = SettingsStore.load(context)
        val database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = BackupRepository(database)
            val original = BackupFixtures.payload()
            val replacement = original.replacementPayload()

            assertFalse(repository.hasRestorableLocalData(original.settings))
            repository.restoreBackup(context, context.contentResolver, archive(context, "original", original))
            assertTrue(repository.hasRestorableLocalData(original.settings))

            val safetyBackup = File(context.cacheDir, "safety-${System.nanoTime()}.gymtrack-backup")
            repository.createBackup(
                contentResolver = context.contentResolver,
                destination = Uri.fromFile(safetyBackup),
                settings = original.settings,
                appVersion = "test",
                databaseSchemaVersion = 10,
            )

            repository.restoreBackup(context, context.contentResolver, archive(context, "replacement", replacement))
            assertEquals(replacement, repository.snapshot(replacement.settings))

            repository.restoreBackup(context, context.contentResolver, Uri.fromFile(safetyBackup))
            assertEquals(original, repository.snapshot(original.settings))
        } finally {
            database.close()
            SettingsStore.save(context, oldSettings)
        }
    }

    private fun archive(context: Context, name: String, payload: GymTrackBackupPayload): Uri {
        val file = File(context.cacheDir, "backup-test-$name-${System.nanoTime()}.gymtrack-backup")
        file.outputStream().use { BackupArchive.write(it, payload, "test", 10, 1_000L) }
        return Uri.fromFile(file)
    }
}
