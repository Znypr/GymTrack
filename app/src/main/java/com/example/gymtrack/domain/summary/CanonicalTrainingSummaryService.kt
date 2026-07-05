package com.example.gymtrack.domain.summary

import com.example.gymtrack.domain.repository.CanonicalWorkoutRepository
import java.time.ZoneId

class CanonicalTrainingSummaryService(
    private val repository: CanonicalWorkoutRepository,
    private val builder: TrainingSummaryBuilder = TrainingSummaryBuilder(),
) {
    suspend fun getByWorkoutId(
        workoutId: String,
        zoneId: ZoneId,
        annotations: TrainingSummaryAnnotations = TrainingSummaryAnnotations(),
    ): TrainingSummary? = repository.getById(workoutId)?.let { details ->
        builder.build(details, zoneId, annotations)
    }

    suspend fun getJsonByWorkoutId(
        workoutId: String,
        zoneId: ZoneId,
        annotations: TrainingSummaryAnnotations = TrainingSummaryAnnotations(),
    ): String? = getByWorkoutId(workoutId, zoneId, annotations)
        ?.let(TrainingSummaryJson::encode)
}
