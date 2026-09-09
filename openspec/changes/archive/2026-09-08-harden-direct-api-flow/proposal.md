## Why

A UX + platform-mechanics assessment of the Direct API generation flow (prompted by dogfooding the plugin on Gemini's free tier) surfaced real, deterministic platform defects — confirmed against the source and reproduced by a sandbox test-drive (SEVEREs in the IDE log):

- **Disposer memory leaks.** The Browse and Console tool-window content are added without a `Content` disposer (unlike Explore, which does it right), so the Console's `ConsoleView` is never disposed and the Browse panel's `Alarm`s + VFS message-bus subscription accumulate on every tool-window rebuild — a growing leak within a session and a `SEVERE` at shutdown. A throwaway `GettingStartedPanel` created only for state detection also leaks.
- **Credential I/O on the EDT.** `AiCredentialStore` reads/writes `PasswordSafe` (a `@RequiresBackgroundThread` API) on the EDT from the settings panel (provider-change and Test) and, via `isConfigured()`, from ~8 UI paths — a real freeze risk on Keychain/KeePass and a `SlowOperations` `SEVERE`.
- **Cancellation is cosmetic.** Single-generate runs a *cancellable* `Task.Backgroundable`, but nothing checks `indicator.isCanceled()`, the HTTP call isn't interruptible, and the Cancel item routes to the Generate-All flag — so "Cancel" does nothing until the 5-minute timeout. The broad `catch (Exception)` also swallows `ProcessCanceledException`.
- **`ArtifactFileWatcher`** never disconnects its app-wide `VFS_CHANGES` subscription on dispose, so dead subscriptions accumulate one per generation.

These affect every user, not just Direct API users, and are the kind of platform-correctness debt that compounds silently.

## What Changes

- **Credential store off the EDT.** Move `PasswordSafe` get/set to a background thread with `invokeLater(..., ModalityState)` (the pattern `OpenSpecSettingsPanel.detectCli` already uses); make `isConfigured()` an EDT-safe cached has-key check instead of a synchronous keystore read.
- **Proper disposal.** Set a `Content` disposer on Browse/Console/Getting-Started content; make the owning panels `Disposable` and release their `ConsoleView`/`Alarm`s/message-bus connection; make `GettingStartedPanel.detectState` static so no throwaway instance is registered in the Disposer.
- **Honest cancellation.** Guard the post-HTTP write/UI with `indicator.isCanceled()`, rethrow `ProcessCanceledException` before the generic catch, and make the single-generate Cancel actually cancel (or not be offered).
- **`ArtifactFileWatcher`** captures and `disconnect()`s its `MessageBusConnection` on dispose.
- **BREAKING**: none — no user-facing behavior change except the *absence* of freezes/leaks and a working Cancel.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `edt-compliance`: add requirements extending the plugin's platform-correctness rules to (1) credential-store I/O off the EDT, (2) disposable-lifetime discipline for tool-window content/panels and their subscriptions, and (3) honest cancellation of cancellable background tasks.

## Impact

- **Code:** `ai/AiCredentialStore`, `ai/DirectApiService` (has-key cache), `settings/OpenSpecSettingsPanel` + `settings/OpenSpecConfigurable`, `toolwindow/OpenSpecToolWindowFactory` + `OpenSpecToolWindowPanel` + `OpenSpecConsolePanel` + `GettingStartedPanel`, `toolwindow/WorkflowActionPanel` (cancel), `util/ArtifactFileWatcher`. No new external dependency.
- **Platform:** all fixes use long-stable APIs (`executeOnPooledThread`, `invokeLater(Runnable, ModalityState)`, `Content.setDisposer`, `Disposer.dispose`, `MessageBusConnection.disconnect`, `ProgressIndicator.isCanceled`) — 2024.2+ safe. ⚠️ Do NOT adopt `PasswordSafe.getAsync` (its signature drift fails Plugin Verifier). Because new `com.intellij.*` references are added, the pre-push `verifyPlugin` gate SHALL run.
- **Tests:** disposer/threading correctness is partly assertable in unit tests and partly only via `verifyPlugin`/`uiSmoke` — the test strategy is set with test-engineer at apply.
