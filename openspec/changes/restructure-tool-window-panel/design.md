# Design: restructure-tool-window-panel

## Approach

Trim the Browse tab's file-navigation half and keep only the editor-irreplaceable model/process surfaces. Concretely:

1. **Drop the Specs / Archive / Config tree nodes.** File navigation moves to the Project View (which already shows `openspec/**` across the whole IDE family and opens `spec.md` into the editor + platform Markdown preview for free).
2. **Re-home the content search** (requirement-body + scenario-text matching — the tree's one genuinely-unique value) as a **Search Everywhere contributor**, not an always-on tree.
3. **Keep** the Changes subtree (status-bearing selector), the workflow/pipeline panel, the badged consolidated change-deltas preview, the Coordination tab, and the Console/CLI/AI status.
4. **Reframe the preview pane** to render for **change** nodes (badged deltas); plain specs open in the editor.

## Agent rationale (why this shape — recorded for the implementing session)

**plugin-ui-specialist (UX verdict).** The panel is *half redundant chrome, half load-bearing*. The Specs/Archive/Config subtrees + plain-spec preview duplicate the Project View + editor + Markdown preview, and a right-anchored second file tree fights the IntelliJ idiom (right = models/processes, left = navigation). The editor-irreplaceable half — change workflow status/badges, generate pipeline, consolidated badged deltas, Coordination (XDG state with no file representation), content search over bodies — is why the panel is **restructured, not removed**.

**jetbrains-platform-guru (feasibility verdict — all VIABLE on the 242 floor).**
- **Content-search → Search Everywhere: VIABLE, no fallback needed.** Use the **legacy** API — EP `com.intellij.searchEverywhereContributor` + `SearchEverywhereContributorFactory<T>` + `WeightedSearchEverywhereContributor<T>` — stable and **non-deprecated across 242→252**. `fetchWeightedElements(pattern, ProgressIndicator, Processor)` runs **off-EDT** with a progress indicator; it needs **no index** — it reuses `SpecParsingService.parseAllSpecs()` (VFS walk inside `ReadAction.compute`) + the pure, already-tested `SpecContentMatcher`, so the "matches bodies + scenarios, not just filenames" behavior is preserved by construction. Make it **DumbAware** (pure VFS + line scan, no platform index) → content search even works during indexing. Navigate via `OpenFileDescriptor` on the EDT, jumping with the existing `RequirementAnchors`.
- **DO NOT touch the new Search Everywhere API** (`SeItemsProvider`/`SeItem`, shipped 2025.12; legacy contributor slated for deprecation 2026.2) — both are **outside** the 242→252 window and would `ClassNotFoundException` on 2024.2. Build on the legacy interface now; log a 2026.2 migration as a future item.
- **Project View owns file navigation.** `StructureViewExtension` is the *wrong* abstraction (it outlines the open file, not a cross-file index) — avoid. A `TreeStructureProvider` decorator is added surface for cosmetic gain — not worth it.
- **verifyPlugin gates this change** — new `com.intellij.*` SE references. `build`/`test` compile against the build SDK and cannot prove no post-242 SE symbol crept in; only the Plugin Verifier can. The pre-push `verifyPlugin` gate + CI cover it.

**openspec-guru (on-model — DESIGN-TIME CHECK, confirm before implementing).** Provisional read: this is **presentation-only** — it introduces no new OpenSpec concept and removes none. Specs remain truth (list/show/validate) and are still viewable (in the editor); changes remain work (status/progress) and keep their in-panel surfaces. Moving content-search to Search Everywhere and letting specs open in the editor does not add a code↔spec link or any off-model concept. **Confirm with openspec-guru at implementation** that "specs viewed only in the editor + content-search in Search Everywhere" carries no upstream-model conflict before deleting the in-panel spec viewer.

## Components Affected

- `toolwindow/SpecTreeModel.java` — drop `buildSpecsNode`/`buildArchiveNode`/`buildConfigNode`; `TreeNodeType.SPECS/SPEC_DOMAIN/REQUIREMENT/ARCHIVE/CONFIG/CONFIG_ENTRY` and `filterNode` become dead (prune). `resolveChangeName` already recognizes only `CHANGE`/`CHANGE_DONE`, so selection→workflow wiring survives.
- `toolwindow/OpenSpecToolWindowPanel.java` — remove/repurpose the tree search field; preview renders for change nodes only; **preserve the preview accessible-name state machine** (`PREVIEW_RENDERED_NAME`, `PREVIEW_EMPTY_NAME`, `PREVIEW_CHANGE_DELTAS_NAME`, `PREVIEW_CHANGE_DELTAS_BADGED_NAME`) — the journeys assert on these.
- `toolwindow/SpecContentMatcher.java` + `services/SpecParsingService.java` — **reused unchanged** by the new contributor.
- **New** `SearchEverywhereContributorFactory` + `WeightedSearchEverywhereContributor` (in `.../search/`), registered in `plugin.xml`.
- `SpecPreviewRenderer.classify(...MAIN_SPEC...)` + tests stay green (pure), just uninvoked from the tree — keep the class.

## The under-scoped risk — uiSmoke ready-gate coupling (call this out loudly)

`hasText("Specs")` is the **readiness gate of nearly every journey**, not just the spec-navigation one — `OpenSpecUiSmokeTest.kt` lines 152, 478, 645, 706 and `MarketplaceScreenshotTour.kt` line ~299 all `waitUntil { hasText("Specs") }`. Removing the top-level "Specs" label **breaks most journeys' ready-wait**. The change MUST:
- introduce a new stable ready-token (e.g. the Changes group header or a Browse landmark) and update **every** `waitUntil` ready-gate in lockstep;
- **rewrite Journey 8** (`previewPaneRendersSelectedSpec`, ~lines 506-538) — it navigates the deleted Specs→greeting→Requirement path and asserts "OpenSpec preview rendered"; re-point it at the new Search-Everywhere content-search + editor-open path (or the change-deltas preview);
- **restage `MarketplaceScreenshotTour`** (~lines 299-340), which repeats that expansion for a screenshot;
- treat journey edits as part of this change; they run in `src/integrationTest` (headful uiSmoke, `caffeinate -dimsu`), which `build`/`test` do not catch.

## Trade-offs

- **Reclaims vertical space** for the status/pipeline/deltas surfaces (today cramped under a large redundant tree).
- **Small user re-learn** — specs are now browsed in the Project View (one click away) and searched via Search Everywhere (arguably *more* discoverable than a buried tree field). Low cost.
- **Main cost is the uiSmoke blast radius** above, not the production change (the tree/preview edits are mechanical).
- Content-search-in-SE loses the always-visible-tree affordance; mitigated by an optional dedicated search action if discoverability outside Search Everywhere is wanted (guru: strictly more code, defer unless needed).
