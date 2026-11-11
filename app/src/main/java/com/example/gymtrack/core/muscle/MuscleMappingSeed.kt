package com.example.gymtrack.core.muscle

import com.example.gymtrack.core.muscle.Muscle.ABS
import com.example.gymtrack.core.muscle.Muscle.ADDUCTORS
import com.example.gymtrack.core.muscle.Muscle.BICEPS
import com.example.gymtrack.core.muscle.Muscle.BRACHIALIS
import com.example.gymtrack.core.muscle.Muscle.CHEST_LOWER
import com.example.gymtrack.core.muscle.Muscle.CHEST_UPPER
import com.example.gymtrack.core.muscle.Muscle.DELT_FRONT
import com.example.gymtrack.core.muscle.Muscle.DELT_REAR
import com.example.gymtrack.core.muscle.Muscle.DELT_SIDE
import com.example.gymtrack.core.muscle.Muscle.ERECTORS
import com.example.gymtrack.core.muscle.Muscle.FOREARM_EXTENSORS
import com.example.gymtrack.core.muscle.Muscle.FOREARM_FLEXORS
import com.example.gymtrack.core.muscle.Muscle.GASTROCNEMIUS
import com.example.gymtrack.core.muscle.Muscle.GLUTE_MAX
import com.example.gymtrack.core.muscle.Muscle.GLUTE_MED
import com.example.gymtrack.core.muscle.Muscle.HAMSTRINGS
import com.example.gymtrack.core.muscle.Muscle.LATS
import com.example.gymtrack.core.muscle.Muscle.OBLIQUES
import com.example.gymtrack.core.muscle.Muscle.QUADS
import com.example.gymtrack.core.muscle.Muscle.RHOMBOIDS_TERES
import com.example.gymtrack.core.muscle.Muscle.SERRATUS
import com.example.gymtrack.core.muscle.Muscle.SOLEUS
import com.example.gymtrack.core.muscle.Muscle.TIBIALIS
import com.example.gymtrack.core.muscle.Muscle.TRAPS_MID_LOWER
import com.example.gymtrack.core.muscle.Muscle.TRAPS_UPPER
import com.example.gymtrack.core.muscle.Muscle.TRICEPS
import com.example.gymtrack.core.util.ExerciseIdentityResolver

/** How well a seed mapping's source was checked (`docs/design/ANATOMY_MAPPING.md` legend). */
enum class CitationStatus(val code: String, val label: String) {
    /** Citation details checked online in the science review (2026-11-10). */
    VERIFIED("V", "Study checked"),

    /** Well-known paper cited from reviewer knowledge; DOI to re-check before showing in the app. */
    KNOWN("K", "Study (DOI unchecked)"),

    /** Expert consensus or functional anatomy; no direct study found. */
    CONSENSUS("C", "Expert consensus"),
}

/**
 * One row of the science-reviewed mapping (#309). [row] is the row number in
 * `docs/design/ANATOMY_MAPPING.md` §2, so every mapping can be traced to its justification.
 * [statusNote] is the doc's status cell verbatim when it is mixed (e.g. `V (delt: C)`).
 */
data class MuscleSeedEntry(
    val row: Int,
    val exercise: String,
    val primary: Set<Muscle>,
    val secondary: Set<Muscle>,
    val source: String,
    val status: CitationStatus,
    val statusNote: String? = null,
) {
    init {
        require(primary.isNotEmpty()) { "Seed row $row has no primary muscle" }
        require(primary.intersect(secondary).isEmpty()) { "Seed row $row lists a muscle twice" }
    }
}

/**
 * The built-in exercise → muscle mapping (#309). Lives in code, never in the database: user edits
 * are stored as overrides (`exercise_muscles`, #310) and a reset deletes them, so the seed can be
 * corrected in an app update without a migration.
 *
 * Lookup order for an exercise name (as stored in `exercises.name`, which is the
 * [ExerciseIdentityResolver] canonical name) and the set row's modifier:
 * 1. variant rules on the canonical base key (incline/decline bench, seated calf raise, chin-up,
 *    free vs supported row),
 * 2. the base key itself,
 * 3. the WorkoutParser keywords listed in the doc (deadlift, romanian, lunge, back extension,
 *    crunch …) for names the identity layer does not canonicalise.
 *
 * Exercises the doc does not cover return null (unmapped), never a guess.
 */
object MuscleMappingSeed {
    private fun e(
        row: Int,
        exercise: String,
        primary: Set<Muscle>,
        secondary: Set<Muscle>,
        source: String,
        status: CitationStatus,
        statusNote: String? = null,
    ) = MuscleSeedEntry(row, exercise, primary, secondary, source, status, statusNote)

    private const val RODRIGUEZ_RIDAO_2020 = "Rodríguez-Ridao D et al. 2020, IJERPH 17(19):7339, doi:10.3390/ijerph17197339"
    private const val YOUDAS_2010 = "Youdas JW et al. 2010, J Strength Cond Res 24(12):3404-3414, doi:10.1519/JSC.0b013e3181f1598c"
    private const val LEHMAN_2004 = "Lehman GJ et al. 2004, Dyn Med 3:4, doi:10.1186/1476-5918-3-4"
    private const val NETO_2020 = "Neto WK et al. 2020, J Sports Sci Med 19(1):195-203, PMC7039033"
    private const val MAEO_2021 = "Maeo S et al. 2021, Med Sci Sports Exerc 53(4):825-837, doi:10.1249/MSS.0000000000002523"
    private const val KINOSHITA_2023 = "Kinoshita M et al. 2023, Front Physiol 14:1272106, doi:10.3389/fphys.2023.1272106"
    private const val OLIVEIRA_2009 = "Oliveira LF et al. 2009, J Sports Sci Med 8(1):24-29"
    private const val MAEO_2023 = "Maeo S et al. 2023, Eur J Sport Sci 23(7):1240-1250, doi:10.1080/17461391.2022.2100279"
    private const val ESCAMILLA_2006 = "Escamilla RF et al. 2006, Phys Ther 86(5):656-671, doi:10.1093/ptj/86.5.656"
    private const val NO_STUDY = "No direct study found; functional anatomy"

    val BENCH_BARBELL = e(1, "Bench Press (flat, barbell)", setOf(CHEST_LOWER, CHEST_UPPER), setOf(DELT_FRONT, TRICEPS), RODRIGUEZ_RIDAO_2020, CitationStatus.VERIFIED)
    val BENCH_DUMBBELL = e(
        2, "Bench Press (flat, dumbbell)", setOf(CHEST_LOWER, CHEST_UPPER), setOf(DELT_FRONT, TRICEPS),
        "Saeterbakken AH, van den Tillaar R, Fimland MS 2011, J Sports Sci 29(5):533-538, doi:10.1080/02640414.2010.543916", CitationStatus.KNOWN,
    )
    val BENCH_INCLINE = e(3, "Incline press", setOf(CHEST_UPPER), setOf(CHEST_LOWER, DELT_FRONT, TRICEPS), RODRIGUEZ_RIDAO_2020, CitationStatus.VERIFIED)
    val BENCH_DECLINE = e(
        4, "Decline press", setOf(CHEST_LOWER), setOf(CHEST_UPPER, TRICEPS),
        "Glass SC, Armstrong T 1997, J Strength Cond Res 11(3):163-167", CitationStatus.KNOWN,
    )
    val OVERHEAD_PRESS = e(
        5, "Overhead press", setOf(DELT_FRONT), setOf(DELT_SIDE, TRICEPS),
        "Saeterbakken AH, Fimland MS 2013, J Strength Cond Res 27(7):1824-1831, doi:10.1519/JSC.0b013e318276b873", CitationStatus.KNOWN,
    )
    val PUSH_UP = e(
        6, "Push-up", setOf(CHEST_LOWER), setOf(CHEST_UPPER, DELT_FRONT, TRICEPS, SERRATUS),
        "Calatayud J et al. 2015, J Strength Cond Res 29(1):246-253, doi:10.1519/JSC.0000000000000589; " +
            "serratus: Ludewig PM et al. 2004, Am J Sports Med 32(2):484-493, doi:10.1177/0363546503258911",
        CitationStatus.KNOWN,
    )
    val DIP = e(
        7, "Dip / Machine Dip", setOf(CHEST_LOWER, TRICEPS), setOf(DELT_FRONT),
        "McKenzie A et al. 2022, IJERPH 19(20):13211, doi:10.3390/ijerph192013211", CitationStatus.VERIFIED, statusNote = "V (delt: C)",
    )
    val FLY = e(
        8, "Chest Fly / Pec Deck", setOf(CHEST_LOWER), setOf(CHEST_UPPER, DELT_FRONT),
        "Solstad TEJ et al. 2020, J Sports Sci Med 19(4):645-651 (pec deck extrapolated)", CitationStatus.VERIFIED,
    )
    val LATERAL_RAISE = e(
        9, "Lateral Raise", setOf(DELT_SIDE), setOf(TRAPS_UPPER),
        "Coratella G et al. 2020, IJERPH 17(17):6015, doi:10.3390/ijerph17176015", CitationStatus.VERIFIED,
    )
    val REAR_DELT_FLY = e(
        10, "Rear Delt Fly", setOf(DELT_REAR), setOf(RHOMBOIDS_TERES, TRAPS_MID_LOWER),
        "Reinold MM et al. 2004, JOSPT 34(7):385-394, doi:10.2519/jospt.2004.34.7.385; " +
            "Ekstrom RA et al. 2003, JOSPT 33(5):247-258, doi:10.2519/jospt.2003.33.5.247",
        CitationStatus.KNOWN, statusNote = "K (fly extrapolated from prone rehab exercises)",
    )
    val FACE_PULL = e(11, "Face pull", setOf(DELT_REAR), setOf(RHOMBOIDS_TERES, TRAPS_MID_LOWER), NO_STUDY, CitationStatus.CONSENSUS)
    val SHRUG = e(
        12, "Shrug", setOf(TRAPS_UPPER), emptySet(),
        "Pizzari T et al. 2014, Clin Biomech 29(2):201-205, doi:10.1016/j.clinbiomech.2013.11.011", CitationStatus.VERIFIED, statusNote = "V (DOI: K)",
    )
    val LAT_PULLDOWN = e(
        13, "Lat Pulldown", setOf(LATS), setOf(RHOMBOIDS_TERES, BICEPS, BRACHIALIS),
        "Andersen V et al. 2014, J Strength Cond Res 28(4):1135-1142, doi:10.1519/JSC.0000000000000232; $LEHMAN_2004",
        CitationStatus.VERIFIED, statusNote = "V (Andersen DOI: K)",
    )
    val PULL_UP = e(14, "Pull-Up / Assisted Pull-Up", setOf(LATS), setOf(RHOMBOIDS_TERES, TRAPS_MID_LOWER, BICEPS, BRACHIALIS), YOUDAS_2010, CitationStatus.KNOWN)
    val CHIN_UP = e(15, "Chin-up", setOf(LATS), setOf(BICEPS, BRACHIALIS, RHOMBOIDS_TERES), YOUDAS_2010, CitationStatus.KNOWN)
    val ROW_FREE = e(
        16, "Row (bent-over / free T-Bar)", setOf(LATS, TRAPS_MID_LOWER, RHOMBOIDS_TERES), setOf(DELT_REAR, BICEPS, BRACHIALIS, ERECTORS),
        "Fenwick CMJ, Brown SHM, McGill SM 2009, J Strength Cond Res 23(2):350-358, doi:10.1519/JSC.0b013e3181942019", CitationStatus.KNOWN,
    )
    val ROW_SUPPORTED = e(
        17, "Row (dumbbell, cable, machine, supported)", setOf(LATS, TRAPS_MID_LOWER, RHOMBOIDS_TERES), setOf(DELT_REAR, BICEPS, BRACHIALIS),
        LEHMAN_2004, CitationStatus.VERIFIED,
    )
    val DEADLIFT = e(
        18, "Deadlift", setOf(GLUTE_MAX, ERECTORS), setOf(HAMSTRINGS, QUADS, ADDUCTORS),
        "Escamilla RF et al. 2002, Med Sci Sports Exerc 34(4):682-688, doi:10.1097/00005768-200204000-00019; $NETO_2020",
        CitationStatus.KNOWN, statusNote = "K / V",
    )
    val ROMANIAN_DEADLIFT = e(
        19, "Romanian / stiff-leg deadlift", setOf(HAMSTRINGS, GLUTE_MAX), setOf(ERECTORS, ADDUCTORS),
        "McAllister MJ et al. 2014, J Strength Cond Res 28(6):1573-1580, doi:10.1519/JSC.0000000000000302", CitationStatus.KNOWN,
    )
    val BACK_EXTENSION = e(
        20, "Back extension / hyperextension", setOf(ERECTORS), setOf(GLUTE_MAX, HAMSTRINGS),
        "Callaghan JP, Gunning JL, McGill SM 1998, Phys Ther 78(1):8-18, doi:10.1093/ptj/78.1.8", CitationStatus.VERIFIED, statusNote = "V (DOI: K)",
    )
    val SQUAT = e(
        21, "Squat", setOf(QUADS, GLUTE_MAX), setOf(ADDUCTORS, ERECTORS),
        "Kubo K, Ikebukuro T, Yata H 2019, Eur J Appl Physiol 119(9):1933-1942, doi:10.1007/s00421-019-04181-y", CitationStatus.VERIFIED,
    )
    val FRONT_SQUAT = e(
        22, "Front squat", setOf(QUADS, GLUTE_MAX), setOf(ADDUCTORS, ERECTORS),
        "Gullett JC et al. 2009, J Strength Cond Res 23(1):284-292, doi:10.1519/JSC.0b013e31818546bb", CitationStatus.KNOWN,
    )
    val LEG_PRESS = e(
        23, "Leg Press", setOf(QUADS), setOf(GLUTE_MAX, ADDUCTORS),
        "Escamilla RF et al. 2001, Med Sci Sports Exerc 33(9):1552-1566, doi:10.1097/00005768-200109000-00020", CitationStatus.KNOWN,
    )
    val HACK_SQUAT = e(24, "Hack Squat / Reverse Hack Squat / pendulum", setOf(QUADS), setOf(GLUTE_MAX, ADDUCTORS), NO_STUDY, CitationStatus.CONSENSUS)
    val SPLIT_SQUAT = e(
        25, "Bulgarian split squat", setOf(QUADS, GLUTE_MAX), setOf(ADDUCTORS, GLUTE_MED),
        "Mackey ER, Riemann BL 2021, Int J Exerc Sci 14(1):533-543; $NETO_2020", CitationStatus.VERIFIED,
    )
    val LUNGE = e(
        26, "Lunge", setOf(QUADS, GLUTE_MAX), setOf(ADDUCTORS, GLUTE_MED),
        "Muyor JM et al. 2020, PLoS One 15(4):e0230841, doi:10.1371/journal.pone.0230841", CitationStatus.VERIFIED,
    )
    val LEG_EXTENSION = e(
        27, "Leg Extension", setOf(QUADS), emptySet(),
        "Ema R et al. 2013, Eur J Appl Physiol 113(11):2691-2703, doi:10.1007/s00421-013-2700-1", CitationStatus.KNOWN,
    )
    val LYING_LEG_CURL = e(28, "Leg curl (lying)", setOf(HAMSTRINGS), emptySet(), MAEO_2021, CitationStatus.KNOWN)
    val SEATED_LEG_CURL = e(29, "Seated Leg Curl", setOf(HAMSTRINGS), emptySet(), MAEO_2021, CitationStatus.KNOWN)
    val HIP_THRUST = e(
        30, "Hip Thrust", setOf(GLUTE_MAX), setOf(HAMSTRINGS, QUADS),
        "Contreras B et al. 2015, J Appl Biomech 31(6):452-458, doi:10.1123/jab.2014-0301; " +
            "Plotkin DL et al. 2023, Front Physiol 14:1279170, doi:10.3389/fphys.2023.1279170",
        CitationStatus.KNOWN, statusNote = "K / V",
    )
    val GLUTE_BRIDGE = e(31, "Glute bridge", setOf(GLUTE_MAX), setOf(HAMSTRINGS), NETO_2020, CitationStatus.VERIFIED)
    val HIP_ABDUCTION = e(
        32, "Hip Abduction", setOf(GLUTE_MED), setOf(GLUTE_MAX),
        "Reiman MP, Bolgla LA, Loudon JK 2012, Physiother Theory Pract 28(4):257-268, doi:10.3109/09593985.2011.604981",
        CitationStatus.KNOWN, statusNote = "K (indirect)",
    )
    val HIP_ADDUCTION = e(33, "Hip Adduction", setOf(ADDUCTORS), emptySet(), NO_STUDY, CitationStatus.CONSENSUS)
    val CALF_STANDING = e(34, "Calf Raise (standing, knee straight)", setOf(GASTROCNEMIUS, SOLEUS), emptySet(), KINOSHITA_2023, CitationStatus.VERIFIED)
    val CALF_SEATED = e(35, "Calf Raise (seated, knee bent)", setOf(SOLEUS), emptySet(), KINOSHITA_2023, CitationStatus.VERIFIED)
    val TIBIALIS_RAISE = e(36, "Tibialis raise", setOf(TIBIALIS), emptySet(), NO_STUDY, CitationStatus.CONSENSUS)
    val BICEPS_CURL = e(37, "Biceps Curl", setOf(BICEPS), setOf(BRACHIALIS), OLIVEIRA_2009, CitationStatus.KNOWN)
    val HAMMER_CURL = e(
        38, "Hammer Curl / Brachialis Curl", setOf(BRACHIALIS), setOf(BICEPS, FOREARM_EXTENSORS),
        "Kleiber T, Kunz L, Disselhorst-Klug C 2015, Front Physiol 6:215, doi:10.3389/fphys.2015.00215", CitationStatus.VERIFIED,
    )
    val PREACHER_CURL = e(39, "Preacher Curl", setOf(BICEPS), setOf(BRACHIALIS), OLIVEIRA_2009, CitationStatus.KNOWN)
    val TRICEPS_PUSHDOWN = e(40, "Triceps Pushdown", setOf(TRICEPS), emptySet(), MAEO_2023, CitationStatus.VERIFIED, statusNote = "V (DOI: K)")
    val OVERHEAD_TRICEPS = e(41, "Overhead Triceps Extension", setOf(TRICEPS), emptySet(), MAEO_2023, CitationStatus.VERIFIED)
    val LYING_TRICEPS = e(42, "Skull crusher / lying Triceps Extension", setOf(TRICEPS), emptySet(), NO_STUDY, CitationStatus.CONSENSUS)
    val WRIST_CURL = e(43, "Wrist curl", setOf(FOREARM_FLEXORS), emptySet(), NO_STUDY, CitationStatus.CONSENSUS)
    val WRIST_EXTENSION = e(44, "Wrist extension / reverse wrist curl", setOf(FOREARM_EXTENSORS), emptySet(), NO_STUDY, CitationStatus.CONSENSUS)
    val CRUNCH = e(45, "Crunch / Sit-Up", setOf(ABS), setOf(OBLIQUES), ESCAMILLA_2006, CitationStatus.VERIFIED, statusNote = "V (DOI: K)")
    val LEG_RAISE = e(46, "Hanging Leg Raise", setOf(ABS), setOf(OBLIQUES), ESCAMILLA_2006, CitationStatus.VERIFIED)
    val PLANK = e(
        47, "Plank", setOf(ABS), setOf(OBLIQUES),
        "Schoenfeld BJ et al. 2014, Sports Biomech 13(3):296-306, doi:10.1080/14763141.2014.942355", CitationStatus.KNOWN,
    )
    val ROTATION = e(48, "Russian twist / Pallof press", setOf(OBLIQUES), setOf(ABS), NO_STUDY, CitationStatus.CONSENSUS)

    /** Every seed row, in doc order. */
    val ALL: List<MuscleSeedEntry> = listOf(
        BENCH_BARBELL, BENCH_DUMBBELL, BENCH_INCLINE, BENCH_DECLINE, OVERHEAD_PRESS, PUSH_UP, DIP, FLY,
        LATERAL_RAISE, REAR_DELT_FLY, FACE_PULL, SHRUG, LAT_PULLDOWN, PULL_UP, CHIN_UP, ROW_FREE,
        ROW_SUPPORTED, DEADLIFT, ROMANIAN_DEADLIFT, BACK_EXTENSION, SQUAT, FRONT_SQUAT, LEG_PRESS,
        HACK_SQUAT, SPLIT_SQUAT, LUNGE, LEG_EXTENSION, LYING_LEG_CURL, SEATED_LEG_CURL, HIP_THRUST,
        GLUTE_BRIDGE, HIP_ABDUCTION, HIP_ADDUCTION, CALF_STANDING, CALF_SEATED, TIBIALIS_RAISE,
        BICEPS_CURL, HAMMER_CURL, PREACHER_CURL, TRICEPS_PUSHDOWN, OVERHEAD_TRICEPS, LYING_TRICEPS,
        WRIST_CURL, WRIST_EXTENSION, CRUNCH, LEG_RAISE, PLANK, ROTATION,
    )

    /** Canonical base keys ([ExerciseIdentityResolver.comparisonToken] of the canonical name). */
    private val byBaseKey: Map<String, MuscleSeedEntry> = mapOf(
        "lateral_raise" to LATERAL_RAISE,
        "rear_delt_fly" to REAR_DELT_FLY,
        "lat_pulldown" to LAT_PULLDOWN,
        "assisted_pull_up" to PULL_UP,
        "diagonal_row" to ROW_SUPPORTED,
        "shrug" to SHRUG,
        "leg_press" to LEG_PRESS,
        "hack_squat" to HACK_SQUAT,
        "reverse_hack_squat" to HACK_SQUAT,
        "pendulum_squat" to HACK_SQUAT,
        "leg_extension" to LEG_EXTENSION,
        "seated_leg_curl" to SEATED_LEG_CURL,
        "hip_thrust" to HIP_THRUST,
        "hip_abduction" to HIP_ABDUCTION,
        "hip_adduction" to HIP_ADDUCTION,
        "machine_dip" to DIP,
        "biceps_curl" to BICEPS_CURL,
        "hammer_curl" to HAMMER_CURL,
        "brachialis_curl" to HAMMER_CURL,
        "preacher_curl" to PREACHER_CURL,
        "triceps_pushdown" to TRICEPS_PUSHDOWN,
        "triceps_extension" to LYING_TRICEPS,
        "overhead_triceps_extension" to OVERHEAD_TRICEPS,
        "pec_deck" to FLY,
        "chest_fly" to FLY,
        "hanging_leg_raise" to LEG_RAISE,
        "sit_up" to CRUNCH,
        "squat" to SQUAT,
        "bulgarian_split_squat" to SPLIT_SQUAT,
    )

    /** Parser keywords for names the identity layer leaves as typed. First match wins. */
    private val keywordRules: List<Pair<List<String>, MuscleSeedEntry>> = listOf(
        listOf("romanian", "stiff leg", "stiff legged", "rdl") to ROMANIAN_DEADLIFT,
        listOf("deadlift") to DEADLIFT,
        listOf("back extension", "hyperextension") to BACK_EXTENSION,
        listOf("front squat") to FRONT_SQUAT,
        listOf("split squat", "bulgarian") to SPLIT_SQUAT,
        listOf("lunge") to LUNGE,
        listOf("hack squat", "pendulum") to HACK_SQUAT,
        listOf("seated leg curl", "seated hamstring") to SEATED_LEG_CURL,
        listOf("leg curl", "hamstring curl") to LYING_LEG_CURL,
        listOf("glute bridge") to GLUTE_BRIDGE,
        listOf("reverse wrist curl", "wrist extension") to WRIST_EXTENSION,
        listOf("wrist curl") to WRIST_CURL,
        listOf("tibialis", "tib raise") to TIBIALIS_RAISE,
        listOf("skull crusher") to LYING_TRICEPS,
        listOf("face pull") to FACE_PULL,
        listOf("reverse fly", "reverse pec deck") to REAR_DELT_FLY,
        listOf("chin up", "chinup") to CHIN_UP,
        listOf("push up", "pushup") to PUSH_UP,
        listOf("overhead press", "shoulder press", "military press") to OVERHEAD_PRESS,
        listOf("dip") to DIP,
        listOf("crunch") to CRUNCH,
        listOf("russian twist", "pallof") to ROTATION,
        listOf("plank") to PLANK,
        listOf("leg raise", "knee raise") to LEG_RAISE,
        listOf("pulldown") to LAT_PULLDOWN,
        listOf("squat") to SQUAT,
    )

    /**
     * The seed row for an exercise, or null when the doc does not cover it.
     *
     * @param name `exercises.name` (the canonical name, or the typed name for unknown exercises).
     * @param modifier the set row's modifier (`incline`, `seated`, `dumbbell` …), if any.
     */
    fun lookup(name: String, modifier: String? = null): MuscleSeedEntry? {
        val text = " " + ExerciseIdentityResolver.identityText(listOfNotNull(name, modifier).joinToString(" ")) + " "
        val keys = linkedSetOf(
            ExerciseIdentityResolver.comparisonToken(name),
            ExerciseIdentityResolver.resolve(rawName = name, modifier = modifier).canonicalId,
        )
        keys.forEach { key ->
            variant(key, text)?.let { return it }
            byBaseKey[key]?.let { return it }
        }
        // "Incline Dumbbell Press" never reaches the bench_press key: the press keywords decide.
        if (text.has("press") && !text.has("leg press")) {
            if (text.has("incline")) return BENCH_INCLINE
            if (text.has("decline")) return BENCH_DECLINE
        }
        if (text.has("calf") || text.has("calve")) return if (text.has("seated")) CALF_SEATED else CALF_STANDING
        return keywordRules.firstOrNull { (words, _) -> words.any { text.has(it) } }?.second
    }

    private fun variant(key: String, text: String): MuscleSeedEntry? = when (key) {
        "bench_press" -> when {
            text.has("incline") -> BENCH_INCLINE
            text.has("decline") -> BENCH_DECLINE
            text.has("dumbbell") || text.has("db") -> BENCH_DUMBBELL
            else -> BENCH_BARBELL
        }
        "calf_raise" -> if (text.has("seated")) CALF_SEATED else CALF_STANDING
        "pull_up" -> if (text.has("chin") || text.has("undergrip") || text.has("supinated")) CHIN_UP else PULL_UP
        // Free rows load the erectors; machine, cable, dumbbell and chest-supported rows do not.
        "row" -> if (text.has("bent over") || text.has("barbell") || text.has("pendlay")) ROW_FREE else ROW_SUPPORTED
        "t_bar_row" -> if (text.has("supported") || text.has("chest") || text.has("machine")) ROW_SUPPORTED else ROW_FREE
        else -> null
    }

    private fun String.has(words: String): Boolean = contains(" $words ")
}
