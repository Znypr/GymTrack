# Canonical data transition

The version 9 canonical tables exist beside the legacy workout tables.

`CanonicalImportRunner` reads legacy records, creates deterministic canonical projections, and writes changed aggregates in one Room transaction.

Rules:

- legacy rows are never deleted;
- raw note text is retained;
- exercise and set order is explicit;
- only explicit weight units are stored;
- uncertain structure is marked `NEEDS_REVIEW`;
- an exact second run creates no duplicate rows;
- the current projection version is `canonical-import-v1`;
- imported historical workouts use status `PARTIAL` because completion state is unavailable.

JVM and emulator tests verify deterministic keys, ordering, flags, timing, review behavior, and idempotence.
