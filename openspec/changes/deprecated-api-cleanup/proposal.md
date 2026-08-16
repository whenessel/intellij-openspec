## Why

The JetBrains Marketplace Plugin Verifier flags the plugin for **scheduled-for-removal** and deprecated IntelliJ Platform API usage. The scheduled-for-removal calls are the load-bearing risk: when JetBrains actually deletes those methods in a future IDE, each call flips from a report-only warning into a `NoSuchMethodError`-class **compatibility problem** at runtime — and the plugin's compatibility floor is open-ended (`sinceBuild = 242`, no `untilBuild`), so it must keep working on every future build. Two such usages have a replacement that is non-deprecated across the **entire** supported range (242 → the current 262), so they can be fixed now; the remaining flagged usages are plain-deprecated (not for-removal) and have **no** replacement available at the 242 floor, so they cannot be fixed without bumping the floor and are deliberately deferred. This closes the removable-API exposure and adds a guard so it cannot silently reappear. Tracked in the project tracker.

## What Changes

- **Fix the two scheduled-for-removal browse-listener calls** (`dialogs/NewStoreDialog`, `dialogs/NewWorksetDialog`): replace the for-removal 4-arg `addBrowseFolderListener(title, description, project, descriptor)` with the floor-safe 1-arg `addBrowseFolderListener(new TextBrowseFolderListener(descriptor.withTitle(...).withDescription(...), project))` form — the exact pattern `settings/OpenSpecSettingsPanel` already uses. The dialog titles/descriptions are preserved by moving them onto the descriptor. (`FileChooserDescriptor.withTitle()`/`.withDescription()` and the 1-arg overload are non-deprecated at both 242 and 262; the 2-arg `(Project, descriptor)` overload is deliberately **not** used because it is 243+ and would fail to compile against the 242 build SDK.)
- **Fix the scheduled-for-removal renderer factory** (`search/SpecSearchEverywhereContributor`): replace `SimpleListCellRenderer.create(...)` with a direct `SimpleListCellRenderer` subclass overriding `customize(...)` — the class and its `customize` contract are non-deprecated at both 242 and 262, producing identical rendered text and tooltip.
- **Add a Plugin Verifier scheduled-for-removal canary** so that any *future* for-removal usage fails `verifyPlugin` immediately rather than surfacing later as a Marketplace warning or a runtime break. IPGP 2.18.1's `failureLevel` **cannot** express "fail on scheduled-for-removal but allow plain-deprecated": the verifier folds for-removal usages into its "Deprecated API usages" report section and only names them "scheduled for removal" on the verdict line IPGP's problem-collector skips, so the only `failureLevel` that catches them (`DEPRECATED_API_USAGES`) would also fail the deferred plain-deprecated usages. (Verified empirically — `SCHEDULED_FOR_REMOVAL_API_USAGES` in `failureLevel` did not fail a build with a real for-removal usage present.) The canary is therefore a `doLast` on `verifyPlugin` that scans the verifier's own report files for the for-removal-only wording ("… will be removed in …" per usage; "scheduled for removal API" on the verdict), which plain-deprecated usages never emit — and skips the `.html` reports so a static legend can't false-trip it. `failureLevel` is separately set to a **pinned replica of the IPGP 2.18.1 default** (`COMPATIBILITY_PROBLEMS`, `INTERNAL_API_USAGES`, `OVERRIDE_ONLY_API_USAGES`) so a future IPGP default-weakening can't silently reduce coverage; `DEPRECATED_API_USAGES` stays out (the deferred plain-deprecated usages stay green). After this change there are zero scheduled-for-removal usages, so the canary is green; it is proven to bite (RED) on a reintroduced for-removal usage and to stay green (GREEN) with only the plain-deprecated usages present.
- **Deliberately defer three plain-deprecated (not for-removal) usages**, each with a documented reason, because no non-deprecated replacement exists at the 242 floor:
  - `ActionUtil.invokeAction` (5 sites) — the `performAction(action, event)` replacement first appears at **252 (2025.2)**; already tracked as the "migrate when the floor bumps to 2025.2" item. Plain-deprecated, will not hard-break.
  - `ReadAction.compute(ThrowableComputable)` (5 sites) — at 262 this is a blocking-context nudge toward coroutine `readActionBlocking`, **not** for-removal; the clean `computeBlocking` replacement does not exist at 242, and the `nonBlocking().executeSynchronously()` alternative changes semantics (cancellable) — wrong for these straight synchronous file reads. Left as-is.
  - `TerminalToolWindowManager.createShellWidget` (1 site) — plain-deprecated at 262 toward the Reworked Terminal API, which is absent at 242; the call site is already reflection-tolerant (result held as `Object`, `instanceof` guard, `catch (Throwable)` fallback).

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `ci`:
  - **Scheduled-for-removal API usage fails verification** (new requirement) — the Plugin Verifier gate SHALL treat scheduled-for-removal IntelliJ Platform API usage as a build failure, so a call to a method the platform will delete cannot merge; plain-deprecated usage that has no replacement at the compatibility floor remains permitted (report-only) and is tracked for migration when the floor advances.

## Impact

- **`src/main`** — three like-for-like platform-API swaps with **no behavior change**: `dialogs/NewStoreDialog`, `dialogs/NewWorksetDialog` (browse listener), `search/SpecSearchEverywhereContributor` (renderer). No capability's user-facing behavior changes; the dialogs pick the same folders and the search list renders the same text/tooltip.
- **Build/CI** — `build.gradle.kts`: `failureLevel` pinned to the IPGP 2.18.1 default (guards against a future default-weakening), plus a `doLast` on `verifyPlugin` that fails on scheduled-for-removal findings in the verifier's report files (the canary; `.html` reports skipped). Deprecated stays report-only.
- **`OpenSpecSettingsPanel` is intentionally untouched** — it already uses the modern 1-arg form and is not deprecated at 262.
- **Authoritative gate is `./gradlew verifyPlugin`** across 242 → 262 — `./gradlew build`/`test` compile against the 242 SDK only and cannot see the 262 side; the pre-push verify hook already triggers on `com.intellij.*` diffs.
- **No `CHANGELOG` entry** — this is internal platform-API hygiene with no user-visible behavior change (per the changelog-scope rule); the forward-compat benefit is real but not a user-facing feature.
- **No coverage-floor ratchet** — UI/search wiring with no new unit-testable branch logic.
- **Out of scope** — the three deferred deprecations above (each requires a platform-floor bump); no `sinceBuild` change (floor stays 242, preserving 2024.2+ across the whole IDE family).
