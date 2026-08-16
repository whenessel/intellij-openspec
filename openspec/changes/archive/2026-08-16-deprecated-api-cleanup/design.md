## Context

The Marketplace Plugin Verifier reports deprecated and scheduled-for-removal IntelliJ Platform API usage for the shipped plugin. The plugin's compatibility range is **`sinceBuild = 242` (2024.2) with no `untilBuild`** — open-ended — so any code must compile against the 242 SDK *and* keep running on every future IDE (262 / 2026.2 is now live). That two-sided constraint is what decides which flagged usages are fixable now.

A key fact reframes urgency: the IntelliJ Platform Gradle Plugin's default verifier failure set does **not** include deprecated or scheduled-for-removal usage, so today all of these are **report-only** — `verifyPlugin` is green. The reason to act on the *scheduled-for-removal* ones is that when the platform actually deletes those methods, the call becomes a binary **compatibility problem** (a `NoSuchMethodError` class of failure), which **is** in the default failure set — i.e. it turns into a hard break on a future IDE.

## Per-API verdict (ground-truthed against the 242 and 262 platform sources)

| API | Sites | Class | Verdict |
|---|---|---|---|
| `addBrowseFolderListener` 4-arg | `NewStoreDialog`, `NewWorksetDialog` | scheduled-for-removal | **fix now (floor-safe)** |
| `SimpleListCellRenderer.create` | `SpecSearchEverywhereContributor` | scheduled-for-removal | **fix now (floor-safe)** |
| `ActionUtil.invokeAction` | 5 sites | plain-deprecated | defer — `performAction()` is 252+ |
| `ReadAction.compute(ThrowableComputable)` | 5 sites | plain-deprecated (blocking-context nudge at 262) | defer — `computeBlocking` absent at 242; `nonBlocking()` changes semantics |
| `TerminalToolWindowManager.createShellWidget` | 1 site | plain-deprecated | defer — Reworked Terminal API absent at 242; call is already reflection-tolerant |

`OpenSpecSettingsPanel` is **not** in the list: it already uses the modern 1-arg form and is not deprecated at 262.

## Decisions

### Browse listeners — use the 1-arg form, NOT the "blessed" 2-arg form

The deprecation javadoc steers callers to `addBrowseFolderListener(Project, FileChooserDescriptor)`, but that overload was **introduced at 243** and does not exist in the 242 SDK — using it would fail to compile against the build SDK (the same class of trap as the `PlatformProjectOpenProcessor.attachToProject` incident, except caught at compile rather than runtime). The floor-safe path — non-deprecated at *both* 242 and 262 — is the 1-arg `addBrowseFolderListener(TextBrowseFolderListener)` overload with the title/description moved onto the descriptor:

```java
pathField.addBrowseFolderListener(new TextBrowseFolderListener(
        FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Store Folder")
                .withDescription("Choose the folder where the store should live"),
        project));
```

`FileChooserDescriptor.withTitle()`/`.withDescription()` and `createSingleFolderDescriptor()`/`createSingleFileNoJarsDescriptor()` are all non-deprecated at 242 and 262. This is the pattern `OpenSpecSettingsPanel` already uses. **Residual note:** the platform is steering toward the 2-arg form, so the 1-arg overload may itself deprecate in some build > 262 — when the floor eventually reaches 243+, switch all such sites to `addBrowseFolderListener(project, descriptor.withTitle(...).withDescription(...))`.

### Renderer — subclass, don't use the factory

Both `SimpleListCellRenderer.create(...)` static factories are for-removal at 262, but the class itself and its abstract `customize(JList, T, int, boolean, boolean)` contract are non-deprecated at both branch points. Subclass directly and set text/tooltip inside `customize` (calling `setText`/`setToolTipText` on the renderer, which extends the list-cell label). The Kotlin `listCellRenderer` DSL the javadoc points to is the long-term path but requires Kotlin and a newer floor — inappropriate for a Java plugin at 242.

### Verifier guard — a report-scan canary, because `failureLevel` structurally can't do it

The intent is "fail `verifyPlugin` on scheduled-for-removal usage, but still allow the deferred plain-deprecated usages." **IPGP 2.18.1 cannot express that**, discovered the hard way: setting `failureLevel = [..., SCHEDULED_FOR_REMOVAL_API_USAGES]` did **not** fail a build that had a real for-removal usage present (verifier verdict stayed "Compatible", Gradle exit 0). Traced to source: the plugin-verifier reports the "scheduled for removal" phrase only on the inline **verdict line**, which IPGP's `collectProblems` skips (it scans the lines *after* the verdict), and it folds every for-removal usage into the detail report's **"Deprecated API usages"** section. So the only `FailureLevel` that would trip on a for-removal usage is `DEPRECATED_API_USAGES` — which also trips on the five plain-`@Deprecated` `ActionUtil.invokeAction` calls. There is **no** `failureLevel` combination that fails on scheduled-for-removal while passing plain-deprecated. `SCHEDULED_FOR_REMOVAL_API_USAGES` in `failureLevel` is inert, and this is identical on IPGP `main`, so no upgrade fixes it.

The working guard is a **`doLast` on `verifyPlugin`** that scans the verifier's own report files (which are always written — default `verificationReportsFormats` includes PLAIN) for wording the verifier emits **only** for for-removal usages, keyed on captured real output:
- per-usage detail: `"… will be removed in …"` (emitted iff `deprecationInfo.forRemoval`, across method/class/field/interface usage types) — present in `deprecated-usages.txt` for the for-removal line, **absent** for the plain-deprecated lines;
- verdict: `"N usages of scheduled for removal API"` — present in `verification-verdict.txt` only when the count is > 0.

The scan skips `.html` reports (so a static legend can never false-trip it) and a `doFirst` wipes `verificationReportsDirectory` first (never grep stale reports). Proven with the repo's RED/GREEN discipline: reintroducing one for-removal call makes `verifyPlugin` **fail** (canary throws), and with the two fixes applied — the five plain-deprecated usages still present — it **passes**.

`failureLevel` is still set, but only as a **pinned replica of the IPGP 2.18.1 default** (`COMPATIBILITY_PROBLEMS`, `INTERNAL_API_USAGES`, `OVERRIDE_ONLY_API_USAGES`, taken from the pinned IPGP source, not memory) — its value is guarding against a *future* IPGP weakening its own default, not gating scheduled-for-removal. `DEPRECATED_API_USAGES` stays out. Both compose independently with `ides { recommended() }` (which resolved to 242/243/251/252 here).

### Deferrals are documented, not silent

The three deferred APIs are plain-deprecated (not for-removal) and each is blocked by the 242 floor: `invokeAction` → `performAction` needs 252; `ReadAction.compute` → `computeBlocking` needs a post-242 build and the code is never in a coroutine context so the nudge is inert; `createShellWidget` → Reworked Terminal API absent at 242 and the site already degrades gracefully. They are recorded here and tracked for the eventual floor bump, rather than force-fixed via reflection/version-branching for no real safety gain (none will hard-break).

## Risks / trade-offs

- **Compile-at-242 vs run-at-262 blind spot.** `build`/`test` only see 242. The authoritative check is `./gradlew verifyPlugin` against the `recommended()` IDE set, with the new failure level active (the pre-push hook already runs it on `com.intellij.*` diffs; run locally under `caffeinate`). Note the automated guard bites only on the IDEs `recommended()` actually resolves to (the forRemoval markers are read from those builds' bytecode) — it is not a proof over every build in the range. The full-range (242 *and* 262) floor-safety of the chosen replacements was established separately by direct inspection of the platform sources at both branch points; the guard is the ongoing canary against *new* forRemoval usage, not a substitute for that inspection.
- **The guard is a canary, not a cage.** A future IDE's newly-introduced scheduled-for-removal API we happen to call would now fail CI — which is the point (catch it at the diff, not at a Marketplace warning or a user crash).

## Out of scope

- Any `sinceBuild` bump (floor stays 242, preserving 2024.2+ across the whole IDE family).
- The three deferred deprecations.
- Any user-facing behavior or UI change — all three edits are like-for-like.
