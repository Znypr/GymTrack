# Editor save pipeline

## Purpose

The editor has two persistence responsibilities with different cost and durability requirements:

1. preserve an in-progress draft quickly;
2. finalize a complete workout and refresh its derived statistics consistently.

These operations must not trigger export or external integration implicitly.

## Save triggers

Which operation a save runs depends on the workout. The **running workout** is the one with the
Finish button (a new workout, or the one whose timer is active). A **past workout** is any other
one: finished workouts, and workouts left with Back that a newer workout has replaced.

| Trigger | Running workout | Past workout (or one already `COMPLETED`) |
| --- | --- | --- |
| New line (Enter) | Draft save | History-edit save |
| Exercise-mode flag change | Draft save | History-edit save |
| Application or editor `ON_STOP` | Draft save | History-edit save |
| Back navigation | Draft save (the timer keeps running) | History-edit save |
| Finish (running workout only) | Workout finalization | – |
| History export action | Explicit export | Explicit export |

Category and learnings changes mark the editor dirty and are included in the next save request.

Before #325, Back on a past workout only saved a draft, so Stats never saw corrections to old
workouts. Every save of a past workout now re-derives its Stats data. Past workouts are edited
rarely and only one workout is re-parsed per save, so this costs little. Stats is then correct
even when the app is killed before the user leaves the editor.

## Concurrency model

All editor writes pass through `EditorSaveCoordinator`.

- Writes are serialized with a coroutine `Mutex`.
- Every request receives a monotonically increasing revision.
- A queued draft that has already been superseded by a newer request is skipped.
- A finalization request is never dropped.
- The editor does not navigate away until finalization succeeds.
- A failed write keeps the editor open and displays a snackbar error.
- Completion mode blocks subsequent lifecycle autosaves from replacing the final snapshot.
- New workouts use one stable timestamp for timer state, draft identity, and final workout identity.

This prevents slower older saves from completing after newer saves and overwriting current editor state.

## Draft persistence

Draft save calls `NoteRepository.saveNote` only. It intentionally does not:

- parse workout text;
- replace derived set rows;
- rebuild statistics;
- generate CSV;
- access public storage;
- perform network or Creator OS work.

## Workout finalization (Finish)

Finish calls `WorkoutRepository.saveCompletedWorkout`. It runs in one Room transaction:

1. upsert the compatibility note;
2. parse its complete workout text and resolve exercise identities;
3. replace all derived `sets` rows for that workout;
4. write the canonical workout, exercises and sets with status `COMPLETED` and `ended_at` set
   from the Finish time, using the rule in `docs/WORKOUT_DURATION.md`;
5. enqueue the local training summary.

If any step fails, the transaction rolls back and navigation does not occur. Persisted timer
state is cleared after finalization succeeds and before navigation completes.

## History-edit save (past workouts, #325)

`EditorViewModel` sends drafts of a past workout to `WorkoutRepository.saveEditedWorkout`. The
rule is `shouldRefreshDerivedDataOnSave`: not the running workout, or canonical status
`COMPLETED`. `saveEditedWorkout` runs in one Room transaction. It saves the note, replaces the
derived `sets` rows and rewrites the canonical rows, through `CanonicalImportSource.merge`:

- the completion is never changed: status is never lowered, and a `COMPLETED` row keeps its
  `ended_at` (the Finish time) and `created_at`;
- `updated_at` moves to the edit time;
- no training summary is enqueued, because that stays tied to the explicit Finish.

If the re-derivation fails, the text is still saved as a plain draft, so nothing typed is lost.
The error is not rethrown, because Back would then fail every time and trap the user in the
editor. The derived rows are re-derived on the next successful save of that workout.

## Export boundary

CSV export remains an explicit selection action on the workout history screen. Export failure is propagated to the caller and shown to the user. Export failure cannot roll back or alter saved workout data.

## Validation

Automated tests cover:

- superseded queued drafts;
- serialization of an in-flight save followed by a newer save;
- finalization routing through the completion writer;
- which saves re-derive a past workout (`HistoryEditRefreshPolicyTest`);
- `COMPLETED` rows keeping their Finish time on re-derivation (`CanonicalMergeFinishTimeTest`);
- on Room (instrumented, `WorkoutRepositoryEditedWorkoutTest`): a finished workout edited later
  gets new `sets` rows and keeps `COMPLETED` and its Finish time;
- timer duration, pause, resume, and stable workout switching.

Deferred runtime validation is tracked in #165 and #166.
