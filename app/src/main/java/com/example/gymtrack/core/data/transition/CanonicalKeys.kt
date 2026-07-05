package com.example.gymtrack.core.data.transition

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

internal object CanonicalKeys {
    fun workout(legacyTimestamp: Long): String = key("workout", legacyTimestamp.toString())

    fun category(name: String, colorArgb: Long): String =
        key("category", "${normalize(name)}:$colorArgb")

    fun legacyExercise(legacyExerciseId: Long): String =
        key("legacy-exercise", legacyExerciseId.toString())

    fun namedExercise(name: String): String = key("named-exercise", normalize(name))

    fun alias(exerciseKey: String, alias: String): String =
        key("exercise-alias", "$exerciseKey:${normalize(alias)}")

    fun workoutExercise(workoutKey: String, position: Int): String =
        key("workout-exercise", "$workoutKey:$position")

    fun workoutSet(workoutExerciseKey: String, position: Int): String =
        key("workout-set", "$workoutExerciseKey:$position")

    fun normalize(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")

    private fun key(namespace: String, value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$namespace:$value".toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }.take(32)
    }
}
