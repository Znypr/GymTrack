# Anatomy spec: 26-region muscle map (lead decision, 2026-11-10)

Tickets: #309 (taxonomy and sourced mapping), #311 (anatomically accurate map). User decisions:
- ~26 regions;
- teal ramp + hatching (Normal) and pencil shading (Notebook);
- neutral weekly levels: none 0 / low 1–4 / moderate 5–9 / high 10+;
- the map must be "scientifically accurate", not the ellipse placeholder.

## Region IDs (use exactly these keys everywhere)
| # | id | Muscle(s) | Front view | Back view |
|---|---|---|---|---|
| 1 | `deltFront` | anterior deltoid | ✓ | |
| 2 | `deltSide` | lateral deltoid | ✓ (cap edge) | ✓ (cap edge) |
| 3 | `deltRear` | posterior deltoid | | ✓ |
| 4 | `chestUpper` | pectoralis major, clavicular head | ✓ | |
| 5 | `chestLower` | pectoralis major, sternocostal head | ✓ | |
| 6 | `serratus` | serratus anterior | ✓ | |
| 7 | `trapsUpper` | upper trapezius | ✓ (neck slope) | ✓ |
| 8 | `trapsMidLower` | middle + lower trapezius | | ✓ |
| 9 | `rhomboidsTeres` | rhomboids, teres major/minor, infraspinatus | | ✓ |
| 10 | `lats` | latissimus dorsi | ✓ (lateral sliver) | ✓ |
| 11 | `erectors` | erector spinae (lumbar/thoracic) | | ✓ |
| 12 | `biceps` | biceps brachii | ✓ | |
| 13 | `brachialis` | brachialis only (brachioradialis → forearmExtensors, science review) | ✓ (lateral to biceps) | |
| 14 | `triceps` | triceps brachii (all heads) | | ✓ |
| 15 | `forearmFlexors` | wrist/finger flexors | ✓ | |
| 16 | `forearmExtensors` | wrist/finger extensors + brachioradialis | ✓ (thin lateral strip = brachioradialis) | ✓ |
| 17 | `abs` | rectus abdominis | ✓ | |
| 18 | `obliques` | external/internal obliques | ✓ | ✓ (flank) |
| 19 | `gluteMax` | gluteus maximus | | ✓ |
| 20 | `gluteMed` | gluteus medius/minimus (+TFL) | ✓ (hip, small) | ✓ |
| 21 | `quads` | quadriceps (4 heads, one region) | ✓ | |
| 22 | `adductors` | adductor group | ✓ (inner thigh) | ✓ (inner thigh) |
| 23 | `hamstrings` | biceps femoris, semitendinosus, semimembranosus | | ✓ |
| 24 | `gastrocnemius` | gastrocnemius | ✓ (inner edge) | ✓ |
| 25 | `soleus` | soleus | ✓ (edges) | ✓ (lower sides) |
| 26 | `tibialis` | tibialis anterior | ✓ | |

A muscle visible in both views uses the **same id**, so both regions take the same fill.

## Component API (for `project/Anatomy.dc.html`)
- Props:
  - `levels`: object id → 0..3 (0 none, 1 low, 2 moderate, 3 high); a missing key is none. A special value `-1` = unmapped (dotted outline);
  - `palette`: `"teal"` | `"pencil"`;
  - `width` (int, default 360);
  - `view`: `"both"` | `"front"` | `"back"`;
  - `legend` (boolean);
  - `labels` (boolean): when true, region names with leader lines, for the review board;
  - `highlight` (region id, optional): thicker outline on one region (the "next muscle" guidance).
- **Teal fills:**
  - none: #3A3A3C fill + #6C6C70 outline;
  - low: hatch, #1A8A7C lines on #0E3F39;
  - moderate: dense hatch / cross-hatch in #23B3A0;
  - high: solid #2DD4BF.
  - Body silhouette #1A1A1C, non-muscle parts (head, hands, knees, feet) #232325.
- **Pencil fills:**
  - none: paper #FBF8EF with graphite outline #2B2A28;
  - low: sparse 40° hatch;
  - moderate: denser hatch;
  - high: cross-hatch #2B2A28 on #D9D3C4.
  - Silhouette outline graphite, paper inside.
- The legend shows four swatches (none, low, moderate, high) with the words. Pattern always differs, never colour alone.
- Accessibility: `role="img"` + `aria-label` listing the regions at each level.

## Accuracy requirements
- Proportions: an adult figure ~7.5 heads tall, athletic but not bodybuilder-exaggerated, gender-neutral.
- Shapes follow real muscle bellies and borders:
  - pec fibres fan from sternum/clavicle to the humerus, upper (clavicular) above the lower head;
  - three distinct deltoid heads (front, side and rear) over the shoulder;
  - lats as the V-shaped wings from the lower back to the armpit, visible as a sliver at the front;
  - traps as a diamond from the skull base to the mid-back, split upper and mid/lower;
  - rhomboids/teres between the shoulder blades and the lateral scapular border;
  - erectors as twin columns beside the spine;
  - biceps on the front of the upper arm, brachialis visible laterally;
  - triceps horseshoe on the back of the arm;
  - forearm flexors on the palm side, extensors on the back;
  - rectus abdominis in segments with the linea alba, obliques on the flank;
  - glute max as the large rounded mass, glute med above it on the upper outer hip;
  - quads as the teardrop/vastus shapes over the front thigh;
  - adductors as the inner thigh wedge;
  - hamstrings on the back thigh splitting to both sides of the knee;
  - gastrocnemius as the two heads forming the calf "heart", soleus beneath and beside it, tibialis along the front shin.
- Separations as thin dark (Normal) or ink (Pencil) lines, so adjacent regions read as distinct even at low level.
- Original artwork drawn from scratch in SVG paths. No tracing or copying of any existing atlas, app or stock image.
- Minimum size (lead decision after science review fix 13): the **largest piece** of each id must be ≥6 px at the rendered size. At `view="both"` width 360, the ids below that are brachialis, deltSide, serratus and tibialis. They get (a) an enlarged invisible hit area (≥24 dp) in the app and (b) an entry in the accompanying text list, so they're never reachable by colour/shape alone. Single-view mode (`view="front"`/`"back"`, scale 0.9) is preferred wherever space allows.

