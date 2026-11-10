# GymTrack (ZnyprFit) R8 rules for release and releaseCheck builds (#337).
#
# Most of the app needs no rules:
# - Room 2.6 ships consumer rules that keep the generated *_Impl database/DAO classes
#   it loads by name, and the DAO code is generated at compile time (no reflection).
# - Compose, Navigation, Lifecycle and DataStore ship their own consumer rules.
# - Backup and cache JSON use org.json (an Android framework class, never shrunk) with
#   hand-written field names, so no data class needs to keep its member names.
#
# What does need a rule: enum constant NAMES that are written to storage and read back.
# The app persists Enum.name into Room columns (WorkoutStatus, ExerciseMode, WeightUnit,
# LegacyMigrationStatus, ExerciseFlag), backups and the stats cache (TimeRange,
# MetricImpact), then restores them with valueOf()/enumValues().firstOrNull { it.name == x }.
# A renamed or unboxed enum would silently fall back to defaults and corrupt user data.
# Keeping every app enum costs a few hundred bytes and removes that whole class of bug.
-keep enum com.example.gymtrack.** { *; }

# Readable stack traces in Play Console / Android Vitals after mapping.txt is applied.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
