package com.example.gymtrack.data

import android.content.Context
import androidx.room.*

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val timestamp: Long,
    val title: String,
    val text: String,
    val categoryName: String?,
    val categoryColor: Long?,
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY timestamp ASC")
    suspend fun getAll(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)
}

@Database(entities = [NoteEntity::class], version = 3)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: NoteDatabase? = null

        fun getDatabase(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "note_database",
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
