package com.example.gymtrack.core.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

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
    val setTotalCount: Int,
)

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val exerciseId: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val muscleGroup: String?,
    val aliases: String,
)

@Entity(
    tableName = "sets",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["exerciseId"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["exerciseId"]), Index(value = ["workoutId"])],
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
    val relativeTime: String? = null,
    val absoluteTime: String? = null,
    val weightUnit: String = WeightUnit.KG.storageValue,
)

@Entity(
    tableName = "training_summary_outbox",
    primaryKeys = ["workout_id", "schema_version"],
    indices = [
        Index(value = ["state"]),
        Index(value = ["updated_at"]),
    ],
)
data class TrainingSummaryOutboxEntity(
    @ColumnInfo(name = "workout_id") val workoutId: String,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    @ColumnInfo(name = "summary_json") val summaryJson: String,
    val state: String,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int,
    @ColumnInfo(name = "last_error") val lastError: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

data class GraphPoint(val originTimestamp: Long, val avgVal: Float)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY timestamp ASC")
    fun getAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY timestamp ASC")
    suspend fun getAllForBackup(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllForRestore(notes: List<NoteEntity>)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("DELETE FROM notes")
    suspend fun deleteAllForRestore()

    @Query("SELECT * FROM notes WHERE timestamp = :id LIMIT 1")
    suspend fun getById(id: Long): NoteEntity?

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun getCount(): Int
}

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises")
    fun getAllExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises ORDER BY exerciseId")
    suspend fun getAllForBackup(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): ExerciseEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(exercise: ExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllForRestore(exercises: List<ExerciseEntity>)

    @Query("DELETE FROM exercises")
    suspend fun deleteAllForRestore()

    @Query("SELECT * FROM exercises WHERE aliases LIKE '%' || :query || '%'")
    suspend fun findByAlias(query: String): List<ExerciseEntity>

    @Query(
        """
        SELECT
            T1.exerciseId,
            T1.name,
            COUNT(T2.exerciseId) AS setTotalCount
        FROM exercises AS T1
        LEFT JOIN sets AS T2 ON T1.exerciseId = T2.exerciseId
        GROUP BY T1.exerciseId, T1.name
        ORDER BY setTotalCount DESC, T1.name ASC
        """,
    )
    fun getExercisesSortedByCount(): Flow<List<ExerciseWithCount>>

    @Query(
        """
        SELECT
            T1.exerciseId,
            T1.name,
            COUNT(T2.exerciseId) AS setTotalCount
        FROM exercises AS T1
        JOIN sets AS T2 ON T1.exerciseId = T2.exerciseId
        WHERE T2.workoutId >= :minTimestamp
        GROUP BY T1.exerciseId, T1.name
        ORDER BY setTotalCount DESC, T1.name ASC
        """,
    )
    fun getExercisesWithCountAfter(minTimestamp: Long): Flow<List<ExerciseWithCount>>
}

@Dao
interface SetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(sets: List<SetEntity>)

    @Query("SELECT * FROM sets ORDER BY setId")
    suspend fun getAllForBackup(): List<SetEntity>

    @Query("DELETE FROM sets")
    suspend fun deleteAllForRestore()

    @Query("DELETE FROM sets WHERE workoutId = :workoutId")
    suspend fun deleteSetsForWorkout(workoutId: Long)

    @Query("DELETE FROM sets WHERE workoutId NOT IN (SELECT timestamp FROM notes)")
    suspend fun deleteOrphanedSets()

    @Query("SELECT COUNT(*) FROM sets")
    suspend fun getCount(): Int

    @Query(
        """
        SELECT
            workoutId AS originTimestamp,
            CAST(SUM(weight * reps) AS REAL) / SUM(reps) AS avgVal
        FROM sets
        WHERE exerciseId = :exerciseId
        GROUP BY workoutId
        ORDER BY workoutId ASC
        """,
    )
    fun getAverageWeightHistory(exerciseId: Long): Flow<List<GraphPoint>>

    @Transaction
    suspend fun replaceSetsForWorkout(workoutId: Long, sets: List<SetEntity>) {
        deleteSetsForWorkout(workoutId)
        if (sets.isNotEmpty()) {
            insertSets(sets)
        }
    }
}

@Dao
interface TrainingSummaryOutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrainingSummaryOutboxEntity)

    @Query("SELECT * FROM training_summary_outbox WHERE workout_id = :workoutId AND schema_version = :schemaVersion LIMIT 1")
    suspend fun get(workoutId: String, schemaVersion: Int): TrainingSummaryOutboxEntity?

    @Query("SELECT * FROM training_summary_outbox WHERE state = 'PENDING' ORDER BY updated_at ASC, workout_id ASC")
    suspend fun getPending(): List<TrainingSummaryOutboxEntity>

    @Query("SELECT COUNT(*) FROM training_summary_outbox")
    suspend fun getCount(): Int
}

@Database(
    entities = [
        NoteEntity::class,
        ExerciseEntity::class,
        SetEntity::class,
        TrainingSummaryOutboxEntity::class,
        CanonicalCategoryEntity::class,
        CanonicalExerciseEntity::class,
        CanonicalExerciseAliasEntity::class,
        CanonicalWorkoutEntity::class,
        CanonicalWorkoutExerciseEntity::class,
        CanonicalWorkoutSetEntity::class,
    ],
    version = 11,
)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun setDao(): SetDao
    abstract fun trainingSummaryOutboxDao(): TrainingSummaryOutboxDao
    abstract fun canonicalCategoryDao(): CanonicalCategoryDao
    abstract fun canonicalExerciseDao(): CanonicalExerciseDao
    abstract fun canonicalWorkoutDao(): CanonicalWorkoutDao
    abstract fun canonicalWorkoutExerciseDao(): CanonicalWorkoutExerciseDao
    abstract fun canonicalWorkoutSetDao(): CanonicalWorkoutSetDao
    abstract fun backupDao(): BackupDao

    companion object {
        @Volatile
        private var INSTANCE: NoteDatabase? = null

        fun getDatabase(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "note_database",
                )
                    .addMigrations(*ALL_DATABASE_MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}