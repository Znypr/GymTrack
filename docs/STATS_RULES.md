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

## Cache

The persisted Stats cache key includes the display unit and a signature of the finished/unit facts.
`StatsPersistentCacheStore.CACHE_VERSION` is 2. Entries built under the old rules are ignored.
