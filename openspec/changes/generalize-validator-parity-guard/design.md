## Context

See proposal.md — Why. The relevant tests and seams:

- `ValidatorVerdictParityTest` (platform, heavy): materializes the 13-item corpus from `fixtures/cli/1.6.0/parity-corpus/` and asserts the plugin's per-item ERROR-based verdict equals each item's `valid` in `fixtures/cli/1.6.0/validate-parity-corpus.json`. **Plugin-vs-CLI parity, anchored at 1.6.0, default mode.**
- `ValidatorVerdictVersionStabilityTest` (platform-free, light): asserts the `id → valid` maps of the 1.6.0 and 1.7.0 corpora are equal. **CLI-vs-CLI stability, hard-coded to the 1.6→1.7 pair.**
- `OpenSpecValidateAction.applyStrictFallbackVerdict(result, strict)` (`...actions/…:106`): the CLI-absent strict fallback. Flips a passing result to FAIL if any issue is a WARNING whose rule does not start with `config-`. When the CLI is present, the CLI's own `--strict` verdict is authoritative (no re-flip).

`validate --all --json` per-item `{id,type,valid}` is byte-stable 1.3.0→1.7.0; the strict verdict map is byte-identical to the non-strict map on this corpus (no item is valid-with-only-a-CLI-warning). The clearest strict-flipping CLI warning is `PURPOSE_TOO_BRIEF` (the one this corpus would exercise); the CLI defines a few others (delta-description-too-brief, delta-missing-requirements), but the plugin implements *none* of the CLI's warning rules — so no plugin WARNING mirrors a CLI strict-failure.

## Goals / Non-Goals

**Goals**
- Make cross-generation stability version-agnostic by construction (default and strict).
- Fix the CLI-absent strict fallback so it is never more restrictive than the CLI, and lock it with a strict parity block.

**Non-Goals**
- Backfilling pre-1.6 captures of the current corpus (Decision 2).
- Adding a `spec-purpose-brief` WARNING to *match* the CLI's one strict warning, or promoting `delta-spec-sections` from WARNING to ERROR — both are laxness-closing enhancements, separate from removing the over-restriction.

## Decisions

### Decision 1 — Auto-discovery over committed corpora, anchored at 1.6.0
Rewrite `ValidatorVerdictVersionStabilityTest` to enumerate `fixtures/cli/<gen>/validate-parity-corpus.json` on the classpath (`getResource("/fixtures/cli").toURI()` → `Files.list`, filtering to dirs containing that file — the idiom already in `SpecParserCliStructureContractTest`) and assert each equals the `1.6.0` anchor. Version-agnostic by construction. The filename filter must match `validate-parity-corpus.json` **exactly** (not a `…*` glob) so the strict twin (Decision 5) never bleeds into the default comparison.

### Decision 2 — No backfill of pre-1.6 generations
Real 1.3.0/1.4.1/1.5.0 runs over the current corpus flip two items legitimately (`fenced-scenario` at 1.4, `second-line-keyword` at 1.6) — the CLI's own rules evolved. A frozen "identical map across 1.3–1.7" assertion is therefore impossible, not merely costly; it would encode false drift. The corpus is a 1.6-era artifact captured only at generations that share its dialect. The 1.3.0 floor's shape stability is locked by its own 2-item fixture. The spec records this boundary so a future author does not add pre-1.6 captures to the equality set.

### Decision 3 — Heavy plugin-vs-CLI harness stays 1.6-only for the version dimension
`plugin ≡ 1.6.0-CLI` (heavy test) ∧ `1.6.0-CLI ≡ Nth-captured-CLI` (light test) ⟹ `plugin ≡ Nth-captured-CLI`. No per-version cloning. Adding a *strict* dimension at 1.6 (Decision 4) does not reintroduce per-version cloning — it is a second axis at the same anchor.

### Decision 4 — Fix `applyStrictFallbackVerdict` as an allow-list; strict parity block on `ValidatorVerdictParityTest`
The bug: the fallback flips on any non-`config-` WARNING, but every built-in non-config WARNING is a plugin-invented lint the CLI never emits (`change-artifact-missing`, `change-schema-incompatible`, `spec-title-required`, `delta-removed-fields`) or a condition the CLI reports as an ERROR, not a warning (`delta-spec-sections`). So the fallback reds `info-change`/`nameless-change` (missing `design.md`/`tasks.md`) that the CLI reports strict-valid.

Fix: flip only if some WARNING's rule is in `CLI_MIRRORING_STRICT_WARNINGS`, an explicit set that is **empty today**. ERRORs still fail via the existing `!result.passed()` early return.

- **Allow-list over deny-list (load-bearing):** the invariant is one-directional (never *more* restrictive). A deny-list makes a new rule flip by default (stricter) → forgetting to exclude a lint reintroduces this exact bug class. An allow-list makes a new rule not flip by default (laxer, CLI-absent only) → forgetting to include a genuinely CLI-mirroring warning is a benign parity gap, not an invariant violation. Both are behaviorally identical today; the allow-list fails safe.
- **Test placement:** add a strict block to the existing platform `ValidatorVerdictParityTest` — it already materializes the corpus and computes the full `issues` list; the strict block is one more pass. Per item, scope `issues` by the existing `/<id>/`-or-`endsWith(/<id>)` marker (the `endsWith` form catches change-dir-level issues like `change-artifact-missing`), build the per-item `ValidationResult`, and call the **real** `applyStrictFallbackVerdict(perItem, true)` — never an inline re-implementation, so the assertion tracks the fix and cannot silently degenerate into the non-strict check. Expose the method `public static` (precedented by `combineWithCli` directly below it, exposed for the same reason).

### Decision 5 — Strict fixture + cross-generation strict stability
Capture `fixtures/cli/1.7.0/validate-parity-corpus-strict.json` (real `validate --all --strict --json` over the shared 1.6 corpus markdown, `root.path` sanitized to `/fixture/parity-corpus`). Committed under `1.7.0/` because 1.7.0 is the capturing CLI (fixtures-README layout rule). Extend the discovery test to also glob `validate-parity-corpus-strict.json` and assert each strict map equals the non-strict 1.6 anchor — cross-generation strict stability in the platform-free tier, leaving the parity block to own plugin-vs-CLI-strict.

## Risks / Trade-offs

- **Strict block degenerates into the non-strict check** (its deepest trap) → compute via the real `applyStrictFallbackVerdict`, and add an anchor assertion that a `change-artifact-missing` WARNING is actually present on a CLI-strict-valid item (`info-change`), so the WARNING branch is provably exercised and the fix provably spares a genuinely-present lint warning.
- **Vacuous green** (empty glob, one corpus, all-invalid strict set) → assert the `/fixtures/cli` resource resolves; ≥ 2 corpora incl. anchor and a `{1.6.0,1.7.0}` floor; identical 13-item key set across corpora; and the exact strict-valid id count (9) so a future all-invalid re-capture can't hide the flip logic.
- **`-strict` fixture bleeding into the default comparison** → the non-strict discovery filter matches the exact filename `validate-parity-corpus.json`, and the strict discovery matches `validate-parity-corpus-strict.json`; the two never intersect.
- **A real future tightened verdict** trips the guard on the next capture → intended signal; investigate the CLI change, never edit a committed fixture to force green.

## Migration Plan

Not applicable — no data/state migration. The durable next-generation capture recipe (isolated `HOME`/`XDG_*`, `OPENSPEC_TELEMETRY=0`, reuse `1.6.0/parity-corpus/` markdown as input, `validate --all [--strict] --json`, sanitize `root.path`, commit under `fixtures/cli/<gen>/`) is documented in the fixtures README so a future generation is a mechanical addition both discovery tests pick up automatically.
