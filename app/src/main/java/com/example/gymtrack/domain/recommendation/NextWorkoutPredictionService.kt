package com.example.gymtrack.domain.recommendation

import com.example.gymtrack.domain.model.LegacyMigrationStatus
import com.example.gymtrack.domain.model.WorkoutDetails
import com.example.gymtrack.domain.model.WorkoutStatus
import kotlin.math.roundToInt

class NextWorkoutPredictionService {
    fun predictNextWorkout(
        workouts: List<WorkoutDetails>,
        nowEpochMillis: Long,
    ): NextWorkoutSuggestion? {
        val entries = workouts
            .asSequence()
            .mapNotNull(::toHistoryEntry)
            .sortedBy(WorkoutHistoryEntry::startedAtEpochMillis)
            .toList()

        if (entries.isEmpty()) return null

        val labels = entries.map(WorkoutHistoryEntry::label)
        val distinctLabels = labels.distinct()
        val recentLabels = labels.takeLast(MAX_RECENT_LABELS)

        if (distinctLabels.size == 1) {
            val label = distinctLabels.single()
            return NextWorkoutSuggestion(
                workoutLabel = label,
                confidence = if (entries.size >= SINGLE_LABEL_MEDIUM_SAMPLE_SIZE) {
                    SuggestionConfidence.MEDIUM
                } else {
                    SuggestionConfidence.LOW
                },
                reason = "Only $label appears in saved workout history.",
                evidence = NextWorkoutEvidence(
                    completedWorkoutCount = entries.size,
                    recentLabels = recentLabels,
                    basis = PredictionBasis.SINGLE_OBSERVED_WORKOUT,
                    daysSinceSuggested = daysSince(entries.last().startedAtEpochMillis, nowEpochMillis),
                ),
            )
        }

        val lastLabel = entries.last().label
        val transitions = entries.zipWithNext()
            .filter { (current, _) -> current.label == lastLabel }
            .map { (_, next) -> next.label }

        if (transitions.isNotEmpty()) {
            val rankedTransitions = transitions
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, Int>> { it.value }
                        .thenBy { labels.lastIndexOf(it.key) * -1 },
                )
            val best = rankedTransitions.first()
            val supportPercent = (best.value.toDouble() / transitions.size.toDouble() * 100).roundToInt()

            return NextWorkoutSuggestion(
                workoutLabel = best.key,
                confidence = transitionConfidence(
                    bestCount = best.value,
                    transitionCount = transitions.size,
                    completedWorkoutCount = entries.size,
                ),
                reason = "Saved history most often follows $lastLabel with ${best.key} (${best.value}/${transitions.size} transitions, $supportPercent%).",
                evidence = NextWorkoutEvidence(
                    completedWorkoutCount = entries.size,
                    recentLabels = recentLabels,
                    basis = PredictionBasis.HISTORICAL_TRANSITION,
                    previousWorkoutLabel = lastLabel,
                    matchingTransitionCount = best.value,
                    totalTransitionCount = transitions.size,
                    daysSinceSuggested = entries
                        .lastOrNull { it.label == best.key }
                        ?.let { daysSince(it.startedAtEpochMillis, nowEpochMillis) },
                ),
            )
        }

        val recurringLabels = labels
            .groupingBy { it }
            .eachCount()
            .filterValues { it >= RECURRING_LABEL_MIN_COUNT }
            .keys

        val leastRecentlyTrained = entries
            .filter { it.label in recurringLabels && it.label != lastLabel }
            .groupBy(WorkoutHistoryEntry::label)
            .mapValues { (_, values) -> values.maxOf(WorkoutHistoryEntry::startedAtEpochMillis) }
            .minByOrNull { (_, lastSeen) -> lastSeen }

        if (leastRecentlyTrained != null) {
            return NextWorkoutSuggestion(
                workoutLabel = leastRecentlyTrained.key,
                confidence = SuggestionConfidence.LOW,
                reason = "No repeated transition from $lastLabel yet; suggesting the least recently trained recurring workout.",
                evidence = NextWorkoutEvidence(
                    completedWorkoutCount = entries.size,
                    recentLabels = recentLabels,
                    basis = PredictionBasis.LEAST_RECENT_RECURRING_WORKOUT,
                    previousWorkoutLabel = lastLabel,
                    daysSinceSuggested = daysSince(leastRecentlyTrained.value, nowEpochMillis),
                ),
            )
        }

        return null
    }

    private fun toHistoryEntry(details: WorkoutDetails): WorkoutHistoryEntry? {
        val workout = details.record.workout
        if (!workout.isPredictionHistoryEligible()) return null
        val label = details.category?.name
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: workout.title.trim().takeIf { it.isNotBlank() }
            ?: return null

        return WorkoutHistoryEntry(
            workoutId = workout.id,
            label = label,
            startedAtEpochMillis = workout.startedAtEpochMillis,
        )
    }

    private fun com.example.gymtrack.domain.model.Workout.isPredictionHistoryEligible(): Boolean {
        if (status == WorkoutStatus.COMPLETED) return true
        return legacyCompatibility?.migrationStatus in legacyPredictionStatuses
    }

    private fun transitionConfidence(
        bestCount: Int,
        transitionCount: Int,
        completedWorkoutCount: Int,
    ): SuggestionConfidence {
        val support = bestCount.toDouble() / transitionCount.toDouble()
        return when {
            completedWorkoutCount >= HIGH_CONFIDENCE_SAMPLE_SIZE && bestCount >= 2 && support >= HIGH_CONFIDENCE_SUPPORT -> SuggestionConfidence.HIGH
            support >= MEDIUM_CONFIDENCE_SUPPORT -> SuggestionConfidence.MEDIUM
            else -> SuggestionConfidence.LOW
        }
    }

    private fun daysSince(startedAtEpochMillis: Long, nowEpochMillis: Long): Long? {
        if (nowEpochMillis < startedAtEpochMillis) return null
        return (nowEpochMillis - startedAtEpochMillis) / MILLIS_PER_DAY
    }

    private data class WorkoutHistoryEntry(
        val workoutId: String,
        val label: String,
        val startedAtEpochMillis: Long,
    )

    private companion object {
        val legacyPredictionStatuses = setOf(
            LegacyMigrationStatus.MIGRATED,
            LegacyMigrationStatus.NEEDS_REVIEW,
        )
        const val MAX_RECENT_LABELS = 8
        const val SINGLE_LABEL_MEDIUM_SAMPLE_SIZE = 3
        const val RECURRING_LABEL_MIN_COUNT = 2
        const val HIGH_CONFIDENCE_SAMPLE_SIZE = 6
        const val HIGH_CONFIDENCE_SUPPORT = 0.67
        const val MEDIUM_CONFIDENCE_SUPPORT = 0.5
        const val MILLIS_PER_DAY = 86_400_000L
    }
}

data class NextWorkoutSuggestion(
    val workoutLabel: String,
    val confidence: SuggestionConfidence,
    val reason: String,
    val evidence: NextWorkoutEvidence,
)

data class NextWorkoutEvidence(
    val completedWorkoutCount: Int,
    val recentLabels: List<String>,
    val basis: PredictionBasis,
    val previousWorkoutLabel: String? = null,
    val matchingTransitionCount: Int? = null,
    val totalTransitionCount: Int? = null,
    val daysSinceSuggested: Long? = null,
)

enum class SuggestionConfidence {
    LOW,
    MEDIUM,
    HIGH,
}

enum class PredictionBasis {
    HISTORICAL_TRANSITION,
    SINGLE_OBSERVED_WORKOUT,
    LEAST_RECENT_RECURRING_WORKOUT,
}
