package com.example.gymtrack.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- ENTITIES ---
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val timestamp: Long,
    val title: String,
    val text: String,
    val categoryName: String?,
    val categoryColor: Long?,
    val learnings: String?,
)

data class ExerciseWithCount(
    val exerciseId: Long,
    val name: String,
    val setTotalCount: Int
)

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val exerciseId: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val muscleGroup: String?,
    val aliases: String
)

@Entity(
    tableName = "sets",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["exerciseId"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["exerciseId"]), Index(value = ["workoutId"])]
)
data class SetEntity(
    @PrimaryKey(autoGenerate = true) val setId: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val weight: Float,
    val reps: Int,
    val isUnilateral: Boolean,
    val modifier: String? = null,
    val brand: String? = null,

    // NEW COLUMNS
    val relativeTime: String? = null, // e.g. "0'05" (Time since last set)
    val absoluteTime: String? = null  // e.g. "77'45" (Time since workout start)
)


// DTO for Graphing
data class GraphPoint(val originTimestamp: Long, val avgVal: Float)

// --- DAOS ---

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY timestamp ASC")
    fun getAll(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)
}

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises")
    fun getAllExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): ExerciseEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(exercise: ExerciseEntity): Long

    @Query("SELECT * FROM exercises WHERE aliases LIKE '%' || :query || '%'")
    suspend fun findByAlias(query: String): List<ExerciseEntity>

    @Query("""
        SELECT 
            T1.exerciseId, 
            T1.name, 
            COUNT(T2.exerciseId) AS setTotalCount
        FROM exercises AS T1
        LEFT JOIN sets AS T2 ON T1.exerciseId = T2.exerciseId
        GROUP BY T1.exerciseId, T1.name
        ORDER BY setTotalCount DESC, T1.name ASC
    """)
    fun getExercisesSortedByCount(): Flow<List<ExerciseWithCount>>
}

@Dao
interface SetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(sets: List<SetEntity>)

    @Query("DELETE FROM sets WHERE workoutId = :workoutId")
    suspend fun deleteSetsForWorkout(workoutId: Long)

    @Query("SELECT COUNT(*) FROM sets")
    suspend fun getCount(): Int

    @Query("""
        SELECT 
            workoutId AS originTimestamp, 
            CAST(SUM(weight * reps) AS REAL) / SUM(reps) AS avgVal 
        FROM sets 
        WHERE exerciseId = :exerciseId 
        GROUP BY workoutId 
        ORDER BY workoutId ASC
    """)
    fun getAverageWeightHistory(exerciseId: Long): Flow<List<GraphPoint>>
}
// --- DATABASE ---

@Database(entities = [NoteEntity::class, ExerciseEntity::class, SetEntity::class], version = 8)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun setDao(): SetDao

    companion object {
        @Volatile
        private var INSTANCE: NoteDatabase? = null
        fun getDatabase(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, NoteDatabase::class.java, "note_database")
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}