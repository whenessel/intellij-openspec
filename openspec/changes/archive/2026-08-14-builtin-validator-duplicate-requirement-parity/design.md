# Design

## Context

OpenSpec CLI 1.8 added (upstream #1484) a main-spec validation rule: two `### Requirement:` headers with the same canonical name in one spec is an ERROR. It exists because delta reconciliation at archive time would otherwise silently collapse one of the colliding blocks. Although the CHANGELOG frames it as an archive-time rejection, the same parser (`findMainSpecStructureIssues`) backs the `validate` path, so `openspec validate <spec>` surfaces the ERROR too — which is what the plugin mirrors (the plugin's fallback mirrors `validate`, not archive).

The plugin's CLI-present path already shows the CLI's own duplicate-requirement ERROR verbatim. Only the **CLI-absent built-in fallback** (`BuiltInValidator.validateSpecContent`) misses it, so this change is scoped entirely to that method.

## Exact upstream behavior (captured from the real 1.8.0 CLI + source-confirmed)

Verified by running the real 1.8.0 CLI over hand-built specs in an isolated environment, cross-checked against the installed dist source and the v1.7.0 tag:

- **Emitted issue:** `level: ERROR`, `path: "file"` (a literal string, not the spec path), `line:` the **second (duplicate)** occurrence's line. Message, verbatim shape:
  `Requirement header "### Requirement: <name>" duplicates the requirement declared on line <first>. Requirement names must be unique so spec updates cannot discard one block while updating another.`
  The quoted text is the **full trimmed header line** (`### Requirement: <name>`), not the bare name; `<first>` is the first declaration's line, which appears only in the message text.
- **N occurrences → N−1 errors:** each later occurrence of a repeated name is flagged, each referencing the *first* declaration's line (verified: three `Alpha` headers → errors on the 2nd and 3rd, both pointing at the 1st).
- **Match is exact and case-SENSITIVE:** the key is the trimmed name used directly as a map key. Exterior whitespace is trimmed; interior whitespace is significant; case is significant (`Works` ≠ `works`, `Process  Refund` ≠ `Process Refund`). Verified `valid:true` on a case-only difference.
- **Section-scoped:** only `### Requirement:` headers **inside the `## Requirements` section** (matched case-insensitively) are deduped. Headers before it, or after the next top-level `## ` header, are routed to a different (non-duplicate) diagnostic and never enter the dedup map.
- **Fence-masked:** a `### Requirement:` inside a fenced code block does not count — the CLI strips fenced blocks first. The fallback already runs on `maskFences(...)`, so this is automatic.
- **Independent of the missing-SHALL rule:** both fire. A duplicated requirement that also lacks `SHALL`/`MUST` yields one duplicate ERROR (on the 2nd occurrence) **plus** a missing-keyword issue for each occurrence. The duplicate check is additive to the existing per-requirement loop — it does not skip or supersede the other rules.
- **Delta specs use a different, separate rule:** change delta specs (`openspec/changes/**/specs/**`) are deduped per-operation-section by a different code path with a different message (`Duplicate requirement in ADDED: "<name>"`), `path: "<cap>/spec.md"`, and **no line**. That is **not** this change — the fallback does not reimplement delta-consistency, and applying the main-spec rule to deltas would make the fallback more restrictive than the CLI (a name may legitimately recur across sections).

## Decisions

### D1 — Detect duplicates inside the existing requirement loop, scoped to `## Requirements`

`validateSpecContent` already iterates every `### Requirement:` via `REQUIREMENT_PATTERN` over the fence-masked content. Duplicate detection is added as a name→first-line map populated as the loop runs, but **only for requirements that fall within the `## Requirements` section** — matching the CLI's section-scoping so a stray `### Requirement:` under some other `## ` heading is not deduped (which would over-report). The section bounds are computed once (from the case-insensitive `## Requirements` header to the next top-level `## ` or EOF) on the fence-masked content; a requirement whose header offset is outside those bounds is skipped for the duplicate check only (its existing RFC/scenario checks are unchanged — those are pre-existing behavior, out of scope here).

- The map key is the requirement name exactly as the CLI keys it: `reqHeader.trim()` (the loop's `reqHeader` is already `group(1).trim()`), preserving interior whitespace and case.
- On the first sighting of a name, record `name → reqLine`. On a later sighting, emit the ERROR anchored on the current `reqLine`, message referencing the stored first line — matching N−1 emissions automatically.
- Message mirrors the CLI wording, quoting the full header (`### Requirement: ` + name).

### D2 — New plugin rule id `spec-duplicate-requirement`; a hard ERROR (not a strict-warning)

The CLI emits no machine rule-id (the issue shape is `{level, path, message, line?}` — freeform message only), so the plugin assigns its own internal id, consistent with every other built-in rule. `spec-duplicate-requirement` is an **ERROR that fails the verdict in both default and strict** — the CLI reports the file `valid:false` in default mode — so, unlike the missing-keyword rules, it is **not** added to `CLI_MIRRORING_STRICT_WARNINGS`; there is no severity conditionality and no strict re-promotion to model.

### D3 — Scope strictly to the main-spec path; delta path untouched

The check lives only in `validateSpecContent`, which serves the main-spec fallback (`openspec/specs/**`). The delta-spec path (`validateSingleChange` → the delta validator) is not modified, so the fallback stays no more restrictive than the CLI on delta specs. The delta-side duplicate rules (per-section `Duplicate requirement in ADDED/…`) are a deliberate non-goal — the fallback under-reports them, which is the safe direction, and mirroring them is a possible future follow-up, not this change.

### D4 — Two `## Requirements` sections resolve to first-section-only (cannot over-report)

A main spec with two `## Requirements` sections is pathological and the real CLI's behavior there is unpinned. The section bounds are computed as `[first ## Requirements, next top-level ## )`, so dedup covers the first section only; a name reused in a later `## Requirements` section is not flagged. This is the choice that **cannot over-report** (the failure this change must avoid), chosen deliberately over trying to match an uncaptured CLI behavior. Pinned by a rules test.

### D5 — Validate/Verify surface only; no live editor inspection (matches `spec-scenario-required`)

`SpecFormatInspection` re-implements its own requirement scan (sharing only `maskFences`) and does not call `validateSpecContent`, so the duplicate rule surfaces in the Validate action / Verify panel but **not** as a live editor squiggle. This is deliberate and consistent with `spec-scenario-required`, which is likewise validate-only and absent from the inspection. Adding an editor squiggle would be a second production path plus `SpecFormatInspectionTest` coverage — out of scope for a fallback-parity change.

## Test strategy

Follows the project's contract-test discipline and mirrors the `validate-single-spec-no-body` pattern established in Change A. (test-engineer PLAN mode is consulted at apply start per the routing conventions; this section records the intended shape.)

- **Fixture (captured from the real 1.8.0 CLI, never hand-authored):** `src/test/resources/fixtures/cli/1.8.0/validate-single-spec-duplicate-requirement.json` — a real `openspec validate <spec> --type spec --json` over a two-`Works` main spec, `root.path` sanitized to `/fixture`. The capture recipe (isolated `HOME`/`XDG_*`, telemetry off) is recorded in the fixtures README manifest. The already-captured raw output lives in the session scratchpad and is re-capturable from the recipe.
- **Contract test:** extend `CliContractTest.ValidateContractV18` with a case asserting the duplicate item is `valid:false`, `level:ERROR`, `path:"file"`, `line` = the second occurrence, and the message contains `duplicates the requirement declared on line`. Keying is off `level`+`path`+message-content, never a rule-id (the CLI has none).
- **Built-in validator rule tests (drive the REAL validator, no inline mirror):**
  - `BuiltInValidatorRulesTest` — a duplicate-name main spec yields exactly one `spec-duplicate-requirement` ERROR on the second header; a triple yields two; a case-only difference yields none; a `### Requirement:` inside a fence does not count; a name outside `## Requirements` is not deduped.
  - `BuiltInValidatorTest` (integration) — a duplicate-requirement spec fails the built-in verdict (default and strict); and, to lock the "independent of missing-SHALL" behavior, a duplicated body-less requirement still emits both the duplicate ERROR and the missing-keyword issue.
  - A delta spec that repeats a name across `## ADDED`/`## MODIFIED` produces **no** `spec-duplicate-requirement` error from the fallback (guards D3).
- **Parity guards unaffected — and the shared corpus MUST stay duplicate-free.** The 1.6.0 parity corpus contains no duplicate spec, so `ValidatorVerdictParityTest` and `ValidatorVerdictVersionStabilityTest` verdict maps do not move. This is load-bearing, not incidental: the duplicate rule is a **1.8 tightening** (a duplicate spec is `valid` on 1.6/1.7, `invalid` on 1.8). If a duplicate spec were added to the shared corpus, `ValidatorVerdictVersionStabilityTest`'s default arm (`cliDefaultVerdictsAreNeverStricterThanTheLaxestGeneration`, which models only *relaxations* and treats 1.8 as the laxest generation) would break — 1.8 would be *stricter* than 1.6/1.7 on that item, contradicting its premise. So the duplicate rule is locked exclusively by the standalone single-item fixtures (which the corpus discovery glob deliberately ignores), never the shared corpus. A future contributor must not "helpfully" add it there.
- **No `verifyPlugin`/`uiSmoke`:** no new `com.intellij.*` API is referenced; the change is a pure content-parsing addition plus tests.
- **Coverage:** the change adds covered branches; ratchet the JaCoCo floor only if the measured minimums rise above the current thresholds (they should not fall).

## Risks / trade-offs

- **Over-reporting on unusual main specs.** The section-scoping (D1) is the guard against flagging a `### Requirement:` the CLI would route to `requirement-outside-requirements` instead. Without it the fallback would be more restrictive than the CLI. The contract fixture plus the "name outside `## Requirements`" rule test lock this.
- **Message drift.** The fallback message mirrors the CLI's wording but is plugin-generated; if the CLI reworded it, the CLI-present path would show the new text while the fallback keeps the old. This is acceptable (the fallback is a degraded-mode approximation) and consistent with how the other fallback messages already relate to the CLI's.
