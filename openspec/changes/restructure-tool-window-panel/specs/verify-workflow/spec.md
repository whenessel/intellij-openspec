# Verify Workflow (delta)

This change collapses the plugin-invented "Compliance / Pre-Flight" surface into the on-vocabulary
**Verify** surface and aligns its gate to upstream OpenSpec's two archive-gate conditions. The
`compliance` capability's requirements are absorbed here (see that capability's REMOVED delta).

## ADDED Requirements

### Requirement: Single Verify surface with three archive-readiness states

The plugin SHALL expose ONE pre-archive **Verify** surface — a single tool-window button, the
`OpenSpec.Verify` menu action, and the Archive pre-flight all running the identical check — and SHALL
NOT present a separate "Compliance" surface. The check SHALL fold three dimensions (completeness,
validation, spec-sync) into one result and classify it into one of three states that mirror upstream
`openspec archive`: **READY**, **IN&nbsp;PROGRESS**, and **BLOCKED**. The two gate conditions carry
the same JSON severity upstream; the plugin SHALL distinguish them by an intrinsic hard/soft property,
not by error count.

#### Scenario: Ready to archive
- **WHEN** the check finds no errors and no incomplete work
- **THEN** the Verify dialog SHALL show a green "ready to archive" header and its OK button SHALL read "Archive" and be enabled

#### Scenario: In progress — soft, bypassable task gate
- **WHEN** the change is structurally valid but has incomplete tasks (the `archive_tasks_incomplete` analogue)
- **THEN** the Verify dialog SHALL show a **neutral** (non-red) "In Progress — N incomplete task(s) remain" header
- **AND** its OK button SHALL read "Archive anyway" and remain **enabled**, mirroring upstream's bypassable `--yes` gate — a valid-but-unfinished change SHALL NOT be rendered as an error/blocked

#### Scenario: Blocked — hard validation gate
- **WHEN** the check finds a validation error (the `archive_validation_failed` analogue)
- **THEN** the Verify dialog SHALL show a red "Validation failed — archive blocked" header and its OK button SHALL be **disabled** (the only state that blocks archive)

#### Scenario: Hard block dominates soft
- **WHEN** a change has both incomplete tasks and a validation error
- **THEN** the state SHALL be BLOCKED (the hard gate dominates), not the neutral in-progress state

#### Scenario: Three-dimension check
- **WHEN** Verify runs for a change
- **THEN** it SHALL evaluate completeness (delegated to the completeness+correctness engine), validation (built-in validator), and spec-sync readiness (unmatched MODIFIED delta targets), mapping each finding to its category and its hard/soft gate kind — completeness findings are soft, a validation error is hard, and spec-sync findings are non-gating warnings

#### Scenario: One surface everywhere
- **WHEN** the user runs Verify from the tool-window button, the `OpenSpec.Verify` menu action, or by archiving
- **THEN** all three SHALL run the same check and present the same three-state Verify dialog, whose OK archives the change (enabled for READY and bypassable IN PROGRESS, disabled for a hard BLOCK)

#### Scenario: Verify status chip
- **WHEN** the workflow panel's Verify status indicator reflects a result
- **THEN** it SHALL show one of three states — "✓ Ready to archive" (green), "In progress · archive anyway" (**neutral**), or "N issue(s) — blocked" (red) — and clicking it SHALL run Verify; it SHALL NOT use the word "compliance"

#### Scenario: Verify notification group
- **WHEN** the plugin registers its notification groups
- **THEN** it SHALL register `OpenSpec.Verify` (STICKY_BALLOON) and SHALL NOT register `OpenSpec.Compliance`

## MODIFIED Requirements

### Requirement: Verification report

The plugin SHALL display verification results in the three-state Verify dialog with per-category
sections (Completeness, Validation, Spec Sync), each finding shown with its severity. The archive
gate SHALL be driven by the three-state classification: only a hard validation **BLOCK** disables
archiving; an **IN PROGRESS** change (incomplete tasks, structurally valid) is archivable-anyway
(soft, bypassable), and a **READY** change archives directly.

#### Scenario: Report display
- **WHEN** verification completes
- **THEN** the plugin SHALL show the Verify dialog with a state header (ready/in-progress/blocked) and per-category sections, each finding with a severity-coded label

#### Scenario: Archive gate
- **WHEN** the result classifies as BLOCKED (a validation error)
- **THEN** the plugin SHALL prevent archiving (OK disabled) and show the count of blocking issues
- **AND WHEN** the result classifies as IN PROGRESS (incomplete tasks only)
- **THEN** the plugin SHALL still permit archiving via an "Archive anyway" action rather than blocking, without rendering the change as an error

#### Scenario: Clean report
- **WHEN** no issues are found
- **THEN** the plugin SHALL display a "ready to archive" state with an enabled Archive action
