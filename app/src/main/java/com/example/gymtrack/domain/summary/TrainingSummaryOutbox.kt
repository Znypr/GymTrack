package com.example.gymtrack.domain.summary

const val TRAINING_SUMMARY_OUTBOX_STATUS_PENDING = "PENDING"
const val TRAINING_SUMMARY_OUTBOX_STATUS_DELIVERED = "DELIVERED"
const val TRAINING_SUMMARY_OUTBOX_STATUS_FAILED = "FAILED"

fun trainingSummaryOutboxKey(
    workoutId: String,
    schemaVersion: Int = TRAINING_SUMMARY_SCHEMA_VERSION,
): String {
    require(workoutId.isNotBlank()) { "Summary workout ID must not be blank" }
    require(schemaVersion > 0) { "Summary schema version must be positive" }
    return "$schemaVersion:$workoutId"
}

data class TrainingSummaryOutboxEntry(
    val key: String,
    val workoutId: String,
    val schemaVersion: Int,
    val payloadJson: String,
    val status: String = TRAINING_SUMMARY_OUTBOX_STATUS_PENDING,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val sourceUpdatedAt: String,
) {
    init {
        require(key == trainingSummaryOutboxKey(workoutId, schemaVersion)) {
            "Summary outbox key must match workout ID and schema version"
        }
        require(payloadJson.isNotBlank()) { "Summary payload must not be blank" }
        require(status in SUPPORTED_STATUSES) { "Unsupported summary outbox status: $status" }
        require(attemptCount >= 0) { "Summary outbox attempt count must not be negative" }
        require(createdAtEpochMillis >= 0) { "Summary outbox creation time must not be negative" }
        require(updatedAtEpochMillis >= createdAtEpochMillis) {
            "Summary outbox update time must not be before creation time"
        }
        require(sourceUpdatedAt.isNotBlank()) { "Summary source update time must not be blank" }
    }

    companion object {
        private val SUPPORTED_STATUSES = setOf(
            TRAINING_SUMMARY_OUTBOX_STATUS_PENDING,
            TRAINING_SUMMARY_OUTBOX_STATUS_DELIVERED,
            TRAINING_SUMMARY_OUTBOX_STATUS_FAILED,
        )

        fun pending(
            summary: TrainingSummary,
            payloadJson: String,
            nowEpochMillis: Long,
        ): TrainingSummaryOutboxEntry = TrainingSummaryOutboxEntry(
            key = trainingSummaryOutboxKey(summary.workoutId, summary.schemaVersion),
            workoutId = summary.workoutId,
            schemaVersion = summary.schemaVersion,
            payloadJson = payloadJson,
            status = TRAINING_SUMMARY_OUTBOX_STATUS_PENDING,
            attemptCount = 0,
            lastError = null,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
            sourceUpdatedAt = summary.sourceUpdatedAt,
        )
    }
}

interface TrainingSummaryOutboxRepository {
    suspend fun upsertPending(summary: TrainingSummary, nowEpochMillis: Long)

    suspend fun get(
        workoutId: String,
        schemaVersion: Int = TRAINING_SUMMARY_SCHEMA_VERSION,
    ): TrainingSummaryOutboxEntry?

    suspend fun pending(): List<TrainingSummaryOutboxEntry>
}
