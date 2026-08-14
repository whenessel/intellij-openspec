## MODIFIED Requirements

### Requirement: CLI verdict is authoritative when the CLI is available

When the OpenSpec CLI is available and a `validate` run completes successfully with parseable output, the plugin SHALL treat the CLI's result as authoritative for the artifacts the CLI validates — specs and change deltas — rather than overriding it with the built-in validator's own opinion. The plugin SHALL derive each CLI-validated item's pass/fail from that item's `valid` field in the CLI output (mirroring the client's own rule `valid = strictMode ? errors===0 && warnings===0 : errors===0`), not by re-deriving pass/fail from the severities of the issues the plugin extracted. The built-in validator SHALL NOT contribute spec or change issues that fail the merged verdict when the CLI has produced a verdict for those artifacts.

Because the CLI's `validate` never reads `openspec/config.yaml`, the built-in validator SHALL remain the sole owner of `config.yaml` validation in all paths. For a whole-project validation with the CLI available, the merged verdict SHALL be `cli.passed() && builtInConfig.passed()`, where `builtInConfig` is the built-in validator scoped to `config.yaml`, and the displayed issues SHALL be the union of the CLI's spec/change issues and the built-in's config issues. For a single-spec or single-change target with the CLI available, the verdict SHALL be the CLI's alone (no config component). When the CLI is unavailable, or its run fails or emits unparseable output, the plugin SHALL fall back to the full built-in validator as the verdict — never to a blind pass.

The never-more-restrictive-than-the-CLI guarantee SHALL be evidenced against captured real CLI output, and SHALL be durable across CLI generations. Because CLI generations may legitimately **relax** a default verdict (1.8.0 demoted the missing-`SHALL`/`MUST` rule to a non-failing warning, so more corpus items validate in default than under 1.6.0/1.7.0), the default-mode invariant SHALL be a **subset-of-the-laxest-anchor** relation rather than map-equality: the plugin's default verdict is proven to match the **laxest captured generation** (the default anchor — currently `1.8.0`), and every captured generation's default valid-set SHALL be a subset of that anchor's valid-set, so the plugin is by transitivity never more restrictive than any captured generation, and any future generation that relaxes a default verdict further than the anchor SHALL surface as a parity failure prompting the anchor to be advanced. Under `--strict` the invariant SHALL remain **map-equality**: every captured generation's per-item `id → valid` map SHALL be identical to the strict anchor (the `1.6.0` default map, which `--strict` reproduces on every generation). This invariant is defined only over generations captured against the current corpus dialect; a generation whose own validation rules predate that dialect legitimately verdicts the corpus differently and is not part of the set.

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
- **WHEN** the captured real CLI `--json` fixture for the default anchor generation (`1.8.0`) is parsed
- **THEN** the plugin's per-item pass/fail SHALL equal each item's `valid` field for every item in the fixture

#### Scenario: Verdict parity is stable across every captured CLI generation
- **WHEN** more than one CLI generation's captured `validate --all --json` over the shared verdict-parity corpus is committed as a fixture
- **THEN** in default mode every captured generation's per-item valid-set SHALL be a subset of the laxest anchor generation's valid-set (currently `1.8.0`), so the plugin — proven to match the anchor — is by transitivity never more restrictive than any captured generation, and any future generation that relaxes a default verdict further than the anchor SHALL fail the guard until the anchor is advanced; under `--strict` every captured generation's per-item `id → valid` map SHALL be identical to the strict anchor (the `1.6.0` map)
- **AND** the guard SHALL surface a vacuous pass as a failure: it SHALL require at least two captured generations including the anchor, SHALL require every captured corpus to carry the identical item-id key set, and SHALL pin the known cross-generation divergence — the `1.8.0` default valid-set SHALL equal the `1.6.0` default valid-set plus exactly the requirements the missing-`SHALL` demotion newly validates — so an empty discovery, a classpath regression, a dropped fixture, or a silent collapse of that divergence fails loudly rather than passing on nothing

### Requirement: Spec format validation

The plugin SHALL validate spec files for structural completeness: title heading, requirement blocks, RFC 2119 keywords, and scenario format. When the built-in validator is the verdict (the CLI is absent — see "CLI verdict is authoritative when the CLI is available"), its default-mode verdict for a main spec SHALL NOT exceed the verdict `openspec validate` produces for the same file in its default mode — the plugin MUST NOT be more restrictive than the client it wraps. Accordingly, on the main-spec path (`openspec/specs/**`), the conditions the real CLI reports `valid` in default SHALL be demoted below ERROR: a missing `# Title` heading SHALL be a WARNING (rule `spec-title-required`), because the CLI requires no H1 and derives the spec name from the directory; and a scenario missing a `WHEN` or `THEN` clause SHALL be an INFO (rule `spec-scenario-clauses`), because the CLI performs no clause-structure check. A requirement with no `#### Scenario:` block SHALL remain an ERROR (rule `spec-scenario-required`): the real CLI reports such a requirement `valid:false` — it enforces `.min(1)` scenarios as a schema-level error in addition to its WARNING guide — so demoting it would make the plugin laxer than the CLI. A missing requirement block (`spec-requirement-required`) SHALL remain ERROR, because the CLI errors on it. The **RFC-keyword rules SHALL be severity-conditional on the requirement having a body, matching OpenSpec CLI 1.8's demotion**: a requirement whose body has prose but no `SHALL`/`MUST` whole word (rule `spec-rfc-keywords`, or `spec-rfc-keyword-in-header` when the only keyword sits in the requirement header) SHALL be a **WARNING** in default mode — the 1.8 CLI reports such a requirement `valid` in default — re-promoted to a failing verdict under a strict run via the CLI-mirroring strict-warning set (see "Per-run strict validation"); a requirement with **no body prose at all** (header plus scenarios only) SHALL remain an **ERROR** (`spec-rfc-keywords`), because the 1.8 CLI still errors on a body-less requirement. Requirement headers (`### Requirement:`) SHALL be recognized case-insensitively on the header token, matching OpenSpec CLI 1.4+ parsing. Requirement-keyword presence SHALL be satisfied only by `SHALL` or `MUST` as whole words (matching the CLI's rule on every supported generation — `SHOULD`/`MAY` do not satisfy it), SHALL be evaluated against the requirement body with fenced code blocks masked (matching CLI 1.6 semantics — a keyword appearing only inside a code fence does not satisfy the check), and the header-only-keyword case SHALL still produce a targeted diagnostic directing the author to move the keyword onto a body line, with a quick-fix offered, at the body-conditional severity above. Scenario presence (`#### Scenario:`) SHALL likewise be evaluated with fenced code blocks masked. The inspection SHALL guard against zero-length PSI elements and invalid offsets before creating problem descriptors.

#### Scenario: Missing title heading warns, does not fail
- **WHEN** a spec file has no `# Title` heading and the built-in validator is the verdict
- **THEN** the validator SHALL report a WARNING with code `spec-title-required`, and the file's verdict SHALL NOT fail on that account, because the CLI requires no H1

#### Scenario: Otherwise-valid spec without a title passes
- **WHEN** a spec file has no `# Title` heading but has a requirement block with a SHALL/MUST body line and at least one scenario
- **THEN** the built-in validator SHALL report the `spec-title-required` WARNING and still return a passing verdict (no ERROR), matching the CLI which validates the same file clean

#### Scenario: Missing requirement block
- **WHEN** a spec file has no `### Requirement:` section
- **THEN** the validator SHALL report an ERROR with code `spec-requirement-required`

#### Scenario: SHOULD-only requirement is flagged
- **WHEN** a requirement's body contains `SHOULD` or `MAY` but neither `SHALL` nor `MUST` as a whole word
- **THEN** the validator SHALL report the missing-keyword rule (`spec-rfc-keywords`) as a **WARNING** in default mode and the file's verdict SHALL NOT fail on that account, matching the 1.8 CLI which reports such a body-carrying requirement `valid` in default

#### Scenario: Keyword only inside a code fence is not accepted
- **WHEN** a requirement's only `SHALL`/`MUST` occurrence sits inside a fenced code block
- **THEN** the validator SHALL report the missing-keyword rule (`spec-rfc-keywords`) as a **WARNING** in default mode (the fenced keyword does not satisfy the check after fence masking, but the requirement has a body, so it is advisory in default), matching CLI 1.6 fence masking and the 1.8 default demotion

#### Scenario: Keyword only in the requirement header is flagged
- **WHEN** a requirement's only `SHALL`/`MUST` occurrence sits in the `### Requirement:` header line and its body has prose without either keyword
- **THEN** the validator SHALL report a targeted `spec-rfc-keyword-in-header` diagnostic as a **WARNING** in default mode, offering a quick-fix to move the keyword onto a body line, and the file's verdict SHALL NOT fail on that account in default, matching the 1.8 CLI

#### Scenario: Requirement with no body prose is an error
- **WHEN** a main-spec requirement has a `#### Scenario:` block but no body prose at all between its `### Requirement:` header and the first scenario
- **THEN** the validator SHALL report the missing-keyword rule (`spec-rfc-keywords`) as an **ERROR**, failing the file's verdict in both default and strict modes, matching the 1.8 CLI which still errors on a body-less requirement

#### Scenario: Requirement without a scenario is an error
- **WHEN** a main-spec requirement (outside a change delta) has a SHALL/MUST body line but no `#### Scenario:` block
- **THEN** the validator SHALL report an ERROR with code `spec-scenario-required`, and the file's verdict SHALL fail, matching the CLI which reports such a requirement `valid:false`

#### Scenario: Scenario header only inside a code fence is an error
- **WHEN** a requirement's only `#### Scenario:` header sits inside a fenced code block (so the requirement is effectively scenarioless after fence masking)
- **THEN** the validator SHALL report the `spec-scenario-required` ERROR, applying CLI 1.6 fence-aware scenario counting

#### Scenario: Scenario missing WHEN or THEN is informational only
- **WHEN** a `#### Scenario:` block is missing a `WHEN` clause, a `THEN` clause, or both
- **THEN** the validator SHALL report an INFO with code `spec-scenario-clauses` and the file's verdict SHALL be unaffected, because the CLI performs no clause-structure validation

#### Scenario: Verdict parity with the 1.6 CLI on demoted main-spec rules
- **WHEN** a main spec that the captured real 1.6.0 CLI reports `valid` (e.g. one that is untitled, or has a clauseless scenario, but no ERROR-class problem) is validated by the built-in validator in default mode
- **THEN** the built-in validator SHALL also report it passing, so the plugin's fallback verdict matches the CLI's `valid` flag for every such case

### Requirement: Per-run strict validation

The plugin SHALL offer strict validation as a **per-invocation choice**, not a persistent setting, mirroring the upstream OpenSpec CLI's `validate --strict` flag (which has no durable state). There SHALL be no persisted strict preference — no settings toggle, no `config.yaml` key, no stored state. Strict SHALL be selected per run via a dedicated `OpenSpec.ValidateStrict` action (distinct from the default `OpenSpec.Validate`), exposed as a compact toolbar dropdown alongside the default Validate, as an adjacent menu leaf, and discoverable via Find Action. The default Validate SHALL remain non-strict.

A strict run SHALL be threaded as a per-invocation boolean, never stored on any shared service. When the CLI is available, the plugin SHALL pass `--strict` to `openspec validate` and derive the verdict from the CLI's own `valid` field (which under `--strict` is `errors===0 && warnings===0`). When the CLI is absent, the built-in fallback verdict SHALL fail under strict on any ERROR, and additionally on a WARNING **only when that warning is in the CLI-mirroring strict-warning set** — a warning the real CLI itself emits and fails on under `--strict`. That set SHALL contain the **missing-`SHALL`/`MUST` warning** (`spec-rfc-keywords`, and `spec-rfc-keyword-in-header` for the header-only variant): the 1.8 CLI reports a body-carrying requirement lacking the keyword `valid` in default but `valid:false` under `--strict`, so a strict fallback SHALL likewise flip on it. Every other built-in non-config WARNING SHALL remain outside the set — each is either a plugin-invented lint the CLI never emits (`spec-title-required`, `change-artifact-missing`, `change-schema-incompatible`, `delta-removed-fields`) or a condition the CLI reports as an ERROR rather than a warning (`delta-spec-sections`) — so a fallback whose only issues are those other WARNINGs SHALL PASS under strict; the plugin MUST NOT be more restrictive than the client it wraps. Any flip is a verdict change only, without re-labeling any issue's severity. `config.yaml` guidance stays non-failing in both modes (the CLI never fails on config).

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
- **THEN** the verdict SHALL flip to FAILED only if at least one WARNING is in the CLI-mirroring strict-warning set (a warning the real CLI also emits and fails on under `--strict`) — currently the missing-`SHALL`/`MUST` warning (`spec-rfc-keywords`/`spec-rfc-keyword-in-header`); a fallback whose WARNINGs are each either a plugin-invented lint the CLI never emits (`spec-title-required`, `change-artifact-missing`, `change-schema-incompatible`, `delta-removed-fields`) or a condition the CLI reports as an ERROR rather than a warning (`delta-spec-sections`) SHALL PASS, because the plugin MUST NOT be more restrictive than the CLI
- **AND** when a flip does occur, the warning issues SHALL remain labeled WARNING (not re-severity-ed), and the result SHALL disclose that strict counted the warnings as failures

#### Scenario: Missing-keyword warning flips a strict fallback verdict
- **WHEN** a strict run executes with the CLI absent and the built-in fallback's only issue on an otherwise-valid main spec is a body-carrying requirement lacking `SHALL`/`MUST` (a `spec-rfc-keywords` WARNING)
- **THEN** the default-mode verdict SHALL pass but the strict-mode verdict SHALL FAIL, because the missing-keyword warning is in the CLI-mirroring strict-warning set, matching the 1.8 CLI which reports that spec `valid` in default and `valid:false` under `--strict`

#### Scenario: CLI-absent strict does not flip on a plugin-invented lint warning
- **WHEN** a strict run executes with the CLI absent and the built-in fallback's only issue on an otherwise-valid item is a plugin-invented lint WARNING (e.g. `change-artifact-missing` on a change with no `tasks.md`, or `spec-title-required` on a title-less spec) that the real CLI reports strict-valid
- **THEN** the strict fallback verdict SHALL PASS for that item, matching the CLI's strict verdict

#### Scenario: Strict discloses itself in the summary
- **WHEN** a strict run completes
- **THEN** the summary notification title SHALL read `Validate (strict)`; a warnings-only failure SHALL append `— strict: warnings count as failures` after the `failed (…)` summary, preserving the `failed (` token

#### Scenario: One-time migration notice for a prior strict-on user
- **WHEN** the plugin upgrades and detects that the removed persistent `strictValidation` setting had been enabled
- **THEN** it SHALL show a single one-time notification directing the user to the per-run `Validate (Strict)` action, then never show it again; a user on the default (off) SHALL see no notice
