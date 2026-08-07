## MODIFIED Requirements

### Requirement: Change validation

The plugin SHALL validate each active change's `.openspec.yaml` for schema compatibility with the project's declared version. Schema-name recognition SHALL be CLI-runtime-driven on the same principle as `Config validation`: a change schema is "recognized" when it appears in the union of the built-in fallback set (`VersionSupport.V1_2.getValidSchemas()`) and the CLI-runtime set from `SchemaService.listSchemas()`. The validator SHALL use `VersionSupport.getValidSchemas()` only as the built-in fallback portion of that union, not as the canonical valid-set. Two plugin-invented lints that the CLI never emits SHALL always be non-failing WARNINGs, so the plugin is never more restrictive than the client it wraps: `change-artifact-missing` (a missing required artifact — the CLI never checks artifact-file presence) and `change-proposal-required` (a missing `proposal.md` — the CLI resolves changes by directory existence, not by requiring a proposal; upstream #1182). Neither SHALL be escalated to ERROR by any persistent setting, and neither SHALL fail the verdict under a per-run strict validation (see `Per-run strict validation`) — because the CLI never emits them, they are not in the CLI-mirroring strict-warning set, so the plugin's strict fallback does not flip on them.

#### Scenario: Proposal required
- **WHEN** a change has no `proposal.md`
- **THEN** the validator SHALL report a non-failing WARNING with rule `change-proposal-required` — never an ERROR and never flipping a strict verdict, because the real CLI validates a proposal-less change that has a valid delta as valid (it resolves changes by directory existence, not by requiring `proposal.md`), so failing on it would make the plugin more restrictive than the CLI it wraps

#### Scenario: Required artifacts for version
- **WHEN** the declared version requires specific artifacts and a change is missing one
- **THEN** the validator SHALL report a WARNING with rule `change-artifact-missing` — always a non-failing WARNING, never escalated to ERROR by a persistent setting and never flipping a strict verdict, because the CLI never emits it

#### Scenario: Change schema incompatible with project version
- **WHEN** a change's `.openspec.yaml` declares a schema that is neither in the built-in fallback set nor in `SchemaService.listSchemas()`
- **THEN** the validator SHALL report a WARNING with rule `change-schema-incompatible`. The warning text SHALL list the known-set and indicate CLI status so the user understands why a custom-forked schema might not be visible.

#### Scenario: Custom-forked schema accepted for change when CLI lists it
- **WHEN** the user has forked a custom schema via `openspec schema fork` AND a change's `.openspec.yaml` declares that custom schema
- **THEN** the validator SHALL NOT report `change-schema-incompatible`
