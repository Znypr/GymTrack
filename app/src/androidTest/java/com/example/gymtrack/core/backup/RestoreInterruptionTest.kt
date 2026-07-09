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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestoreInterruptionTest {
    @Test
    fun midRestoreInterruptionPreservesPreviousDatabaseAndSettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val oldSettings = SettingsStore.load(context)
        val database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        var shouldInterrupt = false

        try {
            val repository = BackupRepository(database) { point ->
                if (point == BackupRepository.RestoreInterruptionPoint.AFTER_CANONICAL_ROWS_INSERTED && shouldInterrupt) {
                    shouldInterrupt = false
                    throw SimulatedRestoreInterruption()
                }
            }
            val original = BackupFixtures.payload()
            val replacement = original.replacementPayload()

            repository.restoreBackup(context, context.contentResolver, archive(context, "original", original))
            val databaseBefore = repository.snapshot(original.settings)
            val settingsBefore = SettingsStore.load(context)

            shouldInterrupt = true
            val failure = runCatching {
                repository.restoreBackup(
                    context,
                    context.contentResolver,
                    archive(context, "interrupted", replacement),
                )
            }.exceptionOrNull()

            assertNotNull(failure)
            assertTrue(failure is SimulatedRestoreInterruption)
            assertEquals(databaseBefore, repository.snapshot(original.settings))
            assertEquals(settingsBefore, SettingsStore.load(context))
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

    private class SimulatedRestoreInterruption : RuntimeException("Simulated mid-restore interruption")
}
