## Purpose

Validate and review structured file results before controlled application to authorized OpenSpec planning roots or a separately approved workspace.

## ADDED Requirements

### Requirement: Structured concrete artifact outputs

Automatic artifact generation SHALL return a versioned validated file/patch result constrained by resolved planning roots and requested artifact output contract. A glob SHALL be treated as an allowed pattern, never a concrete filename.

#### Scenario: Multi-file specs
- **WHEN** the requested output pattern is specs/**/*.md and valid results contain two capability files
- **THEN** both concrete files SHALL be previewed and accepted as a scoped batch

#### Scenario: Invalid envelope
- **WHEN** a result has unknown schema version, malformed content or prose in place of the required structure
- **THEN** automatic saving SHALL be blocked with an actionable validation error

#### Scenario: Single text output
- **WHEN** a legacy backend returns text for one concrete allowed artifact
- **THEN** the plugin MAY wrap it as that single file but SHALL NOT infer multiple files from prose

### Requirement: Path and conflict safety

The plugin SHALL validate the full batch before writing and recheck containment, target type, base versions and unsaved edits before applying accepted content. It SHALL reject escapes, invalid paths, duplicate/colliding destinations and unauthorized operations.

#### Scenario: Escape
- **WHEN** a result includes traversal, absolute/drive/UNC paths or a symlink-parent escape
- **THEN** the entire batch SHALL be rejected before any target is written

#### Scenario: Conflict
- **WHEN** a target changed after preview or has unsaved conflicting edits
- **THEN** the plugin SHALL block applying the stale batch and require renewed review

#### Scenario: Duplicate files
- **WHEN** multiple operations map to the same effective destination
- **THEN** the plugin SHALL reject ambiguous operations rather than overwrite sequentially

### Requirement: Reviewed result application

All artifact entry points SHALL use the same safe application policy with concrete-file diff preview, explicit acceptance, recoverable writes and honest partial-failure reporting. Partial or canceled inference SHALL NOT be writable.

#### Scenario: Rejected preview
- **WHEN** the user declines a generated artifact batch
- **THEN** no generated file SHALL be saved

#### Scenario: Write failure
- **WHEN** a bounded accepted batch fails while applying
- **THEN** the plugin SHALL report affected files and recovery options without claiming full success

#### Scenario: Successful batch
- **WHEN** all accepted operations finish
- **THEN** the plugin SHALL refresh actual artifact status and provide undo or documented recovery

### Requirement: Separate workspace Apply permission

Workspace-writing Apply SHALL be a later explicitly enabled capability requiring trusted workspace, reviewed context/permissions, bounded typed approvals and final change review. MVP SHALL retain manual Apply prompt delivery without autonomous writes.

#### Scenario: MVP Apply
- **WHEN** the user selects Apply during the initial integrated release
- **THEN** manual handoff SHALL remain available and autonomous execution SHALL be unavailable

#### Scenario: Approval declined
- **WHEN** a later Apply permission request is denied, expires or the project closes
- **THEN** the plugin SHALL deny/cancel the request and SHALL NOT escalate silently

#### Scenario: Workspace scope
- **WHEN** a later run receives a workspace-write grant
- **THEN** the UI SHALL describe the granted scope and SHALL NOT promise an approval prompt for every file
