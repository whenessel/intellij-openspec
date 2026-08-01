## ADDED Requirements

### Requirement: CLI verdict is authoritative when the CLI is available

When the OpenSpec CLI is available and a `validate` run completes successfully with parseable output, the plugin SHALL treat the CLI's result as authoritative for the artifacts the CLI validates — specs and change deltas — rather than overriding it with the built-in validator's own opinion. The plugin SHALL derive each CLI-validated item's pass/fail from that item's `valid` field in the CLI output (mirroring the client's own rule `valid = strictMode ? errors===0 && warnings===0 : errors===0`), not by re-deriving pass/fail from the severities of the issues the plugin extracted. The built-in validator SHALL NOT contribute spec or change issues that fail the merged verdict when the CLI has produced a verdict for those artifacts.

Because the CLI's `validate` never reads `openspec/config.yaml`, the built-in validator SHALL remain the sole owner of `config.yaml` validation in all paths. For a whole-project validation with the CLI available, the merged verdict SHALL be `cli.passed() && builtInConfig.passed()`, where `builtInConfig` is the built-in validator scoped to `config.yaml`, and the displayed issues SHALL be the union of the CLI's spec/change issues and the built-in's config issues. For a single-spec or single-change target with the CLI available, the verdict SHALL be the CLI's alone (no config component). When the CLI is unavailable, or its run fails or emits unparseable output, the plugin SHALL fall back to the full built-in validator as the verdict — never to a blind pass.

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
- **WHEN** the captured real 1.6.0 CLI `--json` fixture is parsed
- **THEN** the plugin's per-item pass/fail SHALL equal each item's `valid` field for every item in the fixture

### Requirement: Plugin strict-validation is independent of the CLI's --strict

The plugin's `strictValidation` setting SHALL be understood and documented as a plugin-only concept, distinct from the OpenSpec CLI's `--strict` flag. When enabled, it escalates selected plugin-side WARNING rules (e.g. `change-artifact-missing`) to ERROR within the built-in validator. The plugin SHALL NOT pass `--strict` to the CLI as a consequence of this setting; the CLI SHALL always be invoked in its default mode. With the setting disabled (the default), the built-in validator's fallback verdict SHALL NOT exceed the CLI's default-mode verdict for any input the CLI validates.

#### Scenario: Strict setting does not alter the CLI invocation
- **WHEN** `strictValidation` is enabled and a Validate run shells out to the CLI
- **THEN** the CLI command SHALL NOT include `--strict`; the setting affects only built-in severities

#### Scenario: Default setting keeps parity with the CLI default verdict
- **WHEN** `strictValidation` is disabled (default) and a project is validated with the CLI absent
- **THEN** the built-in fallback SHALL NOT fail any item that `openspec validate` (default mode) reports valid

## MODIFIED Requirements

### Requirement: Spec format validation

The plugin SHALL validate spec files for structural completeness: title heading, requirement blocks, RFC 2119 keywords, and scenario format. When the built-in validator is the verdict (the CLI is absent — see "CLI verdict is authoritative when the CLI is available"), its default-mode verdict for a main spec SHALL NOT exceed the verdict `openspec validate` produces for the same file in its default mode — the plugin MUST NOT be more restrictive than the client it wraps. Accordingly, on the main-spec path (`openspec/specs/**`), the two conditions the real CLI reports `valid` SHALL be demoted below ERROR: a missing `# Title` heading SHALL be a WARNING (rule `spec-title-required`), because the CLI requires no H1 and derives the spec name from the directory; and a scenario missing a `WHEN` or `THEN` clause SHALL be an INFO (rule `spec-scenario-clauses`), because the CLI performs no clause-structure check. A requirement with no `#### Scenario:` block SHALL remain an ERROR (rule `spec-scenario-required`): the real CLI reports such a requirement `valid:false` — it enforces `.min(1)` scenarios as a schema-level error in addition to its WARNING guide — so demoting it would make the plugin laxer than the CLI. A missing requirement block (`spec-requirement-required`) and the RFC-keyword rules SHALL likewise remain ERROR, because the CLI errors on those. Requirement headers (`### Requirement:`) SHALL be recognized case-insensitively on the header token, matching OpenSpec CLI 1.4+ parsing. Requirement-keyword presence SHALL be satisfied only by `SHALL` or `MUST` as whole words (matching the CLI's rule on every supported generation — `SHOULD`/`MAY` do not satisfy it), SHALL be evaluated against the requirement body with fenced code blocks masked (matching CLI 1.6 semantics — a keyword appearing only inside a code fence does not satisfy the check), and a keyword appearing only in the requirement header SHALL produce a targeted diagnostic directing the author to move the keyword onto a body line, with a quick-fix offered. Scenario presence (`#### Scenario:`) SHALL likewise be evaluated with fenced code blocks masked. The inspection SHALL guard against zero-length PSI elements and invalid offsets before creating problem descriptors.

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
- **THEN** the validator SHALL report the missing-keyword ERROR, because the CLI accepts only SHALL/MUST

#### Scenario: Keyword only inside a code fence is not accepted
- **WHEN** a requirement's only `SHALL`/`MUST` occurrence sits inside a fenced code block
- **THEN** the validator SHALL report the missing-keyword ERROR, matching CLI 1.6 fence masking

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

### Requirement: Config validation

The plugin SHALL validate `openspec/config.yaml` for structural correctness when the file exists, applying only the rules that mirror upstream OpenSpec's own contract. The built-in validator SHALL own `config.yaml` validation in all paths — including when the CLI is present, since the CLI's `validate` never reads `config.yaml`. That ownership is **display-only and non-failing**: config validation SHALL NOT emit any ERROR-severity issue and SHALL NEVER fail a validation verdict, because `openspec validate` never fails on any `config.yaml` state (verified against the real CLI — a missing, empty, or unrecognized `schema`, and even malformed YAML, all validate clean, since upstream tolerates a missing schema and defaults to `spec-driven`). The file itself SHALL be optional — matching upstream's `readProjectConfig() → null // No config is OK` semantics — so a project initialized with `openspec init` and never customized SHALL produce zero config-related validation issues. When the file does exist, validation SHALL enforce only the fields upstream defines in its Zod `ProjectConfigSchema` (`schema`, `context`, `rules`); fields the plugin reads as internal-only extensions (`version`, `profile`) SHALL NOT fire issues when absent. A missing or empty `schema` field SHALL raise a single WARNING (rule `config-schema-required`) — a hygiene nudge that OpenSpec defaults to `spec-driven` — and SHALL NOT be an ERROR and SHALL NOT emit a second, duplicate required-field issue for the same field. Schema-name recognition SHALL be CLI-runtime-driven: the validator SHALL consider a schema name "recognized" when it appears in the union of (a) the built-in fallback set (`VersionSupport.V1_2.getValidSchemas()`) and (b) the CLI-runtime set from `SchemaService.listSchemas()` when CLI is available and supports schema management. Config parsing SHALL be lenient — unrecognized fields and type mismatches SHALL be silently ignored rather than raising parse errors. The config validation inspection SHALL guard against zero-length PSI elements before creating problem descriptors.

#### Scenario: Config absent is accepted
- **WHEN** `openspec/config.yaml` does not exist
- **THEN** the validator SHALL return a clean `ValidationResult` (passed=true, zero issues). This mirrors upstream's `// No config is OK` behavior — the file is augmentation, not a precondition.

#### Scenario: Config parsed successfully
- **WHEN** `openspec/config.yaml` exists and is valid YAML
- **THEN** the validator SHALL proceed with field-level checks

#### Scenario: Config with unknown fields parsed successfully
- **WHEN** `openspec/config.yaml` contains fields not recognized by the Java model
- **THEN** the parser SHALL ignore unknown fields and extract known fields normally

#### Scenario: Config with type mismatches parsed leniently
- **WHEN** an `openspec/config.yaml` field has a YAML type that does not match the expected Java type (e.g., array where string expected)
- **THEN** the parser SHALL skip the mismatched field and use its default value

#### Scenario: Config parse failure returns defaults
- **WHEN** `openspec/config.yaml` is malformed YAML that cannot be parsed at all
- **THEN** the parser SHALL return a default empty config and log at debug level without showing an IDE notification

#### Scenario: Missing schema is a non-failing warning
- **WHEN** `openspec/config.yaml` exists but has no `schema` field (or `schema` is empty)
- **THEN** the validator SHALL report a single WARNING with rule `config-schema-required`, SHALL NOT report `config-field-required` for the same field, and the result SHALL pass — config warnings never fail the verdict, because `openspec validate` never fails on a missing schema (it defaults to `spec-driven`).

#### Scenario: Schema value invalid for version
- **WHEN** the `openspec/config.yaml` `schema` value is neither in the built-in fallback set (`VersionSupport.V1_2.getValidSchemas()`) nor in the CLI-runtime set from `SchemaService.listSchemas()`
- **THEN** the validator SHALL report a WARNING with rule `config-schema-invalid`. The warning text SHALL list the known-set and indicate CLI status (available / unavailable / below floor) so the user understands why a custom-forked schema might not be visible.

#### Scenario: Built-in schema accepted regardless of CLI state
- **WHEN** the `openspec/config.yaml` `schema` value is one of the built-in fallback set (currently `"spec-driven"` or `"workspace-planning"`)
- **THEN** the validator SHALL NOT report `config-schema-invalid` regardless of whether the CLI is available

#### Scenario: Custom-forked schema accepted when CLI lists it
- **WHEN** the CLI is available and supports schema management, AND the user has run `openspec schema fork spec-driven my-team-flow` so `SchemaService.listSchemas()` returns a `SchemaInfo` with name `"my-team-flow"`, AND an `openspec/config.yaml` declares `schema: my-team-flow`
- **THEN** the validator SHALL NOT report `config-schema-invalid`

#### Scenario: Custom-forked schema with no CLI falls back to built-ins
- **WHEN** the CLI is unavailable (or below the 1.3.0 floor) AND an `openspec/config.yaml` declares a non-built-in schema like `my-team-flow`
- **THEN** the validator SHALL report `config-schema-invalid` with text indicating the CLI is unavailable so custom schemas can't be verified

#### Scenario: Typo schema name continues to warn
- **WHEN** the `openspec/config.yaml` `schema` value is a typo of a built-in (e.g., `"spec-drivenn"`) — not in built-ins AND not in the CLI-runtime list
- **THEN** the validator SHALL report `config-schema-invalid` — the broadened recognition does not mask typos

#### Scenario: Version field absent is accepted
- **WHEN** `openspec/config.yaml` has no `version` field (or `version` is empty)
- **THEN** the validator SHALL NOT report any issue. The `version` key is plugin-internal — it is not in upstream's Zod schema and is silently stripped if present at all. `VersionSupport.fromString(null)` defaults to V1_2 so the plugin's runtime behavior is unaffected.

#### Scenario: Version value unrecognized
- **WHEN** the `version` value is set and does not match any known config-format version in `VersionSupport.allVersions()`
- **THEN** the validator SHALL report a WARNING with rule `config-version-unknown`. The check only runs when a value is present; absence is not a warning.

#### Scenario: No required-field enforcement beyond schema
- **WHEN** `openspec/config.yaml` exists and is missing any field other than `schema` (e.g. `context`, `rules`, `version`, `profile`)
- **THEN** the validator SHALL NOT report `config-field-required` for that field. `config-field-required` is no longer emitted; the only required-field rule is `config-schema-required`, covering the sole field upstream's Zod requires. `context` and `rules` are optional and `version`/`profile` are plugin-internal extensions.

#### Scenario: Profile field absent is accepted
- **WHEN** `openspec/config.yaml` has no `profile` field (or `profile` is empty)
- **THEN** the validator SHALL NOT report any issue. Upstream does not read `profile`; the plugin uses it only for tree-view display and AI-prompt context, both null-safe.

#### Scenario: Empty PSI element during config inspection
- **WHEN** `findElementAt()` or `getFirstChild()` returns a zero-length PSI element during config validation
- **THEN** the inspection SHALL walk up via `getParent()` to find a non-empty ancestor and use it for the problem descriptor

#### Scenario: Legacy config-format version 1.0.0 routes to V1_2 baseline
- **WHEN** a `config.yaml` declares `version: 1.0.0` (the legacy V1_0 baseline, removed from `VersionSupport`)
- **THEN** `VersionSupport.fromString("1.0.0")` SHALL return `V1_2`, and the validator SHALL apply V1_2's required-field and required-artifact rules. No crash; no NPE on a missing enum value.

#### Scenario: Legacy config-format version 1.1.0 routes to V1_2 baseline
- **WHEN** a `config.yaml` declares `version: 1.1.0` (the legacy V1_1 baseline, removed from `VersionSupport`)
- **THEN** `VersionSupport.fromString("1.1.0")` SHALL return `V1_2`, and the validator SHALL apply V1_2's rules

#### Scenario: Current and newer config-format versions resolve correctly
- **WHEN** a `config.yaml` declares `version: 1.2.0`, `1.2.99`, `1.3.0`, or `1.4.x`
- **THEN** `VersionSupport.fromString` SHALL return `V1_2` for all (V1_2 is the only baseline; future format versions will add their own enum entries when they change the config-format shape)

### Requirement: Validate from the Project View context menu

The plugin SHALL offer a Validate action in the IDE's Project View context menu, visible only when the current selection is under an `openspec/` directory. Invoking it SHALL validate the change or spec that owns the clicked file, resolved by mapping the file's path up to its owning directory: a selection under `openspec/specs/<capability>/` SHALL validate that spec; a selection under `openspec/changes/<name>/` for an active (non-archived) change SHALL validate that change; a selection under `openspec/changes/archive/`, at the `openspec/` root, or on a non-item file such as `config.yaml` SHALL fall back to validating the whole project. The action SHALL reuse the shared validation pipeline — the CLI verdict authoritative for specs/changes when the CLI is available, the built-in validator as the fallback when it is not, and the built-in validator as the sole owner of `config.yaml` checks — scoped to the resolved target. It SHALL NOT fabricate a per-file valid/invalid verdict, and its visibility check SHALL be inexpensive (a path check, no blocking I/O or CLI call).

#### Scenario: Menu item hidden outside openspec
- **WHEN** the user right-clicks a file that is not under an `openspec/` directory
- **THEN** the Validate OpenSpec menu item SHALL NOT be shown

#### Scenario: Validating a spec from the tree
- **WHEN** the user right-clicks a file under `openspec/specs/<capability>/` and invokes Validate
- **THEN** the plugin SHALL validate that spec (not the whole project) and report the result to the console

#### Scenario: Validating a change from the tree
- **WHEN** the user right-clicks a file under an active change's `openspec/changes/<name>/` and invokes Validate
- **THEN** the plugin SHALL validate that change and report the result

#### Scenario: Non-item selection falls back to whole-project
- **WHEN** the user invokes Validate on a selection under `openspec/changes/archive/`, at the `openspec/` root, or on `config.yaml`
- **THEN** the plugin SHALL validate the whole project rather than fabricating a single-item verdict for a non-validatable target
