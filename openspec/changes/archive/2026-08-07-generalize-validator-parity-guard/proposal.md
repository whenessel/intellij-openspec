## Why

The plugin's built-in validator must never be *more restrictive* than the OpenSpec CLI it wraps — a spec/change the CLI reports `valid` must not be flipped invalid by the plugin. Today that guarantee is proven against only **one** captured generation and only in **default** mode:

- `ValidatorVerdictVersionStabilityTest` checks that the CLI's own verdicts did not drift between exactly **1.6.0 and 1.7.0** — a hard-coded version pair whose own javadoc asks to "generalize this across every captured generation."
- Nothing guards the invariant under `validate --strict`. Investigating that gap surfaced a **real bug**: the plugin's CLI-absent strict fallback (`applyStrictFallbackVerdict`) fails a verdict on *any* non-`config-` WARNING, but every such WARNING is a plugin-invented lint the CLI never emits (verified against a real `validate --all --strict --json` capture). So a user **without the CLI** who runs strict Validate on a change missing `tasks.md`/`design.md` gets a red verdict the CLI reports strict-*valid* — the exact "more restrictive than the client we wrap" harm the `validation` spec forbids.

With the CLI now shipping 1.7.0 by default (1.8.0 already published upstream), the guard needs to be durable and version-agnostic, and correct in strict mode.

Tracked as the durable-parity-guard item in the OpenSpec CLI 1.7.x support epic.

## What Changes

**Durable, version-agnostic guard (core)**
- Rewrite `ValidatorVerdictVersionStabilityTest` to *discover* every committed `fixtures/cli/*/validate-parity-corpus.json` at run time (matching that exact filename) and assert they all carry the same per-item `id → valid` map as the anchor generation (1.6.0). A future `1.8.0` capture is then covered with **zero test edits**, and any generation that tightens a verdict on the shared corpus fails the build.
- **No backfill of 1.3.0 / 1.4.1 / 1.5.0.** They verdict the current corpus *differently for legitimate reasons* — the CLI's own rules evolved (fence-aware scenario counting arrived in 1.4; multi-line requirement-body keyword reading in 1.6). Capturing them would encode false drift, not detect regressions. The 1.3.0 floor's `validate --json` shape is already covered by its own era-appropriate fixture.
- The heavy 1.6-anchored `ValidatorVerdictParityTest` (plugin-vs-CLI parity) is unchanged for the version dimension — transitivity closes the chain, and it is the real coverage of `BuiltInValidator` over the corpus.

**Strict-mode parity (arm) — includes a `src/main` bug fix**
- **Fix `applyStrictFallbackVerdict`** so the strict fallback flips a passing verdict on a WARNING only when that rule is in an explicit `CLI_MIRRORING_STRICT_WARNINGS` allow-set — i.e. a warning the real CLI itself emits and fails on under `--strict`. That set is **empty today**, so no built-in WARNING flips strict; ERRORs still fail in both modes. This removes the over-restriction on `change-artifact-missing`, `change-schema-incompatible`, `spec-title-required`, `delta-removed-fields`, `delta-spec-sections`. An **allow-list** is chosen over a deny-list deliberately: a future rule then defaults to *not* flipping (laxer, safe) rather than flipping (stricter) — the fail-safe direction for a never-more-restrictive invariant.
- Expose `applyStrictFallbackVerdict` as `public static` (test seam; mirrors `combineWithCli` right below it).
- Add a captured `fixtures/cli/1.7.0/validate-parity-corpus-strict.json` (real `validate --all --strict --json` over the shared corpus) and a **strict-dimension block** to `ValidatorVerdictParityTest` asserting the plugin's per-item strict fallback verdict never exceeds the CLI's strict verdict — driving the *real* fixed method, so it tracks the fix and would go red on the unfixed code.
- Extend the discovery test to also glob `fixtures/cli/*/validate-parity-corpus-strict.json` and assert each strict map equals the non-strict anchor (cross-generation strict stability). The non-strict glob is pinned to the exact filename so the `-strict` fixtures never bleed into it.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `validation`: three requirements change behavior.
  - **CLI verdict is authoritative when the CLI is available** — reword the verdict-parity scenario from "the captured real 1.6.0 CLI" to be anchor-generation-relative, and add a scenario declaring the durable invariant that captured CLI verdicts over the shared corpus are identical across every captured generation.
  - **Per-run strict validation** — the built-in strict fallback must fail on a WARNING only when it mirrors a real CLI strict-failing warning (the set is empty today); a fallback whose only issues are plugin-invented lint WARNINGs SHALL pass under strict, because the plugin must not be more restrictive than the CLI.
  - **Change validation** — correct the now-wrong cross-reference stating `change-artifact-missing` fails the verdict under strict; it never fails the verdict in either mode.

## Impact

- **`src/main` behavior change (one method):** `OpenSpecValidateAction.applyStrictFallbackVerdict` — a CLI-absent strict run no longer reds a project on plugin-invented lint WARNINGs the CLI would pass. No other production code changes.
- **Test + fixtures:** rewrite `ValidatorVerdictVersionStabilityTest`; add a strict block to `ValidatorVerdictParityTest`; update `OpenSpecValidateStrictTest` (its `spec-title-required`-flips assertion encodes the old, buggy semantics); add `fixtures/cli/1.7.0/validate-parity-corpus-strict.json`; document the durable capture recipe in the fixtures README.
- **No `com.intellij.*` API surface touched** — no IntelliJ 2024.2+ compatibility impact and nothing for the Plugin Verifier to catch. Unit/platform test tiers only.
- **JaCoCo coverage floor HOLD** — the fix broadens an existing predicate and adds no meaningful instrumented lines; new tests are assertions over existing paths.
- **Deferred (noted, not done):** adding a `spec-purpose-brief` WARNING to *match* a real CLI strict warning such as `PURPOSE_TOO_BRIEF`, and promoting `delta-spec-sections` to an ERROR to close a default-mode laxness gap — both separate enhancements, out of scope for removing the over-restriction. Also deferred: the plugin's `change-proposal-required` **ERROR**, which the CLI-absent fallback fails in both modes though the real CLI validates a proposal-less change `valid` (a larger never-more-restrictive gap in the "Change validation" requirement this change edits, tracked separately — see the linked tracker entry).
