# Verify 1.7 status id-keying and adopt the CLI's requires[] edges for downstream reasoning

## Why

CLI 1.7's only `status --json` change is additive: a per-artifact `requires: string[]` and a
reorder of `artifacts[]` to schema order (`proposal, specs, design, tasks` — was
`proposal, design, specs, tasks`). Parsing stays green (id/status/missingDeps bind by name; the new
`requires` key was simply dropped), and almost every consumer keys by artifact **id**, so the
reorder is inert for them.

A verification pass found one consumer that is **not** reorder-invariant:
`ArtifactOrchestrationService.getCompletedDownstream` infers "downstream" positionally — every DONE
artifact appearing *after* the queried one in the array. On the 1.7 order that over-lists a sibling
that does not actually depend on the queried artifact (e.g. `getCompletedDownstream("specs")` returns
`["design", "tasks"]` on 1.7 vs `["tasks"]` on 1.6, though `design` requires only `proposal`). This
feeds the "regenerating X may make these already-complete artifacts inconsistent" confirmation, so
the effect is a user-visible **over-warning** — benign (advisory only, never blocking) but wrong, and
the positional heuristic was already approximate for siblings.

The captured 1.7 fixtures already carry the CLI's authoritative dependency edges, so the fidelity fix
is available without new capture: reason from `requires[]` instead of array position.

## What Changes

- **Model the CLI's dependency edges.** `ArtifactInfo` gains an optional `requires: List<String>`
  (5th record component, defaulting to empty) that Gson binds from 1.7+ `status --json`; a 4-arg
  convenience constructor keeps every pre-1.7 / synthetic call site compiling unchanged.
- **Make `getCompletedDownstream` authoritative and reorder-invariant.** It now walks the real
  `requires` edges transitively when the DAG carries them (precise, order-independent — it never
  over-lists a non-dependent sibling), and falls back to the original list-order heuristic when they
  are absent, so 1.3–1.6 behavior is preserved exactly. Extracted as a pure static helper so it is
  pinned directly against captured DAGs.
- **Verify the rest of the reorder surface.** `applyScaffoldingOverrides` is confirmed
  reorder-invariant (the only reordered artifact, `specs`, is a glob it skips; the participating
  non-glob artifacts keep relative order) and pinned by a test that runs the **real** method over both
  the 1.6 and 1.7 orders. The remaining reorderings (pipeline chips, tree children, generation order)
  are cosmetic display-order only, keyed by id — audited, no behavior change.
- **Kill a test-vacuity smell.** `ScaffoldingOverrideTest` no longer hand-copies the production logic;
  it exercises the real `applyScaffoldingOverrides` via a mocked `Project` + real
  `ScaffoldingDetectionService`, so it cannot pass while the method drifts.

## Capabilities

No capability spec changes. `getCompletedDownstream` and the scaffolding-override derivation are not
governed by any capability requirement, and the change adds/removes no user-facing capability — it
makes an existing advisory more accurate and hardens its tests. Marked `skip_specs: true`. The
user-facing improvement is recorded in the changelog.

## Impact

- `ArtifactInfo` (model), `ArtifactOrchestrationService.getCompletedDownstream` (+ a pure helper).
- Tests: `ScaffoldingOverrideTest` (real-method rewrite + reorder-invariance pin),
  `ArtifactOrchestrationServiceTest` (new `CompletedDownstream` coverage of previously-0%-covered
  logic), `CliContractTest.StatusContractV17` (model-level `requires` binding).
- Backed by the committed 1.6.0 + 1.7.0 `status.json` fixtures — no re-capture.
