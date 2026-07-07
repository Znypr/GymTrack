package com.example.gymtrack.core.data

import com.example.gymtrack.domain.summary.TrainingSummaryOutboxEntry

internal fun TrainingSummaryOutboxEntry.toEntity(): TrainingSummaryOutboxEntity = TrainingSummaryOutboxEntity(
    summaryKey = key,
    workoutId = workoutId,
    schemaVersion = schemaVersion,
    payloadJson = payloadJson,
    status = status,
    attemptCount = attemptCount,
    lastError = lastError,
    createdAt = createdAtEpochMillis,
    updatedAt = updatedAtEpochMillis,
    sourceUpdatedAt = sourceUpdatedAt,
)

internal fun TrainingSummaryOutboxEntity.toDomain(): TrainingSummaryOutboxEntry = TrainingSummaryOutboxEntry(
    key = summaryKey,
    workoutId = workoutId,
    schemaVersion = schemaVersion,
    payloadJson = payloadJson,
    status = status,
    attemptCount = attemptCount,
    lastError = lastError,
    createdAtEpochMillis = createdAt,
    updatedAtEpochMillis = updatedAt,
    sourceUpdatedAt = sourceUpdatedAt,
)
