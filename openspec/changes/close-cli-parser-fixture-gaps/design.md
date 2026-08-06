# Design — close CLI-parser fixture gaps

## The four parsers, probed on the installed 1.7 CLI

| Parser | Command | 1.7 reality | Action |
|---|---|---|---|
| `SchemaService.parseSchemaList` | `schemas --json` | emits `source`/`artifacts` — **not** `isBuiltIn`/`artifactIds` | **fix** + contract test |
| `WorkflowProfileService.parseSnapshot` | `config list --json` | `{featureFlags, profile, delivery, workflows[]}`; `workflows` present once a profile is set | contract test (no fix — degrades correctly) |
| `CliDetectionService` `--version` strip | `openspec --version` | bare `1.7.0` | contract test + floor-no-cap |
| `ConfigProfileDetail.fromJson` | `config profile --json` | **rejected** (`unknown option '--json'`) | pin + document; fix deferred |

## The real bug

`parseSchemaList` read `obj.get("isBuiltIn")` and `obj.get("artifactIds")` — keys no OpenSpec CLI
version emits. Against real output every schema resolved to `isBuiltIn=false` + empty `artifactIds`.
Consumed at `OpenSpecSettingsPanel:514` (`info.isBuiltIn()`), so the built-in `spec-driven` was never
labeled built-in. The inline test used the same wrong keys, so it passed. Fixed to read `source`
(`"package"` → built-in) and `artifacts`. No tolerance for the old keys is retained — they never
shipped in real output.

## Non-vacuous discriminators

Each new contract test is written to fail if its parser regresses:
- **parseSchemaList** — asserts `isBuiltIn` true for the `package` schema and false for the `project`
  fork, and the exact `artifacts` list. Reverting the fix flips both to the broken values → red.
- **parseSnapshot** — asserts `getActiveWorkflows()` contains `update`. `update` is in the real
  `config list` workflows but **not** in `CORE_DEFAULTS = {propose, explore, apply, sync, archive}`,
  so the assertion can only pass if the real array was parsed, never via the fallback.
- **version strip** — feeds the raw captured `version.txt` through the real detection and asserts
  `getDetectedVersion()` is `1.7.0`; the floor test asserts a future version (`99.42.0`) still clears
  the 1.3.0 floor, so a regression to an allowlist/equality gate fails.

## `config profile --json` — pinned, not staled

The command is rejected on 1.7, so there is no JSON to contract-test `fromJson` against — the "gap"
here cannot be closed by capture, and that *is* the finding. Per the testing capability's
"unrecapturable fixtures are pinned, not staled" rule, the rejection is captured as evidence and
documented in the consuming test + the manifest, with a graceful-degradation guard (the rejection
text, if it ever reached `fromJson`, yields an empty detail, not a throw). The config-profile spec
scenario still names `config profile --json`; correcting it and re-sourcing the Settings section from
`config list --json` is a separate, already-tracked change — deliberately out of scope here to avoid
conflating a test-debt closure with a behavior/spec change.

## Observations deferred (audit follow-ups)

- **`SchemaInfo.artifactIds()` has no production reader** — the fix makes it data-correct, but only
  `isBuiltIn()` is currently rendered (`OpenSpecSettingsPanel:514`). The artifacts half is latent, not
  a defect.
- **`CORE_DEFAULTS` is stale vs CLI 1.7** — the plugin's CLI-unavailable fallback is
  `{propose, explore, apply, sync, archive}` (5); CLI 1.7's core set adds `update` (6). Deliberately
  **not** changed here: the fallback is version-agnostic (adding a 1.7 workflow could misrepresent
  older CLIs), and the `parseSnapshot` contract test's discriminator depends on `update` being absent
  from `CORE_DEFAULTS`. Worth a separate tracked assessment.

## Scope / tiers

Delta-less (`skip_specs`) — the `parseSchemaList` fix conforms to an existing requirement, and no
capability is added/removed/re-gated. Unit tier only (no platform-API change → no `verifyPlugin`/
`uiSmoke`). Coverage: the `--version` strip path was previously 0% covered; re-measure and ratchet
the floors if the aggregate rose.
