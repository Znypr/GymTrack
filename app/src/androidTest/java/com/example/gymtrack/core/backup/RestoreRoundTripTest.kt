package com.example.gymtrack.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.gymtrack.core.data.NoteDatabase
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.data.SettingsStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestoreRoundTripTest {
    private lateinit var context: Context
    private lateinit var database: NoteDatabase
    private lateinit var repository: BackupRepository
    private lateinit var previousSettings: Settings
    private val files = mutableListOf<File>()

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        previousSettings = SettingsStore.load(context)
        database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BackupRepository(database)
    }

    @After
    fun tearDown() = runBlocking {
        database.close()
        SettingsStore.save(context, previousSettings)
        files.forEach(File::delete)
    }

    @Test
    fun populatedDatabaseCanBeReplacedAndRestoredRowForRow() = runBlocking {
        val original = BackupFixtures.payload()
        val replacement = original.copy(
            settings = original.settings.copy(darkMode = !original.settings.darkMode),
            legacyNotes = original.legacyNotes.map { it.copy(title = "Replacement") },
            canonicalWorkouts = original.canonicalWorkouts.map { it.copy(title = "Replacement") },
            canonicalWorkoutSets = original.canonicalWorkoutSets.map { it.copy(weight = 120.0) },
        )
        val originalUri = archive("original", original)

        repository.restoreBackup(context, context.contentResolver, originalUri)
        val captured = repository.snapshot(SettingsStore.load(context))
        assertEquals(original, captured)

        repository.restoreBackup(
            context,
            context.contentResolver,
            archive("replacement", replacement),
        )
        assertNotEquals(captured, repository.snapshot(SettingsStore.load(context)))

        repository.restoreBackup(context, context.contentResolver, originalUri)
        assertEquals(captured, repository.snapshot(SettingsStore.load(context)))
    }

    @Test
    fun failedReplacementLeavesPreviousDatabaseAndSettingsUnchanged() = runBlocking {
        val original = BackupFixtures.payload()
        repository.restoreBackup(
            context,
            context.contentResolver,
            archive("source", original),
        )
        val before = repository.snapshot(SettingsStore.load(context))
        val template = original.canonicalExercises.single()
        val cyclic = original.copy(
            settings = original.settings.copy(darkMode = !original.settings.darkMode),
            canonicalExercises = listOf(
                template.copy(
                    id = "cycle-a",
                    canonicalName = "Cycle A",
                    normalizedName = "cycle a",
                    parentExerciseId = "cycle-b",
                ),
                template.copy(
                    id = "cycle-b",
                    canonicalName = "Cycle B",
                    normalizedName = "cycle b",
                    parentExerciseId = "cycle-a",
                ),
            ),
            canonicalExerciseAliases = emptyList(),
            canonicalWorkouts = emptyList(),
            canonicalWorkoutExercises = emptyList(),
            canonicalWorkoutSets = emptyList(),
        )

        val failure = runCatching {
            repository.restoreBackup(
                context,
                context.contentResolver,
                archive("cyclic", cyclic),
            )
        }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(failure is InvalidBackupException)
        assertEquals(before, repository.snapshot(SettingsStore.load(context)))
    }

    private fun archive(name: String, payload: GymTrackBackupPayload): Uri {
        val file = File(context.cacheDir, "backup-test-$name-${System.nanoTime()}.gymtrack-backup")
        file.outputStream().use { output ->
            BackupArchive.write(output, payload, "test", 9, 1_000L)
        }
        files += file
        return Uri.fromFile(file)
    }
}
