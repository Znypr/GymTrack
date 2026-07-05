# Editor save pipeline

## Purpose

The editor has two persistence responsibilities with different cost and durability requirements:

1. preserve an in-progress draft quickly;
2. finalize a complete workout and refresh its derived statistics consistently.

These operations must not trigger export or external integration implicitly.

## Save triggers

| Trigger | Operation | Side effects |
| --- | --- | --- |
| New line | Draft save | Writes the compatibility note only |
| Exercise-mode flag change | Draft save | Writes the compatibility note only |
| Application or editor `ON_STOP` | Draft save | Writes the compatibility note only |
| Back navigation | Workout finalization | Writes the note and replaces derived set rows in one Room transaction |
| History export action | Explicit export | Writes CSV and reports success or failure to the user |

Category and learnings changes mark the editor dirty and are included in the next draft or completion request.

## Concurrency model

All editor writes pass through `EditorSaveCoordinator`.

- Writes are serialized with a coroutine `Mutex`.
- Every request receives a monotonically increasing revision.
- A queued draft that has already been superseded by a newer request is skipped.
- A finalization request is never dropped.
- The editor does not navigate away until finalization succeeds.
- A failed write keeps the editor open and displays a snackbar error.
- Completion mode blocks subsequent lifecycle autosaves from replacing the final snapshot.

This prevents slower older saves from completing after newer saves and overwriting current editor state.

## Draft persistence

Draft save calls `NoteRepository.saveNote` only. It intentionally does not:

- parse workout text;
- replace derived set rows;
- rebuild statistics;
- generate CSV;
- access public storage;
- perform network or Creator OS work.

## Workout finalization

Back navigation calls `WorkoutRepository.saveCompletedWorkout`.

The repository uses one Room transaction to:

1. upsert the compatibility note;
2. parse its complete workout text;
3. resolve exercise identities;
4. replace all derived set rows for that workout.

If any step fails, the transaction rolls back and navigation does not occur.

## Export boundary

CSV export remains an explicit selection action on the workout history screen. Export failure is propagated to the caller and shown to the user. Export failure cannot roll back or alter saved workout data.

## Validation

Automated tests cover:

- superseded queued drafts;
- serialization of an in-flight save followed by a newer save;
- finalization routing through the completion writer.

Runtime regression validation should cover:

1. enter several sets rapidly;
2. toggle exercise modes repeatedly;
3. background and reopen the app;
4. leave the editor immediately after an edit;
5. reopen the workout and verify the latest text, timestamps, flags, category, and learnings;
6. export from history and verify an export failure leaves the workout unchanged.
