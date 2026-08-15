# Tasks

Additive fallback-parity change: the CLI-absent built-in validator gains two change-delta discovery ERRORs (misplaced-delta, no-deltas) that the CLI already emits. Delta discovery becomes recursive. No parity-corpus anchor moves. A test-engineer PLAN pass ran at implementation start; its findings (plain-prose misplaced control, positive-issue-at-depth for recursion, control-paired suppression tests, two boundary fixtures, stale-test cleanup) are folded in below.

## 1. Capture the CLI fixtures

- [x] 1.1 Captured from the **real 1.8.0 CLI** (isolated `HOME`/`XDG_*`, telemetry off): `validate-single-change-misplaced-delta.json` (ERROR `path:"spec.md"`, no line), `validate-single-change-no-deltas.json` (ERROR `path:"file"`), `validate-single-change-skip-specs.json` (valid + INFO), `validate-single-change-nested-delta.json` (valid, depth-2). Plus two **boundary locks** per the PLAN: `…-misplaced-plus-valid.json` (sole misplaced ERROR, no no-deltas co-fire) and `…-misplaced-valid-content.json` (root delta with valid content still misplaced → path-based).
- [x] 1.2 Sanitized `root.path` → `/fixture`; machine-path scan clean across all six.
- [x] 1.3 Added the `1.8.0/` README manifest entries (incl. the boundary locks + the note that no-deltas is a parity twin of `1.7.0/validate-single-change-invalid.json`).

## 2. Recursive delta discovery + the two ERRORs

- [x] 2.1 `BuiltInValidator.validateDeltaSpecs` — replaced the one-level directory scan with a recursive walk (`validateDeltaSpecsUnder`): a regular file named exactly `spec.md` whose parent is `specs/` → `delta-spec-misplaced` ERROR (content-independent, not structurally validated); a `spec.md` at depth ≥ 1 (any depth) → `validateDeltaSpecStructure`. Returns the count of `spec.md` found (misplaced counts), or −1 when the change dir is unresolvable.
- [x] 2.2 `BuiltInValidator.validateSingleChange` — emits `delta-none-found` ERROR when `deltaCount == 0 && required.contains("specs") && !skipSpecs` (skip_specs read via `ChangeMetadata.isSkipSpecs()`, null-guarded). A misplaced root `spec.md` counts as found (misplaced-only ⇒ misplaced ERROR alone). `required.contains("specs")` documented inline as defensive (constant-true on the single baseline).
- [x] 2.3 Both new rules are hard ERRORs (fail default + strict), not in `CLI_MIRRORING_STRICT_WARNINGS`. Exact-lowercase `spec.md`; a directory named `spec.md` is walked as a capability folder, never flagged.

## 3. Tests

- [x] 3.1 `CliContractTest.ChangeDeltaDiscoveryContractV18` — raw-JSON assertions per shape: misplaced (level/`path:"spec.md"`/**no line**/**exactly one issue**/no rule-id/parser-fails), no-deltas (`path:"file"`/message), skip-specs (valid + INFO), nested-valid (valid, issues empty), + the two boundary locks (misplaced+valid → sole misplaced; valid-content → still misplaced).
- [x] 3.2 `BuiltInValidatorTest` (integration, `validateChange(name)`, keyed off **rule id**): misplaced (plain-prose control — ERROR present, `delta-spec-sections` WARNING **absent**, no no-deltas); misplaced-valid-content still errors; nested multi-segment discovered + not misplaced; **nested delta structurally validated** (broken deep delta → `delta-requirement-scenario` at depth 2 — proves recursion descends, not vacuous); no-deltas (metadata-null path); skip_specs suppresses (control-paired with the firing test); misplaced-only no double-fire (control-paired); directory-named-`spec.md` not misplaced.
- [x] 3.3 Existing-test audit: renamed the stale/near-vacuous `testDeltaSpecWithoutSectionsTriggersWarning` → `testChangeWithNoSpecsDirErrorsViaValidateChanges` (real `delta-none-found` assertion via the all-changes path; stale "LocalFileSystem doesn't work in temp VFS" comment removed); added a valid delta to `bad-change` in `testMissingProposalIsNonFailingWarning` so it stays a clean single-variable test. Full build confirms no other regression.

## 4. Documentation (public / vendor-neutral)

- [x] 4.1 `docs/openspec-support.md` — bottom-line now notes the fallback closed the misplaced-delta (1.7.0) + no-deltas discovery gaps, recursive discovery, `skip_specs` honored.
- [x] 4.2 `CHANGELOG.md` `## Unreleased` → **Added**: misplaced-delta + no-deltas errors, framed as CLI parity; recursive discovery; `skip_specs` stays valid.
- [x] 4.3 `docs/feature-reference.md` — added both rules to the CLI-absent fallback-severity examples.

## 5. Verify

- [x] 5.1 `./gradlew build` green — full suite + JaCoCo floors ratcheted 0.390/0.368/0.366 → 0.391/0.369/0.368 (measured 0.3977/0.3764/0.3762).
- [x] 5.2 `openspec validate builtin-validator-delta-discovery-parity --strict` clean; anti-leak grep over changed files + fixtures clean.
- [x] 5.3 No `verifyPlugin`/`uiSmoke` gate triggers — no new `com.intellij.*` reference (`VirtualFile`/`LocalFileSystem` already used).
