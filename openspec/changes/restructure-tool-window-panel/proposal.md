# Restructure the OpenSpec tool window: trim file-navigation redundancy, keep the model surfaces

## Why

The right-anchored "OpenSpec" tool window's Browse tab currently stacks a file-navigation tree (Specs / Archive / Config top-level nodes) on top of the workflow panel and preview. A UI evaluation (plugin-ui-specialist) found the panel is **half redundant chrome, half load-bearing** — and the redundant half is real estate the editor already owns:

**Redundant with the Project View / editor / Markdown preview:**
- The **Specs / Archive / Config** subtrees are a second file-navigation tree — double-clicking a node just opens the same file the Project View opens. Archive and Config are pure duplicates; the Specs tree's *navigation* overlaps the editor's Structure view.
- The **preview pane rendering a plain spec** duplicates the editor's built-in Markdown preview.
- A right-anchored **second file tree also fights the IntelliJ idiom** — right-anchored tool windows are for *models/processes* (Database, Gradle), left-anchored for *navigation* (Project, Structure).

**Editor-irreplaceable (why the panel stays, not removed):**
- **Change workflow status** — the `name [status] X/Y` task rollup + per-artifact ready/blocked/done badges (computed cross-file state that appears nowhere else).
- **The generate pipeline** — the chips that advance a change through its artifact DAG (a process, not a file).
- **Consolidated badged deltas** — ADDED/MODIFIED/REMOVED aggregated across capabilities; the editor only ever shows one raw delta `spec.md`.
- **Coordination** — store/workset state lives in XDG paths with *no* file-tree representation.
- **Content search** — matches requirement bodies + scenario text, which the Project View's filename search can't.

## What Changes

Restructure the Browse tab toward the model/process surfaces, letting the Project View own file navigation:

- **Keep:** the **Changes** subtree (the status-bearing selector that drives the workflow panel + deltas preview), the workflow/pipeline panel, the badged change-deltas preview, the Coordination tab, and the Console/CLI/AI status.
- **Drop/demote:** the **Archive** and **Config** top-level subtrees (pure file-navigation duplicates — send users to the Project View).
- **Specs:** drop the always-on Specs *navigation* tree, but **preserve its unique content search** (requirement-body + scenario text) — re-homed as a search entry point (a Search-Everywhere contributor or dedicated search action), pending the platform feasibility check in design.
- **Reframe the preview pane** to render for **change** nodes (the badged deltas) and let plain spec files open in the editor, rather than duplicating the editor's Markdown preview.

### Align the pre-archive check to OpenSpec's own vocabulary and gate model

The panel's pre-archive check is currently a plugin-invented **"Compliance / Pre-Flight"** concept, exposed as a *second* icon button alongside the redundant on-vocabulary **"Verify"** button — and it renders a change with merely-unfinished tasks in red "error — archive is blocked" language. Upstream OpenSpec has no "compliance"; its word for the pre-archive check is **verify**, and its `archive` command splits the gate into two differently-severe conditions: unfinished tasks are a **soft, bypassable Warning** (archivable with `--yes`), while a structural **validation failure** is the only **hard block** (bypass only `--no-validate`). The plugin conflates both under one red dialog, over-stating the task case — the exact "plugin stricter than the client it wraps" anti-pattern the validator-parity work already corrected elsewhere.

- **Collapse the two buttons into one on-vocabulary "Verify" surface** that runs the existing superset check (completeness + validation + spec-sync) and renders **three native states** in one dialog: *ready to archive* (green), *in progress — N/M tasks complete, archive anyway* (neutral, **not** red — mirrors the bypassable `--yes` gate), and *validation failed — archive blocked* (red, the only hard gate).
- **Retire** the redundant verify-only button, its report dialog, and the "compliance" vocabulary everywhere (button, dialog title, status chip, notification group), so "Verify" means the identical check from the tool-window button, the menu action, and the archive pre-flight.

## Capabilities

### Modified Capabilities
- **tree-view** — the tool-window tree drops the Archive/Config/Specs file-navigation nodes; the Changes subtree (status-bearing) remains, and spec content-search is re-homed off the always-on tree.
- **spec-viewer** — the preview pane is reframed to the badged change-deltas surface; plain-spec rendering defers to the editor.
- **verify-workflow** — the pre-archive check collapses to a single on-vocabulary **Verify** surface with three native states (READY / IN&nbsp;PROGRESS / BLOCKED); an unfinished-but-valid change is a neutral, bypassable "In Progress" (mirroring `--yes`), and only a validation error is a hard block. Absorbs the removed `compliance` capability.

### Removed Capabilities
- **compliance** — the plugin-invented "Compliance / Pre-Flight" concept is removed; its three-dimension check, gated dialog, notification group, status chip, and result dialog are absorbed into `verify-workflow` under Verify vocabulary. Nothing is dropped, only renamed and re-modeled.

## Impact

- Affected code: `toolwindow/OpenSpecToolWindowPanel`, `SpecTreeModel`, `SpecContentMatcher`, `SpecPreviewRenderer`, and the search re-homing (new surface TBD by the platform check). For the Verify collapse: `ArchiveReadinessResult` (was `ComplianceResult`, + three-state classifier), `ArchiveReadinessService` (was `ComplianceService`), `VerifyDialog` (was `CompliancePreFlightDialog`), `WorkflowActionPanel` (one Verify button), `OpenSpecVerifyAction`/`OpenSpecArchiveAction`, `OpenSpecNotifier` (`OpenSpec.Verify` group), `plugin.xml`; the `VerifyReportDialog` is retired.
- Affected tests: the uiSmoke journeys that assert the Browse tree groups ("Specs"/"Changes") and the preview accessible-name states must be updated to the new structure; unit tests for `SpecTreeModel` node composition. For Verify: `ArchiveReadinessResultTest` (the three-state classifier, crux = incomplete-tasks → IN&nbsp;PROGRESS not BLOCKED), `VerifyDialogTest` (OK-label/enablement gate), `WorkflowActionPanelTest` (verify status vocabulary), and uiSmoke Journey&nbsp;5 (`archiveGuardsIncompleteChange` → dialog title "Verify — …").
- **Deliberately post-v0.6.0** — this is a UX restructure, distinct from v0.6.0's validation/correctness story; it should ship on its own (v0.7.0-era) with its journey updates.
- Agent rationale + the two design-time checks (Search-Everywhere feasibility on 2024.2; worksets-never-on-disk) are recorded in `design.md` so implementation picks them up.
