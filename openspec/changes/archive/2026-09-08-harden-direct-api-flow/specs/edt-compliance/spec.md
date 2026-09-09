## ADDED Requirements

### Requirement: Credential store I/O off the EDT

`PasswordSafe` read/write operations are blocking (`@RequiresBackgroundThread`) and SHALL NOT be invoked on the EDT. Any UI handler that stores, retrieves, or checks for an API key SHALL perform the `PasswordSafe` access on a background thread (`executeOnPooledThread` or inside an existing background worker), and SHALL post the resulting UI update back to the EDT via `invokeLater(...)` with an explicit `ModalityState` (e.g. `ModalityState.stateForComponent(...)`) so it is not deferred behind a modal dialog. A "does a key exist" check used to gate UI SHALL be an EDT-safe operation (a cached boolean), not a synchronous keystore read.

#### Scenario: Provider selection and connection test do credential I/O off the EDT
- **WHEN** the user changes the Direct API provider dropdown or clicks Test in Settings
- **THEN** the `PasswordSafe` read/write SHALL run on a background thread and the field/label update SHALL be posted back via `invokeLater(..., ModalityState)`, so no blocking keystore access runs on the EDT

#### Scenario: Configuration check does not read the keystore on the EDT
- **WHEN** a UI path needs to know whether Direct API is configured (e.g. to show the delivery selector entry or gate Fast-Forward)
- **THEN** it SHALL read a cached has-key flag rather than performing a synchronous `PasswordSafe.get()` on the EDT

### Requirement: Disposable lifetime for tool-window content and panels

Every tool-window `Content` whose component owns a `Disposable` (a `ConsoleView`, `Alarm`, or `MessageBusConnection`) SHALL be given a disposer via `Content.setDisposer(...)`, and the owning panel SHALL implement `Disposable` and release those resources in `dispose()`. Subscriptions and alarms SHALL be parented to a `Disposable` that is torn down when the content is removed, so nothing is left registered under the application root disposable. A component instantiated only for a transient check SHALL NOT register itself in the `Disposer` (or SHALL be disposed immediately after use).

#### Scenario: Tool-window content is disposed on teardown
- **WHEN** the OpenSpec tool window's content is rebuilt or the project closes
- **THEN** the Browse and Console panels SHALL be disposed via their content disposer — releasing the `ConsoleView`, `Alarm`s, and VFS message-bus connection — with no "registered … but wasn't disposed" leak reported against the plugin

#### Scenario: Transient detection instance does not leak
- **WHEN** the plugin needs only to detect tool-window state (not display a panel)
- **THEN** it SHALL obtain that state without constructing a `Disposer`-registering panel that is then discarded

### Requirement: Honest cancellation of cancellable background tasks

A background task declared cancellable SHALL honor cancellation: it SHALL check `ProgressIndicator.isCanceled()` before applying its results (writing files, showing success UI), and any cancel affordance SHALL either actually abort the work or not be offered. `ProcessCanceledException` SHALL never be swallowed by a broad `catch` — it SHALL be rethrown so the platform can complete cancellation.

#### Scenario: Cancelling a single generation aborts before applying results
- **WHEN** the user cancels a single Direct API generation
- **THEN** the task SHALL NOT write the artifact file or show a "Generated" notification for the cancelled run

#### Scenario: ProcessCanceledException is not reported as a failure
- **WHEN** a `ProcessCanceledException` is raised inside a generation task's `try` block
- **THEN** it SHALL be rethrown rather than caught and surfaced as a "Generation failed" error
