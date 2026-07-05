package com.example.gymtrack.domain.summary

import com.example.gymtrack.domain.model.WorkoutStatus

const val TRAINING_SUMMARY_SCHEMA_VERSION = 1

data class TrainingSummary(
    val schemaVersion: Int = TRAINING_SUMMARY_SCHEMA_VERSION,
    val workoutId: String,
    val date: String,
    val startedAt: String,
    val endedAt: String?,
    val focus: String?,
    val status: WorkoutStatus,
    val durationMinutes: Int?,
    val exerciseCount: Int,
    val setCount: Int,
    val topLifts: List<String>,
    val performanceSignal: PerformanceSignal = PerformanceSignal.UNKNOWN,
    val energy: Int?,
    val recoveryNote: String?,
    val source: String = "GymTrack",
    val sourceUpdatedAt: String,
) {
    init {
        require(schemaVersion > 0) { "Summary schema version must be positive" }
        require(workoutId.isNotBlank()) { "Summary workout ID must not be blank" }
        require(date.isNotBlank()) { "Summary date must not be blank" }
        require(startedAt.isNotBlank()) { "Summary start time must not be blank" }
        require(durationMinutes == null || durationMinutes >= 0) {
            "Summary duration must not be negative"
        }
        require(exerciseCount >= 0) { "Summary exercise count must not be negative" }
        require(setCount >= 0) { "Summary set count must not be negative" }
        require(energy == null || energy in 1..10) { "Energy must be between 1 and 10" }
        require(source.isNotBlank()) { "Summary source must not be blank" }
        require(sourceUpdatedAt.isNotBlank()) { "Summary update time must not be blank" }
    }
}

enum class PerformanceSignal {
    UP,
    STABLE,
    DOWN,
    MIXED,
    UNKNOWN,
}

data class TrainingSummaryAnnotations(
    val energy: Int? = null,
    val recoveryNote: String? = null,
) {
    init {
        require(energy == null || energy in 1..10) { "Energy must be between 1 and 10" }
    }
}
