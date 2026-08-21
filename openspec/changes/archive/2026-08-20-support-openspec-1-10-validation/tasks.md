## 1. Capture the 1.10.0 fixture corpus

- [x] 1.1 Adopt-sync prerequisite (separate direct commit, `.claude/**` only): install OpenSpec CLI 1.10.0 in the local dev environment and run `openspec update` to regenerate the managed `.claude/skills/openspec-*/` and `.claude/commands/opsx/` surfaces (`generatedBy 1.9 → 1.10`). The custom-named tracker/release skills are untouched; the CLAUDE.md clobber-restore (`git checkout HEAD -- .claude/skills/ .claude/commands/`) still applies.
- [x] 1.2 Capture `validate-parity-corpus.json`, `validate-parity-corpus-strict.json`, and `version.txt` from the real 1.10.0 CLI in an isolated `HOME`/`XDG_*` env (telemetry off), reusing the existing `1.6.0/parity-corpus` markdown as capture input; sanitize machine paths to `/fixture/parity-corpus`; place under `src/test/resources/fixtures/cli/1.10.0/`. Confirm default = 12/13 valid, strict = 9/13 valid, and that the twins are identical (modulo `durationMs`) to the committed `1.9.0`/`1.8.0` twins.
- [x] 1.3 Add a `1.10.0/` section to `src/test/resources/fixtures/cli/README.md` (provenance + capture recipe + the identical-to-1.9 note), following the existing per-generation manifest style.

## 2. Wire the durable version guards (tests)

- [x] 2.1 Add `"1.10.0"` to `ValidatorVerdictVersionStabilityTest.FLOOR` so the new corpus is a mandatory discovery. Verify both arms pass unchanged: default subset-of-laxest holds with `DEFAULT_ANCHOR` still `1.8.0`, strict map still equals the `1.6.0` anchor. This test is the non-vacuous guard — a bad/edited 1.10 fixture or a dropped capture fails it.
- [x] 2.2 Add `"1.10.0"` to the supported-versions `@ValueSource` in `CliVersionAtLeastTest` (asserts it clears the `1.3.0` floor and exercises the first two-digit minor segment).

## 3. Advance the single-sourced target version

- [x] 3.1 Bump `openspecTargetVersion` from `1.9.0` to `1.10.0` in `gradle.properties`. This must land together with the 1.10.0 corpus (task 1.2): `TargetVersionSingleSourceTest.targetVersionHasACapturedFixtureCorpus()` fails if the property names a version with no committed fixtures. Confirm `TargetVersionSingleSourceTest` green (fixture present + `version.txt` matches + the ui-smoke workflow references the property, no hardcoded `@fission-ai/openspec@<digit>` literal).

## 4. Verify two-digit-minor ordering

- [x] 4.1 Confirm the shared comparator `CliVersion.compare` orders `1.10.0` above `1.9.0` (it splits on `.` and compares segments numerically — verified in source; 2.2 exercises it). Sweep the codebase for any ad-hoc lexical version ordering (string `sort`/`max`/`compareTo`/`TreeSet<String>` over version dir names or version literals outside `CliVersion`) that could misplace `1.10` below `1.9`; add a targeted assertion if any is found. Known surfaces are safe (the stability guard uses explicit-key lookup + set-membership).

## 5. Declare 1.10.x supported (spec + docs)

- [x] 5.1 Confirm the `plugin-core` delta in this change declares the `1.10.x` line + the "The 1.10.x line is a supported generation" scenario (already written in `specs/plugin-core/spec.md`); the version-floor assertion in 2.2 is its enforcing test.
- [x] 5.2 `docs/openspec-support.md`: add the `1.10.x` supported line, and bump the workflow-availability matrix heading to `CLI 1.3.x → 1.10.x` with 1.10.x in the omit note (byte-identical verdicts to 1.8.x). Do **not** touch the canonical `Current plugin version:` line — that bumps at release-cut.
- [x] 5.3 Reviewed `docs/feature-comparison-matrix.md` — no 1.10.x content change (competitive-feature snapshot; 1.10 is CLI-fidelity parity, version facts live in `openspec-support.md`). Review-stamp / `plugin vX.Y.Z` restatement **left at current** — bumps at `/release-cut 0.10.0` (bumping ahead of `build.gradle.kts` drifts `DocumentationHygieneTest`).
- [x] 5.4 `README` + `CHANGELOG`: add an additive "1.10.x support" entry, vendor-neutral, framed as declared support (not weakened validation), restating that the minimum CLI remains `1.3.0`.

## 6. Verify

- [x] 6.1 `./gradlew build` green — full suite + JaCoCo coverage floor held (no production code changes, so coverage is flat; do not ratchet).
- [x] 6.2 `DocumentationHygieneTest` and `TargetVersionSingleSourceTest` green after the doc + property edits.
- [x] 6.3 `openspec validate support-openspec-1-10-validation --strict` clean; leak-grep the staged files before commit.
