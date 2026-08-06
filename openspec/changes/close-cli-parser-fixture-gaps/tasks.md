# Tasks — close CLI-parser fixture gaps

## 1. Capture real 1.7 fixtures (isolated, sanitized)

- [x] 1.1 `config list --json` (after `config profile core`, so `workflows` is present) → `1.7.0/config-list.json`
- [x] 1.2 `openspec --version` → `1.7.0/version.txt`
- [x] 1.3 `config profile --json` rejection → `1.7.0/config-profile-json-rejected.txt` (evidence; command is dead on 1.7)
- [x] 1.4 Reuse existing `1.7.0/config-validation/schemas-list-with-fork.json` for the `parseSchemaList` contract

## 2. Fix the real parser bug

- [x] 2.1 `SchemaService.parseSchemaList` reads `source` (`package` → built-in) + `artifacts` (was `isBuiltIn`/`artifactIds`, never emitted)

## 3. Contract tests (each fails if its parser regresses)

- [x] 3.1 `SchemaServiceTest` — replace the vacuous inline test with a fixture contract asserting built-in status + artifacts for the package schema and the project fork
- [x] 3.2 `WorkflowProfileServiceTest.ConfigListContract` — real `config list --json`; assert profile + `update` present (proves real array vs `CORE_DEFAULTS` fallback)
- [x] 3.3 `CliDetectionServiceTest` — version-strip against `version.txt` → `1.7.0`; floor-no-cap (future version clears 1.3.0 floor)
- [x] 3.4 `ConfigProfileDetailTest` — document the `config profile --json` rejection + graceful-degradation guard

## 4. Docs + verification

- [x] 4.1 Extend `fixtures/cli/README.md` manifest (3 new fixtures + the pinned config-profile rejection)
- [x] 4.2 CHANGELOG Fixed entry (Settings schema list built-in/artifacts)
- [x] 4.3 `./gradlew build` green; re-measure coverage and ratchet floors if the aggregate rose
- [x] 4.4 Leak-scan before commit
