## Why

The plugin runs its own `BuiltInValidator` and merges it with the CLI as `passed = builtIn.passed() && cli.passed()` — an AND that lets a built-in ERROR override a clean CLI. So a project that `openspec validate --all` reports **clean** can still fail in the plugin, violating the standing principle that the plugin must never be more restrictive than the client it wraps. The root fix is to stop treating the built-in validator as a co-equal verdict when the real CLI is present: **when the CLI ran, defer to it** for what it validates, and keep the built-in validator as the fallback for CLI-less environments. An audit against the real 1.6.0 CLI confirmed both the merge smell and the specific severity drifts in the fallback path.

## What Changes

**Primary — the CLI is authoritative when present:**
- When the OpenSpec CLI is available and its `validate` run succeeds, its verdict SHALL be authoritative for the artifacts it validates (specs and changes). The built-in validator SHALL NOT contribute spec/change ERRORs that override a clean CLI. The plugin derives each item's pass/fail from the CLI's own `valid` field, not by re-deriving severity.
- The built-in validator retains ownership of **`config.yaml` validation only** (the CLI's `validate` never reads config.yaml), so config checks still run alongside a CLI verdict. Net whole-project verdict when the CLI is present: `cli.passed() && builtInConfig.passed()`.
- When the CLI is **absent or its run fails**, the built-in validator is the full fallback verdict, as today.

**Secondary — make the CLI-absent fallback honest (severity parity):** so the fallback verdict also never exceeds the CLI's default-mode verdict, demote only the rules the real CLI reports `valid` for:
- `spec-title-required` (missing `# Title`) ERROR → WARNING (the CLI requires no H1 — verified `valid:true`). No `## Purpose`-required ERROR is added — the principle forbids being *more* strict.
- `spec-scenario-clauses` (WHEN/THEN) ERROR → INFO (the CLI has no such check — verified `valid:true`).
- Config validation is made non-failing: `config-schema-required` ERROR → WARNING, and the redundant `config-field-required` for `schema` is removed. Verified against the real CLI — `openspec validate` never fails on any `config.yaml` state (missing/empty/unknown schema, even malformed YAML all validate clean; upstream defaults a missing schema to `spec-driven`). So config checks are non-failing hygiene nudges only; failing on them would make the plugin stricter than the client.
- **`spec-scenario-required` stays ERROR.** Contrary to the initial audit, capturing the real 1.6.0 CLI shows a scenarioless main-spec requirement is `valid:false` — it fires a Zod `.min(1)` ERROR (`base.schema.js`) in addition to the WARNING guide (`validator.js`). Demoting it would make the plugin *laxer* than the CLI, so it is left as ERROR (and the delta path's `delta-requirement-scenario` stays ERROR too).

**Out of scope — `strictValidation`:** this change deliberately does not touch the plugin's `strictValidation` setting. On-model and UX review found the persistent setting is off-model (upstream models strict only as a per-invocation `--strict` flag with no durable state, and the plugin's setting escalates a plugin-invented rule rather than the CLI's strict rules). Reworking it — likely removing the persistent setting in favor of a per-run strict choice — is tracked as a separate `remove-strict-validation-setting` change so this parity fix stays focused.

Not in scope: no per-CLI-version rule engine and no version selector — deferring to the live CLI already gives real parity for every version, and this session established there is no reliable per-project CLI-version stamp to key rules off. The `version:`/`profile:` config-pollution cleanup (the plugin's scaffolding writes plugin-only fields into `config.yaml`) is a separate follow-up.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `validation`: Introduces a CLI-authoritative merge — when the CLI validates specs/changes, its verdict wins and the built-in validator contributes only `config.yaml` checks; the built-in validator remains the full fallback when the CLI is absent, with the two main-spec severities the CLI reports valid (`spec-title-required` → WARNING, `spec-scenario-clauses` → INFO) demoted so that fallback verdict never exceeds the CLI's (`spec-scenario-required` stays ERROR — the CLI fails it); and the redundant `config-field-required`-for-`schema` ERROR is removed. (`strictValidation` is left untouched — its rework is a separate change.)

## Impact

- **Code:** `OpenSpecValidateAction.runValidation` (the merge/deferral), `ValidationResult` (a CLI-authoritative combine that keeps built-in config issues), `CliOutputParser` (verdict from each item's `valid` field), `BuiltInValidator` (config-only path when the CLI is present; demoted main-spec severities; config double-error removal). No public API or extension-point change.
- **Tests:** Contract cases against **captured real 1.6.0 CLI `--json`** proving that a CLI-clean project stays clean in the plugin even when the built-in validator would have flagged something; plus fallback-path cases (CLI absent) for the demoted severities and the config single-error. This class of drift is exactly what hand-authored fixtures miss.
- **Platform compatibility:** No change; continues to support IntelliJ IDEA 2024.2+.
- **Behavior:** Strictly loosening — a project that was clean never becomes newly failing. The CLI-present path can only *remove* spurious built-in failures; the fallback path only demotes severities.
