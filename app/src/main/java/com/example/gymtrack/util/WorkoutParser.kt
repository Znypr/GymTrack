package com.example.gymtrack.util

import java.util.regex.Pattern
import kotlin.math.min

// DTO
data class ParsedSetDTO(
    val exerciseName: String,
    var weight: Float,
    val reps: Int,
    val isUnilateral: Boolean,
    val modifier: String?,
    val brand: String?,
    val relativeTime: String?,
    val absoluteTime: String?,
    val workoutId: Long = 0L
)

class WorkoutParser {

    companion object {

        private val weightRegex = Pattern.compile("(?i)(?:^|\\s)(\\d+(?:\\.\\d+)?)(?:\\s*(kg|lbs))?(?![xX]|[':]|\\d)")
        private val repsRegex = Pattern.compile("(?i)(?:x\\s*([0-9+]+)|([0-9+]+)\\s*x|([0-9+]+)\\s*reps?)")
        private val repsCaptureRegex = Pattern.compile("([0-9+]+)\\s*x", Pattern.CASE_INSENSITIVE)
        private val metadataStartRegex = Regex("""\s*\(?\d+\s*['’":]\s*\d+""")
        private val plusNumberRegex = Regex("\\+\\d+")
        private val isolatedNumberRegex = Regex("""\b\d+\b""")
        private val parenNumberRegex = Regex("""\(\s*\d+\s*\)""")

    }


    // --- DICTIONARIES & LISTS ---

    private val BODYWEIGHT_EXERCISES = setOf(
        "pullup", "chinup", "dip", "situp", "pushup", "crunch", "hanging leg raise",
        "leg raise", "air squat", "lunge", "hyperextension", "back extension", "muscle up",
        "burpee", "box jump"
    )

    private val CARDIO_EXERCISES = setOf(
        "cycling", "cycle", "bike", "spinning",
        "stairmaster", "stair master", "stairs",
        "treadmill", "running", "jogging", "run",
        "elliptical", "rowing machine", "ergometer", "rowing"
    )

    private val GYM_TERMS = setOf(
        "press", "push", "pull", "row", "raise", "curl", "extension", "dip",
        "squat", "leg", "arm", "chest", "back", "shoulder", "delt", "bicep", "tricep",
        "dumbbell", "barbell", "kettlebell", "cable", "machine", "smith", "rope",
        "bench", "incline", "decline", "fly", "butterfly", "crunch", "situp",
        "adductor", "abductor", "calf", "glute", "hamstring", "quad",
        "deadlift", "romanian", "stiff", "lunge", "step", "stairmaster", "lat",
        "hack", "seated", "lying", "standing", "assisted", "hanging", "hyperextension"
    ) + BODYWEIGHT_EXERCISES + CARDIO_EXERCISES

    private val BRAND_ALIASES = mapOf(
        "hs" to "Hammer Strength", "hammerstrength" to "Hammer Strength",
        "cy" to "Cybex", "cybex" to "Cybex",
        "syg" to "Sygnum",
        "g80" to "Gym80", "gym80" to "Gym80",
        "lf" to "Life Fitness", "lifefitness" to "Life Fitness",
        "atlantis" to "Atlantis", "prime" to "Prime", "technogym" to "Technogym",
        "matrix" to "Matrix", "precor" to "Precor", "nautilus" to "Nautilus",
        "panatta" to "Panatta", "watson" to "Watson", "rogers" to "Rogers"
    )

    // User's Restricted Modifier List
    private val MODIFIER_ALIASES = mapOf(
        "db" to "Dumbbell", "dbs" to "Dumbbell", "dumbell" to "Dumbbell",
        "bb" to "Barbell", "oh" to "Overgrip",
        "ug" to "Undergrip", "og" to "Overgrip"
    )

    // User's Explicitly Allowed Modifiers
    private val KNOWN_MODIFIERS = setOf(
        "wide", "narrow", "medium", "close", "neutral",
        "undergrip", "overgrip", "ug", "og", "n", "oh",
        "single", "arm", "alternating", "standing",
        "high", "low", "iso", "spt",
        "rope", "cable", "smith", "incline", "decline", "dumbbell", "db", "dbs", "barbell", "bb", "supported"
    )

    private val CORE_MAPPINGS = mapOf(
        "bench" to "bench press",
        "latpull" to "lat pulldown",
        "lat pull" to "lat pulldown",
        "latpulldown" to "lat pulldown",
        "rowing" to "row",
        "pendulum" to "pendulum squat",
        "side" to "lateral",
        "rear" to "posterior",
        "medial" to "lateral",
        "pull up" to "pullup", "pullup" to "pullup",
        "stair master" to "stairmaster",
        "hacksquat" to "hack squat",
        "legraise" to "leg raise",
        "ad abductor" to "adductor",
        "buttferfli" to "butterfly",
        "cycle" to "cycling", "bike" to "cycling"
    )

    private val NOISE_WORDS = setOf("uni", "ss", "u", "b", "l", "r", "with", "on", "no", "v")
    private val VOCABULARY = GYM_TERMS + BRAND_ALIASES.keys + BRAND_ALIASES.values + MODIFIER_ALIASES.keys + MODIFIER_ALIASES.values + KNOWN_MODIFIERS

    // --- NEW HELPER: Calculate Reps from String (handles "5+5") ---
    private fun calculateReps(raw: String?): Int {
        if (raw == null) return 0
        // Split by '+' and sum (e.g. "5+5" -> 10)
        return raw.split("+").sumOf { it.trim().toIntOrNull() ?: 0 }
    }

    // --- ALGORITHMS ---

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLen = lhs.length
        val rhsLen = rhs.length
        var costs = IntArray(rhsLen + 1) { it }
        var newCosts = IntArray(rhsLen + 1)
        for (i in 0 until lhsLen) {
            newCosts[0] = i + 1
            for (j in 0 until rhsLen) {
                val cost = if (lhs[i] == rhs[j]) 0 else 1
                newCosts[j + 1] = min(newCosts[j] + 1, min(costs[j + 1] + 1, costs[j] + cost))
            }
            val temp = costs
            costs = newCosts
            newCosts = temp
        }
        return costs[rhsLen]
    }

    private fun autocorrect(word: String): String {
        if (VOCABULARY.contains(word)) return word
        if (word.length < 3) return word
        var bestMatch = word
        var lowestDistance = Int.MAX_VALUE
        for (term in VOCABULARY) {
            val distance = levenshtein(word, term)
            val threshold = if (term.length > 5) 2 else 1
            if (distance <= threshold && distance < lowestDistance) {
                lowestDistance = distance
                bestMatch = term
            }
        }
        return bestMatch
    }

    private fun singularize(text: String): String {
        val words = text.split(" ")
        return words.joinToString(" ") { word ->
            when {
                word.endsWith("ies") -> word.dropLast(3) + "y"
                word.endsWith("ss") -> word
                word.endsWith("s") -> word.dropLast(1)
                else -> word
            }
        }
    }

    // --- PIPELINE LOGIC ---

    private fun extractDetails(rawLine: String, rawMetadata: String): Triple<String, String?, String?> {
        // Step 0: Pre-cleaning
        var cleanedLine = rawLine.lowercase()
            .replace(parenNumberRegex, "")
            .replace(isolatedNumberRegex, "")
            .replace(plusNumberRegex, "")
            .trim()

        // Step 1: Tokenize & Autocorrect
        var tokens = cleanedLine.split("\\s+".toRegex()).toMutableList()
        val correctedTokens = tokens.map { autocorrect(it) }.toMutableList()
        tokens = correctedTokens

        // Step 2: Extract Brands
        val foundBrands = mutableSetOf<String>()
        val remainingTokensStep2 = mutableListOf<String>()
        for (token in tokens) {
            if (BRAND_ALIASES.containsKey(token)) {
                foundBrands.add(BRAND_ALIASES[token]!!)
            } else if (BRAND_ALIASES.values.any { it.equals(token, true) }) {
                foundBrands.add(BRAND_ALIASES.entries.first { it.value.equals(token, true) }.value)
            } else {
                remainingTokensStep2.add(token)
            }
        }
        tokens = remainingTokensStep2

        // Step 3: Extract Modifiers
        val foundModifiers = mutableSetOf<String>()
        val remainingTokensStep3 = mutableListOf<String>()
        for (token in tokens) {
            if (MODIFIER_ALIASES.containsKey(token)) {
                foundModifiers.add(MODIFIER_ALIASES[token]!!)
            } else if (KNOWN_MODIFIERS.contains(token)) {
                val standardized = token.replaceFirstChar { it.titlecase() }
                foundModifiers.add(standardized)
            } else {
                remainingTokensStep3.add(token)
            }
        }
        tokens = remainingTokensStep3

        // Step 4: Core Name Normalization
        var coreName = tokens.joinToString(" ")
            .replace(Regex("[-/.,]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        coreName = singularize(coreName)

        if (CORE_MAPPINGS.containsKey(coreName)) {
            coreName = CORE_MAPPINGS[coreName]!!
        }

        val finalTokens = coreName.split(" ").filter { !NOISE_WORDS.contains(it) }
        coreName = finalTokens.joinToString(" ").replaceFirstChar { it.titlecase() }

        val brandStr = if (foundBrands.isNotEmpty()) foundBrands.joinToString(", ") else null
        val modStr = if (foundModifiers.isNotEmpty()) foundModifiers.joinToString(", ") else null

        return Triple(coreName, modStr, brandStr)
    }

    private fun extractTimes(rawMetadata: String): Pair<String?, String?> {
        if (rawMetadata.isBlank()) return Pair(null, null)
        val relMatch = Regex("""\(\s*(\d+['’":]\d+)\s*["”]?\s*\)""").find(rawMetadata)
        val relativeTime = relMatch?.groupValues?.get(1)
        var remaining = rawMetadata
        if (relMatch != null) remaining = remaining.replace(relMatch.value, "")
        val absMatch = Regex("""(\d+['’":]\d+)""").find(remaining)
        val absoluteTime = absMatch?.groupValues?.get(1)
        return Pair(relativeTime, absoluteTime)
    }

    fun parseWorkout(rawText: String): List<ParsedSetDTO> {
        val results = mutableListOf<ParsedSetDTO>()

        var currentExerciseName = "Unknown"
        var currentModifier: String? = null
        var currentBrand: String? = null
        var currentRelTime: String? = null
        var currentAbsTime: String? = null
        var currentWeight = 0f

        val lines = rawText.split("\n")

        for (line in lines) {
            if (line.isBlank()) continue

            val matchResult = metadataStartRegex.find(line)
            val rawNamePart = if (matchResult != null) line.substring(0, matchResult.range.first) else line
            val rawMetadataPart = if (matchResult != null) line.substring(matchResult.range.first) else ""

            val cleanLine = rawNamePart.trim()

            // Set Detection Logic
            val looksLikeSet = cleanLine.matches(Regex("(?i)^[\\d+]+\\s*x.*")) ||
                    cleanLine.matches(Regex("(?i)^x\\s*[\\d+]+.*")) ||
                    repsRegex.matcher(cleanLine).find() ||
                    cleanLine.matches(Regex("^\\d+$")) ||
                    line.startsWith("    ")

            if (!looksLikeSet) {
                // --- HEADER LINE ---
                currentWeight = 0f

                val (rel, abs) = extractTimes(rawMetadataPart)
                currentRelTime = rel
                currentAbsTime = abs

                val (name, mod, brand) = extractDetails(cleanLine, rawMetadataPart)
                currentExerciseName = name
                currentModifier = mod
                currentBrand = brand

            } else {
                // --- SET LINE ---
                val isCardio = CARDIO_EXERCISES.contains(currentExerciseName.lowercase())
                val isBodyweight = BODYWEIGHT_EXERCISES.contains(currentExerciseName.lowercase())

                var weight = 0f
                var reps = 0

                val lineContent = cleanLine // Start with the full text
                var remainder = cleanLine // Will hold the weight part

                // --- Step A: Extract Reps (Primary Action) ---
                val repsMatcher = repsCaptureRegex.matcher(lineContent)

                if (repsMatcher.find()) {
                    // 1. Get the raw rep string (e.g., "50+50")
                    val rawRepString = repsMatcher.group(1)

                    // 2. Calculate the sum (e.g., 100)
                    reps = calculateReps(rawRepString)

                    // 3. FIX: Use the standard String.replaceFirst to remove the matched text.
                    // The matched text includes the final 'x' (e.g., "50+50x").
                    // We get the exact text matched by the entire expression using match.group(0).
                    val matchedRepExpression = repsMatcher.group(0)
                    remainder = lineContent.replaceFirst(matchedRepExpression, "").trim()
                }

                // --- Step B: Extract Weight from Remainder ---
                if (reps > 0) {
                    val weightMatcher = weightRegex.matcher(remainder)
                    if (weightMatcher.find()) {
                        val found = weightMatcher.group(1)?.toFloatOrNull()
                        if (found != null) weight = found
                    }
                }

                // --- Cardio Logic ---
                if (isCardio) {
                    if (reps == 0 && weight > 0) {
                        reps = weight.toInt() // Weight is duration
                        weight = 0f
                    }
                }

                // --- Carry-Over Logic ---
                if (weight > 0f) currentWeight = weight

                val finalWeight = if (isBodyweight && weight == 0f) 0f else currentWeight

                val isValidSet = if (isBodyweight || isCardio) {
                    reps > 0
                } else {
                    reps > 0 && finalWeight > 0f
                }

                if (isValidSet) {
                    results.add(
                        ParsedSetDTO(
                            exerciseName = currentExerciseName,
                            weight = finalWeight,
                            reps = reps,
                            isUnilateral = line.contains("\u200Cu") || line.lowercase().contains("uni"),
                            modifier = currentModifier,
                            brand = currentBrand,
                            relativeTime = currentRelTime,
                            absoluteTime = currentAbsTime
                        )
                    )
                }
            }
        }
        return results
    }
}