# Anatomy mapping: 26 regions, exercise weights and sources

Reviewer: exercise-science review for tickets #309 and #311 (2026-11-10). This is a literature-checked training-volume taxonomy, **not medical advice**.
Region ids are the 26 binding keys in `ANATOMY_SPEC.md`. Exercise names use GymTrack's canonical base names from `ExerciseIdentity.kt` (`Bench Press`, `Lateral Raise`, `Rear Delt Fly`, `Lat Pulldown`, `Pull-Up`, `Assisted Pull-Up`, `Row`, `T-Bar Row`, `Diagonal Row`, `Shrug`, `Leg Press`, `Hack Squat`, `Reverse Hack Squat`, `Leg Extension`, `Seated Leg Curl`, `Hip Thrust`, `Hip Abduction`, `Hip Adduction`, `Calf Raise`, `Machine Dip`, `Biceps Curl`, `Hammer Curl`, `Brachialis Curl`, `Preacher Curl`, `Triceps Pushdown`, `Triceps Extension`, `Overhead Triceps Extension`, `Pec Deck`, `Chest Fly`, `Hanging Leg Raise`, `Sit-Up`) plus WorkoutParser keywords (`deadlift`, `romanian`, `lunge`, `back extension`/`hyperextension`, `incline`, `decline`, `crunch`).

**Citation status legend**
- **V**: citation details checked online in this review session (2026-11-10).
- **K**: well-known paper cited from reviewer knowledge; details believed correct but not re-checked online this session. Check the DOI before showing it in the app.
- **C**: expert consensus or functional anatomy; **no direct study found**.

---

## 1. Region definitions (26 ids)

Plain-word origin -> insertion, borders the illustrator must respect, and views.

| # | id | Muscles included | Origin -> insertion (plain words) and borders to respect | Views |
|---|---|---|---|---|
| 1 | `deltFront` | anterior deltoid | Outer third of the collarbone -> deltoid bump halfway down the outer upper arm. Front of the shoulder cap. Medial border: the groove against the clavicular pec (deltopectoral groove). | Front |
| 2 | `deltSide` | lateral deltoid | Top outer edge of the shoulder blade (acromion) -> same deltoid bump. The outermost rounded cap. | Front + back (cap edge) |
| 3 | `deltRear` | posterior deltoid | Spine of the shoulder blade -> same deltoid bump. Back of the cap; lower border meets triceps long head and teres/infraspinatus. | Back |
| 4 | `chestUpper` | pectoralis major, clavicular head | Inner half of the collarbone -> front of the upper arm bone below the shoulder. Fibres run down-and-out. Lower border: a faint split from the sternal head at roughly the 2nd-rib level/sternoclavicular line. | Front |
| 5 | `chestLower` | pectoralis major, sternocostal (+abdominal) head | Breastbone, upper 6 rib cartilages and top of the abdominal sheath -> same humerus insertion, fibres fan up-and-out and twist under the clavicular head. Lower border = the lower pec fold. | Front |
| 6 | `serratus` | serratus anterior | Outer surface of ribs 1-8/9 (saw-tooth "fingers") -> inner edge of the shoulder blade. Visible only as 3-5 finger-like slips on the side of the ribcage below the armpit, between the pec/lat edge and the external oblique slips (which interdigitate with it). | Front |
| 7 | `trapsUpper` | upper (descending) trapezius | Skull base, neck ligament, upper neck vertebrae -> outer collarbone and acromion. The neck slope. | Front (neck slope) + back |
| 8 | `trapsMidLower` | middle + lower trapezius | Spines of C7-T12 -> spine of the shoulder blade. The lower diamond tip ends around T12; middle fibres run horizontally between the shoulder blades. | Back |
| 9 | `rhomboidsTeres` | rhomboid major/minor, teres major, teres minor, infraspinatus | Rhomboids: spines C7-T5 -> inner edge of the shoulder blade (lie **deep to** middle traps, mostly not visible). Infraspinatus: lower back face of the shoulder blade -> back of the humeral head. Teres minor/major: outer edge / lower angle of the shoulder blade -> humerus. Visible surface = the scapular area below the scapular spine, lateral to the trapezius edge, above the lat. | Back |
| 10 | `lats` | latissimus dorsi | Lower 6 thoracic spines, lumbar fascia, back of the pelvic crest, lower ribs -> front groove of the upper arm bone (wraps under the armpit with teres major). The V-wings; upper border tucks under the lower traps and teres major. | Front (lateral sliver) + back |
| 11 | `erectors` | erector spinae (iliocostalis, longissimus, spinalis), lumbar + thoracic | Back of the pelvis/sacrum -> ribs and vertebrae up the back. Two columns either side of the spine, most visible in the lumbar region; covered above by traps/lats. | Back |
| 12 | `biceps` | biceps brachii (long + short head) | Two tendons from the shoulder blade (above the socket / coracoid) -> radius tuberosity just below the elbow + forearm fascia. Front of the upper arm; ends ~3 finger-widths above the elbow crease. | Front |
| 13 | `brachialis` | brachialis (see grouping note: drop brachioradialis) | Lower half of the front of the humerus -> ulna just below the elbow. Seen as a strip lateral (and slightly medial) to the lower biceps, between biceps and triceps. | Front (+ lateral sliver) |
| 14 | `triceps` | triceps brachii, long + lateral + medial heads | Long head: below the shoulder socket; lateral/medial heads: back of humerus -> point of the elbow (olecranon). The horseshoe on the back of the arm. | Back |
| 15 | `forearmFlexors` | wrist/finger flexors + pronator teres (palmaris longus, FCR, FCU, FDS) | Inner elbow bump (medial epicondyle) -> palm side of wrist/fingers. Palm-side forearm, upper two-thirds bulky. | Front |
| 16 | `forearmExtensors` | wrist/finger extensors (ECRL/ECRB, EDC, ECU) + brachioradialis | Outer elbow bump and the ridge above it -> back of wrist/fingers; brachioradialis: ridge above the outer elbow -> thumb side of the wrist. Back of forearm plus the thumb-side "mobile wad", which is also visible on the front/outer edge. | Back (+ front lateral edge recommended) |
| 17 | `abs` | rectus abdominis | Pubic bone -> ribs 5-7 cartilage and lower sternum. Two vertical strips separated by the linea alba, 3 tendinous intersections (the "6-pack"), lateral border = linea semilunaris. | Front |
| 18 | `obliques` | external + internal obliques | Lower 8 ribs (interdigitating with serratus and lats) -> iliac crest and abdominal sheath. The flank between the abs' lateral border and the lats. | Front + back (flank) |
| 19 | `gluteMax` | gluteus maximus | Back of the pelvis, sacrum, coccyx -> IT band and the upper back of the femur. The large rounded mass; lower border = the glute fold (which is fat/skin, not exactly the muscle edge, which runs obliquely). | Back |
| 20 | `gluteMed` | gluteus medius + minimus (+ TFL) | Outer face of the pelvic wing -> outer knob of the femur (greater trochanter). TFL: front of the iliac crest -> IT band. Upper-outer hip, above and in front of the glute max. | Front (small, TFL/hip) + back |
| 21 | `quads` | rectus femoris, vastus lateralis, vastus medialis, vastus intermedius | Front of the pelvis (RF) / upper femur (vasti) -> kneecap -> shin tuberosity. Teardrop (VMO) above the inner knee; VL bulges on the outer thigh. Only RF crosses the hip. | Front |
| 22 | `adductors` | adductor longus, brevis, magnus, gracilis, pectineus | Pubic bone / ischium -> inner femur line (gracilis to inner shin). Inner thigh wedge; adductor magnus is visible from the back between hamstrings and gracilis. | Front + back (inner thigh) |
| 23 | `hamstrings` | biceps femoris (long + short), semitendinosus, semimembranosus | Sit bone (ischial tuberosity; BF short head from femur) -> outer side of the knee (fibula head) and inner side (tibia). Back thigh, splitting to both sides of the knee. | Back |
| 24 | `gastrocnemius` | gastrocnemius medial + lateral heads | Back of the femur just above the knee -> Achilles tendon -> heel. The two-headed calf "heart"; medial head lower and bigger. Crosses the knee. | Front (inner edge) + back |
| 25 | `soleus` | soleus | Back of the upper tibia and fibula -> Achilles -> heel. Lies under the gastrocnemius; visible either side of and below the gastroc heads. Does not cross the knee. | Front (edges) + back (lower sides) |
| 26 | `tibialis` | tibialis anterior | Outer upper tibia -> inner midfoot (medial cuneiform/1st metatarsal). Strip just outside the shin bone's sharp edge. | Front |

### Grouping review (keep / split / merge)

| Region | Concern | Recommendation | Reason |
|---|---|---|---|
| `brachialis` (+ brachioradialis upper) | Brachioradialis is split across two ids (13 and 16), so one muscle would take two fills. | **Keep id, remove brachioradialis from it; put all of brachioradialis in `forearmExtensors`** and let `forearmExtensors` show as a thin strip on the front-lateral forearm too. | Brachioradialis originates above the outer elbow with the radial extensor group (lateral compartment, radial nerve), and its whole belly is on the forearm. One muscle = one id keeps counts honest; hammer/reverse curls then credit `forearmExtensors`. Kleiber et al. 2015 show brachioradialis behaves differently from biceps by grip. |
| `rhomboidsTeres` | Mixes three functions: retractors (rhomboids), extensor/internal rotator with lats (teres major), external rotators (infraspinatus, teres minor). Rhomboids are mostly hidden under middle traps. | **Keep** (ids are binding; separating would add 2 regions). Draw it over the infraspinous fossa + lateral scapular border, and note in the region tooltip that rhomboids lie under the middle traps. | Every exercise that loads one of these loads at least one of the others (rows, pulldowns, rear-delt work). A split would add regions too small to draw (teres minor) or invisible (rhomboids). |
| `soleus` vs `gastrocnemius` | Could be merged as "calves". | **Keep split.** | Kinoshita et al. 2023 (MRI): standing calf raises grew gastrocnemius far more than seated (lat. gastroc 12.4% vs 1.7%) while soleus grew similarly. The split is what makes the seated/standing distinction visible. |
| `gluteMed` (+ TFL) | TFL is an anterior hip flexor/abductor; medius/minimus are lateral. | **Keep**, draw TFL as the small front-view part. | Same functional job in abduction exercises; TFL alone is too small to count separately. |
| `quads` (one region) | Rectus femoris is biarticular and grows less in squats (Kubo 2019) and more in leg extensions. | **Keep** one region. | 26-region budget; note the caveat in UI copy if desired. |
| `serratus` | Small, partly hidden. | **Keep**, ensure ≥ 6 px slips at width 360. | Distinct function (scapular protraction), pressing/push-up credit. |
| `trapsUpper` vs `trapsMidLower` | Fine. | **Keep.** | Shrugs vs rows/rear-delt work load them differently (Pizzari 2014; Ekstrom 2003). |
| Missing muscles | Hip flexors (iliopsoas), neck flexors, supraspinatus, subscapularis, quadratus lumborum, deep core. | **Do not add**; leave unmapped. | Not surface-visible or not commonly trained as a target; leaving them out avoids implying precision. |

---

## 2. Exercise -> region mapping

Weights: **P** = primary (1.0 set), **S** = secondary (0.5 set). Muscles that only stabilise isometrically (grip, lats in deadlift, abs in squat) are deliberately **not** counted (see section 3).

### Chest and pressing

| # | Exercise (canonical / parser terms) | Primary 1.0 | Secondary 0.5 | Justification | Source | Status |
|---|---|---|---|---|---|---|
| 1 | Bench Press, flat, barbell | `chestLower`, `chestUpper` | `deltFront`, `triceps` | At 0° the sternal/lower pec is most active and the clavicular head is substantially active; anterior delt and triceps assist. | Rodríguez-Ridao D et al. 2020, *IJERPH* 17(19):7339, https://doi.org/10.3390/ijerph17197339 | V |
| 2 | Bench Press, flat, dumbbell | `chestLower`, `chestUpper` | `deltFront`, `triceps` | Pec activity similar to barbell; triceps lower with dumbbells (still counted 0.5). | Saeterbakken AH, van den Tillaar R, Fimland MS 2011, *J Sports Sci* 29(5):533-538, https://doi.org/10.1080/02640414.2010.543916 | K |
| 3 | Bench Press, incline (≈30-45°), barbell/dumbbell | `chestUpper` | `chestLower`, `deltFront`, `triceps` | Clavicular pec peaks at 30°; above 45° anterior delt rises and pec drops (≥45° could make `deltFront` primary). | Rodríguez-Ridao et al. 2020 (above) | V |
| 4 | Bench Press, decline, barbell/dumbbell | `chestLower` | `chestUpper`, `triceps` | Decline favours the lower (sternocostal) pec; anterior delt demand drops. | Glass SC, Armstrong T 1997, *J Strength Cond Res* 11(3):163-167 (no DOI) | K |
| 5 | Overhead press (standing/seated, BB/DB/machine) | `deltFront` | `deltSide`, `triceps` | Anterior delt is the prime mover; medial delt and triceps assist; upper traps/serratus rotate the scapula (not counted). | Saeterbakken AH, Fimland MS 2013, *J Strength Cond Res* 27(7):1824-1831, https://doi.org/10.1519/JSC.0b013e318276b873 | K |
| 6 | Push-up | `chestLower` | `chestUpper`, `deltFront`, `triceps`, `serratus` | Pec/triceps activity comparable to bench at matched effort; push-up plus loads serratus. | Calatayud J et al. 2015, *J Strength Cond Res* 29(1):246-253, https://doi.org/10.1519/JSC.0000000000000589 ; serratus: Ludewig PM et al. 2004, *Am J Sports Med* 32(2):484-493, https://doi.org/10.1177/0363546503258911 | K |
| 7 | Dip (bar) / Machine Dip | `chestLower`, `triceps` | `deltFront` | Bar dips produce high pec major and triceps peak activation; anterior delt contribution not directly compared (consensus). | McKenzie A et al. 2022, *IJERPH* 19(20):13211, https://doi.org/10.3390/ijerph192013211 | V (delt: C) |
| 8 | Chest Fly (cable, dumbbell) / Pec Deck | `chestLower` | `chestUpper`, `deltFront` | Fly loads pec through horizontal adduction with less triceps; activity lower than bench in the top range but long-length loading. Low-to-high cable fly: make `chestUpper` primary. | Solstad TEJ et al. 2020, *J Sports Sci Med* 19(4):645-651, https://www.jssm.org/jssm-19-645.xml-abst (pec deck: extrapolated) | V |

### Shoulders

| # | Exercise | Primary 1.0 | Secondary 0.5 | Justification | Source | Status |
|---|---|---|---|---|---|---|
| 9 | Lateral Raise (DB/cable/machine) | `deltSide` | `trapsUpper` | Medial delt highest; upper trapezius active, especially at the top. Front delt also active but not counted (avoid inflating front delts). | Coratella G et al. 2020, *IJERPH* 17(17):6015, https://doi.org/10.3390/ijerph17176015 | V |
| 10 | Rear Delt Fly / reverse fly / reverse pec deck | `deltRear` | `rhomboidsTeres`, `trapsMidLower` | Prone horizontal abduction gives high posterior delt, infraspinatus/teres minor and middle-trap activity. | Reinold MM et al. 2004, *J Orthop Sports Phys Ther* 34(7):385-394, https://doi.org/10.2519/jospt.2004.34.7.385 ; Ekstrom RA et al. 2003, *JOSPT* 33(5):247-258, https://doi.org/10.2519/jospt.2003.33.5.247 | K (fly extrapolated from prone rehab exercises) |
| 11 | Face pull | `deltRear` | `rhomboidsTeres`, `trapsMidLower` | Horizontal abduction + external rotation + scapular retraction. **No direct face-pull EMG study found**; mapped from the components above. | no direct study found | C |
| 12 | Shrug | `trapsUpper` | — | Upper trapezius is the target; an upward-rotation shrug (30° abduction) also raises lower trap activity. | Pizzari T et al. 2014, *Clin Biomech* 29(2):201-205, https://doi.org/10.1016/j.clinbiomech.2013.11.011 | V (DOI: K) |

### Back pulls

| # | Exercise | Primary 1.0 | Secondary 0.5 | Justification | Source | Status |
|---|---|---|---|---|---|---|
| 13 | Lat Pulldown (any grip) | `lats` | `rhomboidsTeres`, `biceps`, `brachialis` | Lats are prime movers at all grip widths (wide slightly more lats/infraspinatus); elbow flexors assist. | Andersen V et al. 2014, *J Strength Cond Res* 28(4):1135-1142, https://doi.org/10.1519/JSC.0000000000000232 ; Lehman GJ et al. 2004, *Dyn Med* 3:4, https://doi.org/10.1186/1476-5918-3-4 | V (Andersen DOI: K) |
| 14 | Pull-Up / Assisted Pull-Up (pronated) | `lats` | `rhomboidsTeres`, `trapsMidLower`, `biceps`, `brachialis` | Lats dominant; lower trapezius higher in pull-up than chin-up. | Youdas JW et al. 2010, *J Strength Cond Res* 24(12):3404-3414, https://doi.org/10.1519/JSC.0b013e3181f1598c | K |
| 15 | Chin-up (supinated) | `lats` | `biceps`, `brachialis`, `rhomboidsTeres` | Biceps and pec more active than in pull-up; lats still dominant. | Youdas et al. 2010 (above) | K |
| 16 | Row: barbell bent-over / T-Bar Row (free) | `lats`, `trapsMidLower`, `rhomboidsTeres` | `deltRear`, `biceps`, `brachialis`, `erectors` | Scapular retraction + shoulder extension; free bent-over rows also load the erectors isometrically under high spinal load. | Fenwick CMJ, Brown SHM, McGill SM 2009, *J Strength Cond Res* 23(2):350-358, https://doi.org/10.1519/JSC.0b013e3181942019 | K |
| 17 | Row: dumbbell (one-arm), cable, seated machine, chest-supported T-Bar / Diagonal Row | `lats`, `trapsMidLower`, `rhomboidsTeres` | `deltRear`, `biceps`, `brachialis` | Seated row gave the highest middle-trap/rhomboid activity and a high lat:biceps ratio. Supported rows: no erector credit. | Lehman GJ et al. 2004, *Dyn Med* 3:4, https://doi.org/10.1186/1476-5918-3-4 | V |

### Hinge and lower back

| # | Exercise | Primary 1.0 | Secondary 0.5 | Justification | Source | Status |
|---|---|---|---|---|---|---|
| 18 | Deadlift (conventional / sumo / trap bar) | `gluteMax`, `erectors` | `hamstrings`, `quads`, `adductors` | Hip and back extensors dominate; sumo shifts toward quads/adductors, conventional toward erectors/hamstrings. Traps/lats/grip only isometric (not counted). | Escamilla RF et al. 2002, *Med Sci Sports Exerc* 34(4):682-688, https://doi.org/10.1097/00005768-200204000-00019 ; Neto WK et al. 2020, *J Sports Sci Med* 19(1):195-203 (PMC7039033) | K / V |
| 19 | Romanian / stiff-leg deadlift | `hamstrings`, `gluteMax` | `erectors`, `adductors` | Hamstrings loaded at long length; high hamstring EMG comparable to other hinges. | McAllister MJ et al. 2014, *J Strength Cond Res* 28(6):1573-1580, https://doi.org/10.1519/JSC.0000000000000302 | K |
| 20 | Back extension / hyperextension (45° or 90°) | `erectors` | `gluteMax`, `hamstrings` | Trunk-extension exercises produce the highest extensor activity (and spine load) of the tested extensor exercises. Hip-hinge "glute" version: swap to `gluteMax` primary. | Callaghan JP, Gunning JL, McGill SM 1998, *Phys Ther* 78(1):8-18, https://doi.org/10.1093/ptj/78.1.8 | V (DOI: K) |

### Knee-dominant legs

| # | Exercise | Primary 1.0 | Secondary 0.5 | Justification | Source | Status |
|---|---|---|---|---|---|---|
| 21 | Squat (back, barbell) | `quads`, `gluteMax` | `adductors`, `erectors` | MRI: full squats grew glute max (6.7%) and adductors (6.2%) and vasti; **hamstrings and rectus femoris did not grow**. Adductors 1.0 is defensible for full depth. | Kubo K, Ikebukuro T, Yata H 2019, *Eur J Appl Physiol* 119(9):1933-1942, https://doi.org/10.1007/s00421-019-04181-y | V |
| 22 | Front squat | `quads`, `gluteMax` | `adductors`, `erectors` | Similar muscle activity to back squat with lower compressive knee/spine forces. | Gullett JC et al. 2009, *J Strength Cond Res* 23(1):284-292, https://doi.org/10.1519/JSC.0b013e31818546bb | K |
| 23 | Leg Press | `quads` | `gluteMax`, `adductors` | Knee extensor dominant; glute contribution rises with deep hip flexion / high foot. | Escamilla RF et al. 2001, *Med Sci Sports Exerc* 33(9):1552-1566, https://doi.org/10.1097/00005768-200109000-00020 | K |
| 24 | Hack Squat (and Reverse Hack Squat, pendulum) | `quads` | `gluteMax`, `adductors` | **No direct hack-squat EMG/hypertrophy study found**; mapped from squat and leg-press mechanics (fixed back, more knee-forward). | no direct study found | C |
| 25 | Bulgarian split squat | `quads`, `gluteMax` | `adductors`, `gluteMed` | Hip-dominant joint-moment profile; split squats are in the "very high" glute max activation tier; glute med stabilises the pelvis on one leg. | Mackey ER, Riemann BL 2021, *Int J Exerc Sci* 14(1):533-543 (https://digitalcommons.wku.edu/ijes/vol14/iss1/10); Neto et al. 2020 (above) | V |
| 26 | Lunge (forward/walking/reverse) | `quads`, `gluteMax` | `adductors`, `gluteMed` | Vasti highest, then glute med/max in single-leg squat, lunge and step-up. | Muyor JM et al. 2020, *PLoS One* 15(4):e0230841, https://doi.org/10.1371/journal.pone.0230841 | V |
| 27 | Leg Extension | `quads` | — | Isolated knee extension; grows all four heads, including rectus femoris (which squats grow little). | Ema R et al. 2013, *Eur J Appl Physiol* 113(11):2691-2703, https://doi.org/10.1007/s00421-013-2700-1 | K |

### Hamstrings, glutes, hip

| # | Exercise | Primary 1.0 | Secondary 0.5 | Justification | Source | Status |
|---|---|---|---|---|---|---|
| 28 | Leg curl, lying | `hamstrings` | — | Knee-flexion isolation. Gastrocnemius assists knee flexion but is **not counted** (no hypertrophy evidence; avoids inflating calves). Short head of biceps femoris grows only from knee flexion. | Maeo S et al. 2021, *Med Sci Sports Exerc* 53(4):825-837, https://doi.org/10.1249/MSS.0000000000002523 | K |
| 29 | Seated Leg Curl | `hamstrings` | — | Seated (hip flexed, longer hamstrings) grew hamstrings more than lying (≈ +14% vs +9% in the same study). | Maeo et al. 2021 (above) | K |
| 30 | Hip Thrust | `gluteMax` | `hamstrings`, `quads` | Higher glute EMG than squat, but 9-week MRI showed similar glute growth to squats; less thigh growth. | Contreras B et al. 2015, *J Appl Biomech* 31(6):452-458, https://doi.org/10.1123/jab.2014-0301 ; Plotkin DL et al. 2023, *Front Physiol* 14:1279170, https://doi.org/10.3389/fphys.2023.1279170 | K / V |
| 31 | Glute bridge | `gluteMax` | `hamstrings` | Same hip-extension pattern as hip thrust, shorter range; bridges listed in the glute max EMG review. | Neto WK et al. 2020, *J Sports Sci Med* 19(1):195-203, https://pmc.ncbi.nlm.nih.gov/articles/PMC7039033 | V |
| 32 | Hip Abduction (machine, "abductor") | `gluteMed` | `gluteMax` | Glute med/min are the prime hip abductors; upper glute max fibres assist (more with forward lean). Machine data are sparse; evidence mostly from rehab abduction exercises. | Reiman MP, Bolgla LA, Loudon JK 2012, *Physiother Theory Pract* 28(4):257-268, https://doi.org/10.3109/09593985.2011.604981 | K (indirect) |
| 33 | Hip Adduction (machine, "adductor") | `adductors` | — | Adduction is the defining action of the group. **No direct machine EMG/hypertrophy study found.** | no direct study found | C |

### Calves and shin

| # | Exercise | Primary 1.0 | Secondary 0.5 | Justification | Source | Status |
|---|---|---|---|---|---|---|
| 34 | Calf Raise, standing (or leg-press calf raise, knee straight) | `gastrocnemius`, `soleus` | — | Knee-straight training grew gastrocnemius strongly; soleus grew similarly in standing and seated. | Kinoshita M et al. 2023, *Front Physiol* 14:1272106, https://doi.org/10.3389/fphys.2023.1272106 | V |
| 35 | Calf Raise, seated (knee bent ~90°) | `soleus` | — | Bent knee slackens gastrocnemius: it barely grew (0.6-1.7%) in seated training, so it is not credited. | Kinoshita et al. 2023 (above) | V |
| 36 | Tibialis raise | `tibialis` | — | Ankle dorsiflexion against load; tibialis anterior is the prime dorsiflexor. | no direct study found | C |

> App note: the canonical `Calf Raise` covers "calve machine/calf machine" too. Default to the **standing** mapping unless the text contains "seated".

### Arms and forearms

| # | Exercise | Primary 1.0 | Secondary 0.5 | Justification | Source | Status |
|---|---|---|---|---|---|---|
| 37 | Biceps Curl (DB/BB/cable/machine, supinated) | `biceps` | `brachialis` | Supinated elbow flexion; biceps is the main mover, brachialis always assists. | Oliveira LF et al. 2009, *J Sports Sci Med* 8(1):24-29 (https://www.jssm.org/jssm-08-24.xml-Fulltext) | K |
| 38 | Hammer Curl / Brachialis Curl (neutral or pronated grip) | `brachialis` | `biceps`, `forearmExtensors` | Brachioradialis contribution rises with neutral/pronated grip while biceps stays similar; brachioradialis credited under `forearmExtensors` per the grouping fix. | Kleiber T, Kunz L, Disselhorst-Klug C 2015, *Front Physiol* 6:215, https://doi.org/10.3389/fphys.2015.00215 | V |
| 39 | Preacher Curl | `biceps` | `brachialis` | Shoulder-flexed curl; biceps EMG pattern changes with shoulder position (peak earlier in range). | Oliveira et al. 2009 (above) | K |
| 40 | Triceps Pushdown (rope, bar, V-bar) | `triceps` | — | Elbow extension with the arm at the side. | Maeo S et al. 2023, *Eur J Sport Sci* 23(7):1240-1250, https://doi.org/10.1080/17461391.2022.2100279 | V (DOI: K) |
| 41 | Overhead Triceps Extension | `triceps` | — | Overhead position grew the long head ~1.5x more than neutral (28.5% vs 19.6%), all heads more. | Maeo et al. 2023 (above) | V |
| 42 | Skull crusher / lying Triceps Extension | `triceps` | — | Elbow extension with shoulder partially flexed; mapped by mechanics. **No direct hypertrophy study found** for skull crushers. | no direct study found | C |
| 43 | Wrist curl | `forearmFlexors` | — | Wrist flexion isolates the flexor group. | no direct study found | C |
| 44 | Wrist extension / reverse wrist curl | `forearmExtensors` | — | Wrist extension isolates the extensor group. | no direct study found | C |

### Core

| # | Exercise | Primary 1.0 | Secondary 0.5 | Justification | Source | Status |
|---|---|---|---|---|---|---|
| 45 | Crunch / cable crunch / Sit-Up | `abs` | `obliques` | Trunk flexion: rectus abdominis primary; obliques active. Sit-ups add hip-flexor work (rectus femoris not counted). Cable crunch: extrapolated. | Escamilla RF et al. 2006, *Phys Ther* 86(5):656-671, https://doi.org/10.1093/ptj/86.5.656 | V (DOI: K) |
| 46 | Hanging Leg Raise (knee/leg raise) | `abs` | `obliques` | Hanging knee-up with straps gave among the highest external oblique and rectus activity, with high rectus femoris (hip flexor) demand. | Escamilla et al. 2006 (above) | V |
| 47 | Plank | `abs` | `obliques` | Isometric anti-extension; rectus and obliques active, more with posterior pelvic tilt/long lever. Logged as time, not reps (see caveats). | Schoenfeld BJ et al. 2014, *Sports Biomech* 13(3):296-306, https://doi.org/10.1080/14763141.2014.942355 | K |
| 48 | Russian twist / Pallof press | `obliques` | `abs` | Rotation / anti-rotation is the obliques' main job. **No peer-reviewed EMG study found** (only a 4-subject Pallof pilot abstract, Int J Exerc Sci Conf Proc 8(9), 2021). | no direct study found | C |

### Citation tally (48 rows covering the ~40 requested exercises)

- **Direct study cited (EMG, biomechanics or MRI hypertrophy): 40 rows**, of which 22 rows rest on citations checked online this session (V, including 2 mixed V/K rows) and 18 on well-known citations from reviewer knowledge (K; verify DOIs before shipping in-app). Three of these (rear-delt fly, hip abduction, cable crunch/pec deck) are extrapolated from closely related exercises.
- **Expert consensus, no direct study found: 8 rows** — face pull, hack squat, hip adduction, tibialis raise, skull crusher, wrist curl, wrist extension, russian twist/Pallof (plus the dip's anterior-delt secondary).

---

## 3. Weighting notes and caveats

### Why 1.0 / 0.5
- Pelland et al. compared ways of counting sets in a meta-regression of 67 studies and found the **fractional** method (direct sets = 1, indirect sets = 0.5) gave the strongest relative evidence for predicting hypertrophy, so it is the method used here.
  - Preprint: Pelland J, Remmert J, Robinson Z, Hinson S, Zourdos M 2024, *SportRxiv*, https://doi.org/10.51224/SRXIV.460 (V)
  - Published: *Sports Medicine* 2026; 56(2):481-505, DOI reported as 10.1007/s40279-025-02344-w (V via index; check the suffix).
- The study also found diminishing returns per set, steeper for strength than for size. The app's levels (low 1-4, moderate 5-9, high 10+) are display bands, **not** targets derived from that paper.
- "Secondary" here means a muscle that does meaningful dynamic work through a real range of motion. **Isometric stabilisers are not counted** (grip in pulls, lats/traps in deadlifts, abs in squats, erectors in supported rows). Counting them would make every heavy day "high" for forearms and abs.
- Unilateral sets (`sideMode = UNILATERAL`) count as one set per side; do not double them.
- For long holds (plank) logged as time, count each hold as one set.

### Caveats to show or keep in mind
1. **EMG ≠ hypertrophy.** Surface EMG amplitude shows excitation, not growth. Plotkin 2023 is a direct example: hip thrusts had higher glute EMG than squats but similar glute growth. Where an MRI training study exists (Kubo 2019, Kinoshita 2023, Maeo 2021/2023, Plotkin 2023, Ema 2013), it was preferred over EMG.
2. **Most studies are small** (10-35 people), often untrained men, short (8-12 weeks). Effects in trained lifters may differ.
3. **Individual variation** in limb lengths, technique, grip width, depth, bench angle and range of motion changes the split, sometimes by a whole category (e.g. incline ≥45° shifts to front delts; deep vs half squat changes glute/adductor share; sumo vs conventional deadlift).
4. **Machines differ by brand** (Prime, Atlantis, Hammer Strength…); the mapping uses the base exercise, not the brand.
5. Regional growth within a muscle (e.g. upper vs lower pec) is less certain than whole-muscle growth; the chest split is supported mostly by EMG, not regional hypertrophy data.
6. The map is a **training-volume summary**, not an injury, pain or rehab tool.

### Top uncertainties
1. Chest split: flat bench credited 1.0 to both pec heads; regional hypertrophy evidence for upper vs lower pec is limited.
2. Adductors in squats (0.5 vs 1.0) and hamstrings in squats (0, per Kubo 2019) — depth-dependent.
3. Elbow-flexor credit from pulls (0.5 biceps + 0.5 brachialis on every row/pulldown) may overstate arm volume; and the brachioradialis move to `forearmExtensors` needs the lead's OK.
