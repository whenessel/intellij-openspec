# Tasks — verify 1.7 reorder + adopt requires[] for downstream

## 1. Model the CLI dependency edges

- [x] 1.1 Add optional `requires: List<String>` to `ArtifactInfo` (5th component, null→empty) + a 4-arg convenience constructor so pre-1.7/synthetic call sites compile unchanged
- [x] 1.2 Confirm Gson 2.13 binds `requires` from 1.7 `status --json` via the canonical constructor (asserted in `CliContractTest.StatusContractV17`)

## 2. Make downstream reasoning authoritative

- [x] 2.1 Extract `ArtifactOrchestrationService.completedDownstream(artifacts, id)` as a pure static helper
- [x] 2.2 Consume `requires[]` transitively when present; fall back to the positional list-order heuristic when absent (preserve 1.3–1.6 behavior)
- [x] 2.3 Leave `applyScaffoldingOverrides` on its list-order heuristic (already reorder-invariant; different semantics) — comment why

## 3. Verify + pin the reorder surface

- [x] 3.1 Rewrite `ScaffoldingOverrideTest` to exercise the **real** `applyScaffoldingOverrides` (mock `Project` + real `ScaffoldingDetectionService`); delete the hand-copy
- [x] 3.2 Add a reorder-invariance pin (real override over 1.6 vs 1.7 order → identical status/missingDeps, guarded)
- [x] 3.3 Add `ArtifactOrchestrationServiceTest.CompletedDownstream` (specs→[tasks] both gens; excludes design on 1.7; proposal→[design,tasks]; positional fallback)
- [x] 3.4 Add the model-level `requires` binding assertion to `StatusContractV17`
- [x] 3.5 Audit the cosmetic consumers (chips, tree children, generation order) — display-order only, keyed by id (documented in design)

## 4. Docs + verification

- [x] 4.1 CHANGELOG Fixed entry (regenerate no longer over-warns)
- [x] 4.2 `./gradlew build` green; re-measure coverage and ratchet floors if the aggregate rose
- [x] 4.3 Leak-scan before commit
