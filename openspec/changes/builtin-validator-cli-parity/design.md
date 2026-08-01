## Context

The plugin ships a Java reimplementation of OpenSpec's validation rules (`BuiltInValidator`) so validation works when the CLI is absent. When the CLI *is* present, `OpenSpecValidateAction.runValidation` runs the built-in validator first and merges the CLI result via `ValidationResult.merge`, where `allPassed = builtIn.passed() && cli.passed()`. That AND is the root defect: a built-in ERROR reds the merged verdict even when the CLI ran clean, so a project `openspec validate --all` reports valid can fail in the plugin. This is the reported symptom (CLI clean on the command line, plugin red in the IDE).

Two things are true at once, and the fix addresses both. First, the *merge* is wrong: when the client we wrap has actually run and produced a verdict, we should trust it for what it covers rather than overriding it with our own reimplementation. Second, the built-in validator's *severities* have drifted stricter than the CLI on the main-spec path — which matters whenever the CLI is absent and the built-in is the only verdict. A parallel audit against the installed 1.6.0 client confirmed each drift from `dist/core/validation/validator.js` and `constants.js`.

The CLI's `validate` command validates specs and change deltas but **never reads `config.yaml`** (verified: zero references in `validate.js`/`core/validation/`). So config validation is the one area the built-in validator must always own — there is no CLI verdict to defer to there.

## Goals / Non-Goals

**Goals:**
- When the CLI is present and succeeds, the plugin's verdict for specs/changes equals the CLI's own `valid` verdict — the built-in validator cannot override a clean CLI.
- The built-in validator still contributes `config.yaml` checks when the CLI is present (the CLI doesn't cover them).
- When the CLI is absent, the built-in fallback's default-mode verdict never exceeds the CLI's default-mode verdict for the same input.
- Demotions preserve the diagnostic as guidance (WARNING/INFO) rather than deleting it.
- Contract-tested against captured real CLI output so the CLI-clean-stays-clean guarantee and the fallback severities can't silently regress.

**Non-Goals:**
- No per-CLI-version rule engine and no version selector. Deferring to the live CLI already yields real parity for every installed version; and this session established there is no reliable on-disk stamp of a project's authored CLI version to branch rules on. The built-in fallback tracks a single current generation (1.6).
- Not adding any check where the plugin is currently *laxer* than the CLI (e.g. `## Purpose`-required, delta-body SHALL/MUST). The principle is one-directional: never *more* strict.
- Not passing `--strict` to the CLI, and not changing what the plugin's `strictValidation` setting does (only documenting it).
- Not doing the `version:`/`profile:` config-pollution cleanup here (separate follow-up).

## Decisions

**1. The CLI verdict is authoritative when the CLI is available (the headline).**
Replace the symmetric `builtIn.passed() && cli.passed()` merge with a CLI-authoritative combine:
- **CLI present & run succeeded:** for a whole-project target, the verdict is `cli.passed() && builtInConfig.passed()`, where `builtInConfig` is the built-in validator scoped to `config.yaml` only. The displayed issues are the CLI's spec/change issues unioned with the built-in's config issues. For a single spec/change target, the verdict is purely the CLI's (no config component). The built-in validator's spec/change rules do not run into the verdict when the CLI covered them.
- **CLI absent or run failed:** the full built-in validator (with the demoted severities from Decision 4) is the verdict, exactly as the fallback does today.

Implementation: `runValidation` calls `validator.validateConfig()` for the config component instead of the full built-in when the CLI is available, and a new `ValidationResult` combine (e.g. `mergeCliAuthoritative(cli, builtInConfig)`) replaces `merge` on that path. `merge` (the AND) is retained only where two built-in results are combined, if anywhere; the CLI path no longer uses it. Alternative considered: keep the AND but demote every drifted built-in severity to match the CLI. Rejected as the *primary* mechanism — it makes correctness depend on perfect, perpetual hand-maintained severity parity against a moving upstream target; deferring to the CLI is robust by construction. (We still do the demotions, but as the fallback-path safety net, not the load-bearing fix.)

**2. Derive the CLI verdict from each item's `valid` field.** `CliOutputParser.parseJsonOutput` currently re-derives `passed` from the severities of the issues it extracted, and only extracts issues from `valid==false` items. Reading `item.valid` directly is the faithful mapping to the CLI's own rule (`valid = strictMode ? errors===0 && warnings===0 : errors===0`) and is what makes "the CLI is authoritative" literally true, including under a hypothetical `--strict` run. This is a contract-test target against captured `--json`.

**3. The built-in validator owns `config.yaml`, but non-failingly.** Because the CLI never validates config, the built-in config checks run in both paths — but purely as display/guidance. Empirically, `openspec validate` never fails on *any* `config.yaml` state: a missing, empty, or unrecognized `schema`, and even malformed YAML, all validate clean (upstream tolerates a missing schema and defaults to `spec-driven`; the Zod `schema.min(1)` is applied leniently — a missing field defaults rather than rejects). So `config-schema-required` is a WARNING, not an ERROR, and config validation emits no ERROR at all and never fails a verdict. An earlier draft kept it as ERROR on the false premise that a missing schema "breaks the client"; empirical capture disproved that, so failing on it would make the plugin stricter than the client it wraps. `builtInConfig.passed()` is therefore always true; the config component contributes warnings for display, never a verdict change.

**4. Demote only the main-spec severities the real CLI reports valid (fallback-path parity).** In `BuiltInValidator.validateSpecFile`: `spec-title-required` ERROR→WARNING (CLI requires no H1 — captured `valid:true`) and `spec-scenario-clauses` ERROR→INFO (the CLI has no clause-structure check — captured `valid:true`). No `## Purpose`-required ERROR is added (that would be *stricter* than the plugin's current behavior). These only affect the CLI-absent verdict and the editor inspections' text, but keep the fallback honest.

**`spec-scenario-required` stays ERROR — corrected after empirical capture.** The initial audit (reading `validator.js`) concluded a scenarioless main-spec requirement was only a WARNING (`REQUIREMENT_NO_SCENARIOS`). Capturing the real 1.6.0 CLI showed the item is actually `valid:false`: the requirements schema enforces `.min(1)` scenarios as a Zod ERROR (`base.schema.js`) *in addition to* the WARNING guide. Demoting it would make the built-in fallback *laxer* than the CLI (the plugin would pass a spec `openspec validate` fails) and would break the existing captured-CLI `ValidatorVerdictParityTest`. So it stays ERROR, and the delta path's `delta-requirement-scenario` stays ERROR. This is a concrete instance of why the project contract-tests against captured real output rather than inferred source.

**5. Remove the redundant `config-field-required` ERROR for `schema`.** `getRequiredConfigFields()` returns `{schema}` only, so the required-fields loop's sole effect is a duplicate ERROR for a missing `schema:` that `config-schema-required` already reports. Remove the loop; `config-schema-required` is the single source. `getRequiredConfigFields()` stays on `VersionSupport` for future baselines.

**6. Document `strictValidation` as plugin-only.** A note in the spec and a code comment clarifying it escalates plugin WARNINGs to ERRORs within the built-in validator and is never the CLI's `--strict`. No behavior change.

## Risks / Trade-offs

- **[Deferring to the CLI hides a real problem the built-in would have caught]** → Accepted and correct by design: if the client we wrap considers the project valid, so do we. The built-in's stricter opinions were the *bug*. Config checks (the one area the CLI doesn't cover) still run.
- **[Losing config validation if we deferred too far]** → Mitigated by Decision 3: the built-in always validates `config.yaml`, even when the CLI is present; deferral applies only to specs/changes.
- **[A malformed/misparsed CLI run silently passing]** → The CLI-authoritative path applies only when the run *succeeded* and produced parseable JSON; a failed or unparseable run falls back to the full built-in validator (current behavior), never to a blind pass.
- **[Fallback corpus captured from 1.6.0 drifts when the user's CLI differs]** → Known contract-test discipline: re-capture when the CLI version changes; the parity test fails loudly. The fallback targets the stable default-mode verdict.
- **[Config validation being non-failing means a genuinely misconfigured project isn't blocked]** → Accepted and correct: `openspec validate` itself never fails on config (verified), so blocking would be stricter than the client. Config issues remain visible as WARNINGs (console + editor inspection) to guide the user without failing the verdict.

## Migration Plan

Pure verdict/severity change, no data migration. Rollback is reverting the diff. Because the change only *loosens* the verdict (CLI-present path can only remove spurious built-in failures; fallback path only demotes), no previously-passing project becomes failing.
