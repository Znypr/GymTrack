package com.example.gymtrack.core.data

import com.example.gymtrack.domain.summary.TrainingSummary
import com.example.gymtrack.domain.summary.TrainingSummaryJson
import com.example.gymtrack.domain.summary.TrainingSummaryOutboxEntry
import com.example.gymtrack.domain.summary.TrainingSummaryOutboxRepository
import com.example.gymtrack.domain.summary.trainingSummaryOutboxKey

class RoomTrainingSummaryOutboxRepository(
    private val dao: TrainingSummaryOutboxDao,
) : TrainingSummaryOutboxRepository {
    override suspend fun upsertPending(summary: TrainingSummary, nowEpochMillis: Long) {
        val entry = TrainingSummaryOutboxEntry.pending(
            summary = summary,
            payloadJson = TrainingSummaryJson.encode(summary),
            nowEpochMillis = nowEpochMillis,
        )
        dao.upsert(entry.toEntity())
    }

    override suspend fun get(
        workoutId: String,
        schemaVersion: Int,
    ): TrainingSummaryOutboxEntry? = dao.get(
        trainingSummaryOutboxKey(workoutId, schemaVersion),
    )?.toDomain()

    override suspend fun pending(): List<TrainingSummaryOutboxEntry> = dao.getPending().map { entity ->
        entity.toDomain()
    }
}
