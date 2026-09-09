## Context

See proposal.md — Why. The defects were confirmed against the source by jetbrains-platform-guru and reproduced in a sandbox test-drive. Two templates already in this codebase are the correct patterns to copy: **`ExplorePanel`** (implements `Disposable`, and its content sets `exploreContent.setDisposer(explorePanel)` — `OpenSpecToolWindowFactory:114`) and **`OpenSpecSettingsPanel.detectCli`** (capture `ModalityState.stateForComponent` → `executeOnPooledThread` → `invokeLater(..., modality)`).

## Goals / Non-Goals

**Goals:** no `PasswordSafe` access on the EDT; no leaked tool-window disposables; a Cancel that tells the truth; a file-watcher that unsubscribes.

**Non-Goals:** any change to the Direct API request/response contract or the generation result; the UX-connectedness work (chip animation, batch button, revise loop) — that is a separate change; fully interruptible HTTP mid-flight (see Decision 3).

## Decisions

**1. Off-EDT credential access + a has-key cache.**
- Move the `PasswordSafe` get/set in `OpenSpecSettingsPanel.onProviderChanged`/`testApiConnection` off the EDT (fold the Test path's credential I/O into its existing `SwingWorker.doInBackground`; run the provider-change read on a pooled thread → `invokeLater(..., ModalityState.stateForComponent(mainPanel))`).
- Add a **has-key cache** in `AiCredentialStore` (a static app-global `Map<AiProvider,Boolean>` — the correct scope, since keys are provider-scoped and app-global in PasswordSafe), kept fresh by `storeApiKey`/`removeApiKey` and by a cheap synchronous `markHasApiKey(provider, present)`. `DirectApiService.isConfigured()` reads `hasApiKeyCached(...)`, fixing every EDT site (`OpenSpecToolWindowFactory`, `WorkflowActionPanel`, `OpenSpecConfigurable.apply`, SetupWizard) at once.
- *Cold-cache is the sharp edge:* `hasApiKeyCached` must **never block on the EDT**. On a cache miss it reads `PasswordSafe` only when off the EDT (or when there is no Application, i.e. unit tests); on the EDT it schedules an off-EDT warm and returns `false` for now. To keep that fallback rare, the cache is **warmed eagerly off the EDT at project open** in `OpenSpecProjectService.StartupDetection` (a `ProjectActivity`, off-EDT) for the configured provider, so the tool window's `isConfigured()` gate reads a populated cache on first show rather than racing the async warm. EDT-side write paths (`OpenSpecConfigurable.apply`, `SetupWizardModel.persist`) call `markHasApiKey(provider, true)` synchronously before scheduling the blocking keystore write, so a rebuild right after sees Direct API configured.
- *Alternative — `PasswordSafe.getAsync`:* **rejected.** Its signature drifted (`Promise` → `suspend fun` returning `Ephemeral`); it is uncallable from Java and **fails Plugin Verifier across 242→253**. Use the plain synchronous API on a pooled thread.

**2. Dispose tool-window content via the Content disposer.**
- Make `OpenSpecConsolePanel` and `OpenSpecToolWindowPanel` implement `Disposable`; in `dispose()` release the `ConsoleView` (`Disposer.dispose(consoleView)`), and reparent the panel's `Alarm`s and `project.getMessageBus().connect()` to `this` (so they die with the panel). Then `browseContent.setDisposer(browsePanel)` / `consoleContent.setDisposer(consolePanel)` in `createNormalContent` (mirroring Explore).
- Kill the throwaway leak by making `GettingStartedPanel.detectState` a `static State detectState(Project)` — the state check reads only `project`, so `createToolWindowContent` no longer constructs a discarded, `Disposer`-registering instance.

**3. Honest cancellation (minimal-correct).**
- Guard the post-HTTP write/UI with `if (indicator.isCanceled()) return;` before the `invokeLater` that writes the file; rethrow `ProcessCanceledException` with a `catch (ProcessCanceledException pce) { throw pce; }` before the generic `catch`.
- Route the single-generate Cancel to a real per-run cancel keyed by **artifact id** — a `Map<String,ProgressIndicator> activeGenerations`, not a single shared field. Right-clicking Generate on two chips (each mutates `nextArtifactId`) runs both concurrently; a shared field would let one indicator overwrite the other, so cancelling chip A would cancel B. The run registers its indicator under its id and removes it (conditional `remove(id, indicator)`) when it ends; `onCancelGeneration(artifactId)` cancels exactly that run.
- *HTTP interruptibility:* the blocking `HttpClient.send` isn't aborted mid-flight; the run completes in the background but its result is discarded by the `isCanceled` guard. Full interrupt (send on a cancellable future) is a **stretch, deferred** — the guard makes cancel observably correct (no file written, no success UI) which is what the user perceives.

**4. `ArtifactFileWatcher` disconnects.** Capture the `MessageBusConnection` from `connect(this)` into a field; `disconnect()` it in `dispose()` (idempotent). Optional: guard `fireDetected` re-entrancy with `AtomicBoolean.compareAndSet` (harmless today — refresh is idempotent).

## Risks / Trade-offs

- **`edt-compliance` scope broadens** from "threading" to "threading + disposable lifetime" → its `## Purpose` SHALL be broadened when the delta syncs (Purpose edits are made directly on the main spec, not via delta).
- **`verifyPlugin` pre-push gate runs** (new `com.intellij.*` references) → ~3–5 min once IDE archives are cached. Load-bearing: only `verifyPlugin` can catch a platform-API incompatibility these fixes might introduce.
- **Testability is uneven** → EDT-safety and disposal are hard to assert in plain unit tests; rely on `verifyPlugin`'s leak/threading checks, a `uiSmoke` journey where feasible, and unit tests for the parts that are pure (the has-key cache invalidation, the static `detectState`, the watcher `disconnect`). Set the split with test-engineer at apply.

## Migration Plan

Internal robustness fix — no migration, no data change. Rollback is reverting the touched files.
