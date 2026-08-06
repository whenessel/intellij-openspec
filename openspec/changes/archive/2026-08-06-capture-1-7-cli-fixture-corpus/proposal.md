# Capture the CLI 1.7.0 contract-test fixture corpus

## Why

The plugin was aligned to OpenSpec CLI 1.7.0 by two earlier changes that each
captured the narrow slice of 1.7.0 output they needed (change-metadata files and
config-validation JSON). The rest of the parsed CLI surface — status, instructions,
validate, single-item validate, schema tooling, templates, the store family, change
deltas, spec structure, and `update` — is still contract-tested only against the
`1.6.0/` captures. 1.7.0 is now the npm `latest` default install, and its
`status --json` output genuinely differs from 1.6.0 (per-artifact `requires[]` plus a
reorder of `artifacts[]`/`missingDeps[]` to schema order), yet no committed fixture
proves the plugin's parsers stay correct on that shape.

The testing capability already mandates the remedy: *"New-generation captures SHALL land
under the new generation's version-named directory with their own generation-specific
tests, and the existing generation's fixtures and assertions SHALL be preserved
verbatim."* This change executes that lifecycle for the 1.7.0 generation.

## What Changes

- **Capture the full 1.7.0 CLI output corpus** into `src/test/resources/fixtures/cli/1.7.0/`,
  mirroring the family layout of `1.6.0/`: status (×3), instructions (×3), validate +
  single-item validate + strict-warning (×5), schema-validate/schema-which/templates (×7),
  the store register/doctor family (×5), change-deltas (×3), spec-structure (×11), the
  validate-parity-corpus output, and the `update` transcripts (×3). Each is captured from
  the real 1.7.0 client under an isolated `HOME`/`XDG_*` env with machine paths sanitized.
- **Add `…V17` contract-test nests** alongside the existing `…V16` nests in the seven
  consuming contract tests. Most mirror their V16 twin verbatim against the 1.7.0 twin
  fixture; three encode the real 1.7.0 deltas:
  - **status** — the nest keys artifact status/`missingDeps` **by id** (not array index) so
    the schema-order reorder is inert, and adds one JSON-level assertion that the additive
    `requires[]` edges are present in the captured output.
  - **instructions** — the `unlocks[]` reorder to schema order is asserted as observed.
  - **update** — the legacy "Files to remove" list is asserted at its 1.7.0 count.
- **Reuse the committed `1.6.0/parity-corpus` markdown as the capture *input*** for the
  spec-structure and validate-parity families (only the 1.7.0 *outputs* are re-captured),
  so the parity corpus is authored once and the verdict-stability check compares the two
  generations' outputs directly.
- **Extend the fixture provenance manifest** (`fixtures/cli/README.md`) with the 1.7.0
  recipes and the observed 1.6→1.7 deltas.

The 1.6.0 fixtures and their assertions are left untouched.

## Capabilities

No capability spec changes. This change is pure contract-test infrastructure that conforms
to the existing `ci` capability's *"New-generation captures are added without weakening
legacy coverage"* scenario; it neither adds, removes, nor re-gates any user-facing behavior.
Declaring 1.7.x a formally supported generation (the `plugin-core` supported-versions delta,
docs, and coverage-floor work) is a separate change. Because there is no delta, the change
carries `skip_specs: true` in its `.openspec.yaml` — itself an exercise of the 1.7.0 metadata
feature.

## Impact

- New captured fixtures under `src/test/resources/fixtures/cli/1.7.0/` and new `…V17` test
  nests; no `src/main` change, so aggregate coverage is flat and the JaCoCo floors are **not**
  ratcheted.
- Grounds the remaining 1.7 epic work: the durable version-agnostic parity guard generalizes
  over these per-generation corpora, and any future `requires[]`-driven DAG derivation has a
  captured shape to build against.
- Feature-delta analysis: `docs/cli-versions/1.7.md`.
