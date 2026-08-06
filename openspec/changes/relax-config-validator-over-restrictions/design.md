# Design — capture-backed config-validator relaxations

## Principle

Every demotion is justified by **captured real 1.7.0 CLI output** proving the CLI is clean on that exact config shape — never by an in-code assumption. Fixtures live in `fixtures/cli/1.7.0/config-validation/` and are asserted by `CliConfigToleranceContractTest` (the re-capture tripwire).

## The five relaxations

| # | Site | Change |
|---|---|---|
| 1 | `BuiltInValidator` `config-version-unknown` | **Deleted.** The CLI never reads `version:`; it's stripped by upstream's Zod. The `getVersion()` reader stays (config-format-axis fallback). |
| 2 | `ConfigValidationInspection` `profile:` nag | **Deleted.** `profile` is the global workflow profile, not a project field. The validation spec already accepts an absent `profile`; the inspection now matches. |
| 3 | `config-schema-required` (validator + inspection) | **WARNING → INFO/INFORMATION.** A missing `schema:` validates clean on the CLI (defaults `spec-driven`), so a warning would be stricter; kept as an advisory nudge because `schema` is upstream's one documented-required field. |
| 4 | `config-schema-invalid` + `change-schema-incompatible` | **Guarded** on `knownSetIsAuthoritative()` (`SchemaService.isSchemaSupported()`). Offline the known-set collapses to the built-in floor, so a legit fork would falsely red; the CLI never rejects a schema name. When the CLI is present, a genuine typo still warns. |
| 5 | `version:` Explore surface + stale comments | Surface removed (`ExploreContextService`); the "tree reads version/profile" comments corrected — `getProfile()` has zero readers, and `SpecTreeModel` reads only `getEffectiveVersion` (indirect, for required-artifact selection). |

## What is deliberately NOT changed

- The `getVersion`/`getEffectiveVersion` and `getProfile` readers (legacy on-disk safety; `getEffectiveVersion` is the config-format-axis fallback).
- `VersionSupport`'s single-baseline config-format axis pin.
- The tolerant change-metadata parse (a prior change, already shipped).

## Testing

- **Contract** (`CliConfigToleranceContractTest`): parse the captured `openspec validate --all --json` fixtures for a `version: 9.9.9` config, a missing-`schema:` config, and a custom-fork config — each proves a clean CLI verdict; and parse `schemas --json` proving a project fork is listed (the guard's premise).
- **Both-direction guard** (`BuiltInValidatorTest`): `config-schema-invalid` and `change-schema-incompatible` each get a CLI-unavailable → clean test AND a CLI-authoritative → still-warns test (a `SchemaService` mock via `ServiceContainerUtil.replaceService`). A one-direction "never fire" test would be vacuous.
- **Inversions**: `testUnknownVersionIsClean` (was `…TriggersWarning`), `testMissingSchemaIsANonFailingInfoNudge` (INFO, was WARNING), inspection `testMissingSchemaIsInformationNudge` + `testMissingProfileProducesNoProblem` (real `openspec/`-parented files).
- **Drift fix**: `ConfigVersionValidationTest` was a mirror reimplementation whose config-version tests were a false-green (they'd pass with the production change reverted); its config-version half is removed and repointed to the service-driven `BuiltInValidatorTest`.

## Note

`verifyPlugin`/`uiSmoke` are not load-bearing (validator/inspection logic; no new platform API). The `config-schema-required` INFO uses the existing `Severity.INFO` / `ProblemHighlightType.INFORMATION`.
