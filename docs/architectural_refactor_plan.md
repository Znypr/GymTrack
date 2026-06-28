# Architectural Refactor Plan

This document outlines the refactor plan for GymTrack based on the architectural assessment. It summarises the major changes implemented to modernise the codebase and improve data integrity, performance, and maintainability.

## Summary of Changes

- **Canonical data model**: Introduced typed entities (`Workout`, `WorkoutExercise`, `WorkoutSet`, `Exercise`, `Category`) and `WorkoutDraft` to replace note‐like text as the single source of truth【182555096927973†L764-L779】.
- **Atomic transactions**: Implemented a single transactional save operation using Room’s `@Transaction` to persist a complete workout aggregate (metadata, exercises, sets), eliminating partial writes【986744391719321†L586-L609】.
- **Autosave & concurrency**: Added a `Mutex` to serialise draft saves and track revisions, preventing race conditions during autosave【816962193347595†L51-L57】【816962193347595†L63-L69】. Autosave now writes only the draft; finalisation is a separate, explicit action.
- **Separated pipelines**: Distinct flows for autosave, finalisation and export. Export uses a `WorkoutExporter` and returns explicit success/failure results instead of silently writing files【123501780859170†L1096-L1115】.
- **Statistics via SQL**: Statistics are computed using typed SQL queries rather than reparsing raw text, as per the recommendation to query Room tables directly【957880779882198†L1306-L1312】.
- **Stable identifiers and normalised schema**: Workouts now use generated UUIDs rather than timestamps for primary keys, and all tables have explicit foreign keys, unique indices and ordering fields【335946457138050†L556-L563】.
- **AppContainer and use cases**: Introduced an `AppContainer` to provide dependencies and encapsulate repositories. Use cases (`SaveDraftUseCase`, `FinalizeWorkoutUseCase`, `ExportWorkoutUseCase`) centralise business logic【123501780859170†L1156-L1170】.
- **Improved error handling**: Export operations return typed result models (`Success`/`Failure`) so the UI can display messages appropriately. Navigation no longer handles import/export or error handling.
- **Settings and state holders**: ViewModels now expose immutable state via flows and only persist settings in response to user actions, following DataStore guidelines【123501780859170†L1121-L1125】.

## Code Skeleton

A code skeleton implementing these changes has been provided separately as `gymtrack_code.zip` in the ChatGPT conversation (file id: file-Gra6vqN62eWiJLtD4YASwq). This archive contains:

- Domain models for the canonical workout schema.
- Room entities and DAOs with foreign keys and aggregate queries.
- Repository interfaces and implementations with draft storage, atomic transactions and mutex serialisation.
- Use case classes encapsulating autosave, finalisation and export.
- AppContainer and ViewModels demonstrating how to wire the architecture.
- A stub `WorkoutExporter` with explicit result types.

Developers can use this skeleton as a starting point for integrating the new architecture into the existing GymTrack codebase. The recommended next step is to merge the `architectural-refactor` branch and incrementally replace legacy components with the new modules.
