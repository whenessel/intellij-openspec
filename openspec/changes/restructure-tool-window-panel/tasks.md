# Tasks: restructure-tool-window-panel

> Deliberately scoped post-v0.6.0. Implementation begins only after the v0.6.0 cut.

## Pre-implementation checks (agent triggers)

- [x] 0.1 **openspec-guru** — confirm "specs viewed only in the editor + content-search in Search Everywhere (no in-panel spec viewer/tree)" carries no upstream-model conflict before deleting the in-panel spec surface. **PASS (verified vs CLI 1.6.0):** upstream models specs as list/show/validate only — no status/progress/coverage — so the in-panel Specs tree + plain-spec preview is presentation, not a model concept; removing it drops zero upstream state (list→Project View + SE contributor, show→editor Markdown, validate→untouched). Re-homed content-search is a text search over spec source markdown (no persistent per-spec state), categorically unlike the removed @spec scorecard. Retained Changes/deltas/coordination surfaces all on-model. Spec-hygiene note: update `tree-view` + `spec-viewer` Modified-Capability scenarios in lockstep.
- [x] 0.2 **jetbrains-platform-guru** — feasibility confirmed (legacy `SearchEverywhereContributor` viable on 242; do NOT touch the 2025.12/2026.2 SE API). Also confirmed the Starter/Driver can drive Search Everywhere headfully (`ui.searchEverywherePopup()` + `resultsList.items` renderer-text read + keyboard-only input) for Journey 8.

## Implementation Tasks

- [x] 1.1 Add a `WeightedSearchEverywhereContributor` + `SearchEverywhereContributorFactory` (new `.../search/` package) that fetches off-EDT via `SpecParsingService.parseAllSpecs()` + `SpecContentMatcher`, is `DumbAware`, and navigates to a requirement via `RequirementAnchors`; register the EP `com.intellij.searchEverywhereContributor` in `plugin.xml`.
- [x] 1.2 Dropped `buildSpecsNode`/`buildArchiveNode`/`buildConfigNode` + `filterNode`/`cloneSubtree` + the query path from `SpecTreeModel`; `buildModel()` is Changes-only; pruned `TreeNodeType` to `CHANGES/CHANGE/CHANGE_DONE/ARTIFACT*/MISSING_ARTIFACT/DELTA_SPEC/HINT`. Cascaded fixes: `SpecTreeCellRenderer.iconForType`, `SpecPreviewRenderer.classify`, `OpenSpecToolWindowPanel`.
- [x] 1.3 Removed the always-on tree search field (+ its `Alarm`/`DocumentListener`/Ctrl+F) from `OpenSpecToolWindowPanel`; preview reframed to change nodes only (requirementName always null; `SPEC_DOMAIN` context-menu arm dropped); the preview accessible-name state machine is **preserved**.
- [x] 1.4 Kept `SpecPreviewRenderer` (MAIN_SPEC render branch retained + still unit-tested via `renderMarkdown`, just no longer routed from `classify`) + `SpecContentMatcher` + `SpecParsingService`.

## Testing Tasks

- [x] 2.1 SE contributor unit-tested (`SpecRequirementSearchTest` body+scenario matching, `RequirementLineFinderTest` fence-aware navigation target) — from task 1.1.
- [x] 2.2 Migrated `SpecTreeModel`-area tests to Changes-only: deleted `SpecTreeModelConfigTest`/`SpecContentFilterTest`/`SpecPreviewFileReadTest`; ported `SpecParsingIntegrationTest` (tree-walk→parser assertions), `SpecPreviewRenderTest`, `SpecTreeCellRendererTest`, `TreeSelectionSyncTest`.
- [x] 2.3 **uiSmoke lockstep:** swapped every `hasText("Specs")` ready-gate → `hasText("Changes")` (OpenSpecUiSmokeTest lines 152/480/645/706 + tour 299); **rewrote Journey 8** `previewPaneRendersSelectedSpec` → `searchEverywhereFindsSpecContent` (drives `ui.searchEverywherePopup()`, types a body-only token, asserts the requirement NAME surfaces in `resultsList.items` — keyboard-only, editor-open left to the unit tests per the guru); retired the tour's shot 07 (spec-preview superseded by shot 01's spec-in-editor).
- [x] 2.4 `./gradlew build` green (unit + coverage floor lowered 0.385/0.360/0.360 with recorded justification) AND `verifyPlugin` **Compatible** IC-242/243/251/252.
- [~] 2.5 Headful `caffeinate -dimsu ./gradlew uiSmoke` — **10/11 green** (incl. the trimmed `toolWindowRendersSeededProject`, Verify `archiveGuardsIncompleteChange`). Journey 8 (`searchEverywhereFindsSpecContent`) first run failed on a **too-tight 30s poll** — the driver screenshot proved SE correctly surfaced "Friendly greeting" from the body token (the index-free contributor just populates the "All" tab a beat later than indexed ones); fixed by a 30s→90s poll bump. The confirming re-run hung on **headful saturation** (documented gotcha — reboot is the clean gate); re-run `--tests "*searchEverywhere*"` after a reboot to close this box. The fix is a timeout-only change (cannot red a passing test).

## Verify/Compliance collapse (folded in — align pre-archive check to OpenSpec vocabulary)

- [x] 3.0 **openspec-guru + plugin-ui-specialist + test-engineer** — terminology ruling ("verify", not "compliance"; two native gate codes soft/hard, both `severity:"error"`), single-surface design (3-tier dialog, neutral IN PROGRESS), and the three-state classifier test strategy. Recorded in design.md.
- [x] 3.1 Model: `ComplianceResult → ArchiveReadinessResult` with `ArchiveReadiness{READY,IN_PROGRESS,BLOCKED}`, per-finding `BlockKind{HARD,SOFT,NONE}`, and the pure `classify()`/`stateMessage()`/`stateColor()`/`isHardBlocked()` helpers (VALIDATION error = HARD, completeness = SOFT, warnings = NONE).
- [x] 3.2 Service: `ComplianceService → ArchiveReadinessService` (`verify(name)`), distinct from the internal `VerificationService` engine it wraps.
- [x] 3.3 Dialog: `CompliancePreFlightDialog → VerifyDialog` (title "Verify — <change>"), three-tier header — neutral Information icon + "Archive anyway" (enabled) for IN PROGRESS, red + disabled OK only for a hard validation BLOCK.
- [x] 3.4 Panel: collapse the two icon buttons (`complianceIconButton` + `verifyIconButton`) into one Verify button (`AllIcons.Actions.ProjectWideAnalysisOn`, accessible name); merge `onComplianceCheck`/`onVerify`; three-state verify status chip (neutral middle); add accessible names to all icon buttons.
- [x] 3.5 Actions/notifier/xml: repoint `OpenSpecVerifyAction` + `OpenSpecArchiveAction` to `ArchiveReadinessService`+`VerifyDialog` (Verify flows to archive on OK); `OpenSpecNotifier` `OpenSpec.Compliance → OpenSpec.Verify`; `plugin.xml` service + notification group; retire `VerifyReportDialog`.
- [x] 3.6 Tests: `ArchiveReadinessResultTest` (three-state classifier; crux `incompleteTasksOnly_isInProgress_notBlocked`), `VerifyDialogTest` (OK label/enablement gate + neutral-not-red header color), `ArchiveReadinessServiceMappingTest` (the real fold: incomplete→SOFT/IN_PROGRESS not HARD/BLOCKED — closes the service-seam regression), `WorkflowActionPanelTest` verify vocabulary; delete the former compliance tests + `VerifyReportDialogTest`.
- [x] 3.7 uiSmoke Journey 5 (`archiveGuardsIncompleteChange`) — dialog title "Compliance Check — …" → "Verify — …" (present/cancel/notPresent) + doc note.
- [x] 3.8 Delta specs: `verify-workflow` MODIFIED (single Verify surface + three states); `compliance` REMOVED (absorbed). `openspec validate --strict` passes.
- [x] 3.9 `./gradlew build` green (unit + ratcheted coverage floors 0.394/0.371/0.365) AND `verifyPlugin` **Compatible** on IC-242/243/251/252.
- [ ] 3.10 Headful `caffeinate -dimsu ./gradlew uiSmoke` — Journey 5 green against the renamed dialog (runs with 2.5).
- [x] 3.11 **intellij-code-reviewer + test-engineer AUDIT** — review clean (threading correct; one layering nit applied: `stateColor` moved model→`VerifyDialog`, model kept UI-free); audit pass-with-nits, the Medium finding (untested service→category seam) closed by `ArchiveReadinessServiceMappingTest`.

## Change-node visual hierarchy (folded in — the Changes subtree is the elevated surface)

- [x] 4.0 **plugin-ui-specialist + jetbrains-platform-guru** — opinion: the real fix is un-tinting the change NAME (not just the count); keep the `[status]` tag colored (only inline proposed↔applied cue), dim `X/Y` gray, and do NOT green `N/N` (done is the icon badge's story, on the distinct artifact-DAG signal). Migration to `ColoredTreeCellRenderer` is low-risk with an in-repo precedent (`CoordinationCellRenderer`); selection is platform-handled. Recorded in design.md.
- [x] 4.1 `SpecTreeModel.TreeNodeData` carries structured `ChangeLabelParts(name, status, taskCounts)` on change nodes; `buildChangeLabel` (flat string) kept intact for search/tooltip/tests.
- [x] 4.2 `SpecTreeCellRenderer` migrated `DefaultTreeCellRenderer → ColoredTreeCellRenderer`: name `REGULAR`, `[status]` in meaning color, `X/Y` `GRAYED_ATTRIBUTES`; bold/italic states re-expressed as cached `SimpleTextAttributes`; manual selection-color branches dropped (platform-handled).
- [x] 4.3 Pure `changeFragments(parts)` seam + tests (name un-tinted, status colored, count grayed incl. `8/8` not greened, UNKNOWN/no-tasks omissions); existing `iconForType`/label/config/selection-sync tests unaffected.
- [x] 4.4 `build` + `verifyPlugin` (Compatible IC-242/243/251/252) green; coverage floors held at safe margin (0.394/0.371/0.365).
- [ ] 4.5 Headful `caffeinate -dimsu ./gradlew uiSmoke` — eyeball the fragmented change row (name default-colored, gray count) + selected-row legibility on light/Darcula (runs with 2.5; tree `hasText` gates unaffected — the concatenated fragment text is byte-identical to the old label).
