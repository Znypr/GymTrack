package com.example.gymtrack.domain.notebookimport

import com.example.gymtrack.domain.model.ExerciseMode
import com.example.gymtrack.domain.model.WeightUnit

/**
 * Review-first draft model for importing handwritten notebook workouts.
 *
 * This model deliberately stays separate from canonical workout storage. It can hold uncertain,
 * low-confidence, and rejected recognition output, but canonical history may only be written after
 * the user confirms the reconstructed workout structure.
 */
private const val LOW_CONFIDENCE_REVIEW_THRESHOLD = 0.85

data class RecognitionConfidence(
    val value: Double,
) {
    init {
        require(value in 0.0..1.0) { "Recognition confidence must be between 0 and 1" }
    }

    val isLowConfidence: Boolean
        get() = value < LOW_CONFIDENCE_REVIEW_THRESHOLD
}

enum class ReviewState {
    NEEDS_REVIEW,
    CONFIRMED,
    REJECTED,
}

enum class ProcessingLocation {
    ON_DEVICE,
    CLOUD_OPT_IN,
    HYBRID_OPT_IN,
}

enum class SourceImageRetentionPolicy {
    DELETE_AFTER_CONFIRMATION,
    KEEP_UNTIL_USER_DELETES,
    DO_NOT_STORE_SOURCE_IMAGE,
}

data class NotebookImportConsent(
    val allowExternalProcessing: Boolean = false,
    val sourceImageRetentionPolicy: SourceImageRetentionPolicy =
        SourceImageRetentionPolicy.DELETE_AFTER_CONFIRMATION,
)

data class NotebookLineProvenance(
    val pageId: String,
    val lineNumber: Int? = null,
    val sourceText: String? = null,
) {
    init {
        require(pageId.isNotBlank()) { "Provenance page id must not be blank" }
        require(lineNumber == null || lineNumber > 0) { "Line numbers are one-based" }
    }
}

data class RecognizedField<T>(
    val value: T? = null,
    val confidence: RecognitionConfidence,
    val reviewState: ReviewState = ReviewState.NEEDS_REVIEW,
    val provenance: NotebookLineProvenance,
) {
    init {
        require(reviewState != ReviewState.CONFIRMED || value != null) {
            "A confirmed recognized field must contain an explicit value"
        }
    }

    val needsReview: Boolean
        get() = reviewState == ReviewState.NEEDS_REVIEW

    val isLowConfidence: Boolean
        get() = confidence.isLowConfidence
}

data class NotebookPageDraft(
    val id: String,
    val position: Int,
    val sourceFingerprintSha256: String,
    val sourceUri: String? = null,
    val capturedAtEpochMillis: Long? = null,
) {
    init {
        require(id.isNotBlank()) { "Page id must not be blank" }
        require(position >= 0) { "Page position must not be negative" }
        require(sourceFingerprintSha256.isNotBlank()) { "Page fingerprint must not be blank" }
        require(capturedAtEpochMillis == null || capturedAtEpochMillis >= 0) {
            "Capture time must not be negative"
        }
    }
}

enum class ExerciseResolutionKind {
    MATCH_EXISTING,
    CREATE_NEW,
    UNRESOLVED,
}

data class ExerciseResolution(
    val kind: ExerciseResolutionKind = ExerciseResolutionKind.UNRESOLVED,
    val exerciseId: String? = null,
    val canonicalName: String? = null,
    val reviewState: ReviewState = ReviewState.NEEDS_REVIEW,
) {
    init {
        when (kind) {
            ExerciseResolutionKind.MATCH_EXISTING -> {
                require(!exerciseId.isNullOrBlank()) {
                    "Matched exercises require an existing exercise id"
                }
            }
            ExerciseResolutionKind.CREATE_NEW -> {
                require(!canonicalName.isNullOrBlank()) {
                    "New exercise resolutions require a canonical name"
                }
            }
            ExerciseResolutionKind.UNRESOLVED -> {
                require(exerciseId == null && canonicalName == null) {
                    "Unresolved exercise resolutions must not carry canonical identifiers"
                }
            }
        }
        require(reviewState != ReviewState.CONFIRMED || kind != ExerciseResolutionKind.UNRESOLVED) {
            "Unresolved exercise mappings cannot be confirmed"
        }
    }

    val isResolvedForImport: Boolean
        get() = reviewState == ReviewState.CONFIRMED && kind != ExerciseResolutionKind.UNRESOLVED
}

data class NotebookSetDraft(
    val id: String,
    val position: Int,
    val repetitions: RecognizedField<Int>? = null,
    val weight: RecognizedField<Double>? = null,
    val weightUnit: RecognizedField<WeightUnit>? = null,
    val notes: RecognizedField<String>? = null,
    val reviewState: ReviewState = ReviewState.NEEDS_REVIEW,
) {
    init {
        require(id.isNotBlank()) { "Set draft id must not be blank" }
        require(position >= 0) { "Set position must not be negative" }
        require(repetitions != null || weight != null || notes != null) {
            "A recognized notebook set must retain at least one recognized field"
        }
        require(repetitions?.value == null || repetitions.value > 0) {
            "Recognized repetitions must be positive"
        }
        require(weight?.value == null || weight.value >= 0.0) {
            "Recognized weight must not be negative"
        }
        require(notes?.value == null || notes.value.isNotBlank()) {
            "Recognized notes must not be blank"
        }
    }

    val hasUnresolvedFields: Boolean
        get() = reviewState == ReviewState.NEEDS_REVIEW || recognizedFields.any { it.needsReview }

    val isReadyForCanonicalImport: Boolean
        get() = reviewState == ReviewState.CONFIRMED &&
            repetitions?.reviewState == ReviewState.CONFIRMED &&
            weightUnitIsReadyForCanonicalImport

    private val weightUnitIsReadyForCanonicalImport: Boolean
        get() = weight == null ||
            weight.reviewState == ReviewState.CONFIRMED &&
            weightUnit?.reviewState == ReviewState.CONFIRMED &&
            weightUnit.value != WeightUnit.UNKNOWN

    private val recognizedFields: List<RecognizedField<*>>
        get() = listOfNotNull(repetitions, weight, weightUnit, notes)
}

data class NotebookExerciseDraft(
    val id: String,
    val position: Int,
    val recognizedName: RecognizedField<String>,
    val recognizedMode: RecognizedField<ExerciseMode>,
    val exerciseResolution: ExerciseResolution = ExerciseResolution(),
    val sets: List<NotebookSetDraft>,
    val reviewState: ReviewState = ReviewState.NEEDS_REVIEW,
) {
    init {
        require(id.isNotBlank()) { "Exercise draft id must not be blank" }
        require(position >= 0) { "Exercise position must not be negative" }
        require(recognizedName.value == null || recognizedName.value.isNotBlank()) {
            "Recognized exercise name must not be blank"
        }
        require(sets.isNotEmpty()) { "Recognized exercises require at least one proposed set" }
        require(sets.map { it.id }.distinct().size == sets.size) {
            "Set draft ids must be unique within an exercise"
        }
        require(sets.map { it.position }.distinct().size == sets.size) {
            "Set draft positions must be unique within an exercise"
        }
    }

    val hasUnresolvedFields: Boolean
        get() = reviewState == ReviewState.NEEDS_REVIEW ||
            recognizedName.needsReview ||
            recognizedMode.needsReview ||
            !exerciseResolution.isResolvedForImport ||
            sets.any { it.hasUnresolvedFields }

    val isReadyForCanonicalImport: Boolean
        get() = reviewState == ReviewState.CONFIRMED &&
            recognizedName.reviewState == ReviewState.CONFIRMED &&
            recognizedMode.reviewState == ReviewState.CONFIRMED &&
            exerciseResolution.isResolvedForImport &&
            sets.all { it.isReadyForCanonicalImport }
}

data class NotebookWorkoutDraft(
    val id: String,
    val sourcePageIds: Set<String>,
    val startedAtEpochMillis: RecognizedField<Long>,
    val title: RecognizedField<String>? = null,
    val notes: RecognizedField<String>? = null,
    val exercises: List<NotebookExerciseDraft>,
    val reviewState: ReviewState = ReviewState.NEEDS_REVIEW,
) {
    init {
        require(id.isNotBlank()) { "Workout draft id must not be blank" }
        require(sourcePageIds.isNotEmpty()) { "Workout drafts require source page provenance" }
        require(sourcePageIds.none { it.isBlank() }) { "Source page ids must not be blank" }
        require(startedAtEpochMillis.value == null || startedAtEpochMillis.value >= 0) {
            "Recognized workout start time must not be negative"
        }
        require(title?.value == null || title.value.isNotBlank()) {
            "Recognized workout title must not be blank"
        }
        require(notes?.value == null || notes.value.isNotBlank()) {
            "Recognized workout notes must not be blank"
        }
        require(exercises.isNotEmpty()) { "Workout drafts require at least one exercise" }
        require(exercises.map { it.id }.distinct().size == exercises.size) {
            "Exercise draft ids must be unique within a workout"
        }
        require(exercises.map { it.position }.distinct().size == exercises.size) {
            "Exercise draft positions must be unique within a workout"
        }
    }

    val hasUnresolvedFields: Boolean
        get() = reviewState == ReviewState.NEEDS_REVIEW ||
            startedAtEpochMillis.needsReview ||
            title?.needsReview == true ||
            notes?.needsReview == true ||
            exercises.any { it.hasUnresolvedFields }

    val isReadyForCanonicalImport: Boolean
        get() = reviewState == ReviewState.CONFIRMED &&
            startedAtEpochMillis.reviewState == ReviewState.CONFIRMED &&
            exercises.all { it.isReadyForCanonicalImport }
}

data class NotebookImportBatchDraft(
    val id: String,
    val pages: List<NotebookPageDraft>,
    val workouts: List<NotebookWorkoutDraft> = emptyList(),
    val processingLocation: ProcessingLocation = ProcessingLocation.ON_DEVICE,
    val consent: NotebookImportConsent = NotebookImportConsent(),
    val reviewState: ReviewState = ReviewState.NEEDS_REVIEW,
) {
    init {
        require(id.isNotBlank()) { "Notebook import batch id must not be blank" }
        require(pages.isNotEmpty()) { "Notebook import batches require at least one page" }
        require(pages.map { it.id }.distinct().size == pages.size) {
            "Page ids must be unique within an import batch"
        }
        require(pages.map { it.position }.distinct().size == pages.size) {
            "Page positions must be unique within an import batch"
        }
        require(pages.map { it.sourceFingerprintSha256 }.distinct().size == pages.size) {
            "Duplicate page fingerprints must be resolved before import"
        }
        val knownPageIds = pages.map { it.id }.toSet()
        require(workouts.all { workout -> workout.sourcePageIds.all { it in knownPageIds } }) {
            "Workout drafts can only reference pages from their import batch"
        }
        require(processingLocation == ProcessingLocation.ON_DEVICE || consent.allowExternalProcessing) {
            "External notebook processing requires explicit user consent"
        }
    }

    val hasUnresolvedFields: Boolean
        get() = reviewState == ReviewState.NEEDS_REVIEW || workouts.any { it.hasUnresolvedFields }

    val canWriteCanonicalHistory: Boolean
        get() = workouts.isNotEmpty() && workouts.all { it.isReadyForCanonicalImport }
}
