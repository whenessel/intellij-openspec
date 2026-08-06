# Tasks

## 1. Capture-backed fixtures
- [x] 1.1 Capture real 1.7.0 `openspec validate --all --json` for a `version: 9.9.9` config, a missing-`schema:` config, and a custom-fork config, plus `openspec schemas --json` listing a project fork, into `fixtures/cli/1.7.0/config-validation/` (isolated env, sanitized, leak-clean). Add the manifest row.
- [x] 1.2 `CliConfigToleranceContractTest` — parse each `.validate.json` (clean verdict, no config/version/schema issue) and the `schemas --json` (project fork present) through the plugin's own parsers.

## 2. Validator + inspection relaxations
- [x] 2.1 `BuiltInValidator`: delete `config-version-unknown`; demote `config-schema-required` WARNING → INFO; guard `config-schema-invalid` and `change-schema-incompatible` on a new `knownSetIsAuthoritative()` (`SchemaService.isSchemaSupported()`); fix the stale `profile`/tree comment. Keep the `getVersion` reader and `VersionSupport` pin.
- [x] 2.2 `ConfigValidationInspection`: demote the schema nudge WARNING → INFORMATION; delete the `profile:` nag.
- [x] 2.3 `ExploreContextService`: stop surfacing `version:`; keep the `getVersion` reader.

## 3. Tests
- [x] 3.1 `BuiltInValidatorTest`: invert `testUnknownVersionTriggersWarning` → `testUnknownVersionIsClean`; split the invalid-schema + change-schema tests into CLI-unavailable-clean + CLI-authoritative-still-warns (mock `SchemaService` via `ServiceContainerUtil.replaceService`); `config-schema-required` INFO.
- [x] 3.2 `ConfigValidationInspectionTest`: add `testMissingProfileProducesNoProblem` + `testMissingSchemaIsInformationNudge` on real `openspec/`-parented config files.
- [x] 3.3 `ExploreContextServiceTest`: assert `version:` is no longer surfaced.
- [x] 3.4 `ConfigVersionValidationTest`: remove the drifted config-version mirror (a false-green) + the CLI-unavailable change-schema mirror; repoint to the service-driven `BuiltInValidatorTest`.

## 4. Docs, spec, build
- [x] 4.1 `validation` delta — MODIFIED *Config validation* (whole requirement restated; version-unknown scenario → no issue; missing-schema scenario → INFO; custom-fork-no-CLI scenario → no warning; typo/invalid scenarios gated on authoritative known-set). `openspec validate --strict` passes.
- [x] 4.2 `CHANGELOG.md` `## Unreleased` (Fixed) — vendor-neutral, plugin-user-facing.
- [x] 4.3 `./gradlew build` green; ratchet JaCoCo floors if coverage rises, with recorded justification.
- [x] 4.4 Pre-commit leak scan; confirm fixtures/spec/changelog vendor-neutral for the public mirror.
