# ADR 0003: Notebook Import Processing Architecture

**Status:** Proposed  
**Date:** 2026-07-09  
**Issue:** #177  
**Related:** #125, #176

## Context

GymTrack needs a path for importing handwritten notebook history without turning photos or OCR output into a second workout database.

The parent issue requires:

- user confirmation before canonical workout writes;
- clear distinction between source images, extracted text, confidence data, review state, and canonical workouts;
- explicit unit handling;
- duplicate detection;
- resumable batches;
- privacy, retention, and diagnostics behavior;
- compatibility with the canonical workout model and typed editor/import state.

The current architecture already treats canonical workout, exercise, occurrence, set, exercise alias, and category data as typed storage rather than note text. Notebook import must feed that model only after review.

## Decision

Use a review-first, hybrid-capable import pipeline.

```text
Notebook images
  -> page draft records and fingerprints
  -> preprocessing and page ordering
  -> recognition output with confidence and provenance
  -> proposed workout drafts
  -> user review and exercise mapping
  -> explicit confirmation
  -> single canonical import transaction
```

Recognition may be implemented on-device first, with optional cloud or hybrid processing later. Any path that sends source images or extracted notebook content off-device must require explicit user consent before processing.

The import draft model is separate from canonical storage. It may store unknown values, low-confidence values, rejected values, source text, and provenance. Canonical workout history can only be written from fully confirmed draft workouts.

## Processing architecture

### Default path

The default product behavior is local-first:

- normal workout logging remains fully offline;
- notebook import can exist as an optional migration flow;
- failed recognition must not affect existing workouts;
- no source image leaves the device without explicit opt-in.

### Optional external path

A more accurate external/AI path can be added behind explicit consent. The first implementation must keep processing location visible in the import session state so the UI can explain whether processing is on-device or external.

External processing is not a canonical data source. It only produces proposed draft data for review.

### Recognition provider boundary

Recognition providers return page-level text lines with confidence and provenance. They do not interpret workouts, map exercises, or write canonical data.

Provider descriptors declare processing location. On-device providers may run without external consent. Cloud or hybrid providers are blocked unless the import consent explicitly allows external processing.

A deterministic fixture-line provider exists only for tests and representative sample evaluation. It is not OCR and is not a product recognition implementation.

## Privacy and retention

The initial retention policy is explicit and conservative:

- source images default to `DELETE_AFTER_CONFIRMATION`;
- users may choose to keep source images until manual deletion if an audit trail is wanted;
- users may choose not to store source images after extraction;
- extracted text, confidence values, and draft data are deletion targets separate from canonical workouts;
- deletion actions for source and intermediate import data must never delete canonical workouts;
- diagnostics exclude raw notebook images and extracted text by default.

Consent copy must state where processing happens, how source images are retained, and that existing workout history is unchanged until review and confirmation.

## Review and correction model

Each recognized field carries:

- proposed value;
- confidence score;
- review state;
- page and line provenance;
- original source text when available.

Low confidence alone does not write data. A low-confidence value can become importable only after user confirmation or correction.

Exercise names are resolved separately from recognized text. A proposed exercise may be matched to an existing exercise by canonical name or alias, proposed as a new exercise, or left unresolved when ambiguous. Matching proposals are never confirmed automatically.

## Canonical import rules

A notebook workout can be written to canonical storage only when:

- the batch-level review state is confirmed;
- the workout-level review state is confirmed;
- the workout date/start time is confirmed;
- every exercise is confirmed;
- every exercise mapping is resolved;
- every set selected for import has confirmed performance data;
- weighted sets have an explicit known unit, not `UNKNOWN`;
- all referenced source pages belong to the same import batch.

The write itself must be one transaction. Partial failure must leave canonical history unchanged.

## Duplicate and resumability strategy

The import batch tracks source page fingerprints before workout interpretation. Duplicate page fingerprints must be resolved before import continues.

### Source page fingerprints

The first duplicate gate uses SHA-256 over exact source bytes. This catches accidental re-selects of the same captured or uploaded image before recognition starts.

Exact-byte fingerprints are not a full visual duplicate detector. A rotated crop, recompressed image, screenshot, or second photo of the same notebook page may produce a different SHA-256 value. Perceptual image matching and canonical workout duplicate checks are later layers.

Page intake assigns draft page positions from the user-provided source order. Later page-ordering work may propose a corrected order from dates, page numbers, or recognition output, but the original upload order remains available as provenance.

### Resumable batch state

The first resumability layer is a pure state model, not persistence. It records:

- batch status;
- per-page processing status;
- processed and failed page counts;
- whether the batch can resume;
- whether the batch can become ready for canonical import.

Recoverable page failures keep the batch resumable and return it to review. Fatal batch failures and user cancellation are terminal. Marking a batch ready or imported is rejected unless pages are processed and every import draft is confirmed.

### Page preprocessing and ordering

Preprocessing records deterministic page metadata and review hints without mutating source images. It can capture dimensions, orientation, rotation, quality issues, detected page numbers, and detected dates.

Page ordering produces proposals, not automatic canonical decisions. Detected page numbers sort before dates; dates sort before original upload order. Reordered pages and low-confidence ordering proposals require review. Original upload order remains available as provenance.

### Text interpretation

The first text interpreter consumes recognized page lines and produces reviewable workout drafts. It recognizes only fixture-safe date, title, and simple set-line patterns. Missing dates, unknown weights, unknown repetitions, unknown units, unresolved exercise modes, and unmatched exercises remain unresolved.

Interpreter warnings identify page and line positions without including raw notebook text. This keeps diagnostics useful without leaking notebook content by default.

### Exercise matching, duplicates, and fixture metrics

Exercise matching uses normalized canonical names and aliases to propose reviewable mappings. Exact single matches propose an existing exercise. Unknown names propose creating a new exercise. Ambiguous matches remain unresolved.

Draft duplicate detection can flag exact reconstructed workout duplicates and same-date possible duplicates inside the import batch. It does not delete, merge, reject, confirm, or compare against persisted canonical history yet.

Fixture metrics compare expected workout, exercise, set, and unresolved-field counts for representative samples. These metrics are deterministic and can run before real OCR exists.

Later implementation should add:

- Room or DataStore persistence for batch state;
- page-level processing status indexes;
- workout-level review status indexes;
- source fingerprint indexes;
- perceptual page duplicate hints;
- image preprocessing adapters that produce page metadata without storing transformed images by default;
- on-device OCR provider implementation behind the provider boundary;
- richer notation parsing for supersets, unilateral work, bodyweight sets, notes, crossed-out values, and personal abbreviations;
- canonical duplicate checks based on date, exercise order, set values, and provenance.

## Initial implementation slices

The first slice adds a pure Kotlin draft domain model and JVM tests. It intentionally does not add OCR, image capture, Room tables, network calls, or canonical writes.

The second slice adds pure Kotlin page intake and exact-byte fingerprinting. Android camera/gallery code should read bytes outside the domain layer and pass them to the intake helper. The helper returns `NotebookPageDraft` records with deterministic positions, metadata, and SHA-256 fingerprints.

The third slice adds pure Kotlin resumable batch state. It defines progress and terminal-state semantics without choosing the later persistence backend.

The fourth slice adds pure Kotlin preprocessing metadata and page-order proposals. It does not decode bitmaps or modify images.

The fifth slice adds the recognition provider boundary and deterministic fixture-line provider for tests. It does not add OCR, ML dependencies, or external networking.

The sixth slice adds pure Kotlin privacy policy, consent copy, deletion-target mapping, source-image deletion eligibility, and diagnostics redaction rules. It does not persist consent, delete files, or show UI.

The seventh slice adds pure Kotlin recognized-text interpretation into reviewable draft rows. It does not match exercises, confirm fields, or write canonical data.

The eighth slice adds pure Kotlin exercise matching, draft duplicate detection, and fixture accuracy metrics. It does not confirm mappings, mutate persisted workouts, or compare against canonical history.

This keeps the high-risk invariants testable before UI, storage, or recognition implementation starts.

## Child issue split after this ADR

The parent #177 should be decomposed into reviewable subtasks in this order:

1. capture/upload notebook pages and compute fingerprints;
2. persist resumable import batch state;
3. add page preprocessing and ordering;
4. add recognition provider abstraction and on-device prototype;
5. add optional external processing consent and privacy copy;
6. interpret recognized text into workout draft rows;
7. add exercise matching and alias review;
8. add review/correction UI;
9. add confirmed canonical import transaction;
10. add duplicate workout resolution;
11. add retention/deletion controls and diagnostics rules;
12. add representative notebook fixtures and accuracy metrics.

## Consequences

Positive:

- uncertain recognition remains visible rather than silently guessed;
- privacy choices are modeled before external processing exists;
- duplicate page checks can happen before expensive interpretation;
- canonical workout history stays protected behind confirmation and transaction boundaries;
- tests can validate import safety before Compose and Room work.

Trade-offs:

- this adds a new draft layer before user-visible functionality exists;
- cloud accuracy decisions are deferred until the consent and provider boundary is implemented;
- exact-byte fingerprints do not catch every visual duplicate;
- preprocessing metadata is not actual image enhancement yet;
- privacy copy is modeled before the final UI wording and layout are designed;
- the first text interpreter intentionally supports only narrow fixture patterns;
- matching and duplicates are draft-level only until review UI and canonical import exist;
- the first slices will not yet import real notebook photos end-to-end.

## Validation

Before this feature is ready for master, validate:

- clear, messy, abbreviated, corrected, mixed-unit, and multi-page notebook samples;
- pages without dates;
- pages uploaded out of order;
- interrupted imports and app restart;
- duplicate page and duplicate workout attempts;
- explicit deletion of source images and extracted draft data;
- imported workouts appearing correctly in history, statistics, and exercise history.
