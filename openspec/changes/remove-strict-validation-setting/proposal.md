## Why

The plugin's `strictValidation` is a **persistent settings checkbox** ("warnings become errors"), but on-model and UX review found that model wrong on two counts. Upstream OpenSpec models strict only as a **per-invocation `openspec validate --strict` flag** with no durable state anywhere (no config key, no env var); a persisted "strict mode" invents state the client can't represent — the same off-model pattern as the removed `@spec` scorecard. And a hidden set-once global that silently flips the validation verdict makes IDE results **non-reproducible**: a developer and CI disagree for an invisible reason. Compounding it, the plugin's "strict" currently escalates a *plugin-invented* rule (`change-artifact-missing`) the CLI never checks — so it shares only a name with the CLI's `--strict`. This change replaces the persistent setting with a **per-run strict choice**, mirroring how the client actually models it.

## What Changes

- **Remove the persistent `strictValidation` setting** — the Settings checkbox, the `isStrictValidation()`/`setStrictValidation()` accessors, and the `configurable` wiring. The persisted state field is dropped (the platform silently ignores the orphaned `<option>` on load; no migration needed for correctness).
- **Add a per-run "Validate (Strict)" action** (`OpenSpec.ValidateStrict`, a subclass of the Validate action passing `strict=true`). It appears as a compact **toolbar dropdown** ("Validate ▾" → normal / strict) and as an adjacent **menu leaf**, and is discoverable in Find Action. The default Validate stays a frictionless one-click. The Project-View scoped Validate stays normal-only (scoped strict is niche; whole-project is the CI-parity case).
- **Strict is threaded per-invocation**, never stored: a `boolean strict` flows through `runValidation` → `cliArgs` (append `--strict`, so the console echoes `openspec validate --all --strict`) and through the built-in validator. When the CLI is present its `--json` `valid` field already reflects the strict verdict (parser covered by the existing `validate-strict-warning-only.json` fixture). When the CLI is absent, the built-in fallback flips the **verdict** (`passed = strict ? errors==0 && warnings==0 : errors==0`) without re-labeling any issue — so strict means the same thing in both modes.
- **Strict is never silent.** A strict run's balloon title reads `Validate (strict)` and, when it fails on warnings only, the body appends `— strict: warnings count as failures` (the `passed (`/`failed (` tokens the uiSmoke + screenshot-tour assert on are preserved); the console echoes the `--strict` command and its verdict line names strict.
- **`change-artifact-missing` becomes an always-on non-failing WARNING**, decoupled from strict. It is a plugin-invented lint the CLI never runs; making it an honest WARNING (rather than a strict-escalated fake ERROR) is more truthful and loses no signal.
- **The SpecSync "MODIFIED targets a missing requirement" block is decoupled from strict** into its own always-on, clearly-named sync-safety guard, surfaced in the Sync preview (the real decision point) with the "strict mode" framing dropped. This is a data-safety concern (an unresolvable MODIFIED delta genuinely can't be applied), not a validation-strictness one — and it was a third, non-Validate reader of the setting being removed.
- **One-time migration notice** for the minority who had the checkbox ON: a single sticky notification ("Strict validation is now per-run") pointing them at the new action. Users with the default (off) see nothing.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `validation`: Introduces a per-run strict validation choice (the `OpenSpec.ValidateStrict` action + non-silent disclosure + CLI-absent verdict flip) replacing the persistent `strictValidation` setting; `change-artifact-missing` becomes an always-on non-failing WARNING.
- `spec-sync`: The MODIFIED-targets-missing-requirement block is decoupled from the removed strict setting into its own always-on sync-safety guard surfaced in the sync preview.

## Impact

- **Code:** new `OpenSpecValidateStrictAction`; `OpenSpecValidateAction.runValidation`/`cliArgs`/`commandLine` + `ValidateTarget` gain a `strict` flag; `BuiltInValidator` verdict path gains a `strict` parameter and `change-artifact-missing` becomes WARNING; `SpecSyncService` drops the strict branch for its own guard; `OpenSpecSettings`/`OpenSpecSettingsPanel`/`OpenSpecConfigurable` lose the checkbox + accessors; `plugin.xml` gains the action + toolbar group. No public API added beyond `AnAction` subclasses.
- **Tests:** a captured `validate --all --strict --json` contract case (the fixture exists) proving the CLI-present strict verdict; built-in fallback verdict-flip unit tests; `change-artifact-missing`-as-WARNING and SpecSync-guard tests; a release-gated uiSmoke journey asserting the `Validate (strict)` disclosure.
- **Platform compatibility:** two-action approach uses only long-stable `AnAction`/`ActionGroup` API — no verifier risk; continues to support IntelliJ IDEA 2024.2+.
- **Behavior:** the *outcome* of strict is unchanged (warnings fail); it moves from a hidden persistent mode to a visible per-run choice. No project's default (non-strict) verdict changes.
