# Stats rules

What each Stats number counts, and how weights are compared. Code: `feature/stats/StatsCalculator.kt`,
`StatsUnits.kt`, `StatsChartModels.kt`, progress SQL in `SetDao`.

## Which workouts count (#322)

Stats counts **finished** workouts only. A workout is finished when either:

- its canonical row is `COMPLETED` (the user tapped Finish), or
- it has derived rows in the legacy `sets` table. These are written only at Finish, by import
  (which goes through Finish), or by the one-time legacy repair for old databases.

A draft left with Back and never finished is **not** counted in Workouts, Weekly Avg, Avg Sets,
Favorite, Training Insights, Weekly Consistency, Workout Duration or the time-of-day heat map. The
Exercise Progress chart reads the `sets` table, so it already holds finished workouts only. All
cards use the same set of workouts.

Unfinished workouts stay visible: the Workouts badge shows "+N unfinished, not counted" when the
selected range holds any.

Known limit: in a database from before completion tracking, the legacy repair wrote `sets` rows for
every note, so an old draft from that time counts as finished. There is no record to tell them apart.

## Weight units (#326)

- **Stored unit.** Each set's unit is the one it was typed in: the explicit `kg`/`lb` in the text, or
  else the default unit in force at Finish (see docs/WEIGHT_UNITS.md). Stats reads it from the legacy
  `sets` rows of that workout, which were written at Finish.
- **Display unit.** Stats shows every weight in the user's current default unit
  (`Settings.defaultWeightUnit`). Changing the default changes the display only, never stored data.
- **Convert first, then aggregate.** Every set is converted to the display unit (1 lb = 0.45359237 kg)
  before any sum, average or comparison. Raw numbers are never added across units.
- **Progress SQL** returns kilograms: each `sets` row is multiplied by 0.45359237 when its
  `weightUnit` is `LB`. The chart converts kilograms to the display unit and labels the axis with it.
- **Training Insights** label Strength with the display unit.
- **Unknown unit.** If a set's unit cannot be known (no stored rows, or stored rows that do not line
  up with the text and mix units), it is left out of Strength, and the row says how many sets were
  left out. It is never guessed.
- **Missing load is not a drop.** Strength averages only workouts with a known load. When one side of
  the comparison has no known load, Strength is shown as neutral, not as a decline.

## Timed sets (#283)

A timed set is a set row like `60s` (a 60 s hold) or `45s 10` (a 45 s hold with 10 units of load),
see docs/EDITOR_BEHAVIOUR.md. The parser stores its time in `durationSeconds` and gives it 0 reps.

- **They are sets.** Timed sets count in Avg Sets, Favorite/top exercises, and the Training
  Insights set counts (Density, Depth). The Stats screen shows a "Timed sets" badge with their
  number and total hold time when the range holds any.
- **They have no reps.** They add nothing to Reps, and they are never part of Strength (load per
  rep). A timed set is never treated as 0 kg × 0 reps, and a workout whose only sets of an exercise
  are timed gives no point on the weight chart (no fake 0 kg point).
- **Progress chart.** Timed sets of an exercise get their own chart, in one honest unit per
  exercise:
  - `s`: average hold per timed set in that workout. Used when any timed set of the exercise has no
    load (or a load of unknown unit).
  - `kg·s` / `lb·s`: average load × seconds per timed set, load converted to the display unit
    first. Used only when every timed set of the exercise has a known load.
  - An exercise with both rep sets and timed sets shows the weight chart and, under it, the
    timed chart. Seconds are never put on a kg axis or mixed into a kg series.
- Series line up with the weight chart's variants: exercise identity is resolved from the stored
  name, modifier, brand and side the same way the progress query does.

## Cache and speed (#303)

- **Parse once.** A Stats load parses each note at most once. Parsed results (digests) are
  memoized by timestamp, text, row metadata and stored units, so switching the range or the display
  unit parses nothing again. Parsing is spread over up to 4 cores, off the main thread.
- **No work at startup.** The Stats ViewModel is created with the navigation host, but it computes
  nothing until the Stats screen collects its state (#126).
- **Cache key.** Range, display unit, a signature of every note (timestamp, text, metadata,
  category) and a signature of the finished/unit facts, plus the day for ranges other than All
  Time. Every part is stable across app restarts (String hashes and numbers only; enum
  `hashCode()` is an identity hash and must never be in a key).
- **Memory, then disk.** One engine per process keeps the last 8 results in memory; finished
  builds are also persisted (`StatsPersistentCacheStore`, SharedPreferences). The write finishes
  even if the screen is closed right after the result is shown. One persisted entry per range +
  unit: a newer history replaces the older entry. Note text is not persisted; the counted notes are
  re-attached from the live list after a read.
- `StatsPersistentCacheStore.CACHE_VERSION` is 3. Entries of older versions are deleted on the
  next write.
