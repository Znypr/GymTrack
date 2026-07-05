package com.example.gymtrack.core.data.summary

import com.example.gymtrack.core.data.NoteDatabase
import com.example.gymtrack.core.data.TrainingSummaryOutboxEntity
import com.example.gymtrack.core.data.transition.CanonicalImportRunner
import com.example.gymtrack.domain.repository.CanonicalWorkoutRepository
import com.example.gymtrack.domain.summary.TRAINING_SUMMARY_SCHEMA_VERSION
import com.example.gymtrack.domain.summary.TrainingSummaryAnnotations
import com.example.gymtrack.domain.summary.TrainingSummaryBuilder
import com.example.gymtrack.domain.summary.TrainingSummaryJson
import java.time.ZoneId

internal fun interface WorkoutCompletionHandler {
    suspend fun complete(
        legacyTimestamp: Long,
        isNewWorkout: Boolean,
    )
}

internal fun interface TrainingSummarySnapshotWriter {
    suspend fun write(item: TrainingSummaryOutboxEntity)
}

internal class RoomTrainingSummarySnapshotWriter(
    private val database: NoteDatabase,
) : TrainingSummarySnapshotWriter {
    override suspend fun write(item: TrainingSummaryOutboxEntity) {
        database.trainingSummaryOutboxDao().upsert(item)
    }
}

internal class TrainingSummaryCompletionCoordinator(
    private val database: NoteDatabase,
    private val importer: CanonicalImportRunner,
    private val repository: CanonicalWorkoutRepository,
    private val snapshotWriter: TrainingSummarySnapshotWriter =
        RoomTrainingSummarySnapshotWriter(database),
    private val builder: TrainingSummaryBuilder = TrainingSummaryBuilder(),
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
) : WorkoutCompletionHandler {
    override suspend fun complete(
        legacyTimestamp: Long,
        isNewWorkout: Boolean,
    ) {
        importer.run()

        val imported = database.canonicalWorkoutDao()
            .getByLegacyTimestamp(legacyTimestamp)
            ?: error("Canonical workout missing after import: $legacyTimestamp")
        val completedAt = currentTimeMillis()
        val inferredEnd = if (isNewWorkout && imported.endedAt == null) completedAt else null
        val updated = database.canonicalWorkoutDao().markCompleted(
            workoutId = imported.id,
            endedAt = inferredEnd,
            updatedAt = completedAt,
        )
        check(updated == 1) { "Canonical workout completion update failed: ${imported.id}" }

        val details = repository.getById(imported.id)
            ?: error("Canonical workout missing after completion: ${imported.id}")
        val summary = builder.build(
            details = details,
            zoneId = zoneId(),
            annotations = TrainingSummaryAnnotations(),
        )
        snapshotWriter.write(
            TrainingSummaryOutboxEntity(
                workoutId = summary.workoutId,
                schemaVersion = TRAINING_SUMMARY_SCHEMA_VERSION,
                payloadJson = TrainingSummaryJson.encode(summary),
                sourceUpdatedAt = details.record.workout.updatedAtEpochMillis,
                enqueuedAt = completedAt,
                attemptCount = 0,
                lastError = null,
            ),
        )
    }
}

internal class TrainingSummaryCompletionRestorer(
    private val database: NoteDatabase,
) {
    suspend fun restore() {
        database.trainingSummaryOutboxDao().getAll().forEach { snapshot ->
            database.canonicalWorkoutDao().markCompleted(
                workoutId = snapshot.workoutId,
                endedAt = null,
                updatedAt = snapshot.sourceUpdatedAt,
            )
        }
    }
}
