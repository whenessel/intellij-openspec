# Design — verify 1.7 reorder + adopt requires[] for downstream

## The reorder's blast radius (audited, every `getArtifacts()` consumer)

| Consumer | Effect of the 1.7 reorder | Verdict |
|---|---|---|
| `applyScaffoldingOverrides` | `specs` is glob-skipped; the non-glob triple keeps relative order | invariant (status + missingDeps) |
| **`getCompletedDownstream`** | positional "DONE after id" → over-lists a sibling on 1.7 | **not invariant → fixed** |
| `getGenerationOrder`/`getReadyArtifacts`/`getNextReadyArtifact` | which READY sibling is "first" can flip | cosmetic (order only; deps still respected) |
| `VerificationService` completeness | iterates non-DONE for warnings | invariant in substance (finding set identical) |
| `SpecTreeModel` artifact nodes | children added in list order; ids in a Set | cosmetic (renders schema order) |
| `WorkflowActionPanel` pipeline chips | `get(i)` for layout, state keyed by id | cosmetic (chip order) |
| `BulkArchiveDialog`, count rollups | done-count / size | invariant |

Only `getCompletedDownstream` carries a real behavior change; everything else is invariant or
cosmetic-display-order. No test asserts artifact display order, so the cosmetic reorderings break
nothing.

## Why `requires[]` over the positional heuristic

`getCompletedDownstream(x)` answers "which already-DONE artifacts could a regeneration of `x` make
inconsistent" — i.e. the DONE artifacts that depend on `x`. The CLI's `requires` edges are exactly
that relation (reversed), so consuming them is both **precise** (never a non-dependent sibling) and
**order-independent** (immune to this and any future array reorder). The walk is **transitive** —
a regeneration can ripple through a dependency chain — matching the positional heuristic's original
"everything downstream" intent while dropping its order-coincidental false positives.

**Fallback is mandatory, not optional.** Pre-1.7 `status --json` has no `requires`, so a pure
edge-based implementation would return nothing on 1.3–1.6 (a silent regression of the warning). The
derivation therefore branches on presence: if any artifact carries a non-empty `requires`, use the
edges; otherwise use the original list-order heuristic verbatim. In spec-driven 1.7 output `tasks`
always requires `specs`+`design`, so "edges present" reliably distinguishes 1.7+ from pre-1.7.

## Model change: additive record component

`ArtifactInfo` becomes a 5-component record (`+ List<String> requires`). Gson 2.13 binds it through
the canonical constructor from 1.7 JSON; a 4-arg convenience constructor (`requires = List.of()`)
keeps all ~38 existing construction sites compiling — so the blast radius is the model file plus the
one consumer, not a repo-wide edit. `applyScaffoldingOverrides` is deliberately **left on** its
list-order heuristic: it is already correct (glob-skip invariance) and its "scaffolded-earlier
blocks later" semantics is about scaffolding propagation, not dependency edges — converting it would
change meaning for no correctness gain.

## Test strategy (test-engineer-reviewed)

- **Real-method fidelity.** `ScaffoldingOverrideTest` drops its hand-copied `applyOverrides` and calls
  the production method via `@Mock Project` + a real `ScaffoldingDetectionService` reading `@TempDir`
  files — removing a vacuity smell (the copy could pass while production drifted).
- **Reorder-invariance pin.** Run the real override over the same scaffolded files using the two real
  captured orderings; assert identical id→status/missingDeps, guarded by a first assertion that the
  orderings actually differ (no silent tautology).
- **Downstream derivation** (`CompletedDownstream` nested, pinning previously-0%-covered logic):
  `specs → [tasks]` on **both** generations (correct + invariant); `specs` downstream excludes the
  unrelated sibling `design` on 1.7; `proposal → [design, tasks]` (transitive, DONE-only); and a
  no-`requires` DAG exercises the positional fallback.
- **Model binding.** `CliContractTest.StatusContractV17` asserts `requires()` is populated off the
  parsed model (Gson-through-canonical), complementing the existing JSON-level edge assertion.

Hand-built `ArtifactInfo` lists are fine here — none of this hand-writes external-output *shape*; the
parse contract stays owned by the contract tests, and the order axis is driven from real fixtures.
Unit tier only — no `verifyPlugin`/`uiSmoke` (no platform-API change). Coverage: `getCompletedDownstream`
was 0% covered; re-measure and ratchet the floors up if the aggregate rose.
