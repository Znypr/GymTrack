package com.example.gymtrack.core.util

import kotlin.math.abs

enum class ExerciseIdentityConfidence {
    HIGH,
    MEDIUM,
    LOW,
    AMBIGUOUS,
}

enum class ExerciseEquipment {
    BODYWEIGHT,
    CABLE,
    MACHINE,
    DUMBBELL,
    BARBELL,
    SMITH_MACHINE,
    UNKNOWN,
}

enum class ExerciseAttachment {
    ROPE,
    STRAIGHT_BAR,
    V_BAR,
    EZ_BAR,
    HANDLE,
    UNKNOWN,
}

enum class ExerciseSideMode {
    BILATERAL,
    UNILATERAL,
    ALTERNATING,
    UNKNOWN,
}

enum class ExerciseVariantLabelKind {
    BRAND,
    ATTACHMENT,
    EQUIPMENT,
    SIDE,
    WARNING,
}

data class ExerciseVariantLabel(
    val text: String,
    val kind: ExerciseVariantLabelKind,
)

data class ExerciseIdentity(
    val rawName: String,
    val canonicalName: String,
    val canonicalId: String,
    val aliases: Set<String> = emptySet(),
    val equipment: ExerciseEquipment = ExerciseEquipment.UNKNOWN,
    val attachment: ExerciseAttachment? = null,
    val brand: String? = null,
    val sideMode: ExerciseSideMode = ExerciseSideMode.UNKNOWN,
    val confidence: ExerciseIdentityConfidence = ExerciseIdentityConfidence.LOW,
    val warnings: List<String> = emptyList(),
) {
    val baseComparisonKey: String = canonicalId

    val strictComparisonKey: String = listOfNotNull(
        canonicalId,
        equipment.takeUnless { it == ExerciseEquipment.UNKNOWN }?.name?.lowercase(),
        attachment?.takeUnless { it == ExerciseAttachment.UNKNOWN }?.name?.lowercase(),
        brand?.let(ExerciseIdentityResolver::comparisonToken),
        sideMode.takeUnless { it == ExerciseSideMode.UNKNOWN }?.name?.lowercase(),
    ).joinToString(":")

    /**
     * Weight/progress charts must not merge load-incompatible variants. A dumbbell lateral
     * raise, machine lateral raise, cable lateral raise, unilateral variant, or brand-specific
     * machine can share the base exercise for prediction, but not the same progress series.
     */
    val progressComparisonKey: String = strictComparisonKey.ifBlank { baseComparisonKey }

    companion object {
        fun unknown(rawName: String): ExerciseIdentity {
            val displayName = rawName.trim().ifBlank { "Unknown exercise" }
            val id = ExerciseIdentityResolver.comparisonToken(displayName)
            return ExerciseIdentity(
                rawName = rawName,
                canonicalName = displayName,
                canonicalId = id,
                aliases = setOf(displayName).filterMeaningfulAliases(displayName),
                confidence = ExerciseIdentityConfidence.LOW,
                warnings = listOf("No canonical exercise rule matched."),
            )
        }
    }
}

fun ExerciseIdentity.variantLabels(): List<String> = variantLabelSpecs().map { it.text }

fun ExerciseIdentity.variantLabelSpecs(): List<ExerciseVariantLabel> = buildList {
    brand?.takeIf(String::isNotBlank)?.let { label ->
        add(ExerciseVariantLabel(label, ExerciseVariantLabelKind.BRAND))
    }
    attachment?.displayLabel()?.let { label ->
        add(ExerciseVariantLabel(label, ExerciseVariantLabelKind.ATTACHMENT))
    }
    when (equipment) {
        ExerciseEquipment.CABLE -> add(ExerciseVariantLabel("Cable", ExerciseVariantLabelKind.EQUIPMENT))
        ExerciseEquipment.DUMBBELL -> add(ExerciseVariantLabel("Dumbbell", ExerciseVariantLabelKind.EQUIPMENT))
        ExerciseEquipment.BARBELL -> add(ExerciseVariantLabel("Barbell", ExerciseVariantLabelKind.EQUIPMENT))
        ExerciseEquipment.SMITH_MACHINE -> add(ExerciseVariantLabel("Smith", ExerciseVariantLabelKind.EQUIPMENT))
        ExerciseEquipment.MACHINE -> if (brand.isNullOrBlank()) {
            add(ExerciseVariantLabel("Machine", ExerciseVariantLabelKind.EQUIPMENT))
        }
        ExerciseEquipment.BODYWEIGHT -> add(ExerciseVariantLabel("Bodyweight", ExerciseVariantLabelKind.EQUIPMENT))
        ExerciseEquipment.UNKNOWN -> Unit
    }
    when (sideMode) {
        ExerciseSideMode.UNILATERAL -> add(ExerciseVariantLabel("Unilateral", ExerciseVariantLabelKind.SIDE))
        ExerciseSideMode.ALTERNATING -> add(ExerciseVariantLabel("Alternating", ExerciseVariantLabelKind.SIDE))
        ExerciseSideMode.BILATERAL,
        ExerciseSideMode.UNKNOWN -> Unit
    }
    if (confidence == ExerciseIdentityConfidence.AMBIGUOUS) {
        add(ExerciseVariantLabel("Review", ExerciseVariantLabelKind.WARNING))
    }
}.distinctBy { it.text to it.kind }

/**
 * [ExerciseIdentity.canonicalName] without the words already shown as one of [chips] (#331):
 * "Chest Press Prime" with a [Prime] chip shows as "Chest Press". Display only.
 */
fun ExerciseIdentity.titleWithout(chips: List<ExerciseVariantLabel>): String {
    val phrases = chips.flatMap(::chipWords).map { it.split(" ") }.sortedByDescending { it.size }
    val words = canonicalName.split(" ").filter(String::isNotBlank)
    val kept = mutableListOf<String>()
    var i = 0
    while (i < words.size) {
        val match = phrases.firstOrNull { phrase ->
            i + phrase.size <= words.size && phrase.indices.all { words[i + it].lowercase() == phrase[it] }
        }
        if (match == null) kept += words[i]
        i += match?.size ?: 1
    }
    return kept.joinToString(" ").ifBlank { canonicalName }
}

private fun chipWords(label: ExerciseVariantLabel): Set<String> = when (label.kind) {
    ExerciseVariantLabelKind.BRAND -> ExerciseIdentityResolver.brandWords(label.text)
    ExerciseVariantLabelKind.ATTACHMENT,
    ExerciseVariantLabelKind.EQUIPMENT -> equipmentChipWords[label.text].orEmpty()
    ExerciseVariantLabelKind.SIDE,
    ExerciseVariantLabelKind.WARNING -> emptySet()
}

/** Typed words behind each equipment/attachment chip of [variantLabelSpecs]. */
private val equipmentChipWords = mapOf(
    "Cable" to setOf("cable"),
    "Dumbbell" to setOf("dumbbell", "db"),
    "Barbell" to setOf("barbell", "bar"),
    "Smith" to setOf("smith"),
    "Machine" to setOf("machine"),
    "Bodyweight" to setOf("bodyweight"),
    "Straight bar" to setOf("straight bar", "straightbar", "bar"),
    "Rope" to setOf("rope"),
    "V-bar" to setOf("v bar", "vbar", "v-bar"),
    "EZ-bar" to setOf("ez bar", "ezbar", "ez-bar"),
    "Handle" to setOf("handle"),
)

private fun ExerciseAttachment.displayLabel(): String = when (this) {
    ExerciseAttachment.ROPE -> "Rope"
    ExerciseAttachment.STRAIGHT_BAR -> "Straight bar"
    ExerciseAttachment.V_BAR -> "V-bar"
    ExerciseAttachment.EZ_BAR -> "EZ-bar"
    ExerciseAttachment.HANDLE -> "Handle"
    ExerciseAttachment.UNKNOWN -> "Attachment"
}

object ExerciseIdentityResolver {
    private val whitespaceRegex = Regex("\\s+")
    private val punctuationRegex = Regex("[^a-z0-9]+")
    private val unilateralTokenRegex = Regex("""(^|\s)(uni|unilateral|single|one arm|one leg)(\s|$)""")
    private val alternatingTokenRegex = Regex("""(^|\s)(alternating|alternate)(\s|$)""")
    private val brandAliases = mapOf(
        "at" to "Atlantis",
        "atl" to "Atlantis",
        "atlantis" to "Atlantis",
        "prime" to "Prime",
        "hs" to "Hammer Strength",
        "hammerstrength" to "Hammer Strength",
        "cy" to "Cybex",
        "cybex" to "Cybex",
        "g80" to "Gym80",
        "gym80" to "Gym80",
        "syg" to "Gym80",
        "sygnum" to "Gym80",
        "lf" to "Life Fitness",
        "lifefitness" to "Life Fitness",
        "technogym" to "Technogym",
        "matrix" to "Matrix",
        "precor" to "Precor",
        "nautilus" to "Nautilus",
        "panatta" to "Panatta",
        "bb" to "BootyBuilder",
        "bootybuilder" to "BootyBuilder",
        "rl" to "Realleader",
        "realleader" to "Realleader",
    )
    private val spacedBrandNames = mapOf(
        "hammer strength" to "Hammer Strength",
        "life fitness" to "Life Fitness",
        "real leader" to "Realleader",
        "booty builder" to "BootyBuilder",
    )
    private const val MIN_TYPO_LENGTH = 5
    private val pullDownRegex = Regex("""\bpull down\b""")
    private val latPullRegex = Regex("""\blat pull\b""")
    private val abbreviations = mapOf(
        "bp" to "bench press",
        "ohp" to "overhead press",
        "rdl" to "romanian deadlift",
        "dl" to "deadlift",
        "bss" to "bulgarian split squat",
        "flye" to "fly",
        "flyes" to "fly",
        "dumbell" to "dumbbell",
        "dbs" to "dumbbell",
        "kgs" to "kg",
        "lbs" to "lb",
        "hacksquat" to "hack squat",
    )

    /** Equipment and attachment words: they become chips, not part of the exercise. */
    private val equipmentWords = setOf(
        "dumbbell", "db", "barbell", "bar", "cable", "machine", "smith", "rope", "straight", "straightbar",
        "ez", "ezbar", "v", "vbar", "handle", "bodyweight",
    )

    /**
     * Exercise words that typos and joined words resolve to. A minimal copy of the parser's
     * vocabulary, which this layer does not share.
     */
    private val exerciseWords = setOf(
        "press", "push", "pull", "row", "rowing", "raise", "curl", "extension", "dip", "squat", "leg", "arm",
        "chest", "back", "shoulder", "delt", "biceps", "triceps", "kettlebell", "bench", "incline", "decline",
        "fly", "butterfly", "crunch", "adductor", "abductor", "calf", "calve", "glute", "hamstring", "quad",
        "deadlift", "romanian", "stiff", "lunge", "step", "lat", "hack", "seated", "lying", "standing",
        "assisted", "hanging", "hyperextension", "pushdown", "pulldown", "pullup", "pushup", "chinup", "situp",
        "diagonal", "diag", "overhead", "hip", "thrust", "lateral", "side", "rear", "front", "face", "split",
        "bulgarian", "dead", "hang", "plank", "preacher", "hammer", "brachial", "shrug", "pec", "deck",
        "reverse", "bent", "over", "pullover", "kickback", "crossover", "upright", "goblet", "sumo",
        "military", "flat", "wide", "close", "narrow", "neutral", "grip", "single", "alternating",
        "alternate", "unilateral", "bilateral", "skull", "crusher", "wrist", "trap",
    ) + equipmentWords - setOf("v", "ez", "db")

    /** Words that are never spell-corrected: exercise words, brands and row markers. */
    private val knownWords = exerciseWords + brandAliases.keys +
        setOf("hammer", "strength", "real", "leader", "booty", "builder", "life", "fitness", "uni", "ss", "l6", "kg", "lb")

    fun resolve(
        rawName: String,
        parsedName: String = rawName,
        modifier: String? = null,
        brand: String? = null,
        isUnilateral: Boolean = false,
    ): ExerciseIdentity {
        val rawNormalized = identityText(rawName)
        val parsedNormalized = identityText(parsedName)
        val modifierNormalized = identityText(modifier.orEmpty())
        val combined = listOf(rawNormalized, parsedNormalized, modifierNormalized)
            .filter(String::isNotBlank)
            .joinToString(" ")
        val resolvedBrand = brand?.trim()?.takeIf(String::isNotEmpty)
            ?: detectBrand(rawNormalized)
            ?: detectBrand(parsedNormalized)

        val equipment = detectEquipment(combined, resolvedBrand)
        val attachment = detectAttachment(combined, equipment)
        val sideMode = detectSideMode(rawNormalized, modifierNormalized, isUnilateral)
        val warnings = mutableListOf<String>()

        val canonical = canonicalNameFor(rawNormalized, parsedNormalized, combined, warnings)
        val confidence = when {
            warnings.any { it.contains("ambiguous", ignoreCase = true) } -> ExerciseIdentityConfidence.AMBIGUOUS
            canonical == parsedName.trim() && canonical.equals(rawName.trim(), ignoreCase = true) -> ExerciseIdentityConfidence.MEDIUM
            canonical.equals("Unknown exercise", ignoreCase = true) -> ExerciseIdentityConfidence.LOW
            else -> ExerciseIdentityConfidence.HIGH
        }

        val aliases = buildAliases(
            canonicalName = canonical,
            rawName = rawName,
            parsedName = parsedName,
        )

        return ExerciseIdentity(
            rawName = rawName,
            canonicalName = canonical,
            canonicalId = comparisonToken(canonical),
            aliases = aliases,
            equipment = equipment,
            attachment = attachment,
            brand = resolvedBrand,
            sideMode = sideMode,
            confidence = confidence,
            warnings = warnings.distinct(),
        )
    }

    /** Everything a user may type for [brand]: `hs`, `hammerstrength`, `hammer strength`. */
    internal fun brandWords(brand: String): Set<String> =
        (brandAliases + spacedBrandNames).filterValues { it == brand }.keys + normalize(brand)

    fun comparisonToken(raw: String): String = normalize(raw)
        .replace(" ", "_")
        .ifBlank { "unknown_exercise" }

    private fun canonicalNameFor(
        raw: String,
        parsed: String,
        combined: String,
        warnings: MutableList<String>,
    ): String {
        val cleanedRaw = stripContextTokens(raw)
        val cleanedParsed = stripContextTokens(parsed)
        val cleanedCombined = stripContextTokens(combined)
        // Words left once equipment and brand are removed: "cable curl", "curl db" -> {curl}.
        val coreWords = cleanedCombined.split(" ")
            .filterNot { it in equipmentWords || it in brandAliases }
            .toSet()

        return when {
            cleanedCombined.contains("triceps extension bar") && !cleanedCombined.contains("pushdown") -> {
                warnings += "Ambiguous triceps extension bar wording; could be cable straight-bar extension or lying barbell extension."
                "Triceps Extension"
            }
            cleanedCombined.contains("triceps pushdown") -> "Triceps Pushdown"
            cleanedCombined.contains("bar triceps pushdown") -> "Triceps Pushdown"
            cleanedCombined.contains("pushdown") && cleanedCombined.contains("triceps") -> "Triceps Pushdown"
            coreWords == setOf("pushdown") -> "Triceps Pushdown"
            cleanedCombined.contains("overhead extension") || cleanedCombined.contains("overhead triceps extension") -> {
                "Overhead Triceps Extension"
            }
            cleanedCombined.contains("triceps extension") -> "Triceps Extension"
            cleanedCombined.contains("bench press") -> "Bench Press"
            cleanedCombined.contains("hip thrust") -> "Hip Thrust"
            cleanedCombined.contains("lateral raise") -> "Lateral Raise"
            cleanedCombined.contains("lat raise") -> "Lateral Raise"
            cleanedCombined.contains("side raise") -> "Lateral Raise"
            cleanedCombined.contains("lat pulldown") -> "Lat Pulldown"
            cleanedCombined.contains("assisted pullup") || cleanedCombined.contains("assisted pull up") -> "Assisted Pull-Up"
            cleanedCombined.contains("pullup") || cleanedCombined.contains("pull up") -> "Pull-Up"
            cleanedCombined.contains("t bar row") || cleanedCombined.contains("tbar row") || cleanedCombined.contains("tbar rowing") ||
                cleanedCombined.contains("t bar machine") -> "T-Bar Row"
            cleanedCombined.contains("diag rowing") || cleanedCombined.contains("diagonal rowing") -> "Diagonal Row"
            cleanedCombined.contains("rear delt row") -> "Rear Delt Row"
            cleanedCombined == "row" || cleanedCombined.contains(" row") -> "Row"
            cleanedCombined.contains("rear delt") || cleanedCombined.contains("rear fly") -> "Rear Delt Fly"
            cleanedCombined == "butterfly" || cleanedCombined.contains("butterfly") -> "Pec Deck"
            cleanedCombined == "fly" || cleanedCombined.contains(" fly") -> "Chest Fly"
            cleanedCombined.contains("seated hamstring") -> "Seated Leg Curl"
            cleanedCombined.contains("leg extension") -> "Leg Extension"
            cleanedCombined.contains("leg press") -> "Leg Press"
            cleanedCombined.contains("reverse hack squat") -> "Reverse Hack Squat"
            cleanedCombined.contains("hack squat") -> "Hack Squat"
            cleanedCombined.contains("adductor") -> "Hip Adduction"
            cleanedCombined.contains("abductor") -> "Hip Abduction"
            cleanedCombined.contains("calve machine") || cleanedCombined.contains("calf machine") ||
                cleanedCombined.contains("calf raise") -> "Calf Raise"
            cleanedCombined.contains("dip machine") -> "Machine Dip"
            cleanedCombined.contains("preacher curl") -> "Preacher Curl"
            cleanedCombined.contains("hammer curl") -> "Hammer Curl"
            cleanedCombined.contains("brachial curl") -> "Brachialis Curl"
            cleanedCombined.contains("biceps curl") || coreWords == setOf("curl") -> "Biceps Curl"
            cleanedCombined.contains("split squat") -> "Bulgarian Split Squat"
            coreWords == setOf("squat") || coreWords == setOf("back", "squat") -> "Squat"
            cleanedCombined.contains("dead hang") || coreWords == setOf("hang") -> "Dead Hang"
            cleanedCombined.contains("shrug") -> "Shrug"
            cleanedCombined.contains("hanging leg raise") -> "Hanging Leg Raise"
            cleanedCombined.contains("sit up") -> "Sit-Up"
            cleanedCombined == "rl" || cleanedCombined == "realleader" -> {
                warnings += "Only Realleader brand marker remained after parsing; exercise needs manual review."
                "Unknown exercise"
            }
            cleanedCombined == "bb" || cleanedCombined == "bootybuilder" || cleanedCombined == "booty builder" -> {
                warnings += "Only BootyBuilder brand marker remained after parsing; exercise needs manual review."
                "Unknown exercise"
            }
            cleanedParsed.isNotBlank() -> cleanedParsed.toTitleCase()
            cleanedRaw.isNotBlank() -> cleanedRaw.toTitleCase()
            else -> "Unknown exercise"
        }
    }

    private fun detectEquipment(combined: String, brand: String?): ExerciseEquipment = when {
        combined.contains("bodyweight") -> ExerciseEquipment.BODYWEIGHT
        combined.contains("cable") || combined.contains("pushdown") -> ExerciseEquipment.CABLE
        combined.contains("dumbbell") || combined.contains(" db ") || combined.endsWith(" db") -> ExerciseEquipment.DUMBBELL
        combined.contains("barbell") || combined.startsWith("bar ") -> ExerciseEquipment.BARBELL
        combined.contains("smith") -> ExerciseEquipment.SMITH_MACHINE
        !brand.isNullOrBlank() -> ExerciseEquipment.MACHINE
        combined.contains("machine") || combined.contains("leg press") || combined.contains("leg extension") ||
            combined.contains("leg curl") || combined.contains("adductor") || combined.contains("abductor") ||
            combined.contains("hack squat") || combined.contains("pec deck") || combined.contains("butterfly") -> ExerciseEquipment.MACHINE
        combined.contains("pullup") || combined.contains("pull up") || combined.contains("sit up") ||
            combined.contains("hanging leg raise") -> ExerciseEquipment.BODYWEIGHT
        else -> ExerciseEquipment.UNKNOWN
    }

    private fun detectAttachment(combined: String, equipment: ExerciseEquipment): ExerciseAttachment? = when {
        equipment == ExerciseEquipment.BARBELL -> null
        combined.contains("t bar row") || combined.contains("t bar machine") -> null
        combined.contains("rope") -> ExerciseAttachment.ROPE
        combined.contains("v bar") || combined.contains("vbar") -> ExerciseAttachment.V_BAR
        combined.contains("ez bar") || combined.contains("ezbar") -> ExerciseAttachment.EZ_BAR
        combined.contains("straight bar") || combined.contains(" bar ") || combined.endsWith(" bar") ||
            combined.startsWith("bar ") -> ExerciseAttachment.STRAIGHT_BAR
        combined.contains("handle") -> ExerciseAttachment.HANDLE
        else -> null
    }

    private fun detectBrand(normalized: String): String? {
        spacedBrandNames.entries.firstOrNull { normalized.contains(it.key) }?.let { return it.value }
        val tokens = normalized.split(" ").filter(String::isNotBlank)
        return tokens.firstNotNullOfOrNull(brandAliases::get)
    }

    private fun detectSideMode(raw: String, modifier: String, isUnilateral: Boolean): ExerciseSideMode {
        val combined = "$raw $modifier"
        return when {
            alternatingTokenRegex.containsMatchIn(combined) -> ExerciseSideMode.ALTERNATING
            isUnilateral || unilateralTokenRegex.containsMatchIn(combined) -> ExerciseSideMode.UNILATERAL
            raw.isNotBlank() -> ExerciseSideMode.BILATERAL
            else -> ExerciseSideMode.UNKNOWN
        }
    }

    private fun buildAliases(
        canonicalName: String,
        rawName: String,
        parsedName: String,
    ): Set<String> {
        val aliases = linkedSetOf(rawName.trim(), parsedName.trim())
        when (canonicalName) {
            "Triceps Pushdown" -> aliases += listOf("tricep pushdown", "triceps pushdown", "bar tricep pushdown", "tricep pushdown bar")
            "Triceps Extension" -> aliases += listOf("tricep extension", "triceps extension")
            "Overhead Triceps Extension" -> aliases += listOf("overhead extension", "overhead tricep extension", "overhead triceps extension")
            "Lateral Raise" -> aliases += listOf("lat raise", "side raise", "lateral raises")
            "Lat Pulldown" -> aliases += listOf("latpulldown", "lat pull", "lat pull down")
            "Diagonal Row" -> aliases += listOf("diag rowing", "diagonal rowing")
            "T-Bar Row" -> aliases += listOf("tbar row", "tbar rowing", "tbar machine", "t bar row")
            "Seated Leg Curl" -> aliases += listOf("seated hamstring", "seated hamstring curl", "seated leg curl")
            "Pec Deck" -> aliases += listOf("butterfly", "pec deck")
            "Rear Delt Fly" -> aliases += listOf("rear delt", "rear fly", "reverse fly")
            "Calf Raise" -> aliases += listOf("calve machine", "calf machine", "calf raise")
            "Sit-Up" -> aliases += listOf("situp", "sit up", "situp l6", "sit up l6")
        }
        return aliases.filterMeaningfulAliases(canonicalName)
    }

    private fun normalize(raw: String): String {
        var value = raw.lowercase()
            .replace(Regex("""\blatpulldown\b"""), "lat pulldown")
            .replace(Regex("""\bhacksquat\b"""), "hack squat")
            .replace(Regex("""\btbar\b"""), "t bar")
            .replace(Regex("""\bsitup\b"""), "sit up")
            .replace(Regex("""\btricep\b"""), "triceps")
            .replace(Regex("""\bbicep\b"""), "biceps")
            .replace(punctuationRegex, " ")
            .replace(whitespaceRegex, " ")
            .trim()
        return value
    }

    /**
     * [normalize] plus the grouping rules of #318, per word: abbreviations (`bp`, `rdl`, `ohp`),
     * plural -> singular, joined words (`legpress`) and a conservative typo fix. Used for
     * grouping and display only; the raw text is never rewritten.
     */
    internal fun identityText(raw: String): String = normalize(raw)
        .split(" ")
        .filter(String::isNotBlank)
        .joinToString(" ", transform = ::canonicalWord)
        .replace(pullDownRegex, "pulldown")
        .replace(latPullRegex, "lat pulldown")

    private fun canonicalWord(word: String): String {
        abbreviations[word]?.let { return it }
        if (word.length < 3 || word.any(Char::isDigit) || word in knownWords) return word
        val singular = singularize(word)
        if (singular in knownWords) return singular
        return correctTypo(word) ?: splitJoined(singular) ?: correctTypo(singular) ?: singular
    }

    private fun singularize(word: String): String = when {
        word.length < 4 -> word
        word.endsWith("sses") || word.endsWith("ches") || word.endsWith("shes") -> word.dropLast(2)
        word.endsWith("ies") && word.length > 4 -> word.dropLast(3) + "y"
        word.endsWith("ss") || word.endsWith("us") || word.endsWith("is") || word.endsWith("as") -> word
        word.endsWith("s") -> word.dropLast(1)
        else -> word
    }

    /** `legpress` -> `leg press`, only when exactly one split gives two exercise words. */
    private fun splitJoined(word: String): String? = (3..word.length - 3)
        .map { word.take(it) to word.drop(it) }
        .filter { (head, tail) -> head in exerciseWords && tail in exerciseWords }
        .singleOrNull()
        ?.let { (head, tail) -> "$head $tail" }

    /**
     * Same rules as the parser's autocorrect (#300/#351), so a typo never becomes a different
     * exercise: known words are kept, the word has at least [MIN_TYPO_LENGTH] letters, the first
     * letter matches, and exactly one exercise word is one edit away. Swapping two neighbouring
     * letters counts as one edit (`sqaut`, `rasie`). A missing doubled last letter (`pres`,
     * `barbel`) is fixed from 4 letters.
     */
    private fun correctTypo(word: String): String? {
        (word + word.last()).takeIf { word.length >= 4 && it in exerciseWords }?.let { return it }
        if (word.length < MIN_TYPO_LENGTH) return null
        return exerciseWords
            .filter { it.first() == word.first() && abs(it.length - word.length) <= 1 && editDistance(word, it) == 1 }
            .singleOrNull()
    }

    /** Optimal string alignment distance: Levenshtein plus swaps of neighbouring letters. */
    private fun editDistance(a: String, b: String): Int {
        val d = Array(a.length + 1) { i -> IntArray(b.length + 1) { j -> if (i == 0) j else if (j == 0) i else 0 } }
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
                }
            }
        }
        return d[a.length][b.length]
    }

    private fun stripContextTokens(raw: String): String {
        val stripped = raw.split(" ")
            .filterNot { it in setOf("uni", "unilateral", "bilateral", "l6", "ss", "bb") }
            .joinToString(" ")
            .replace(whitespaceRegex, " ")
            .trim()
        return stripped
    }

    private fun String.toTitleCase(): String = split(" ")
        .filter(String::isNotBlank)
        .joinToString(" ") { word ->
            when (word) {
                "t" -> "T"
                "bar" -> "Bar"
                "up" -> "Up"
                else -> word.replaceFirstChar { it.titlecase() }
            }
        }
}

private fun Iterable<String>.filterMeaningfulAliases(canonicalName: String): Set<String> {
    val normalizedCanonical = ExerciseIdentityResolver.comparisonToken(canonicalName)
    return map(String::trim)
        .filter(String::isNotEmpty)
        .filter { ExerciseIdentityResolver.comparisonToken(it) != normalizedCanonical }
        .distinctBy(ExerciseIdentityResolver::comparisonToken)
        .toSet()
}
