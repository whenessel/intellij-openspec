## ADDED Requirements

### Requirement: Per-run strict validation

The plugin SHALL offer strict validation as a **per-invocation choice**, not a persistent setting, mirroring the upstream OpenSpec CLI's `validate --strict` flag (which has no durable state). There SHALL be no persisted strict preference — no settings toggle, no `config.yaml` key, no stored state. Strict SHALL be selected per run via a dedicated `OpenSpec.ValidateStrict` action (distinct from the default `OpenSpec.Validate`), exposed as a compact toolbar dropdown alongside the default Validate, as an adjacent menu leaf, and discoverable via Find Action. The default Validate SHALL remain non-strict.

A strict run SHALL be threaded as a per-invocation boolean, never stored on any shared service. When the CLI is available, the plugin SHALL pass `--strict` to `openspec validate` and derive the verdict from the CLI's own `valid` field (which under `--strict` is `errors==0 && warnings==0`). When the CLI is absent, the built-in fallback verdict SHALL fail under strict on any ERROR or any non-config WARNING — as a verdict flip only, without re-labeling any issue's severity. `config.yaml` guidance stays non-failing in both modes (the CLI never fails on config), so strict means the same thing whether or not the CLI is present.

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
- **WHEN** a strict run executes with the CLI absent and the built-in fallback produces a result with zero errors but one or more warnings
- **THEN** the verdict SHALL be FAILED (strict flips the threshold), the warning issues SHALL remain labeled WARNING (not re-severity-ed), and the result SHALL disclose that strict counted the warnings as failures

#### Scenario: Strict discloses itself in the summary
- **WHEN** a strict run completes
- **THEN** the summary notification title SHALL read `Validate (strict)`; a warnings-only failure SHALL append `— strict: warnings count as failures` after the `failed (…)` summary, preserving the `failed (` token

#### Scenario: One-time migration notice for a prior strict-on user
- **WHEN** the plugin upgrades and detects that the removed persistent `strictValidation` setting had been enabled
- **THEN** it SHALL show a single one-time notification directing the user to the per-run `Validate (Strict)` action, then never show it again; a user on the default (off) SHALL see no notice

## MODIFIED Requirements

### Requirement: Change validation

The plugin SHALL validate each active change's `.openspec.yaml` for schema compatibility with the project's declared version. Schema-name recognition SHALL be CLI-runtime-driven on the same principle as `Config validation`: a change schema is "recognized" when it appears in the union of the built-in fallback set (`VersionSupport.V1_2.getValidSchemas()`) and the CLI-runtime set from `SchemaService.listSchemas()`. The validator SHALL use `VersionSupport.getValidSchemas()` only as the built-in fallback portion of that union, not as the canonical valid-set. The `change-artifact-missing` rule (a plugin-invented lint for a missing required artifact — the CLI never checks artifact-file presence) SHALL always be a non-failing WARNING; it SHALL NOT be escalated to ERROR by any persistent setting. Under a per-run strict validation (see `Per-run strict validation`) it fails the verdict only via the strict warnings-count-as-failures rule, remaining labeled WARNING.

#### Scenario: Proposal required
- **WHEN** a change has no `proposal.md`
- **THEN** the validator SHALL report an ERROR with rule `change-proposal-required`

#### Scenario: Required artifacts for version
- **WHEN** the declared version requires specific artifacts and a change is missing one
- **THEN** the validator SHALL report a WARNING with rule `change-artifact-missing` — always a non-failing WARNING, never escalated to ERROR by a persistent setting

#### Scenario: Change schema incompatible with project version
- **WHEN** a change's `.openspec.yaml` declares a schema that is neither in the built-in fallback set nor in `SchemaService.listSchemas()`
- **THEN** the validator SHALL report a WARNING with rule `change-schema-incompatible`. The warning text SHALL list the known-set and indicate CLI status so the user understands why a custom-forked schema might not be visible.

#### Scenario: Custom-forked schema accepted for change when CLI lists it
- **WHEN** the user has forked a custom schema via `openspec schema fork` AND a change's `.openspec.yaml` declares that custom schema
- **THEN** the validator SHALL NOT report `change-schema-incompatible`
