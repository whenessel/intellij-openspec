# validation (delta)

## MODIFIED Requirements

### Requirement: Config validation

The plugin SHALL validate `openspec/config.yaml` for structural correctness when the file exists, applying only the rules that mirror upstream OpenSpec's own contract. The built-in validator SHALL own `config.yaml` validation in all paths — including when the CLI is present, since the CLI's `validate` never reads `config.yaml`. That ownership is **display-only and non-failing**: config validation SHALL NOT emit any ERROR-severity issue and SHALL NEVER fail a validation verdict, because `openspec validate` never fails on any `config.yaml` state (verified against the real CLI — a missing, empty, or unrecognized `schema`, any `version` value, and even malformed YAML all validate clean, since upstream tolerates a missing schema and defaults to `spec-driven`). The file itself SHALL be optional — matching upstream's `readProjectConfig() → null // No config is OK` semantics — so a project initialized with `openspec init` and never customized SHALL produce zero config-related validation issues. When the file does exist, validation SHALL enforce only the fields upstream defines in its Zod `ProjectConfigSchema` (`schema`, `context`, `rules`); fields the plugin reads as internal-only extensions (`version`, `profile`) SHALL NOT fire issues. A missing or empty `schema` field SHALL raise a single **INFO advisory** (rule `config-schema-required`) — a hygiene nudge that OpenSpec defaults to `spec-driven` — never a WARNING or ERROR, because a warning on a file the CLI validates clean would be stricter than the client the plugin wraps. Schema-name recognition SHALL be CLI-runtime-driven AND gated on an **authoritative** known-set: a `schema` value SHALL be checked for recognition only when the CLI is available and supports schema management (so the known-set reflects real project-local forks via `openspec schemas --json`); when the known-set is non-authoritative (CLI absent or below floor), the check SHALL be skipped rather than warn on a fork the plugin cannot see. A plugin-internal `version` value SHALL NOT raise any issue — it is stripped by upstream's Zod and never read by the CLI. Config parsing SHALL be lenient — unrecognized fields and type mismatches SHALL be silently ignored rather than raising parse errors. The config validation inspection SHALL guard against zero-length PSI elements before creating problem descriptors.

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
- **THEN** the validator SHALL report a single **INFO advisory** with rule `config-schema-required`, SHALL NOT report `config-field-required` for the same field, and the result SHALL pass. It is an advisory, not a WARNING or ERROR — `openspec validate` is clean on a missing schema (it defaults to `spec-driven`), so a warning would be stricter than the CLI.

#### Scenario: Schema value invalid for version
- **WHEN** the CLI is available and supports schema management, AND the `openspec/config.yaml` `schema` value is neither in the built-in fallback set (`VersionSupport.V1_2.getValidSchemas()`) nor in the CLI-runtime set from `SchemaService.listSchemas()`
- **THEN** the validator SHALL report a WARNING with rule `config-schema-invalid`. The warning text SHALL list the known-set and indicate CLI status so the user understands why a custom-forked schema might not be visible. The check runs only when the known-set is authoritative (see the CLI-unavailable scenario).

#### Scenario: Built-in schema accepted regardless of CLI state
- **WHEN** the `openspec/config.yaml` `schema` value is one of the built-in fallback set (currently `"spec-driven"` or `"workspace-planning"`)
- **THEN** the validator SHALL NOT report `config-schema-invalid` regardless of whether the CLI is available

#### Scenario: Custom-forked schema accepted when CLI lists it
- **WHEN** the CLI is available and supports schema management, AND the user has run `openspec schema fork spec-driven my-team-flow` so `SchemaService.listSchemas()` returns a `SchemaInfo` with name `"my-team-flow"`, AND an `openspec/config.yaml` declares `schema: my-team-flow`
- **THEN** the validator SHALL NOT report `config-schema-invalid`

#### Scenario: Custom-forked schema with no CLI falls back to built-ins
- **WHEN** the CLI is unavailable (or below the 1.3.0 floor) AND an `openspec/config.yaml` declares a non-built-in schema like `my-team-flow`
- **THEN** the validator SHALL NOT report `config-schema-invalid` — the known-set is non-authoritative (it collapses to the built-in floor and cannot see project-local forks), so the check is skipped rather than warn on a schema the real CLI accepts. Schema recognition resumes when the CLI is present to supply the authoritative set.

#### Scenario: Typo schema name continues to warn
- **WHEN** the CLI is available and supports schema management, AND the `openspec/config.yaml` `schema` value is a typo of a built-in (e.g., `"spec-drivenn"`) — not in built-ins AND not in the CLI-runtime list
- **THEN** the validator SHALL report `config-schema-invalid` — the authoritative-known-set gate does not mask a genuine typo when the CLI supplies the real set

#### Scenario: Version field absent is accepted
- **WHEN** `openspec/config.yaml` has no `version` field (or `version` is empty)
- **THEN** the validator SHALL NOT report any issue. The `version` key is plugin-internal — it is not in upstream's Zod schema and is silently stripped if present at all. `VersionSupport.fromString(null)` defaults to V1_2 so the plugin's runtime behavior is unaffected.

#### Scenario: Version value unrecognized
- **WHEN** the `version` value is set and does not match any known config-format version in `VersionSupport.allVersions()`
- **THEN** the validator SHALL NOT report any issue. `version` is plugin-internal and the CLI never reads it — `openspec validate` is clean for any value — so warning on an unrecognized value would be stricter than the CLI. `VersionSupport.fromString` routes any value (including legacy `1.0.0`/`1.1.0`) to the V1_2 baseline.

#### Scenario: No required-field enforcement beyond schema
- **WHEN** `openspec/config.yaml` exists and is missing any field other than `schema` (e.g. `context`, `rules`, `version`, `profile`)
- **THEN** the validator SHALL NOT report `config-field-required` for that field. `config-field-required` is no longer emitted; the only required-field rule is `config-schema-required`, covering the sole field upstream's Zod requires. `context` and `rules` are optional and `version`/`profile` are plugin-internal extensions.

#### Scenario: Profile field absent is accepted
- **WHEN** `openspec/config.yaml` has no `profile` field (or `profile` is empty)
- **THEN** the validator AND the config inspection SHALL NOT report any issue. Upstream does not read `profile` (it is the global workflow profile, not a project config field), and the plugin has no reader for it; its absence is never a config issue.

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
