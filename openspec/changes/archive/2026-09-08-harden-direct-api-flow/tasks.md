## 1. Credential store off the EDT

- [x] 1.1 Add a **has-key cache** to `DirectApiService` (per-provider boolean, warmed off-EDT at project open; invalidated in `AiCredentialStore.storeApiKey`/`removeApiKey`). Make `isConfigured()` read the cache (EDT-safe). Verify by a unit test: store→has-key true, remove→false, and `isConfigured()` never calls `PasswordSafe`.
- [x] 1.2 `OpenSpecSettingsPanel.onProviderChanged`: move the `AiCredentialStore` read off the EDT (`executeOnPooledThread` → `invokeLater(..., ModalityState.stateForComponent(mainPanel))`); keep the combo/mask UI edits on the EDT.
- [x] 1.3 `OpenSpecSettingsPanel.testApiConnection`: move the credential `store`/`get` into the existing `SwingWorker.doInBackground` (already off-EDT alongside the HTTP call).
- [x] 1.4 Sweep the remaining EDT `PasswordSafe` sites (`OpenSpecConfigurable.apply`, `SetupWizardDialog`/`SetupWizardModel`) — route each through the cache or off-EDT. Verify: no `PasswordSafe.get/set` on an EDT path remains.

## 2. Dispose tool-window content and panels

- [x] 2.1 `OpenSpecConsolePanel implements Disposable`; `dispose()` calls `Disposer.dispose(consoleView)`. In `createNormalContent`, `consoleContent.setDisposer(consolePanel)`.
- [x] 2.2 `OpenSpecToolWindowPanel implements Disposable`; reparent its `Alarm`s to `this` and change `project.getMessageBus().connect()` → `connect(this)`; `browseContent.setDisposer(browsePanel)`.
- [x] 2.3 Make `GettingStartedPanel.detectState` a `static State detectState(Project)`; update the instance callers and remove the throwaway construction in `OpenSpecToolWindowFactory` (:29 and :83) so no discarded `Disposer`-registering panel remains.

## 3. Honest cancellation

- [x] 3.1 `WorkflowActionPanel.executeGeneration`: add `if (indicator.isCanceled()) return;` before the result-writing `invokeLater`; add `catch (ProcessCanceledException pce) { throw pce; }` before the generic `catch (Exception)`; route the single-generate Cancel to a real per-run cancel instead of `onCancelGenerateAll()`. Verify: a cancelled single generation writes no file and shows no "Generated" notification, and a `ProcessCanceledException` is not surfaced as "Generation failed".

## 4. ArtifactFileWatcher

- [x] 4.1 Capture the `MessageBusConnection` from `connect(this)` into a field and `disconnect()` it in `dispose()` (idempotent). Optional: guard `fireDetected` re-entrancy with `AtomicBoolean.compareAndSet`. Verify by a unit/integration test that `dispose()` disconnects.

## 5. Docs + spec

- [x] 5.1 At sync, broaden the `edt-compliance` `## Purpose` to cover disposable lifetime alongside threading.
- [x] 5.2 CHANGELOG `## Unreleased → Fixed` entry (no user-facing behavior change beyond the absence of freezes/leaks and a working Cancel).

## 6. Verify

- [x] 6.1 **test-engineer PLAN** (invoke at apply start) — split what is unit-testable (has-key cache, static `detectState`, watcher `disconnect`, cancel guard) from what only `verifyPlugin`/`uiSmoke` can catch (EDT-safety, disposer leaks).
- [x] 6.2 `./gradlew build` green (test + JaCoCo floor).
- [x] 6.3 Run the leak/threading gates: `caffeinate -dimsu ./gradlew verifyPlugin` (green on 242/243/251/252) and `caffeinate -dimsu ./gradlew uiSmoke` — all journeys pass; the tool-window journey builds the disposable panels and reaches clean IDE shutdown with zero `ObjectTree` (leak) / `SlowOperations` (EDT) / ERROR-severity lines. (uiSmoke does not drive the provider-pick credential flow; that EDT path is covered by the structural guards + `AiCredentialStoreTest` + verifyPlugin.)
- [x] 6.4 **intellij-code-reviewer** after implementation (diff touches PSI/VFS/EDT/services/disposers).
