## 1. Fix the strict-fallback over-restriction (src/main)

- [x] 1.1 In `OpenSpecValidateAction`, introduce `CLI_MIRRORING_STRICT_WARNINGS` (an explicit, currently-empty rule-name `Set`) with a doc comment: add a rule here only when source-verified that `openspec validate --strict` emits an equivalent WARNING (e.g. a future port of `PURPOSE_TOO_BRIEF`)
- [x] 1.2 Rewrite `applyStrictFallbackVerdict` so the flip condition is "some issue is a WARNING whose rule ∈ `CLI_MIRRORING_STRICT_WARNINGS`" (allow-list), keeping the `!strict || !result.passed()` early return; the empty set means no built-in WARNING flips strict today
- [x] 1.3 Widen `applyStrictFallbackVerdict` to `public static` (test seam, precedented by `combineWithCli` directly below it)
- [x] 1.4 Update `OpenSpecValidateStrictTest.strictFallback_flipsVerdictOnWarnings_withoutReseveritying` — it asserts the OLD (buggy) `spec-title-required`-flips semantics; rewrite/rename (e.g. `strictFallback_doesNotFlipOnCliSilentLintWarnings`) to assert a warnings-only fallback whose warning is any of the five CLI-silent lints PASSES under strict, issue still labeled WARNING. Keep the config and no-op tests

## 2. Generalize the cross-generation stability guard (core)

- [x] 2.1 Rewrite `ValidatorVerdictVersionStabilityTest` to discover every `fixtures/cli/*/validate-parity-corpus.json` on the classpath (resolve `getResource("/fixtures/cli").toURI()` + `Files.list`, matching the **exact** filename — mirroring `SpecParserCliStructureContractTest`), keeping `getResourceAsStream` for the JSON read
- [x] 2.2 Define `ANCHOR = "1.6.0"`; assert every other discovered non-strict corpus's `id → valid` map equals the anchor's, with a message naming the offending generation and item
- [x] 2.3 Also discover `fixtures/cli/*/validate-parity-corpus-strict.json` and assert each strict map equals the non-strict 1.6.0 anchor (cross-generation strict stability); ensure the two globs never intersect (exact filenames)
- [x] 2.4 Vacuity guards: `assertNotNull` the `/fixtures/cli` resource; discovered non-strict set size ≥ 2, contains the anchor, and `{1.6.0, 1.7.0} ⊆ discovered`; every discovered corpus carries the identical 13-item id key set; rename the method to reflect the generalized intent and update the javadoc (durable invariant + transitivity)
- [x] 2.5 Confirm `ValidatorVerdictParityTest`'s existing version-dimension assertions are unchanged (only a strict block is added in §3)

## 3. Strict parity arm (fixture + platform assertion)

- [x] 3.1 Capture `fixtures/cli/1.7.0/validate-parity-corpus-strict.json` from real `openspec validate --all --strict --json` over the shared `1.6.0/parity-corpus/` markdown (isolated `HOME`/`XDG_*`, `OPENSPEC_TELEMETRY=0`), per the recipe in §5.1 / the fixtures README; verify sanitization before committing — `root.path` = `/fixture/parity-corpus`, 13 items, 9 valid
- [x] 3.2 Add a strict-dimension block to `ValidatorVerdictParityTest`: for each strict-fixture id, scope the built-in `issues` per item (existing `/<id>/`-or-`endsWith("/"+id)` marker), build the per-item `ValidationResult`, and assert `OpenSpecValidateAction.applyStrictFallbackVerdict(perItem, true).passed()` equals the captured CLI-strict `valid` — calling the REAL method (never an inline re-implementation)
- [x] 3.3 Anti-degeneracy anchor: assert a `change-artifact-missing` WARNING is actually present on a CLI-strict-valid item (`info-change`), so the WARNING branch is provably exercised and the fix provably spares a present lint warning; assert the exact strict-valid id count (9)

## 4. Prove the guards actually bite

- [x] 4.1 Confirm the §3 strict block goes RED against the pre-fix `applyStrictFallbackVerdict` (on `info-change`/`nameless-change`) and GREEN after the §1 fix
- [x] 4.2 In the scratchpad (never touching a committed fixture), add a scratch generation with one boolean flipped and confirm the §2 discovery test reds naming the generation + item; confirm anchor-removal / shrunk-key-set also reds via the vacuity guards; discard the scratch tree

## 5. Documentation fidelity

- [x] 5.1 Add a "durable next-generation capture" recipe to `src/test/resources/fixtures/cli/README.md` (both `validate-parity-corpus.json` and the `-strict` twin): isolated env, reuse `1.6.0/parity-corpus/` markdown as input, sanitize `root.path`, commit under `fixtures/cli/<gen>/`; note that a differing map is a real tightened-verdict signal, not a fixture to edit
- [x] 5.2 Update the CHANGELOG `## Unreleased` — a user-facing **Fixed** note that a CLI-absent strict Validate no longer reds a project on plugin-only lint warnings the CLI accepts

## 6. Verify

- [x] 6.1 `openspec validate generalize-validator-parity-guard --strict` is clean (three MODIFIED requirements restate all scenarios, drop none)
- [x] 6.2 `./gradlew build` green (suite + JaCoCo `jacocoTestCoverageVerification`); confirm the coverage floor **holds** at the current baseline and record the HOLD rationale in the `build.gradle.kts` baseline comment block
- [x] 6.3 Run the tracker/host leak-guard grep on staged files before commit (pattern per the repo's contributor guide)
