package com.example.gymtrack.core.data.transition

import com.example.gymtrack.core.data.CanonicalCategoryEntity
import com.example.gymtrack.core.data.CanonicalWorkoutEntity
import com.example.gymtrack.core.data.CanonicalWorkoutExerciseEntity
import com.example.gymtrack.core.data.CanonicalWorkoutSetEntity
import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.data.NoteEntity
import com.example.gymtrack.core.util.WorkoutParser
import com.example.gymtrack.core.util.buildNoteRowMetadata
import com.example.gymtrack.core.util.parseNoteText

internal const val CANONICAL_IMPORT_VERSION = "canonical-import-v1"

internal data class CanonicalWorkoutProjection(
    val workout: CanonicalWorkoutEntity,
    val workoutExercises: List<CanonicalWorkoutExerciseEntity>,
    val workoutSets: List<CanonicalWorkoutSetEntity>,
)

internal class LegacyWorkoutProjector(
    private val parser: WorkoutParser = WorkoutParser(),
) {
    fun project(
        note: NoteEntity,
        category: CanonicalCategoryEntity?,
        exercises: CanonicalExerciseCatalog,
    ): CanonicalWorkoutProjection {
        val parsedNote = parseNoteText(note.text, note.rowMetadata)
        val sourceLines = parsedNote.first.mapIndexed { index, text ->
            SourceLine(
                text = text,
                elapsedText = parsedNote.second.getOrNull(index).orEmpty(),
                flag = parsedNote.third.getOrNull(index) ?: ExerciseFlag.BILATERAL,
            )
        }

        val reviewReasons = mutableListOf<String>()
        val blocks = splitIntoBlocks(sourceLines, reviewReasons)
        if (note.text.isNotBlank() && blocks.isEmpty()) reviewReasons += "no exercise blocks found"

        val workoutKey = CanonicalKeys.workout(note.timestamp)
        val workoutExercises = mutableListOf<CanonicalWorkoutExerciseEntity>()
        val workoutSets = mutableListOf<CanonicalWorkoutSetEntity>()
        val elapsedOffsets = mutableListOf<Int>()

        blocks.forEachIndexed { blockPosition, block ->
            val parsedSets = parser.parseWorkout(block.asParserText(), rowMetadata = block.asParserMetadata())
            val sourceSetLines = block.setLines.filter { it.text.isNotBlank() }
            if (parsedSets.size != sourceSetLines.size) {
                reviewReasons += "exercise ${blockPosition + 1} parsed ${parsedSets.size}/${sourceSetLines.size} sets"
            }

            val parsedNames = parsedSets.map { CanonicalKeys.normalize(it.exerciseIdentity.canonicalName) }
                .filter(String::isNotEmpty)
                .distinct()
            if (parsedNames.size > 1) reviewReasons += "exercise ${blockPosition + 1} has inconsistent set names"

            val fallbackName = cleanHeader(block.header.text)
            val firstSet = parsedSets.firstOrNull()
            val exerciseName = firstSet
                ?.exerciseIdentity
                ?.canonicalName
                ?.takeIf(String::isNotBlank)
                ?: firstSet?.exerciseName?.takeIf(String::isNotBlank)
                ?: fallbackName
            if (exerciseName.isBlank() || exerciseName.equals("Unknown exercise", ignoreCase = true) ||
                exerciseName.equals("Unknown", ignoreCase = true)
            ) {
                reviewReasons += "exercise ${blockPosition + 1} has no reliable name"
            }
            parsedSets.flatMap { it.exerciseIdentity.warnings }.distinct().forEach { warning ->
                reviewReasons += "exercise ${blockPosition + 1}: $warning"
            }

            val aliases = parsedSets
                .flatMap { it.exerciseIdentity.aliases + it.exerciseName + it.exerciseIdentity.rawName }
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinctBy(CanonicalKeys::normalize)
            val canonicalExercise = exercises.resolve(exerciseName, aliases)
            val occurrenceKey = CanonicalKeys.workoutExercise(workoutKey, blockPosition)
            val headerOffset = parseElapsedSeconds(block.header.elapsedText)
            if (headerOffset != null) elapsedOffsets += headerOffset

            val modifiers = parsedSets.mapNotNull { it.modifier }.distinct()
            val brands = parsedSets.mapNotNull { it.brand }.distinct()
            if (modifiers.size > 1) reviewReasons += "exercise ${blockPosition + 1} has inconsistent modifiers"
            if (brands.size > 1) reviewReasons += "exercise ${blockPosition + 1} has inconsistent brands"

            workoutExercises += CanonicalWorkoutExerciseEntity(
                id = occurrenceKey,
                workoutId = workoutKey,
                exerciseId = canonicalExercise.id,
                position = blockPosition,
                mode = block.header.flag.name,
                modifier = modifiers.firstOrNull(),
                equipmentBrand = brands.firstOrNull(),
                startedAtOffsetSeconds = headerOffset,
                startedAt = headerOffset?.let { note.timestamp + it * 1000L },
                legacyRelativeTimeText = parsedSets.firstOrNull()?.relativeTime,
                legacyAbsoluteTimeText = block.header.elapsedText.takeIf(String::isNotBlank) ?: parsedSets.firstOrNull()?.absoluteTime,
            )

            parsedSets.forEachIndexed { setPosition, parsedSet ->
                val source = sourceSetLines.getOrNull(setPosition)
                val performedOffset = source?.elapsedText?.let(::parseElapsedSeconds)
                if (performedOffset != null) elapsedOffsets += performedOffset
                workoutSets += CanonicalWorkoutSetEntity(
                    id = CanonicalKeys.workoutSet(occurrenceKey, setPosition),
                    workoutExerciseId = occurrenceKey,
                    position = setPosition,
                    repetitions = parsedSet.reps.takeIf { it > 0 },
                    weight = parsedSet.weight.toDouble().takeIf { it > 0.0 },
                    weightUnit = source?.text?.let(::explicitWeightUnit),
                    durationSeconds = null,
                    distanceMeters = null,
                    performedAtOffsetSeconds = performedOffset,
                    rpe = null,
                    rir = null,
                )
            }
        }

        val distinctReasons = reviewReasons.distinct()
        val migrationStatus = if (distinctReasons.isEmpty()) "MIGRATED" else "NEEDS_REVIEW"
        val migrationMessage = if (distinctReasons.isEmpty()) {
            CANONICAL_IMPORT_VERSION
        } else {
            "$CANONICAL_IMPORT_VERSION: ${distinctReasons.take(4).joinToString("; ")}".take(240)
        }

        return CanonicalWorkoutProjection(
            workout = CanonicalWorkoutEntity(
                id = workoutKey,
                legacyTimestamp = note.timestamp,
                startedAt = note.timestamp,
                endedAt = elapsedOffsets.maxOrNull()?.let { note.timestamp + it * 1000L },
                categoryId = category?.id,
                title = note.title,
                learnings = note.learnings.orEmpty(),
                status = "PARTIAL",
                rawDraftText = note.text,
                legacyMigrationStatus = migrationStatus,
                legacyMigrationMessage = migrationMessage,
                createdAt = note.timestamp,
                updatedAt = note.timestamp,
            ),
            workoutExercises = workoutExercises,
            workoutSets = workoutSets,
        )
    }

    private fun splitIntoBlocks(sourceLines: List<SourceLine>, reviewReasons: MutableList<String>): List<ExerciseBlock> {
        val blocks = mutableListOf<ExerciseBlock>()
        var current: MutableExerciseBlock? = null
        sourceLines.forEach { line ->
            when {
                line.text.isBlank() -> Unit
                line.text.firstOrNull()?.isWhitespace() == true -> {
                    if (current == null) reviewReasons += "set row without exercise header" else current?.setLines?.add(line)
                }
                else -> {
                    current?.let { blocks += it.freeze() }
                    current = MutableExerciseBlock(header = line)
                }
            }
        }
        current?.let { blocks += it.freeze() }
        return blocks
    }

    private fun cleanHeader(raw: String): String = raw.trim().replace(Regex("\\s*\\(\\s*\\d+['’]\\d{2}(?:''|\")?\\s*\\)\\s*$"), "").trim()

    private fun parseElapsedSeconds(raw: String): Int? {
        val match = Regex("^\\s*(\\d+)['’](\\d{2})(?:''|\")?\\s*$").matchEntire(raw) ?: return null
        val minutes = match.groupValues[1].toIntOrNull() ?: return null
        val seconds = match.groupValues[2].toIntOrNull() ?: return null
        if (seconds !in 0..59) return null
        return minutes * 60 + seconds
    }

    private fun explicitWeightUnit(raw: String): String? = when {
        Regex("(?i)(?:^|[^a-z])lbs?\\b").containsMatchIn(raw) -> "POUND"
        Regex("(?i)(?:^|[^a-z])kg\\b").containsMatchIn(raw) -> "KILOGRAM"
        else -> null
    }

    private data class SourceLine(val text: String, val elapsedText: String, val flag: ExerciseFlag)

    private data class ExerciseBlock(val header: SourceLine, val setLines: List<SourceLine>) {
        fun asParserText(): String = buildList {
            add(header.text)
            addAll(setLines.map { it.text })
        }.joinToString("\n")

        fun asParserMetadata(): String = buildNoteRowMetadata(
            times = buildList {
                add(header.elapsedText)
                addAll(setLines.map { it.elapsedText })
            },
            flags = buildList {
                add(header.flag)
                addAll(setLines.map { it.flag })
            },
        )
    }

    private data class MutableExerciseBlock(val header: SourceLine, val setLines: MutableList<SourceLine> = mutableListOf()) {
        fun freeze(): ExerciseBlock = ExerciseBlock(header, setLines.toList())
    }
}
