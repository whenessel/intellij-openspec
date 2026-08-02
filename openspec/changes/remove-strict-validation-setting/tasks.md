## 1. Contract fixture (strict CLI verdict)

- [x] 1.1 The `src/test/resources/fixtures/cli/1.6.0/validate-strict-warning-only.json` fixture already exists (captured real `validate --all --strict --json`, a warning-only `valid:false` item). Confirm it covers the CLI-strict verdict; capture an additional strict fixture only if a distinct shape is needed.

## 2. Per-run strict: actions + UI

- [x] 2.1 Add `ValidateTarget.strict` (boolean component) + a `withStrict()` factory; default `false`.
- [x] 2.2 Add `OpenSpecValidateStrictAction extends OpenSpecValidateAction`, overriding `actionPerformed` to call `runValidation(project, ValidateTarget.wholeProject().withStrict(), ...)`. Register `OpenSpec.ValidateStrict` in `plugin.xml` beside `OpenSpec.Validate` in the main menu and the tool-window toolbar; wrap the two in a `popup="true"` group ("Validate ▾") for the compact toolbar dropdown. Description: `Validate with --strict (warnings count as failures) — matches CI's "openspec validate --strict"`.
- [x] 2.3 Leave the Project-View scoped Validate normal-only (scoped strict is out of scope).

## 3. Thread strict through the validate pipeline

- [x] 3.1 `runValidation` takes a `boolean strict` (via the target). CLI path: `cliArgs(target, strict)` appends `--strict`; `commandLine(target, strict)` echoes `openspec validate --all --strict`. The parser already reads each item's `valid` field (reflects the CLI's strict verdict).
- [x] 3.2 CLI-absent verdict flip done at the ACTION level via `OpenSpecValidateAction.applyStrictFallbackVerdict` (cleaner than threading `strict` through the shared `BuiltInValidator`): the built-in stays strict-unaware; the action flips the verdict post-hoc. Non-config warnings fail under strict; config warnings stay non-failing (parity with the CLI-present path). No `strict` field on the shared service.
- [x] 3.3 CLI-absent verdict flip: when strict, the built-in result's verdict is `passed = (errorCount==0 && warningCount==0)`; a pure verdict flip on `ValidationResult`, NOT a re-severity of any issue (warnings stay WARNING).

## 4. Non-silent disclosure

- [x] 4.1 Balloon (`showValidationResults`): strict run title → `Validate (strict)`; a warnings-only strict failure appends `— strict: warnings count as failures` AFTER the `failed (…)` summary (preserve the `passed (`/`failed (` tokens the uiSmoke + screenshot-tour assert on).
- [x] 4.2 Console discloses strict via the echoed command (`openspec validate --all --strict`, from `commandLine`). Did NOT change the `ValidationConsoleFormatter` verdict line (avoids a signature/test-churn cascade); the command echo is sufficient non-silent disclosure alongside the balloon title/suffix.

## 5. change-artifact-missing → always-on WARNING

- [x] 5.1 In `BuiltInValidator.validateSingleChange`, drop the strict escalation of `change-artifact-missing` — it is always a non-failing WARNING. Under a strict run it fails the verdict only via the §3.3 warnings-count rule, remaining labeled WARNING.

## 6. SpecSync guard decouple

- [x] 6.1 In `SpecSyncService`, remove the `isStrictValidation()` read (line ~352). The MODIFIED-targets-missing-requirement case becomes an always-on guard (rule `sync-modified-target-missing`): surface it in the sync **preview** with clear wording (requirement + capability, "delta can't be applied — review before syncing") and skip that operation. No strict/lenient branch.

## 7. Remove the persistent setting + migration notice

- [x] 7.1 Remove the "Strict validation" checkbox from `OpenSpecSettingsPanel`, its accessors, and the `OpenSpecConfigurable` apply/reset wiring; remove `isStrictValidation()`/`setStrictValidation()` from `OpenSpecSettings`.
- [x] 7.2 Keep the persisted `State.strictValidation` field for ONE release as a migration-only read + add a `migratedStrictNotice` guard. On first run: if `strictValidation` was `true`, fire one sticky `OpenSpec.System` notification ("Strict validation is now per-run — use Validate (Strict)") with a link to the action, then set the guard and clear the flag. Default (off) users see nothing. (The platform ignores the orphaned `<option>` on load — no crash.)
- [x] 7.3 Grep-before-removing check: confirm the three readers (`BuiltInValidator`, `SpecSyncService`, settings UI) are all re-homed before deleting the accessors.

## 8. Tests

- [x] 8.1 Contract (CLI-present strict): parse `validate-strict-warning-only.json` — a `valid:false` warning-only item under `--strict` → the plugin verdict is FAILED (already asserted by `CliContractTest`; extend if needed for the strict-action path).
- [x] 8.2 `cliArgs`/`commandLine`: `strict=true` appends `--strict` and echoes it; `strict=false` does not.
- [x] 8.3 Built-in fallback verdict-flip (`BuiltInValidatorTest`, CLI absent): a warning-only result passes under normal, FAILS under strict; warnings stay labeled WARNING (no re-severity); a clean result passes under both.
- [x] 8.4 `change-artifact-missing` is always a WARNING (never ERROR), regardless of strict; under strict it flips the verdict via the warnings-count rule.
- [x] 8.5 SpecSync: a MODIFIED op with an unmatched name always surfaces `sync-modified-target-missing` in the preview and skips the op (no setting dependency).
- [x] 8.6 Settings: the panel no longer exposes a strict checkbox; a stored `strictValidation=true` in `openspec.xml` loads without error and fires the one-time migration notice exactly once.
- [x] 8.7 `./gradlew build` (suite + JaCoCo floor). No `verifyPlugin` risk (two-action approach uses only long-stable action API); the pre-push hook won't trip the verifier unless a new `com.intellij.*` reference is added.

## 9. Docs & fidelity

- [x] 9.1 `CHANGELOG.md` (`## Unreleased`): "Strict validation is now a per-run choice — a 'Validate (Strict)' action (toolbar dropdown / menu) that passes `--strict`, replacing the always-on setting. It's disclosed in the result (never a silent mode)."
- [x] 9.2 Update `docs/feature-reference.md` (the Validate rows + drop the "Strict validation" settings row), `docs/getting-started-copilot.md` (settings section), and the feature-comparison-matrix strict row.
- [x] 9.3 Repo-markdown doc surfaces updated (CHANGELOG, feature-reference, getting-started-copilot, feature-comparison-matrix), leak-checked vendor-neutral. Fuller doc-fidelity pass (internal wiki / knowledge base) deferred — repo markdown is the load-bearing public surface for this change.
- [ ] 9.4 (DEFERRED — release-gate follow-up, not yet added) Add a release-gated uiSmoke journey asserting the `Validate (strict)` disclosure (title + "warnings count as failures" suffix) after invoking `OpenSpec.ValidateStrict`. Reminder: the balloon text is asserted in BOTH the uiSmoke journeys and the screenshot tour — grep both when rewording.
