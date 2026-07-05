package com.example.gymtrack.core.backup

import java.nio.charset.StandardCharsets
import org.json.JSONObject

object BackupJsonCodec {
    fun encodePayload(payload: GymTrackBackupPayload): ByteArray = JSONObject()
        .put("settings", payload.settings.asBackupJson())
        .put("legacyNotes", payload.legacyNotes.asJsonArray { it.asBackupJson() })
        .put("legacyExercises", payload.legacyExercises.asJsonArray { it.asBackupJson() })
        .put("legacySets", payload.legacySets.asJsonArray { it.asBackupJson() })
        .put("canonicalCategories", payload.canonicalCategories.asJsonArray { it.toBackupObject() })
        .put("canonicalExercises", payload.canonicalExercises.asJsonArray { it.toBackupObject() })
        .put("canonicalExerciseAliases", payload.canonicalExerciseAliases.asJsonArray { it.toBackupObject() })
        .put("canonicalWorkouts", payload.canonicalWorkouts.asJsonArray { it.toBackupObject() })
        .put("canonicalWorkoutExercises", payload.canonicalWorkoutExercises.asJsonArray { it.toBackupObject() })
        .put("canonicalWorkoutSets", payload.canonicalWorkoutSets.asJsonArray { it.toBackupObject() })
        .toString()
        .toByteArray(StandardCharsets.UTF_8)

    fun decodePayload(bytes: ByteArray): GymTrackBackupPayload = try {
        val root = JSONObject(bytes.toString(StandardCharsets.UTF_8))
        GymTrackBackupPayload(
            settings = root.getJSONObject("settings").asBackupSettings(),
            legacyNotes = root.getJSONArray("legacyNotes").mapJsonObjects { it.asBackupNoteEntity() },
            legacyExercises = root.getJSONArray("legacyExercises").mapJsonObjects { it.asBackupExerciseEntity() },
            legacySets = root.getJSONArray("legacySets").mapJsonObjects { it.asBackupSetEntity() },
            canonicalCategories = root.getJSONArray("canonicalCategories").mapJsonObjects { it.readBackupCategory() },
            canonicalExercises = root.getJSONArray("canonicalExercises").mapJsonObjects { it.readBackupExercise() },
            canonicalExerciseAliases = root.getJSONArray("canonicalExerciseAliases").mapJsonObjects { it.readBackupExerciseAlias() },
            canonicalWorkouts = root.getJSONArray("canonicalWorkouts").mapJsonObjects { it.readBackupWorkout() },
            canonicalWorkoutExercises = root.getJSONArray("canonicalWorkoutExercises").mapJsonObjects { it.readBackupWorkoutExercise() },
            canonicalWorkoutSets = root.getJSONArray("canonicalWorkoutSets").mapJsonObjects { it.readBackupWorkoutSet() },
        )
    } catch (error: Exception) {
        throw InvalidBackupException("Backup data is malformed", error)
    }

    fun encodeManifest(manifest: BackupManifest): ByteArray = JSONObject()
        .put("formatVersion", manifest.formatVersion)
        .put("createdAtEpochMillis", manifest.createdAtEpochMillis)
        .put("appVersion", manifest.appVersion)
        .put("databaseSchemaVersion", manifest.databaseSchemaVersion)
        .put("payloadSha256", manifest.payloadSha256)
        .put("counts", manifest.counts.toJsonObject())
        .toString()
        .toByteArray(StandardCharsets.UTF_8)

    fun decodeManifest(bytes: ByteArray): BackupManifest = try {
        val root = JSONObject(bytes.toString(StandardCharsets.UTF_8))
        BackupManifest(
            formatVersion = root.getInt("formatVersion"),
            createdAtEpochMillis = root.getLong("createdAtEpochMillis"),
            appVersion = root.getString("appVersion"),
            databaseSchemaVersion = root.getInt("databaseSchemaVersion"),
            payloadSha256 = root.getString("payloadSha256"),
            counts = root.getJSONObject("counts").asBackupCounts(),
        )
    } catch (error: Exception) {
        throw InvalidBackupException("Backup manifest is malformed", error)
    }
}

private fun BackupCounts.toJsonObject(): JSONObject = JSONObject()
    .put("legacyNotes", legacyNotes)
    .put("legacyExercises", legacyExercises)
    .put("legacySets", legacySets)
    .put("canonicalCategories", canonicalCategories)
    .put("canonicalExercises", canonicalExercises)
    .put("canonicalExerciseAliases", canonicalExerciseAliases)
    .put("canonicalWorkouts", canonicalWorkouts)
    .put("canonicalWorkoutExercises", canonicalWorkoutExercises)
    .put("canonicalWorkoutSets", canonicalWorkoutSets)

private fun JSONObject.asBackupCounts(): BackupCounts = BackupCounts(
    legacyNotes = getInt("legacyNotes"),
    legacyExercises = getInt("legacyExercises"),
    legacySets = getInt("legacySets"),
    canonicalCategories = getInt("canonicalCategories"),
    canonicalExercises = getInt("canonicalExercises"),
    canonicalExerciseAliases = getInt("canonicalExerciseAliases"),
    canonicalWorkouts = getInt("canonicalWorkouts"),
    canonicalWorkoutExercises = getInt("canonicalWorkoutExercises"),
    canonicalWorkoutSets = getInt("canonicalWorkoutSets"),
)
