# Close latent vacuous-test gaps for four CLI-output parsers

## Why

Four parsers of external CLI output shipped with **no captured fixture** — tested only against
inline hand-authored JSON. That encodes the author's assumed shape, so the test stays green while
the parser is wrong. The 1.7 audit found exactly that: `SchemaService.parseSchemaList` read
`isBuiltIn`/`artifactIds` keys the CLI never emits (real `schemas --json` reports `source` and
`artifacts`), so every listed schema was shown non-built-in with an empty artifact set — a real,
user-visible bug behind a vacuous test. Re-anchoring these parsers to captured real 1.7 output
closes the debt once and catches the bug.

## What Changes

- **Fix `parseSchemaList`** to read the real `source` (`package` = built-in, `project` = fork) and
  `artifacts` keys.
- **Contract-test the three parsers whose commands work on 1.7**, against captured fixtures:
  - `parseSchemaList` vs `schemas-list-with-fork.json` (built-in + fork), replacing the vacuous
    inline test.
  - `WorkflowProfileService.parseSnapshot` vs a real `config list --json` (asserting `update`, which
    is in the real workflows but not the `CORE_DEFAULTS` fallback — proving the real array parsed).
  - `CliDetectionService` `--version` strip vs captured `version.txt`, plus a floor-no-cap assertion
    (1.7.0 and any future version clear the 1.3.0 floor; no allowlist).
- **Document the fourth as pinned, not staled.** `openspec config profile --json` is *rejected* on
  1.7 (`unknown option '--json'`), so `ConfigProfileDetail.fromJson` parses output no current CLI
  produces. Captured as evidence + a graceful-degradation guard; re-sourcing the Settings profile
  section from `config list --json` is a separate change.

## Capabilities

No capability spec changes. The `parseSchemaList` fix makes the code **conform** to the existing
schema-management "Schema listing" requirement (which already mandates showing built-in status and
artifacts); it adds/removes/re-gates no capability. The config-profile spec scenario still references
the now-rejected `config profile --json` — its correction (with the re-source) is the follow-up
change, not this one. Marked `skip_specs: true`; the user-facing fix is in the changelog.

## Impact

- `SchemaService.parseSchemaList` (fix). Fixtures: `1.7.0/config-list.json`, `version.txt`,
  `config-profile-json-rejected.txt` (+ reuse of `schemas-list-with-fork.json`).
- Tests: `SchemaServiceTest`, `WorkflowProfileServiceTest`, `CliDetectionServiceTest`,
  `ConfigProfileDetailTest`. Manifest updated.
