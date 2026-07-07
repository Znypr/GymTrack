# Editor control visibility testing

GymTrack keeps workout completion controls visible only while the currently opened workout can still own active logging.

Covered by `NoteEditorControlVisibilityTest`:

- New workout: timer controls and **Finish** are visible.
- Active workout: timer controls and **Finish** are visible.
- Completed workout: timer controls and **Finish** are absent.
- Historical workout while another workout owns the timer: timer controls and **Finish** are absent.

The test uses stable Compose test tags instead of text or layout traversal:

- `note-editor-finish-action`
- `note-editor-timer-controls`

The repository's connected-test workflow runs `connectedDebugAndroidTest` for pull requests that touch `app/src/androidTest/**`, so this regression coverage is expected to run automatically on relevant PRs.
