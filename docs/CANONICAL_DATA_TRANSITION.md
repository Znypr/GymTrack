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

## Completion write

`WorkoutRepository.saveCompletedWorkout` (Finish in the editor) projects the note with the same
`LegacyWorkoutProjector` and writes the canonical workout itself, in the same transaction as the note,
with `status = COMPLETED` and `updated_at = completedAt`. It also queues the training-summary outbox entry.

## Import rule (startup, `CanonicalImportRunner`)

The runner starts on every cold start, so it must cost almost nothing when nothing changed and must
never undo the completion write (bug #299).

- **Source stamp.** Every projected workout starts its `legacy_migration_message` with
  `canonical-import-v1 src=<hash>`, optionally followed by `: <review reasons>`. The hash is the first
  16 hex characters of a SHA-256 over the import version and every note field the projector reads:
  timestamp, title, text, `rowMetadata`, learnings, normalized category name and category color.
  No schema change; the note text and `rowMetadata` formats are unchanged.
- **Skip before projecting.** The runner reads the legacy-backed workout rows once. A note whose hash
  equals the stamp on its row is skipped without parsing, without reading exercise or set rows and
  without any write. A launch with no changed notes performs zero canonical writes.
- **Never downgrade.** When a note does need work, the stored row is re-read inside the import
  transaction (so a Finish that raced the import wins) and merged: content comes from the new
  projection, but status never goes down (`DRAFT` < `PARTIAL` < `COMPLETED`; unknown statuses are
  kept), `created_at` never moves later and `updated_at` never moves back. A completed workout edited
  later keeps `COMPLETED` and its completion time while its sets follow the edited note.
- **Smallest write.** If the projected exercise and set rows equal the stored ones, only the workout
  row is updated (for example to add a stamp to a row written before stamps existed; reported as
  `restamped`). Otherwise the workout row is written and its exercise and set rows are replaced.
- **Invalidation.** Changing `CANONICAL_IMPORT_VERSION` invalidates every stamp and re-projects once.
  Changes to the legacy exercise catalog or aliases alone do not re-project unchanged notes.

Known gap: rows that were already downgraded to `PARTIAL` by builds before this rule are not
restored to `COMPLETED`; the training-summary outbox still records which workouts were completed.

JVM and emulator tests verify deterministic keys, ordering, flags, timing, review behavior, and idempotence. `CanonicalImportRunnerIdempotenceTest` (JVM, in-memory store) covers completion followed by two imports with zero writes, pre-stamp rows, edited completed workouts and a Finish racing the import; `CanonicalImportCompletionTest` (instrumented) repeats the completion case on Room.
