## 1. Capture real 1.10.0 register fixtures

- [x] 1.1 In an isolated `XDG_DATA_HOME`/`HOME` env (telemetry off) so the real global registry is untouched: `git init` + `openspec init` a fresh healthy root, then run `store register <root> --json` (no `--yes`) FIRST (leaves the root store-less) and `store register <root> --yes --json` SECOND. Save both under `src/test/resources/fixtures/cli/1.10.0/` as `store-register-confirmation-required.json` and `store-register-yes-success.json`.
- [x] 1.2 Sanitize every machine-absolute path to `/fixture/…` matching the existing `1.6.0`/`1.7.0` convention: store root → `/fixture/healthy-root`, the XDG registry path → `/fixture/registry/openspec/stores/registry.yaml`. Grep the two files for `/Users`, `/var/folders`, `/home/`, and the temp base before committing — zero machine paths may remain.
- [x] 1.3 Confirm the captured shapes: the no-`--yes` file has `status[0].code == "store_register_identity_confirmation_required"` (with a `fix` mentioning `--yes`); the `--yes` file has `status: []`, `store.root`, and `created_files` containing `.openspec-store/store.yaml`.
- [x] 1.4 Add a `1.10.0/` register subsection to `src/test/resources/fixtures/cli/README.md` (provenance + the isolated-XDG capture recipe), following the per-generation manifest style.

## 2. Service — probe/retry overload + typed helper (`CoordinationService`)

- [x] 2.1 Add `registerStore(String path, boolean confirmIdentity)`: `confirmIdentity=true` → `runStoreWrite(false, …, "store", "register", path, "--yes", "--json")`; `false` → the existing no-`--yes` shape. Keep `registerStore(String path)` delegating to `registerStore(path, false)` (the probe) so existing callers stay source-compatible.
- [x] 2.2 Add `WriteResult.identityConfirmationRequired()` — a derived accessor returning true iff any `diagnostics` entry has code `store_register_identity_confirmation_required`. Keeps the code string in the coordination layer, out of the panel.
- [x] 2.3 No CLI-version branching: `--yes` is accepted on `store register` from 1.5.0 onward (verified), so the retry is safe across the whole supported range and the probe shape is unchanged.

## 3. Panel — panel-free `orchestrateRegister` seam + confirm dialog (`CoordinationPanel`)

- [x] 3.1 Extract `runWrite`'s `invokeLater` completion tail (CoordinationPanel:598–604 — VFS refresh + `reload()`/`showWriteFailure`) into a shared `completeWrite(WriteResult)`; have `runWrite` call it.
- [x] 3.2 Add package-private **static** `orchestrateRegister(CoordinationService svc, String path, Predicate<WriteResult> confirm, Consumer<WriteResult> complete)`: probe → if `identityConfirmationRequired()` then `confirm.test(probe)` (NO → return, silent abort; YES → `complete.accept(svc.registerStore(path, true))`) → else `complete.accept(probe)`. Panel-free by construction (mirrors `storeHealthMarkers`).
- [x] 3.3 Rewrite `onRegisterStore` (both the `openspec.uismoke.register.store.root` preset branch and the file-chooser branch): resolve path → `executeOnPooledThread` → `orchestrateRegister(service, path, this::confirmIdentityOnEdt, this::completeWrite)`.
- [x] 3.4 `confirmIdentityOnEdt(WriteResult probe)`: first honor the new `openspec.uismoke.register.confirm` smoke seam (`"yes"`/`"no"` → boolean) when set; otherwise `Messages.showYesNoDialog` on the EDT using `probe.message()` + `probe.fix()` verbatim (never hand-written, never stderr), Yes/Cancel with a "Create Store"-style affirmative button. Document both seams in the `onRegisterStore` javadoc.

## 4. Tests — argv guards, parser twins, branch matrix

- [x] 4.1 `StoreWorksetWriteServiceTest`: add a `fixture110(name)` loader and three `mockStatic(CliRunner)` + `cli.verify` tests (benign-default-answer form): (a) `registerStore(path)` invokes `store register <path> --json`, `--yes` variant `never()`, and the confirmation fixture yields `identityConfirmationRequired()==true`; (b) `registerStore(path, true)` invokes `store register <path> --yes --json` and the success fixture yields `success()` + `createdFiles` containing `.openspec-store/store.yaml`; (c) delegation guard — `registerStore(path, false)` produces the identical no-`--yes` argv as the probe.
- [x] 4.2 `StoreWorksetWriteContractTest`: add 1.10 parser twins `parsesReal110StoreRegisterIdentityConfirmationRequired` / `parsesReal110StoreRegisterYesSuccessCreatesIdentity`, plus `identityConfirmationRequired()` fixture coverage — true on the confirmation fixture, **false** on the yes-success fixture AND on a different-code refusal (`store-register-pointer-declared.json`), proving the accessor keys on the specific code, not merely "has an error".
- [x] 4.3 New panel-free `toolwindow/CoordinationPanelRegisterFlowTest` (plain JUnit5 + Mockito, mock `CoordinationService`): drive `orchestrateRegister` over all four branches — no-gate success (retry & confirm never called), gate→YES (confirm once, retry once, `complete` gets the identity-created success), gate→NO (retry & `complete` never called — silent abort), non-gate failure (confirm never called, `complete` gets the failure). Feed probe/retry `WriteResult`s via `parseWriteEnvelope` over the committed fixtures, not hand-built results.
- [x] 4.4 Before landing, confirm the guards bite: temporarily strip `--yes` from the 2-arg method and add it to the probe → `./gradlew test --tests '*StoreWorksetWriteServiceTest' --tests '*CoordinationPanelRegisterFlowTest'` must go red → revert.

## 5. uiSmoke Journey 6 (release-gated) — intentional break

- [x] 5.1 Add the `openspec.uismoke.register.confirm` seam handling (task 3.4) and document it alongside the store.root seam. **Intentional break:** the old Stop C asserted the confirmation *failure dialog*; the two-phase fix converts it to a confirmed *success*, so Journey 6 must change in-step or the release gate reds.
- [x] 5.2 Stop C: set `openspec.uismoke.register.confirm=yes` + `register.store.root=brandNewRoot` (`withIdentity=false`), fire register, assert the `brand-new-store` row renders with no `unhealthy openspec-root` marker (identity created via `--yes`).
- [x] 5.3 Stop A: keep `healthyRoot` (`withIdentity=true`), clear the confirm seam, reframe as the no-confirmation branch — re-registering an already-identity root completes via the probe with no dialog; assert healthy row, no confirmation dialog.
- [x] 5.4 Stop B (pointer refusal): unchanged; ensure the confirm seam is unset/`no` so the pointer refusal is proven not to route through confirmation.
- [x] 5.5 (Optional) NO-path micro-stop: a second `withIdentity=false` root with `register.confirm=no` → assert no new row and no "Coordination Action Failed" dialog (silent-abort end-to-end).

## 6. Spec + verify

- [x] 6.1 Confirm the `store-workset-actions` delta (MODIFIED "Store registration outcome semantics") matches the implemented behavior: probe without `--yes`, actionable confirmation on the gate, `--yes` retry on confirm, silent abort on cancel.
- [x] 6.2 `openspec validate fix-store-register-identity-confirmation --strict` clean.
- [x] 6.3 `./gradlew build` green — full suite + JaCoCo floor. Re-run `jacocoTestCoverageVerification`; only ratchet a counter if its measured value rises by >~0.005 (unlikely). No new `com.intellij.*` API is introduced (`Messages.showYesNoDialog` already used), so `verifyPlugin` is not implicated — but run the pre-push gate normally.
- [x] 6.4 uiSmoke (`caffeinate -dimsu ./gradlew uiSmoke` locally — the authoritative gate; CI ui-smoke is a known false-red) green for Journey 6.
- [x] 6.5 Run the standard staged-file leak-grep for local-infrastructure identifiers before commit; keep any tracker IDs in the gitignored sidecar, never in tracked files.
