# #178 real-history prediction findings

Source: private local analysis of a 224-workout GymTrack backup supplied during development.

Raw workout contents are intentionally not committed. This document records only aggregate findings needed to improve the prediction procedure.

## Data shape

- 224 saved workouts.
- Labels:
  - Push: 79
  - Pull: 78
  - Legs: 67
- Date range: 2025-07-03 to 2026-07-08.
- Canonical workouts in the backup were all stored as `PARTIAL`, even though they represent saved legacy history.
- Most records had `legacyMigrationStatus = MIGRATED`; some had `NEEDS_REVIEW`.

## Important implementation finding

A prediction source that only accepts `WorkoutStatus.COMPLETED` would ignore this real backup entirely.

Prediction history therefore needs to include:

1. canonical `COMPLETED` workouts; and
2. legacy-backed canonical workouts whose migration status is `MIGRATED` or `NEEDS_REVIEW`.

It should still exclude:

- draft workouts;
- pending legacy migrations;
- blank labels.

## Transition structure

Observed transitions:

```text
Push -> Pull: 74 / 79 transitions from Push = 93.7%
Pull -> Legs: 63 / 78 transitions from Pull = 80.8%
Legs -> Push: 63 / 66 transitions from Legs = 95.5%
Pull -> Push: 15 / 78 = 19.2%
Push -> Legs: 4 / 79 = 5.1%
Legs -> Pull: 3 / 66 = 4.5%
Push -> Push: 1 / 79 = 1.3%
```

Recent behavior is even cleaner: in the last 50 workouts, the sequence was exactly PPL:

```text
Push -> Pull: 16 / 16 = 100%
Pull -> Legs: 17 / 17 = 100%
Legs -> Push: 16 / 16 = 100%
```

## Backtest result

Using a simple deterministic transition predictor over saved labels:

- Coverage after minimal warmup: ~99%.
- Overall covered accuracy: ~88%.
- Accuracy after first 50 workouts: ~94%.
- Accuracy in recent months: approximately 100% from 2025-12 through 2026-07 in this backup.

This is strong enough to justify deterministic explainable prediction before any opaque ML.

## Procedure recommendation

Keep the first production predictor deterministic:

1. Load prediction-eligible saved history.
2. Normalize labels by category first, title second.
3. Use recent completed/saved history only.
4. Rank next-workout candidates by observed transitions from the latest label.
5. Use confidence based on support and sample size.
6. Keep reasons observational, not prescriptive.

Do not train an opaque model yet. The user history is already structured enough that a transparent transition model is easier to debug and probably sufficient.

## Next algorithm improvement

The next useful upgrade is a recency-weighted deterministic score:

```text
score(label) = transition support from latest label
             + recent-window transition support
             + least-recently-trained support
             + optional weekday/time support
```

Reasons should remain explainable:

```text
Likely next: Legs
Why:
- Your last saved workout was Pull.
- Pull was followed by Legs in 63/78 historical transitions.
- In the last 50 workouts, Pull was followed by Legs every time.
```

## Exercise-prefill insight

The backup also shows stable exercise order patterns. Useful future defaults:

### Push

High-coverage exercises:

- Pec Deck: 97.5%, median position 1
- Lateral raise: 97.5%, median position 5
- Triceps Pushdown: 91.1%, median position 3
- Bench press: 81.0%, median position 2

### Pull

High-coverage exercises:

- Rear Delt Fly: 94.9%, median position 4
- Lat pulldown: 88.5%, median position 1
- Row: 88.5%, median position 2
- Biceps Curl: 71.8%, median position 5

### Legs

High-coverage exercises:

- Leg extension: 98.5%, median position 4
- Seated Leg Curl: 74.6%, median position 1
- Hip Adduction: 70.1%, median position 5
- Leg press: 70.1%, median position 3
- Reverse Hack Squat: 67.2%, median position 2

This supports a later editable exercise-order prefill, but only after explicit user acceptance.
