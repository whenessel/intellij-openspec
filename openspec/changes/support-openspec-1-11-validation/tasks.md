## 1. Capture the 1.11.0 fixture corpus

- [x] 1.1 Adopt-sync prerequisite (separate direct commit, `.claude/**` only): install OpenSpec CLI 1.11.0 in the local dev environment and run `openspec update` to regenerate the managed `.claude/skills/openspec-*/` and `.claude/commands/opsx/` surfaces (`generatedBy 1.10 → 1.11`). The custom-named tracker/release skills are untouched; the CLAUDE.md clobber-restore (`git checkout HEAD -- .claude/skills/ .claude/commands/`) still applies.
- [x] 1.2 Capture `validate-parity-corpus.json`, `validate-parity-corpus-strict.json`, and `version.txt` from the real 1.11.0 CLI in an isolated `HOME`/`XDG_*` env (telemetry off), reusing the existing `1.6.0/parity-corpus` markdown as capture input; sanitize machine paths to `/fixture/parity-corpus`; place under `src/test/resources/fixtures/cli/1.11.0/`. Confirm default = 12/13 valid, strict = 9/13 valid, and that the twins are identical (modulo `durationMs`) to the committed `1.10.0`/`1.9.0`/`1.8.0` twins — diff each twin against its 1.10.0 counterpart with `del(.durationMs, .items[].durationMs)`; the only differences must be timing.
- [x] 1.3 Add a `1.11.0/` section to `src/test/resources/fixtures/cli/README.md` (provenance + capture recipe + the identical-to-1.10-parity note plus the new placeholder positive-control), following the existing per-generation manifest style.

## 2. Lock the new PURPOSE_IS_PLACEHOLDER rule (positive-control fixture + contract test)

- [x] 2.1 Author a minimal spec whose `## Purpose` opens with a placeholder marker (e.g. `TBD - created by archiving change …`) as capture input, and capture `validate <spec> --type spec --json` (default) and its `--strict` twin from the real 1.11.0 CLI in the isolated env; sanitize machine paths; place under `src/test/resources/fixtures/cli/1.11.0/` (e.g. `validate-purpose-placeholder.json` + `-strict.json`). Confirm default = one `WARNING` issue on `overview` with `valid: true`; strict = `valid: false`.
- [x] 2.2 Add a `CliContractTest` nest (`ValidatePurposePlaceholderContractV1_11`, mirroring `ValidateContractV18`). Two arms against the committed capture: (a) **raw-JSON** — `valid: true` + exactly one issue `level: WARNING`, `path: overview`, message names the placeholder condition (default); `valid: false` (strict); (b) **parser** — `CliOutputParser.parseJsonOutput` reports the default item clean (the WARNING rides a `valid: true` item, which the parser drops) and the strict item failing from the CLI-authoritative `valid` field. The level/path MUST be asserted at raw-JSON because `parseJsonOutput` drops warnings on valid items and overwrites the issue path with `type/id`. Captured real output, not a hand-authored shape; prove non-vacuous (flip the asserted level/path/valid → RED).

## 3. Wire the durable version guards (tests)

- [x] 3.1 Add `"1.11.0"` to `ValidatorVerdictVersionStabilityTest.FLOOR` so the new corpus is a mandatory discovery. Verify both arms pass unchanged: default subset-of-laxest holds with `DEFAULT_ANCHOR` still `1.8.0`, strict map still equals the `1.6.0` anchor. A bad/edited 1.11 fixture or a dropped capture fails it.
- [x] 3.2 Add `"1.11.0"` to the supported-versions `@ValueSource` in `CliVersionAtLeastTest` (asserts it clears the `1.3.0` floor and exercises the two-digit minor segment against the neighboring `1.10.0`).

## 4. Advance the single-sourced target version

- [x] 4.1 Bump `openspecTargetVersion` from `1.10.0` to `1.11.0` in `gradle.properties`. This must land together with the 1.11.0 corpus (task 1.2): `TargetVersionSingleSourceTest.targetVersionHasACapturedFixtureCorpus()` fails if the property names a version with no committed fixtures. Confirm `TargetVersionSingleSourceTest` green (fixture present + `version.txt` matches + the ui-smoke workflow references the property, no hardcoded `@fission-ai/openspec@<digit>` literal).

## 5. Verify two-digit-minor ordering (no new wrinkle expected)

- [x] 5.1 Confirm the shared comparator `CliVersion.compare` orders `1.11.0` above `1.10.0` and `1.9.0` (it splits on `.` and compares segments numerically — already pinned by `CliVersionTest.twoDigitMinor_ordersNumericallyNotLexically`; 3.2 exercises it). Re-sweep for any ad-hoc lexical version ordering (string `sort`/`max`/`compareTo`/`TreeSet<String>` over version dir names or version literals outside `CliVersion`) introduced since 1.10; add a targeted assertion if any is found. Known surfaces are safe (the stability guard uses explicit-key lookup + set-membership).

## 6. Declare 1.11.x supported (spec + docs)

- [x] 6.1 Confirm the `plugin-core` delta in this change declares the `1.11.x` line + the "The 1.11.x line is a supported generation" scenario (already written in `specs/plugin-core/spec.md`); the version-floor assertion in 3.2 and the positive-control test in 2.2 are its enforcing tests.
- [x] 6.2 `docs/openspec-support.md`: add the `1.11.x` supported line, and bump the workflow-availability matrix heading to `CLI 1.3.x → 1.11.x` with 1.11.x in the omit note (byte-identical parity verdicts to 1.8.x; note the new placeholder WARNING as a stricter-direction client addition). Do **not** touch the canonical `Current plugin version:` line — that bumps at release-cut.
- [x] 6.3 Review `docs/feature-comparison-matrix.md` — no 1.11.x content change (competitive-feature snapshot; 1.11 is CLI-fidelity parity, version facts live in `openspec-support.md`). Leave the review-stamp / `plugin vX.Y.Z` restatement at current — it bumps at `/release-cut` (bumping ahead of `build.gradle.kts` drifts `DocumentationHygieneTest`).
- [x] 6.4 `README` + `CHANGELOG`: add an additive "1.11.x support" entry, vendor-neutral, framed as declared support with a new stricter placeholder WARNING captured (not weakened validation), restating that the minimum CLI remains `1.3.0`.

## 7. Verify

- [x] 7.1 `./gradlew build` green — full suite + JaCoCo coverage floor held (no production code changes, so coverage is flat; do not ratchet).
- [x] 7.2 `DocumentationHygieneTest`, `TargetVersionSingleSourceTest`, `ValidatorVerdictVersionStabilityTest`, and the new placeholder contract test green after the fixture + property + doc edits.
- [x] 7.3 `openspec validate support-openspec-1-11-validation --strict` clean; run the repo's anti-leak grep over the staged files before commit (the homelab tracker/host/username identifiers named in CLAUDE.md — do not embed the literal pattern here, it self-trips the pre-push guard).
