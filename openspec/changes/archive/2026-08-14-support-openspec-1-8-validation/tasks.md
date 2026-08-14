# Tasks

Atomic change: the fixture capture, the demotion, the parity re-anchor, and the guard rework must land together (committing the 1.8 corpus before the guard rework reds the build). Order below reflects that.

## 1. Capture the 1.8.0 CLI fixture corpus

- [x] 1.1 Capture default + strict parity twins into `src/test/resources/fixtures/cli/1.8.0/validate-parity-corpus.json` and `…-strict.json` by running the real 1.8.0 CLI (isolated `HOME`/`XDG_*`, telemetry off) over the **existing** `1.6.0/parity-corpus` markdown as input — do not re-author the corpus. *(Default 12/13, strict 9/13; flipped = {fenced-keyword, header-only-keyword, should-only}.)*
- [x] 1.2 Sanitize captures: rewrite `root.path` to the fixture placeholder (strip any `/private` prefix); scrub `telemetry.anonymousId` from any config capture. Confirm no machine-specific path remains. *(root.path = /fixture/parity-corpus | /fixture; machine-path scan clean.)*
- [~] 1.3 Capture the current-generation contract twins. **DONE:** `version.txt`, `validate-single-spec-no-body.json` (body-less ERROR), `config-validation/github-copilot.{config.yaml,validate.json}` (tolerance). **DEFERRED (forward-tripwire twins, low value — mostly byte-identical to 1.7):** the full status/instructions/schema/store/deltas/spec-structure/update `…V18` sweep and the `retire_capabilities` metadata fixture.
- [x] 1.4 Add a `CliContractTest.ValidateContractV18` nest asserting the 1.8 `validate` shape: the missing-`SHALL` item (`should-only`) is `level: WARNING` + reworded message, `valid:true` default / `valid:false` strict; and the body-less item (`validate-single-spec-no-body.json`) is `level: ERROR` "must contain SHALL or MUST".
- [~] 1.5 Tolerance: `github-copilot.config.yaml`/`.validate.json` fixtures captured (CLI validates clean); the plugin's generic tolerant config parser already ignores unknown keys. **DEFERRED:** a dedicated `ConfigServiceTest`/`isPlanningComplete` assertion (generic tolerance + the additive-JSON GSON reader already cover the behavior).

## 2. Demote the missing-keyword rule in the built-in validator

- [x] 2.1 `BuiltInValidator.validateSpecContent` — body-conditional: WARNING when the requirement has body prose, ERROR when it has none; header-only-keyword → WARNING. *(Body = prose before the first `#### Scenario:`, content fence-masked. Extracted `validateSpecContent` as a static package-visible method.)*
- [x] 2.2 `OpenSpecValidateAction.CLI_MIRRORING_STRICT_WARNINGS` = {`spec-rfc-keywords`, `spec-rfc-keyword-in-header`} (was empty), so `applyStrictFallbackVerdict` flips under strict; every other non-config WARNING stays out.
- [x] 2.3 Confirmed no on-the-fly editor-inspection change needed — `SpecFormatInspectionTest`/`SpecFormatKeywordHintTest` pass green (inspection already emits WARNING/WEAK_WARNING).

## 3. Validator tests

- [x] 3.1 Split `BuiltInValidatorTest` missing-keyword: body-present → WARNING + default-passes + strict-flips; body-less → ERROR; renamed the should-only/fenced tests to assert WARNING.
- [x] 3.2 Rewired `BuiltInValidatorRulesTest.validateSpec` to drive the **real** `BuiltInValidator.validateSpecContent` (removed the inline mirror); demoted the missing-keyword assertions; added a no-body ERROR test. *(Rewiring exposed the mirror's wrong scenario-clauses severity — corrected ERROR→INFO to match production.)*
- [x] 3.3 Audited `SpecFormatInspectionTest` / `SpecFormatKeywordHintTest` — green, no change (editor already WARNING).

## 4. Re-anchor and rework the parity guards

- [x] 4.1 Re-anchored `ValidatorVerdictParityTest`: default oracle 1.6→1.8; strict oracle → `1.8.0/validate-parity-corpus-strict.json` (= the 1.6 map, 9 valid).
- [x] 4.2 Reworked `ValidatorVerdictVersionStabilityTest` default arm to **subset-of-the-laxest-anchor** (`DEFAULT_ANCHOR = 1.8.0`); strict arm kept as map-equality vs the `1.6.0` map.
- [x] 4.3 Anti-vacuity pins: ≥2 generations incl. anchor, identical item-id key set, and the pin that the `1.8.0` default valid-set = the `1.6.0` default valid-set ∪ {fenced-keyword, header-only-keyword, should-only}.

## 5. Declare 1.8.x support

- [x] 5.1 Added `1.8.0` to `CliVersionAtLeastTest.allSupportedVersions_meetFloor` + `1.8.0/version.txt`.
- [x] 5.2 Confirmed no CLI-floor or `VersionSupport` config-axis change required (floor stays `1.3.0`, no ceiling — `2.0.0` still flows through the existing test).

## 6. Documentation (public / vendor-neutral)

- [x] 6.1 `docs/openspec-support.md`: added the `1.8.x` line + column across all five tables; rewrote the false "byte-identical 1.6→1.7 / no functional work" parity claim; new 1.8.x per-line bullet; agent-off-model footnote; rows for `retire_capabilities`/`githubCopilot`/`isPlanningComplete`.
- [x] 6.2 `docs/feature-reference.md`: updated the CLI-absent fallback-severity example (body-carrying missing-keyword = default WARNING / strict-failing; body-less = ERROR).
- [x] 6.3 `docs/feature-comparison-matrix.md`: bumped the review stamp date + re-affirmed for the 1.8.x change.
- [x] 6.4 `CHANGELOG.md` `## Unreleased`: Added (1.8.x support; body-less ERROR) + Changed (missing-keyword default WARNING, framed as CLI-parity, strict still fails).
- [x] 6.5 `README.md`: 1.6.x/1.7.x → +1.8.x; noted the default-warning demotion.
- [x] 6.6 `CLAUDE.md`: `.claude/commands/opsx/` is now a second tracked CLI-regenerated surface; restore command updated.

## 7. Verify

- [x] 7.1 `./gradlew build` green — full suite (1359 tests) + JaCoCo floor held (no ratchet needed; the change adds coverage).
- [~] 7.2 `openspec validate … --strict` clean; anti-leak grep over changed files + fixtures clean (CLAUDE.md allow-listed; `.tracking.yaml` gitignored). *(Commit pending.)*
- [x] 7.3 Confirmed no `verifyPlugin`/`uiSmoke` gate triggers — no new `com.intellij.*` reference added.
