## 1. Fixtures — capture real CLI output first (contract-test discipline)

- [x] 1.1 Build a small corpus under `src/test/resources/fixtures/cli/` exercising the cases that matter: a main spec with no `# Title`, a main-spec requirement with no scenario, a scenario missing WHEN/THEN, a fully-valid control, plus a whole-project run whose specs are all valid (to prove CLI-clean stays clean).
- [x] 1.2 Run the real 1.6.0 CLI (`openspec validate <item> --json` / `--all --json`) against that corpus with an isolated `XDG_DATA_HOME`, capture the `--json` output, sanitize machine-specific paths, and commit it. Record the CLI version alongside the fixture.
- [x] 1.3 Confirm each captured item's `valid` flag matches the intended verdict (untitled → valid; clauseless scenario → valid; control → valid; **scenarioless requirement → invalid**, since the CLI enforces `.min(1)` scenarios), so the fixture encodes the CLI's real default-mode behavior, not an assumption.

## 2. CLI-authoritative merge (primary)

- [x] 2.1 In `OpenSpecValidateAction.runValidation`, when the CLI is available and the run succeeds with parseable JSON: use the CLI result as authoritative for specs/changes, and scope the built-in validator to `config.yaml` only for the config component (`validator.validateConfig()`), rather than running the full built-in validator into the verdict.
- [x] 2.2 Add a CLI-authoritative combine to `ValidationResult` (e.g. `mergeCliAuthoritative(cli, builtInConfig)`): verdict = `cli.passed() && builtInConfig.passed()` for whole-project; issues = union(CLI spec/change issues, built-in config issues). For a single spec/change target, verdict = CLI only (no config component).
- [x] 2.3 Preserve the fallback: when the CLI is unavailable, throws, exits abnormally, or emits unparseable output, use the full built-in validator's result (never a blind pass). Keep the existing text-output fallback path consistent.
- [x] 2.4 In `CliOutputParser.parseJsonOutput`, derive each item's pass/fail from that item's `valid` field rather than re-deriving from extracted-issue severities; continue extracting issues for display.

## 3. Built-in validator severity demotions (fallback-path parity)

- [x] 3.1 In `BuiltInValidator.validateSpecFile`, change `spec-title-required` from ERROR to WARNING.
- [x] 3.2 Change `spec-scenario-clauses` (WHEN/THEN) from ERROR to INFO.
- [x] 3.3 Keep `spec-scenario-required` at ERROR — do NOT demote. Verified against the real 1.6.0 CLI: a scenarioless main-spec requirement is `valid:false` (a Zod `.min(1)` schema ERROR in `base.schema.js` alongside the WARNING guide), so demoting would make the plugin laxer than the CLI. The delta path's `delta-requirement-scenario` also stays ERROR.
- [x] 3.4 Verify the RFC-keyword rules (`spec-rfc-keywords`, `spec-rfc-keyword-in-header`) and `spec-requirement-required` remain ERROR — unchanged, faithful to the CLI.

## 4. Config validation is non-failing

- [x] 4.1 In `BuiltInValidator.validateConfig`, demote `config-schema-required` from ERROR to WARNING and remove the redundant `config-field-required` loop. Verified against the real CLI: `openspec validate` never fails on any `config.yaml` state (missing/empty/unknown schema, even malformed YAML all validate clean), so config checks must never fail the verdict. Config validation now emits no ERROR at all.
- [x] 4.2 Mirror the demotion in `ConfigValidationInspection` (missing-schema squiggle ERROR → WARNING). Leave `VersionSupport.getRequiredConfigFields()` in place for future baselines but no longer wired to a duplicate check; inline comments explain both.

## 5. strictValidation documentation

- [x] 5.1 Add a code comment on the `strictValidation` setting (and its consumers in `BuiltInValidator`/`SpecSyncService`) clarifying it is plugin-only and never passed to the CLI as `--strict`.
- [x] 5.2 Ensure the settings-panel label/description conveys "plugin-side: warnings become errors (not the CLI's --strict)".

## 6. Tests

- [x] 6.1 Contract test (CLI-present path): parse the captured fixtures and assert that a CLI-clean project stays clean in the plugin even for inputs the built-in validator would flag (untitled spec, scenarioless requirement); assert the plugin's per-item verdict equals each item's `valid` flag.
- [x] 6.2 Merge test: whole-project with the CLI reporting all specs valid but `config.yaml` missing `schema:` → verdict fails on `config-schema-required` (built-in owns config); single-item target defers entirely to the CLI; failed/unparseable CLI run falls back to the full built-in validator.
- [x] 6.3 Fallback unit tests for `BuiltInValidator` (CLI absent): untitled-but-valid spec passes with a `spec-title-required` WARNING; clauseless scenario passes with a `spec-scenario-clauses` INFO; missing `schema` yields exactly one ERROR (`config-schema-required`) and explicitly no `config-field-required`. Each demotion test asserts BOTH the severity AND `result.passed()==true`.
- [x] 6.4 Regression: a scenarioless main-spec requirement still FAILS with a `spec-scenario-required` ERROR (verified CLI `valid:false`); a genuinely-broken spec (missing requirement, SHOULD-only keyword) and a scenarioless ADDED delta requirement still fail (ERROR where the CLI errors). The captured-CLI `ValidatorVerdictParityTest` must stay green.
- [x] 6.5 `CliOutputParser` test: a `valid:false` warning-only item is read as failing; a `valid:true` warning-bearing item is read as passing.
- [x] 6.6 Run `./gradlew build` (suite + JaCoCo floor). Ratchet the coverage floor up if the new tests raise it.

## 7. Docs & fidelity

- [x] 7.1 Update `CHANGELOG.md` (`## Unreleased`) with a user-facing note: "Validation now defers to the OpenSpec CLI's own verdict when the CLI is available, so the IDE no longer reports errors `openspec validate` wouldn't. When the CLI is absent, the built-in validator matches the CLI's default-mode severities (a missing spec title and WHEN/THEN clause hints are now warnings/info rather than errors)."
- [x] 7.2 Run the doc-fidelity pass for the `validation` capability across the relevant surfaces (repo markdown; internal wiki/KB as applicable), keeping public surfaces vendor-neutral.
