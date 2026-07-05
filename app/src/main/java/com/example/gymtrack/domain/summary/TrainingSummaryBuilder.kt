package com.example.gymtrack.domain.summary

import com.example.gymtrack.domain.model.WeightUnit
import com.example.gymtrack.domain.model.WorkoutDetails
import com.example.gymtrack.domain.model.WorkoutExercise
import com.example.gymtrack.domain.model.WorkoutSet
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class TrainingSummaryBuilder(
    private val topLiftLimit: Int = 3,
) {
    init {
        require(topLiftLimit >= 0) { "Top-lift limit must not be negative" }
    }

    fun build(
        details: WorkoutDetails,
        zoneId: ZoneId,
        annotations: TrainingSummaryAnnotations = TrainingSummaryAnnotations(),
    ): TrainingSummary {
        val workout = details.record.workout
        val started = Instant.ofEpochMilli(workout.startedAtEpochMillis).atZone(zoneId)
        val ended = workout.endedAtEpochMillis?.let { endMillis ->
            Instant.ofEpochMilli(endMillis).atZone(zoneId)
        }
        val durationMinutes = workout.endedAtEpochMillis?.let { endMillis ->
            Duration.ofMillis(endMillis - workout.startedAtEpochMillis).toMinutes().toInt()
        }

        return TrainingSummary(
            workoutId = workout.id,
            date = started.toLocalDate().toString(),
            startedAt = started.toOffsetDateTime().toString(),
            endedAt = ended?.toOffsetDateTime()?.toString(),
            focus = details.category?.name
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: workout.title.trim().takeIf(String::isNotEmpty),
            status = workout.status,
            durationMinutes = durationMinutes,
            exerciseCount = details.record.exercises.size,
            setCount = details.record.sets.size,
            topLifts = buildTopLifts(details),
            performanceSignal = PerformanceSignal.UNKNOWN,
            energy = annotations.energy,
            recoveryNote = annotations.recoveryNote?.trim()?.takeIf(String::isNotEmpty),
            sourceUpdatedAt = Instant.ofEpochMilli(workout.updatedAtEpochMillis)
                .atZone(zoneId)
                .toOffsetDateTime()
                .toString(),
        )
    }

    private fun buildTopLifts(details: WorkoutDetails): List<String> {
        if (topLiftLimit == 0) return emptyList()

        val setsByOccurrence = details.record.sets.groupBy { it.workoutExerciseId }
        return details.record.exercises
            .sortedWith(compareBy(WorkoutExercise::position, WorkoutExercise::id))
            .mapNotNull { occurrence ->
                val exercise = details.exerciseDefinitions[occurrence.exerciseId]
                    ?: return@mapNotNull null
                val bestSet = selectBestSet(setsByOccurrence[occurrence.id].orEmpty())
                    ?: return@mapNotNull null
                formatTopLift(exercise.canonicalName, occurrence, bestSet)
            }
            .take(topLiftLimit)
    }

    private fun selectBestSet(sets: List<WorkoutSet>): WorkoutSet? {
        if (sets.isEmpty()) return null

        val weighted = sets.filter { it.weight != null }
        if (weighted.isNotEmpty()) {
            return weighted.maxWithOrNull(
                compareBy<WorkoutSet>({ it.weight ?: Double.NEGATIVE_INFINITY })
                    .thenBy { it.repetitions ?: Int.MIN_VALUE }
                    .thenByDescending { it.position },
            )
        }

        val repetitions = sets.filter { it.repetitions != null }
        if (repetitions.isNotEmpty()) {
            return repetitions.maxWithOrNull(
                compareBy<WorkoutSet> { it.repetitions ?: Int.MIN_VALUE }
                    .thenByDescending { it.position },
            )
        }

        val durations = sets.filter { it.durationSeconds != null }
        if (durations.isNotEmpty()) {
            return durations.maxWithOrNull(
                compareBy<WorkoutSet> { it.durationSeconds ?: Int.MIN_VALUE }
                    .thenByDescending { it.position },
            )
        }

        return sets.filter { it.distanceMeters != null }.maxWithOrNull(
            compareBy<WorkoutSet> { it.distanceMeters ?: Double.NEGATIVE_INFINITY }
                .thenByDescending { it.position },
        )
    }

    private fun formatTopLift(
        exerciseName: String,
        occurrence: WorkoutExercise,
        set: WorkoutSet,
    ): String {
        val label = buildString {
            append(exerciseName.trim())
            occurrence.modifier?.trim()?.takeIf(String::isNotEmpty)?.let { modifier ->
                append(" (")
                append(modifier)
                append(')')
            }
        }

        return when {
            set.weight != null -> buildString {
                append(label)
                append(' ')
                append(formatNumber(set.weight))
                append(
                    when (set.weightUnit) {
                        WeightUnit.KILOGRAM -> "kg"
                        WeightUnit.POUND -> "lb"
                        WeightUnit.UNKNOWN, null -> " [unit unknown]"
                    },
                )
                set.repetitions?.let { repetitions ->
                    append(" x ")
                    append(repetitions)
                }
            }
            set.repetitions != null -> "$label x ${set.repetitions}"
            set.durationSeconds != null -> "$label ${set.durationSeconds}s"
            set.distanceMeters != null -> "$label ${formatNumber(set.distanceMeters)}m"
            else -> label
        }
    }

    private fun formatNumber(value: Double): String = BigDecimal.valueOf(value)
        .stripTrailingZeros()
        .toPlainString()
}
