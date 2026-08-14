# Tasks

Additive change: the fallback gains one new ERROR rule on the main-spec path. Unlike Change A this is **not** atomically coupled to the parity guards — the 1.6.0 parity corpus has no duplicate spec, so no oracle or anchor moves. A test-engineer PLAN pass ran at the start of implementation; its findings (assert error locations + N−1 references-first, missing-keyword count==2 for independence, positive controls for every negative, and a real-CLI capture of the section-boundary negative) are folded into section 3.

## 1. Capture the 1.8.0 duplicate-requirement fixtures

- [x] 1.1 Captured `src/test/resources/fixtures/cli/1.8.0/validate-single-spec-duplicate-requirement.json` from the **real 1.8.0 CLI** (isolated `HOME`/`XDG_*`, telemetry off) — a real `openspec validate <spec> --type spec --json` over a main spec declaring `### Requirement: Works` twice (ERROR, `path:"file"`, line 15 = 2nd occurrence, message names line 8).
- [x] 1.2 Sanitized: `root.path` → `/fixture`; machine-path scan clean.
- [x] 1.3 Also captured `validate-single-spec-requirement-outside-section.json` (the section-boundary negative — the real CLI does **not** dedup an out-of-section occurrence; it emits `requirement-outside-requirements` instead), per the test-engineer's higher-priority recommendation to lock the one unsafe direction against real output. Added a `1.8.0/` section to the fixtures README manifest documenting the whole generation set (Change A had left it undocumented).

## 2. Add duplicate-requirement detection to the built-in validator

- [x] 2.1 `BuiltInValidator.validateSpecContent` — computes the `## Requirements` section bounds once (case-insensitive header → next top-level `##` or EOF) on the fence-masked content; a `name → first-line` map is populated only for requirements whose header falls within those bounds.
- [x] 2.2 On a repeated name (key = `reqHeader.trim()`, case- and interior-whitespace-sensitive), emits `ValidationIssue(ERROR, path, reqLine, message, "spec-duplicate-requirement")` anchored on the current occurrence, message referencing the stored first line and quoting `### Requirement: <name>`. N occurrences ⇒ N−1 errors, each pointing at the first.
- [x] 2.3 Additive: the existing missing-keyword / scenario checks still run for every occurrence; not added to `CLI_MIRRORING_STRICT_WARNINGS` (it is already a hard ERROR). Two `## Requirements` sections resolve to first-section-only (cannot over-report — design D4). Validate/Verify surface only; no live editor squiggle (design D5, matching `spec-scenario-required`).

## 3. Validator tests

- [x] 3.1 `CliContractTest.ValidateContractV18` — duplicate case: raw-JSON `level:ERROR` / `path:"file"` / `line==15` / message names line 8 / no machine rule-id, plus a `parseJsonOutput` failing-verdict assertion. (Confirmed `parseJsonOutput` discards `path`/`line`, so those are asserted at raw-JSON level only.) Added the section-boundary contract test asserting the CLI emits **no** duplicate for an out-of-section occurrence.
- [x] 3.2 `BuiltInValidatorRulesTest` (drives the real `validateSpecContent`): dup pair (error at 2nd line, message refs 1st); triple (exactly two errors, locations = {2nd,3rd}, each refs 1st, 3rd does NOT ref 2nd); match-key test with positive control (identical→1, case→0, interior-ws→0, exterior-trim→1); header-token-case-differs-name-same→dup; fenced→0 with unfenced positive control; outside-`## Requirements`→0 with in-section positive control; second `## Requirements` section→0; delta-sections→0.
- [x] 3.3 `BuiltInValidatorTest` (integration) — otherwise-clean dup spec is the SOLE ERROR and fails default + strict; a body-carrying-no-keyword dup pair emits one duplicate ERROR **and 2** `spec-rfc-keywords` WARNINGs (count, to catch a loop-skip).
- [x] 3.4 Delta-scope guard — a delta spec repeating a name across `## ADDED`/`## MODIFIED` produces **no** `spec-duplicate-requirement` error (rules test, since the integration delta path can't read temp-VFS delta files).

## 4. Documentation (public / vendor-neutral)

- [x] 4.1 `docs/openspec-support.md` — 1.8.x line + bottom-line now note the fallback mirrors 1.8's main-spec duplicate-requirement ERROR (scoped to `## Requirements`), with delta-consistency kept as a deliberate under-report.
- [x] 4.2 `CHANGELOG.md` `## Unreleased` → **Added**: built-in validation flags a duplicate requirement name as an error, framed as CLI parity (fallback was previously silent where the CLI errors), noting the delta-scope carve-out.
- [x] 4.3 `docs/feature-reference.md` — added the duplicate-requirement rule to the CLI-absent fallback-severity examples.
- [x] 4.4 `docs/feature-comparison-matrix.md` — no change; its per-release snapshot stamp already re-affirms the 1.8.x-support change (no competitive-feature change).

## 5. Verify

- [x] 5.1 `./gradlew build` green — full suite + JaCoCo floors ratcheted 0.388/0.366/0.364 → 0.390/0.368/0.366 (measured 0.3959/0.3751/0.3732).
- [x] 5.2 `openspec validate builtin-validator-duplicate-requirement-parity --strict` clean; anti-leak grep over changed files + fixtures clean.
- [x] 5.3 No `verifyPlugin`/`uiSmoke` gate triggers — no new `com.intellij.*` reference added (the change is `java.util.Map`/regex only).
