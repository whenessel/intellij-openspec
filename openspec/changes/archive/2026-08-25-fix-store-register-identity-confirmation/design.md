# Design

## Decision 1 — Two-phase confirm-then-`--yes`, not unconditional `--yes`

The maintainer's choice: when the CLI asks for confirmation, the plugin asks the user, rather than auto-confirming. This preserves the prior deliberate "never auto-confirm" behavior while making it actionable, and it matches the plugin's established idiom for CLI `--yes` gates — the destructive removals (`store remove`, `workset remove`) already pair an explicit confirmation dialog with `--yes`.

Flow:

1. **Probe** (off-EDT): `store register <path> --json` — no `--yes`. Unchanged shape.
2. If the parsed result reports `store_register_identity_confirmation_required`, **confirm** (EDT): a `Messages.showYesNoDialog` carrying the CLI's own `message` ("Turn this OpenSpec root into store '<name>'?") and `fix` text — never hand-written, never raw stderr, consistent with the surface's "surface parsed status only" rule.
3. On **Yes**, **retry** (off-EDT): `store register <path> --yes --json`, then the normal success completion (VFS refresh + reload). On **No**, abort quietly — no registration, no error dialog.
4. Any other probe outcome (already-a-store success, a `store_root_pointer_declared` / `invalid_store_pointer` / `store_register_root_unhealthy` refusal) flows through the existing completion untouched.

The plugin does not know up-front whether confirmation is needed — the CLI has no dry-run/check flag; it signals the gate only by returning the envelope. So probing first (one command) and confirming only on the gate is the correct port of the CLI's own interactive behavior to a GUI. The confirmation path costs two CLI invocations; every other path costs one, as today.

## Decision 2 — Service surface: an overload + a typed helper (no version gating)

- `registerStore(String path)` stays the probe (`store register <path> --json`) and keeps its callers source-compatible; it delegates to `registerStore(path, false)`.
- `registerStore(String path, boolean confirmIdentity)` adds `--yes` when `confirmIdentity` is true.
- `WriteResult.identityConfirmationRequired()` returns true iff any diagnostic carries code `store_register_identity_confirmation_required`, keeping that code string in the coordination layer (alongside the other `store_register_*` codes) rather than leaking it into the panel.

No CLI-version branching: the `--yes` (and `--id`) options have been defined on `store register` **since 1.5.0** — verified by `npx @fission-ai/openspec@1.5.0 store register --help`, which lists both. Only enforcement tightened over generations. Because the flag is accepted on every supported generation, the `--yes` retry never trips commander.js's unknown-option error on an older CLI, and the probe (no `--yes`) is the exact shape those generations already validated. This keeps the fix free of the per-generation special-casing the requirement warns against.

## Decision 3 — A panel-free static `orchestrateRegister` seam

The probe→confirm→retry policy is extracted into a package-private **static** method on `CoordinationPanel`, mirroring the existing panel-free `storeHealthMarkers` idiom, so the whole branch matrix is unit-testable without constructing `CoordinationPanel` (whose constructor needs a live `ActionManager`, installs a popup menu, builds a platform `Tree`, and starts `reload()` on a pooled thread — it is hostile to headless tests, and `TestDialogManager` has no precedent in this repo):

```java
static void orchestrateRegister(CoordinationService svc, String path,
                                Predicate<WriteResult> confirm,   // panel supplies the EDT Yes/No dialog
                                Consumer<WriteResult> complete) {  // panel supplies completeWrite()
    WriteResult probe = svc.registerStore(path);                  // 1-arg, no --yes
    if (probe.identityConfirmationRequired()) {
        if (!confirm.test(probe)) return;                         // NO → silent abort: no retry, no complete
        complete.accept(svc.registerStore(path, true));           // YES → retry WITH --yes
        return;
    }
    complete.accept(probe);                                       // any other outcome → normal completion
}
```

`runWrite`'s `invokeLater` completion tail (CoordinationPanel:598–604 — VFS refresh + `reload()`/`showWriteFailure`) is extracted into a shared `completeWrite(WriteResult)` used by both `runWrite` and `orchestrateRegister`. `onRegisterStore` resolves the path, then off-EDT calls `orchestrateRegister(service, path, this::confirmIdentityOnEdt, this::completeWrite)`. The `confirmIdentityOnEdt` adapter runs `Messages.showYesNoDialog` on the EDT using `probe.message()` + `probe.fix()` — never hand-written, never stderr.

The existing `openspec.uismoke.register.store.root` seam (the platform file chooser can't be driven over the remote Driver SDK) is preserved, and a **new `openspec.uismoke.register.confirm` seam is required**: the smoke Driver cannot answer a Yes/No dialog by clicking (robot clicks don't land; it can only *dispose* a dialog, which cancels = NO), so `confirmIdentityOnEdt` reads that property and auto-answers when set. Both seams are documented together in the `onRegisterStore` javadoc.

## Decision 4 — Fixtures captured from the real 1.10.0 CLI

The bug slipped past CI because no test asserts the register argv and the 1.5.0 success fixture registered a root that already had `.openspec-store/store.yaml`, so the identity-creation path was never exercised. Capture from the installed top-supported CLI (1.10.0) into `src/test/resources/fixtures/cli/1.10.0/`, matching the existing `1.6.0`/`1.7.0` naming:

- `store-register-confirmation-required.json` — the refusal envelope: `store register <root> --json` (no `--yes`) on a healthy OpenSpec root with no `.openspec-store/store.yaml`.
- `store-register-yes-success.json` — the success envelope: `store register <root> --yes --json` on the same root, yielding `created_files: [".openspec-store/store.yaml"]` and `status: []`.

Capture ritual: isolated `XDG_DATA_HOME` (throwaway temp) so the real registry is untouched; fresh `git init` + `openspec init`; run no-`--yes` first (leaves the root store-less) then `--yes`; sanitize every machine-absolute path to `/fixture/…` (the store root → `/fixture/healthy-root`, the XDG registry path → `/fixture/registry/openspec/stores/registry.yaml`); grep the results for `/Users`, `/var/folders`, `/home/` before committing. Document the recipe in `src/test/resources/fixtures/cli/README.md`.

## Testing

Full plan folded into tasks.md. In brief:

- **Argv-shape guards** (the regression backstops), via `mockStatic(CliRunner)` + `cli.verify`: the probe invokes `store register <path> --json` with NO `--yes`; the retry invokes `store register <path> --yes --json`. Both must bite when the respective flag is added/dropped.
- **Parser twins** at 1.10.0 for the two new fixtures, mirroring the 1.6/1.7 confirmation-required tests.
- **`identityConfirmationRequired()`** coverage.
- **Panel probe→confirm→retry** orchestration — unit-tested panel-free against the static `orchestrateRegister` seam (new `CoordinationPanelRegisterFlowTest`): the four branches (no-gate success, gate→YES→retry-creates-identity, gate→NO→silent-abort, non-gate failure), with probe/retry `WriteResult`s produced by `parseWriteEnvelope` over the committed fixtures.
- **uiSmoke Journey 6** reworked from "assert dead-end failure dialog" to the two-phase flow via the new `openspec.uismoke.register.confirm` seam: Stop C confirms→registers, Stop A is the no-confirmation branch (already-a-store, no dialog), Stop B (pointer refusal) unchanged, plus an optional NO-path stop proving the silent abort.

## Out of scope

The additive convenience flags — `store register --id`, `store setup --remote`, `store setup --no-init-git` (all present since 1.5.0) — are deferred to separate follow-ups so this change stays a tight correctness fix.
