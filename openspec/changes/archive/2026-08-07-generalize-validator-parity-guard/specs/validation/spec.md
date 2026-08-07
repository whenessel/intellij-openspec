## MODIFIED Requirements

### Requirement: CLI verdict is authoritative when the CLI is available

When the OpenSpec CLI is available and a `validate` run completes successfully with parseable output, the plugin SHALL treat the CLI's result as authoritative for the artifacts the CLI validates — specs and change deltas — rather than overriding it with the built-in validator's own opinion. The plugin SHALL derive each CLI-validated item's pass/fail from that item's `valid` field in the CLI output (mirroring the client's own rule `valid = strictMode ? errors===0 && warnings===0 : errors===0`), not by re-deriving pass/fail from the severities of the issues the plugin extracted. The built-in validator SHALL NOT contribute spec or change issues that fail the merged verdict when the CLI has produced a verdict for those artifacts.

Because the CLI's `validate` never reads `openspec/config.yaml`, the built-in validator SHALL remain the sole owner of `config.yaml` validation in all paths. For a whole-project validation with the CLI available, the merged verdict SHALL be `cli.passed() && builtInConfig.passed()`, where `builtInConfig` is the built-in validator scoped to `config.yaml`, and the displayed issues SHALL be the union of the CLI's spec/change issues and the built-in's config issues. For a single-spec or single-change target with the CLI available, the verdict SHALL be the CLI's alone (no config component). When the CLI is unavailable, or its run fails or emits unparseable output, the plugin SHALL fall back to the full built-in validator as the verdict — never to a blind pass.

The never-more-restrictive-than-the-CLI guarantee SHALL be evidenced against captured real CLI output, and SHALL be durable across CLI generations: the plugin's verdict is proven against one **anchor generation**, and the CLI's own verdicts over a shared parity corpus SHALL be shown identical across every generation captured against that corpus, so plugin-vs-generation parity follows by transitivity for each captured generation without cloning the plugin-vs-CLI harness per version. This invariant is defined only over generations captured against the current corpus dialect; a generation whose own validation rules predate that dialect legitimately verdicts the corpus differently and is not part of the map-equality set.

#### Scenario: Clean CLI is not overridden by the built-in validator
- **WHEN** the CLI is available and reports the project's specs/changes `valid`, but the built-in validator's own spec/change rules would have flagged an ERROR for one of them (any residual stricter-than-CLI rule)
- **THEN** the plugin's whole-project verdict SHALL NOT fail on account of that built-in spec/change ERROR — the CLI verdict is authoritative for specs/changes and the built-in validator's spec/change rules do not contribute to the verdict when the CLI covered them

#### Scenario: Whole-project verdict combines CLI specs/changes with built-in config
- **WHEN** a whole-project validation runs with the CLI available
- **THEN** the merged verdict SHALL be `cli.passed() && builtInConfig.passed()`, and the reported issues SHALL be the union of the CLI's spec/change issues and the built-in validator's `config.yaml` issues

#### Scenario: Config warnings surface but do not fail a clean-CLI verdict
- **WHEN** the CLI reports all specs/changes valid and `openspec/config.yaml` is missing its `schema:` field
- **THEN** the built-in `config-schema-required` WARNING SHALL be surfaced for display alongside the CLI's verdict, and the merged whole-project verdict SHALL pass — config checks are non-failing, so the built-in validator never reds a project the CLI reports clean

#### Scenario: Single-item verdict defers entirely to the CLI
- **WHEN** a single spec or change is validated with the CLI available
- **THEN** the verdict SHALL be the CLI's `valid` result for that item, with no config component merged in

#### Scenario: Failed or unparseable CLI run falls back to the built-in validator
- **WHEN** the CLI is available but the `validate` run throws, exits abnormally, or emits output that is not parseable
- **THEN** the plugin SHALL use the full built-in validator's result as the verdict, not a blind pass

#### Scenario: Verdict parity against captured CLI output
- **WHEN** the captured real CLI `--json` fixture for the anchor generation is parsed
- **THEN** the plugin's per-item pass/fail SHALL equal each item's `valid` field for every item in the fixture

#### Scenario: Verdict parity is stable across every captured CLI generation
- **WHEN** more than one CLI generation's captured `validate --all --json` over the shared verdict-parity corpus is committed as a fixture
- **THEN** every captured generation's per-item `id → valid` map SHALL be identical to the anchor generation's map, so the plugin — proven to match the anchor generation — is by transitivity never more restrictive than any captured generation, and any future generation that tightens a verdict on the corpus SHALL fail the guard
- **AND** the guard SHALL surface a vacuous pass as a failure: it SHALL require at least two captured generations including the anchor, and it SHALL require every captured corpus to carry the identical item-id key set, so an empty discovery, a classpath regression, or a dropped fixture fails loudly rather than passing on nothing

### Requirement: Per-run strict validation

The plugin SHALL offer strict validation as a **per-invocation choice**, not a persistent setting, mirroring the upstream OpenSpec CLI's `validate --strict` flag (which has no durable state). There SHALL be no persisted strict preference — no settings toggle, no `config.yaml` key, no stored state. Strict SHALL be selected per run via a dedicated `OpenSpec.ValidateStrict` action (distinct from the default `OpenSpec.Validate`), exposed as a compact toolbar dropdown alongside the default Validate, as an adjacent menu leaf, and discoverable via Find Action. The default Validate SHALL remain non-strict.

A strict run SHALL be threaded as a per-invocation boolean, never stored on any shared service. When the CLI is available, the plugin SHALL pass `--strict` to `openspec validate` and derive the verdict from the CLI's own `valid` field (which under `--strict` is `errors==0 && warnings==0`). When the CLI is absent, the built-in fallback verdict SHALL fail under strict on any ERROR, and additionally on a WARNING **only when that warning is in the CLI-mirroring strict-warning set** — a warning the real CLI itself emits and fails on under `--strict`. That set is currently **empty**: every built-in non-config WARNING is either a plugin-invented lint the CLI never emits (`spec-title-required`, `change-artifact-missing`, `change-schema-incompatible`, `delta-removed-fields`) or a condition the CLI reports as an ERROR rather than a warning (`delta-spec-sections`), so a fallback whose only issues are such WARNINGs SHALL PASS under strict — the plugin MUST NOT be more restrictive than the client it wraps. Any flip is a verdict change only, without re-labeling any issue's severity. `config.yaml` guidance stays non-failing in both modes (the CLI never fails on config).

A strict run SHALL never be silent: the summary notification SHALL identify strict (title `Validate (strict)`), a warnings-only strict failure SHALL append an explanation (`— strict: warnings count as failures`) after the pass/fail summary, and the console SHALL echo the `--strict` command (`openspec validate --all --strict`). The literal `passed (` / `failed (` summary tokens SHALL be preserved.

#### Scenario: Strict is a per-run action, not a persistent setting
- **WHEN** the user wants strict validation
- **THEN** they SHALL invoke the `OpenSpec.ValidateStrict` action (toolbar dropdown, menu, or Find Action); there SHALL be no persistent "strict validation" setting anywhere

#### Scenario: Default Validate is non-strict
- **WHEN** the user invokes the default `OpenSpec.Validate`
- **THEN** the run SHALL use the CLI/plugin default verdict (warnings do not fail), with no `--strict` passed to the CLI

#### Scenario: Strict passes --strict to the CLI when present
- **WHEN** a strict run executes with the CLI available
- **THEN** the CLI invocation SHALL include `--strict`, the console SHALL echo `openspec validate --all --strict`, and the verdict SHALL come from each item's `valid` field

#### Scenario: CLI-absent strict flips the verdict on warnings
- **WHEN** a strict run executes with the CLI absent and the built-in fallback produces a result whose only issues are WARNINGs
- **THEN** the verdict SHALL flip to FAILED only if at least one WARNING is in the CLI-mirroring strict-warning set (a warning the real CLI also emits and fails on under `--strict`); a fallback whose WARNINGs are each either a plugin-invented lint the CLI never emits (`spec-title-required`, `change-artifact-missing`, `change-schema-incompatible`, `delta-removed-fields`) or a condition the CLI reports as an ERROR rather than a warning (`delta-spec-sections`) SHALL PASS, because the plugin MUST NOT be more restrictive than the CLI
- **AND** when a flip does occur, the warning issues SHALL remain labeled WARNING (not re-severity-ed), and the result SHALL disclose that strict counted the warnings as failures

#### Scenario: CLI-absent strict does not flip on a plugin-invented lint warning
- **WHEN** a strict run executes with the CLI absent and the built-in fallback's only issue on an otherwise-valid item is a plugin-invented lint WARNING (e.g. `change-artifact-missing` on a change with no `tasks.md`, or `spec-title-required` on a title-less spec) that the real CLI reports strict-valid
- **THEN** the strict fallback verdict SHALL PASS for that item, matching the CLI's strict verdict

#### Scenario: Strict discloses itself in the summary
- **WHEN** a strict run completes
- **THEN** the summary notification title SHALL read `Validate (strict)`; a warnings-only failure SHALL append `— strict: warnings count as failures` after the `failed (…)` summary, preserving the `failed (` token

#### Scenario: One-time migration notice for a prior strict-on user
- **WHEN** the plugin upgrades and detects that the removed persistent `strictValidation` setting had been enabled
- **THEN** it SHALL show a single one-time notification directing the user to the per-run `Validate (Strict)` action, then never show it again; a user on the default (off) SHALL see no notice

### Requirement: Change validation

The plugin SHALL validate each active change's `.openspec.yaml` for schema compatibility with the project's declared version. Schema-name recognition SHALL be CLI-runtime-driven on the same principle as `Config validation`: a change schema is "recognized" when it appears in the union of the built-in fallback set (`VersionSupport.V1_2.getValidSchemas()`) and the CLI-runtime set from `SchemaService.listSchemas()`. The validator SHALL use `VersionSupport.getValidSchemas()` only as the built-in fallback portion of that union, not as the canonical valid-set. The `change-artifact-missing` rule (a plugin-invented lint for a missing required artifact — the CLI never checks artifact-file presence) SHALL always be a non-failing WARNING; it SHALL NOT be escalated to ERROR by any persistent setting, and it SHALL NOT fail the verdict under a per-run strict validation either (see `Per-run strict validation`) — because the CLI never emits it, it is not in the CLI-mirroring strict-warning set, so the plugin's strict fallback does not flip on it and the plugin is never more restrictive than the CLI.

#### Scenario: Proposal required
- **WHEN** a change has no `proposal.md`
- **THEN** the validator SHALL report an ERROR with rule `change-proposal-required`

#### Scenario: Required artifacts for version
- **WHEN** the declared version requires specific artifacts and a change is missing one
- **THEN** the validator SHALL report a WARNING with rule `change-artifact-missing` — always a non-failing WARNING, never escalated to ERROR by a persistent setting and never flipping a strict verdict, because the CLI never emits it

#### Scenario: Change schema incompatible with project version
- **WHEN** a change's `.openspec.yaml` declares a schema that is neither in the built-in fallback set nor in `SchemaService.listSchemas()`
- **THEN** the validator SHALL report a WARNING with rule `change-schema-incompatible`. The warning text SHALL list the known-set and indicate CLI status so the user understands why a custom-forked schema might not be visible.

#### Scenario: Custom-forked schema accepted for change when CLI lists it
- **WHEN** the user has forked a custom schema via `openspec schema fork` AND a change's `.openspec.yaml` declares that custom schema
- **THEN** the validator SHALL NOT report `change-schema-incompatible`
