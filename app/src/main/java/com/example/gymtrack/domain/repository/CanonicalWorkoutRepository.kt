package com.example.gymtrack.domain.repository

import com.example.gymtrack.domain.model.WorkoutDetails

interface CanonicalWorkoutRepository {
    suspend fun getById(workoutId: String): WorkoutDetails?

    suspend fun getByLegacyTimestamp(legacyTimestamp: Long): WorkoutDetails?

    suspend fun save(details: WorkoutDetails)
}

enum class VerificationStatus {
    MATCH,
    MISMATCH,
    NOT_APPLICABLE,
    MISSING_LEGACY,
    INVALID_CANONICAL,
}

data class VerificationMismatch(
    val path: String,
    val expected: String?,
    val actual: String?,
)

data class WorkoutVerificationReport(
    val workoutId: String,
    val legacyTimestamp: Long?,
    val status: VerificationStatus,
    val mismatches: List<VerificationMismatch> = emptyList(),
)

interface CanonicalWorkoutVerifier {
    suspend fun verify(workoutId: String): WorkoutVerificationReport
}
