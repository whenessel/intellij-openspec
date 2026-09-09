## 1. Capture 1.12.0 fixtures (contract discipline — real CLI output, sanitized)

- [x] 1.1 Capture the parity-corpus twins from the real 1.12.0 binary (isolated `HOME`/`XDG_*`, `OPENSPEC_TELEMETRY=0`): `validate --all --json` (default and `--strict`) over the shared `1.6.0/parity-corpus` markdown → `src/test/resources/fixtures/cli/1.12.0/validate-parity-corpus{,-strict}.json`. Sanitize the project root to `/fixture`. Verify totals unchanged (default 12/13, strict 9/13) and that the normalized diff (`del(.durationMs, .items[].durationMs, .root.path)`) vs the 1.11 twins is **exactly one added `INFO`** — the archive-blocker on `nameless-change`, in BOTH the default and strict twin (`info-change` gains nothing; its delta is ADDED-only). Per-item INFO totals: `info-change` 1, `nameless-change` 2. Not a durationMs-only copy.
- [x] 1.2 `src/test/resources/fixtures/cli/1.12.0/version.txt` = `1.12.0`.
- [x] 1.3 Capture the **archive-blocker positive-control**: a `valid:false` change that carries BOTH an `ERROR` and the new archive-blocker `INFO` ("Archive would refuse this delta…") on **different** delta paths (they dedupe when sharing a path). Capture real `validate <change> --type change --json` output, sanitize, commit under `1.12.0/`. Verify the capture has `valid:false`, at least one `ERROR`, and at least one `INFO` issue with the archive-refusal message.

## 2. Contract test + version guards (unit tier; reconcile exact fixture/test shape with the test-engineer PLAN at apply start)

- [x] 2.1 Add a contract nest (e.g. `ArchiveBlockerInfoContractV1_12`) in `CliContractTest`, two-arm mirroring `ValidatePurposePlaceholderContractV1_11`: (a) **raw-JSON arm** — pin the captured `INFO` level, `overview`/change `path`, and archive-refusal message off the committed capture, plus `valid:false`; (b) **parser arm** — run the capture through `CliOutputParser.parseJsonOutput` and assert the `INFO` surfaces at `Severity.INFO` AND the failing verdict is preserved (the parser overwrites `path` with `type/id`, so assert level/verdict via the parser, path/message via raw JSON). Must fail if the parser stopped mapping `INFO` or read the verdict from severities.
- [x] 2.2 Add `1.12.0` to `ValidatorVerdictVersionStabilityTest.FLOOR` and to `CliVersionAtLeastTest`'s `@ValueSource`. Verify the version-stability guard auto-discovers and passes the new parity twins.
- [x] 2.3 Extend `CliVersionTest.twoDigitMinor_*` with the **anti-lexical** pair `compare("1.12.0","1.9.0") > 0` (numeric `+1` but lexical `−1`, since `'1' < '9'` at index 2 — the case that actually flips under a lexical regression), plus `1.12.0 > 1.11.0` as a monotonicity check and `!atLeast("1.11.0","1.12.0")`. Verify the `1.12.0`-vs-`1.9.0` assertion fails under a lexical comparison (the `1.12.0`-vs-`1.11.0` one does not — it's true lexically too).
- [x] 2.4 Bump `openspecTargetVersion` `1.11.0` → `1.12.0` in `gradle.properties`. Verify `TargetVersionSingleSourceTest` passes (target == committed corpus == `version.txt`).

## 3. Documentation (vendor-neutral, public mirror)

- [x] 3.1 `docs/openspec-support.md`: add the `1.12.x` line and bump the support-matrix heading to `1.3.x–1.12.x`. Verify `DocumentationHygieneTest` stays green (leave doc `plugin vX.Y.Z` restatements to release-cut).
- [x] 3.2 README `1.12.x` mention + fixtures README `## 1.12.0/` section — document the parity twins (differ from 1.11 by 3 added `INFO`, NOT durationMs-only) and the archive-blocker positive-control provenance + capture recipe.
- [x] 3.3 Add a `## Unreleased` CHANGELOG entry: OpenSpec CLI 1.12.x is now a supported line (additive/safe-direction; verdict-neutral archive-blocker INFO surfaced correctly; no built-in change). Verify `DocumentationHygieneTest` green.

## 4. Adopt-sync

- [x] 4.1 Update the local CLI to 1.12.0 and run `openspec update`; verify the managed `.claude/skills/openspec-*` and `.claude/commands/opsx/*` regenerate to `generatedBy: "1.12.0"` and the custom-named tracker/release skills are untouched. Commit the regenerated managed surfaces.

## 5. Verify

- [x] 5.1 Run `./gradlew build` (test + JaCoCo floor) and confirm green. No `src/main` change is expected; if coverage shifts, only ratchet the floor on a visible rise.
