# Design — 1.7.0 fixture corpus capture

## Scope of the 1.6 → 1.7 delta (source-verified)

1.6 → 1.7 is a strictly **additive superset** on every parsed surface. Only three captured
families carry an observable difference; the rest are byte-for-byte twins whose V17 nests are
verbatim copies of the V16 nests repointed at the `1.7.0/` fixture.

| Family | 1.7 delta | V17 nest treatment |
|---|---|---|
| `status --json` | per-artifact `requires: string[]` added; `artifacts[]` + `missingDeps[]` reordered to schema order `[proposal, specs, design, tasks]` | **id-keyed** status/`missingDeps` assertions (index-based would break on the reorder) + one JSON-level `requires`-edge assertion. `ArtifactInfo` has no `requires` field, so Gson drops it — additive-tolerant, no parser change. |
| `instructions --json` | `unlocks[]` reordered to schema order (`proposal` unlocks `["specs","design"]`, was `["design","specs"]`) | assert the reordered `unlocks`. |
| `update` (legacy pending) | `init --tools junie` on 1.7 scaffolds more legacy `opsx-*` files → the "Files to remove" list grows from 4 to the count 1.7 emits | assert the 1.7 count as captured. |
| validate / single-item validate / strict-warning | none (verdict + issue shape stable across 1.3 → 1.7) | verbatim. |
| schema-validate / schema-which / templates | none | verbatim. |
| store register / doctor | none | verbatim. |
| change-deltas `show --type change` | none | verbatim. |
| spec-structure `show --type spec` | none (parser recovers identical requirement/scenario counts) | verbatim, driven off the reused corpus. |

The capture agent's diff report is the authority on which families actually differ; any family
that diverges beyond the three above is treated as a finding, not silently absorbed.

## Capture method

- Every family is captured under an isolated `HOME`/`XDG_DATA_HOME`/`XDG_CONFIG_HOME`/
  `XDG_STATE_HOME` env with `OPENSPEC_TELEMETRY=0`, so the developer's real global OpenSpec
  state is never touched.
- The **only** edits to captured output are path sanitizations, matched to each 1.6.0 twin's
  existing tokens: project roots → `/fixture/demo-project`, `/fixture`, or `/fixture/parity-corpus`
  as the family dictates; the global package prefix → `/fixture/node_modules`; store roots →
  `/fixture/<leaf>`.
- `openspec archive` is never run during capture (it mutates and can abort on constraints).

## Scope trims (avoid duplicate authoring / heavy clones)

- **Parity corpus is authored once.** The spec-structure and validate-parity V17 nests reuse
  the committed `1.6.0/parity-corpus` markdown as capture *input*; only the 1.7.0 *outputs*
  (`spec-structure/*.show.json`, `validate-parity-corpus.json`) are re-captured. This keeps the
  corpus a single source and lets the parity check be a direct output-to-output comparison.
- **Verdict-parity is a lightweight fixture-to-fixture check, not a platform clone.** Rather than
  re-materialize the corpus into a live test project for a 1.7 run of `ValidatorVerdictParityTest`,
  the V17 stability check parses both `1.6.0/validate-parity-corpus.json` and
  `1.7.0/validate-parity-corpus.json` and asserts the id→valid verdict maps are equal — proving
  the CLI's own verdicts did not drift 1.6 → 1.7 (the evidence base for the "never stricter than
  the CLI" invariant), without duplicating the heavy live-validation harness.

## Coverage

The V17 nests re-exercise the **same** parsers as the V16 nests with no `src/main` change, so
aggregate instruction/line/branch coverage is flat. The JaCoCo floors are **not** ratcheted —
ratcheting here would only reduce the CLI-wobble headroom for no coverage gain.

## Delta-less classification

The change carries `skip_specs: true`. It implements toward the existing `ci` capability's
per-generation-capture scenario without changing any requirement's normative text, so there is
no delta spec to author or sync. This is the honest classification and doubles as a live
exercise of the 1.7.0 `skip_specs` metadata field.
