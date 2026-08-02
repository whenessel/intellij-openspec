# Tasks: restructure-tool-window-panel

> Deliberately scoped post-v0.6.0. Implementation begins only after the v0.6.0 cut.

## Pre-implementation checks (agent triggers)

- [ ] 0.1 **openspec-guru** — confirm "specs viewed only in the editor + content-search in Search Everywhere (no in-panel spec viewer/tree)" carries no upstream-model conflict before deleting the in-panel spec surface. (Provisional read in design.md: presentation-only, on-model.)
- [ ] 0.2 **jetbrains-platform-guru** — feasibility already confirmed (legacy `SearchEverywhereContributor` viable on 242; do NOT touch the 2025.12/2026.2 SE API). Re-confirm only if the target floor moves.

## Implementation Tasks

- [ ] 1.1 Add a `WeightedSearchEverywhereContributor` + `SearchEverywhereContributorFactory` (new `.../search/` package) that fetches off-EDT via `SpecParsingService.parseAllSpecs()` + `SpecContentMatcher`, is `DumbAware`, and navigates to a requirement via `RequirementAnchors`; register the EP `com.intellij.searchEverywhereContributor` in `plugin.xml`.
- [ ] 1.2 Drop `buildSpecsNode`/`buildArchiveNode`/`buildConfigNode` from `SpecTreeModel`; prune the now-dead `TreeNodeType.SPECS/SPEC_DOMAIN/REQUIREMENT/ARCHIVE/CONFIG/CONFIG_ENTRY` and `filterNode`. Keep the Changes subtree (`resolveChangeName` already keys off `CHANGE`/`CHANGE_DONE`).
- [ ] 1.3 Remove/repurpose the always-on tree search field in `OpenSpecToolWindowPanel`; reframe the preview to render for change nodes only (plain specs open in the editor), **preserving** the preview accessible-name state machine.
- [ ] 1.4 Keep `SpecPreviewRenderer` + `SpecContentMatcher` + `SpecParsingService` (reused/inert, still unit-tested).

## Testing Tasks

- [ ] 2.1 Unit-test the SE contributor's fetch/match against seeded specs (body + scenario matching preserved) and the navigation target resolution.
- [ ] 2.2 Update `SpecTreeModel` unit tests to the Changes-only node composition.
- [ ] 2.3 **uiSmoke lockstep (the load-bearing risk — see design.md):** introduce a new stable ready-token (the tree still shows "Changes"; retire the `hasText("Specs")` ready-gate) and update **every** `waitUntil` ready-gate in `OpenSpecUiSmokeTest.kt` (lines ~152, 478, 645, 706) and `MarketplaceScreenshotTour.kt` (~299). Rewrite **Journey 8** (`previewPaneRendersSelectedSpec`) to exercise the new Search-Everywhere content-search + editor-open path; restage the screenshot tour.
- [ ] 2.4 `./gradlew build` green (unit + coverage floor) AND **`verifyPlugin` green** (new `com.intellij.*` SE references — the pre-push + CI verify gate is load-bearing here; `build`/`test` cannot catch a post-242 SE symbol).
- [ ] 2.5 Headful `caffeinate -dimsu ./gradlew uiSmoke` — the full suite green with the rewritten Journey 8 + updated ready-gates.
