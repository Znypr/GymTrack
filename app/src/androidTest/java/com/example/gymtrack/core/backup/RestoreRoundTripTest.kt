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
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestoreRoundTripTest {
    @Test
    fun populatedDatabaseRestoresEveryBackedUpTable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val oldSettings = SettingsStore.load(context)
        val database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = BackupRepository(database)
            val original = BackupFixtures.payload()
            val replacement = original.replacementPayload()
            val originalUri = archive(context, "original", original)

            repository.restoreBackup(context, context.contentResolver, originalUri)
            val captured = repository.snapshot(original.settings)
            assertEquals(original, captured)

            repository.restoreBackup(
                context,
                context.contentResolver,
                archive(context, "replacement", replacement),
            )
            assertNotEquals(captured, repository.snapshot(replacement.settings))

            repository.restoreBackup(context, context.contentResolver, originalUri)
            assertEquals(captured, repository.snapshot(original.settings))
        } finally {
            database.close()
            SettingsStore.save(context, oldSettings)
        }
    }

    private fun archive(context: Context, name: String, payload: GymTrackBackupPayload): Uri {
        val file = File(context.cacheDir, "backup-test-$name-${System.nanoTime()}.gymtrack-backup")
        file.outputStream().use { BackupArchive.write(it, payload, "test", 9, 1_000L) }
        return Uri.fromFile(file)
    }
}
