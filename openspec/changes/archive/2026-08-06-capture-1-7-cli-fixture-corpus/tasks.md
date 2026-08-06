# Tasks — 1.7.0 fixture corpus capture

## 1. Capture the 1.7.0 corpus (isolated env, sanitized)

- [x] 1.1 status family → `1.7.0/status.json`, `status-with-context.json`, `status-complete.json` (capture `requires[]` + schema-order reorder faithfully)
- [x] 1.2 instructions family → `1.7.0/instructions-{proposal,specs,tasks}.json` (capture `unlocks[]` reorder)
- [x] 1.3 validate family → `1.7.0/validate.json`, `validate-single-{spec,change,change-invalid}.json`, `validate-strict-warning-only.json`
- [x] 1.4 schema tooling → `1.7.0/schema-validate-{clean,broken,missing-template}.json`, `schema-which-{builtin,project,shadowing}.json`, `templates-builtin.json`
- [x] 1.5 store family → `1.7.0/store-doctor-healthy-empty.json`, `store-register-{fresh-root,pointer-declared,invalid-pointer,confirmation-required}.json`
- [x] 1.6 change-deltas → `1.7.0/change-deltas/{mixed,rename-only,empty}.show.json`
- [x] 1.7 spec-structure + parity (reuse `1.6.0/parity-corpus` input) → `1.7.0/spec-structure/*.show.json` (11) + `validate-parity-corpus.json`
- [x] 1.8 update transcripts → `1.7.0/update-clean.txt`, `update-legacy-pending.txt`, `update-legacy-pending-regenerated.txt`

## 2. Add `…V17` contract-test nests

- [x] 2.1 `CliContractTest`: `StatusContractV17` (id-keyed + `requires`-edge assertion), `InstructionContractV17` (unlocks reorder), `ValidateContractV17`, `SingleItemValidateContractV17`; add a `fixture17()` helper
- [x] 2.2 `SchemaToolingContractTest`: `SchemaValidateContractV17`, `SchemaWhichContractV17`, `TemplatesContractV17` (verbatim twins)
- [x] 2.3 `StoreWorksetContractTest` + `StoreWorksetWriteContractTest`: V17 twins via the existing `fixtureAt("1.7.0", …)` helper
- [x] 2.4 `ChangeDeltasContractTest`: parameterize/refactor the fixture loader over `1.6.0` + `1.7.0`
- [x] 2.5 `SpecParserCliStructureContractTest`: parameterize the structure parity over `1.6.0` + `1.7.0` corpora
- [x] 2.6 `ValidatorVerdictParityTest`: add a lightweight fixture-to-fixture verdict-stability check (1.6.0 vs 1.7.0 id→valid maps equal)
- [x] 2.7 `UpdateOutputParserContractTest`: `RealOutputContractV17` (1.7 "Files to remove" count)

## 3. Manifest + verification

- [x] 3.1 Extend `src/test/resources/fixtures/cli/README.md` with the 1.7.0 recipes + the observed 1.6→1.7 deltas
- [x] 3.2 `./gradlew test` green; coverage floors NOT ratcheted (flat aggregate — no `src/main` change)
- [x] 3.3 Leak-scan staged files (no homelab identifiers) before commit
