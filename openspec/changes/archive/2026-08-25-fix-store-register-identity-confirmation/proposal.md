## Why

The **Register Existing Store** coordination action is a dead-end against the currently-supported OpenSpec CLI line (verified against 1.10.0). `CoordinationService.registerStore(path)` shells a fixed-shape `store register <path> --json`. Since the 1.6–1.9 range the CLI gained a confirmation gate for the "create store identity metadata for a healthy OpenSpec root" path, controlled by a `--yes` flag. Registering a healthy OpenSpec root that is not *yet* a store — the action's primary use case — returns:

```json
{ "store": null, "registry": null, "created_files": [],
  "status": [{ "severity": "error",
    "code": "store_register_identity_confirmation_required",
    "message": "Turn this OpenSpec root into store '<name>'?",
    "fix": "Run interactively or pass --yes to create <root>/.openspec-store/store.yaml." }] }
```

The plugin already *surfaces* this envelope — it pops a failure dialog showing the `message` and `--yes` fix text — but gives the user **no way to act on it**. The prior behavior deliberately "never auto-confirms" (encoded in uiSmoke Journey 6 Stop C), which is the right instinct — turning someone's OpenSpec project into a store writes `.openspec-store/store.yaml` into their repo and deserves a confirmation — but it was left as a dead-end: the user is told to pass `--yes` with no affordance to do so.

The fix makes the existing confirmation **actionable**: when the CLI reports `store_register_identity_confirmation_required`, present a real Yes/Cancel confirmation carrying the CLI's own message and fix; on confirmation, re-invoke with `--yes`; on cancel, abort quietly. This preserves the deliberate "confirm first" intent, mirrors the plugin's established idiom for CLI `--yes` gates (the destructive removals already pair a confirmation dialog with `--yes`), and warns the user that registration will create store-identity metadata.

The defect stayed invisible to CI because no test asserts the register invocation's argv — the confirmation-envelope *parser* is contract-tested (real captures at 1.6.0 and 1.7.0), but nothing verifies that `registerStore` reaches a success path for a healthy not-yet-a-store root, and uiSmoke Journey 6 asserted the dead-end dialog *as* the expected behavior.

## What Changes

- Make the register confirmation actionable. On `store_register_identity_confirmation_required`, the plugin SHALL present a Yes/Cancel confirmation using the CLI's own `message` and `fix`; on confirmation it SHALL re-invoke `store register <path> --yes --json`; on cancel it SHALL abort without registering and without an error dialog.
- Keep the initial `store register <path> --json` invocation (no `--yes`) as the probe, so a root that needs no confirmation (already a store, or a refusal) behaves exactly as today — the plugin never auto-confirms and never silently writes store metadata.
- Capture real CLI fixtures for the gate under the top-supported CLI fixture directory (the confirmation-required refusal and the `--yes` success envelope), and add contract tests that fail if the probe ever gains `--yes` or the retry ever loses it.
- Update uiSmoke Journey 6 to exercise the actionable confirmation (fire register → confirm → the store registers) instead of asserting the dead-end failure dialog.
- Update the `store-workset-actions` spec's registration-outcome requirement to describe the confirm-then-`--yes` behavior.

**Out of scope (deferred):** the additive-only convenience flags the same audit surfaced — `store register --id`, `store setup --remote`, and `store setup --no-init-git` (all present since 1.5.0). These are non-blocking and need their own dialog/UI work; they are separable follow-ups, intentionally not bundled with this correctness fix.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `store-workset-actions`: the store-registration outcome requirement is extended to cover the CLI's store-identity-confirmation gate — the plugin surfaces an actionable confirmation and, on confirmation, registers the root non-interactively via `--yes`.

## Impact

- `CoordinationService.java` — add `registerStore(String path, boolean confirmIdentity)` (the `--yes` retry); keep `registerStore(String path)` as the no-`--yes` probe; add `WriteResult.identityConfirmationRequired()`.
- `CoordinationPanel.java` — `onRegisterStore` gains the probe → confirmation dialog → retry orchestration; likely extracts `runWrite`'s completion tail into a shared helper.
- `src/test/resources/fixtures/cli/1.10.0/` — new captured fixtures for the confirmation-required refusal and the `--yes` success envelope.
- `StoreWorksetWriteServiceTest` / `StoreWorksetWriteContractTest` — argv-shape guards for both phases + 1.10 parser twins + `identityConfirmationRequired()` coverage.
- `OpenSpecUiSmokeTest.kt` (Journey 6) — Stop C reworked to confirm-then-succeed; release-gated.
- `openspec/specs/store-workset-actions/spec.md` — requirement text updated on archive.
