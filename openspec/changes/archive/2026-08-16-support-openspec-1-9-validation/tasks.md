## 1. Capture the 1.9.0 fixture corpus

- [x] 1.1 Capture `validate-parity-corpus.json`, `validate-parity-corpus-strict.json`, and `version.txt` from the real 1.9.0 CLI in an isolated `HOME`/`XDG_*` env (telemetry off), reusing the existing `1.6.0/parity-corpus` markdown as capture input; sanitize machine paths to `/fixture/parity-corpus`; place under `src/test/resources/fixtures/cli/1.9.0/`. Confirm default = 12/13 valid, strict = 9/13 valid, and that the twins are identical (modulo `durationMs`) to the committed `1.8.0` twins.
- [x] 1.2 Add a `1.9.0/` section to `src/test/resources/fixtures/cli/README.md` (provenance + capture recipe + the identical-to-1.8 note), following the existing per-generation manifest style.

## 2. Wire the durable version guards (tests)

- [x] 2.1 Add `"1.9.0"` to `ValidatorVerdictVersionStabilityTest.FLOOR` so the new corpus is a mandatory discovery. Verify both arms pass unchanged: default subset-of-laxest holds with `DEFAULT_ANCHOR` still `1.8.0`, strict map still equals the `1.6.0` anchor. This test is the non-vacuous guard — a bad/edited 1.9 fixture or a dropped capture fails it.
- [x] 2.2 Add `"1.9.0"` to the supported-versions `@ValueSource` in `CliVersionAtLeastTest` (asserts it clears the `1.3.0` floor).

## 3. Declare 1.9.x supported (spec + docs)

- [x] 3.1 Confirm the `plugin-core` delta in this change declares the `1.9.x` line + the "The 1.9.x line is a supported generation" scenario (already written in `specs/plugin-core/spec.md`); the version-floor assertion in 2.2 is its enforcing test.
- [x] 3.2 `docs/openspec-support.md`: add the `1.9.x` line/entry (additive; note parity is byte-identical to 1.8).
- [x] 3.3 Reviewed `docs/feature-comparison-matrix.md` — **no 1.9.x content change** (it's a competitive-feature snapshot; 1.9 is CLI-fidelity parity with no competitive change, and its version facts live in `openspec-support.md`). Review-stamp / `plugin vX.Y.Z` restatement **left at current** — bumps at `/release-cut 0.9.0` (bumping ahead of `build.gradle.kts` drifts `DocumentationHygieneTest`).
- [x] 3.4 `README` + `CHANGELOG`: add an additive "1.9.x support" entry, vendor-neutral, framed as declared support (not weakened validation).

## 4. Verify

- [x] 4.1 `./gradlew build` green — full suite + JaCoCo coverage floor held (no production code changes, so coverage is flat; do not ratchet).
- [x] 4.2 `DocumentationHygieneTest` green after the doc edits.
- [x] 4.3 `openspec validate support-openspec-1-9-validation --strict` clean; leak-grep the staged files before commit.
