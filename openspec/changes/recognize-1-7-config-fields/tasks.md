# Tasks — recognize 1.7 config fields

## 1. Capture real 1.7 config shapes (proven, sanitized)

- [x] 1.1 `operations-and-rules.config.yaml` (schema + `operations` + list-shaped `rules`), proven CLI-accepted (`validate --all` exit 0)
- [x] 1.2 `global-config-defaultstore.json` (global config after `config set defaultStore`)

## 2. Lock tolerance with tests (no production change)

- [x] 2.1 `ConfigServiceTest` — `fromMap` reads `schema`, skips list-valued `rules` without crashing
- [x] 2.2 `BuiltInValidatorTest.validateConfig` — no ERROR, no plugin-invented WARNING on the operations config
- [x] 2.3 `ConfigValidationInspectionTest` — no inspection problems on the operations config
- [x] 2.4 `WorkflowProfileServiceTest` — `parseSnapshot` ignores `defaultStore` in `config list --json`

## 3. Confirm invariants

- [x] 3.1 No `src/main` change; config-format axis untouched (no new `VersionSupport` enum value)
- [x] 3.2 `config-version-unknown` stays removed (deleted by the earlier validator-relaxation change) — no re-introduction

## 4. Docs + verification

- [x] 4.1 Manifest (`fixtures/cli/README.md`) — the 2 config-recognition fixtures
- [x] 4.2 `docs/cli-versions/1.7.md` + support matrix note the recognition
- [x] 4.3 `./gradlew build` green; leak-scan before commit
