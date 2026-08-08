## Context

See `proposal.md` — Why. The built-in validator (used only when the OpenSpec CLI is absent) emits the missing-`SHALL`/`MUST` rule as a blanket ERROR. OpenSpec 1.8.0 changed the CLI's own default verdict for that rule, so the fallback is now stricter than the client it wraps. The plugin has a single built-in validation dialect: the fallback runs precisely because no CLI is available, so it cannot branch its severity on a "detected CLI version" — there is none. The relevant requirements and their new behavior are fully specified in the `validation` and `plugin-core` deltas; this document covers the how/why, not the requirement text.

Constraint that shapes everything below: **the fallback must never be more restrictive than any supported CLI generation**, evidenced against captured real CLI output and kept durable across generations by the version-stability guard.

## Goals / Non-Goals

**Goals:**
- Make the CLI-absent fallback's default verdict match OpenSpec 1.8's demotion of the missing-keyword rule, with `--strict` re-promoting it.
- Keep the cross-generation parity guard honest as generations begin to *relax* (not just hold) default verdicts.
- Prove every behavior against captured real 1.8.0 CLI output, not hand-authored shapes.

**Non-Goals:**
- Version-branching the fallback's severity by CLI line (the fallback has no CLI to detect — see Decisions).
- Mirroring 1.8's new duplicate-requirement or scenario-loss ERRORs in the fallback (separate change / documented limitation).
- Any agent-support feature (off-model).

## Decisions

### D1 — Missing-keyword severity is conditional on the requirement having a body
A requirement whose body has prose but no `SHALL`/`MUST` whole word becomes a **WARNING** (`spec-rfc-keywords`, or `spec-rfc-keyword-in-header` for the header-only variant); a requirement with **no body prose at all** stays an **ERROR**. This mirrors the 1.8 CLI exactly: its default `validate` reports the body-carrying case `valid` (warning) but still errors on a body-less requirement. Body-presence is determined the same way the keyword check already scans the body (fenced code blocks masked), so "has a keyword-eligible body" and "has body prose" share one notion of the requirement body.

- *Alternative — blanket WARNING:* rejected. It would be **laxer** than 1.8 on the body-less case (1.8 errors there), i.e. the fallback would pass a spec the CLI fails — a different-direction parity break.
- *Alternative — keep blanket ERROR:* rejected. It is the current defect: more restrictive than 1.8.

### D2 — One dialect, pick the laxer severity across the supported range
The built-in validator is pinned to a single dialect and cannot simultaneously emit ERROR for 1.3–1.7 and WARNING for 1.8. "Never more restrictive than the CLI" is directional: being **laxer** than 1.3–1.7 (which errored) is the safe direction; being **stricter** than 1.8 (which warns) is the forbidden one. So the single dialect adopts the laxer 1.8 WARNING. This is a deliberate, acknowledged relaxation of a spec-quality check for users on a pre-1.8 CLI who have it *uninstalled* (only then does the fallback run).

- *Alternative — branch severity on detected CLI version:* rejected. The fallback executes only when the CLI is absent, so there is no version to branch on; a single dialect is the whole premise of the fallback.
- *Mitigations:* `--strict` re-promotes the warning to a failing verdict; the on-the-fly editor inspection already surfaces the missing keyword at WARNING/WEAK_WARNING regardless; the docs frame this as honoring the parity north-star, not weakening standards.

### D3 — Parity guard: subset-of-laxest (Option A) over per-generation maps (Option B)
The default arm of the version-stability guard relaxes from *map-equality vs the 1.6 anchor* to *subset-of-the-laxest-anchor* (anchor `1.8.0`): no captured generation may report an item `valid` that the plugin (≡ 1.8 default) rejects. The strict arm keeps exact map-equality (every generation's strict map = the 1.6 map). Anti-vacuity pins are retained and extended: ≥2 generations incl. the anchor, identical item-id key sets across corpora, and a pin that the 1.8 default valid-set equals the 1.6 default valid-set **plus exactly** the requirements the demotion newly validates.

- *Why over Option B (a hand-declared per-generation `id→valid` map):* Option A preserves the guard's zero-edit durability for an additive/relaxing generation and directly encodes the real safety property ("never more restrictive than any captured generation"). Option B gives full per-generation fidelity but forces a hand-encoded expected map on every new generation (which must not be read from the fixture, or the test is vacuous) — more maintenance for fidelity the id-set + strict-equality guards already largely cover.
- *Trade-off:* Option A does not independently re-verify each *older* generation's exact default map (only ⊆ anchor). Accepted: the strict-arm equality + id-set/size pins cover fixture integrity, and a future generation that relaxes *further* than 1.8 surfaces first as a `ValidatorVerdictParityTest` failure (plugin vs the newer oracle), whose fix is a one-line anchor bump alongside a D1-style demotion — the human decision lands exactly where it belongs.

### D4 — Re-anchor `ValidatorVerdictParityTest` default oracle 1.6 → 1.8
The plugin's fallback now matches the 1.8 default map exactly (intentionally laxer than 1.6/1.7), so the default oracle it is proven against is the captured `1.8.0` default fixture. The strict oracle stays the `1.6.0` map (which `--strict` reproduces on every generation). This test drives `applyStrictFallbackVerdict`, so its strict arm also verifies D1's strict re-promotion.

### D5 — Capture the corpus from the real 1.8.0 CLI, reusing the 1.6 corpus markdown as input
Fixtures under `fixtures/cli/1.8.0/` are produced by running the real 1.8.0 CLI in an isolated XDG environment over the *existing* 1.6.0 corpus markdown (authored once, never re-authored), then sanitizing machine paths and telemetry ids. Default + strict parity twins, plus current-generation contract twins and `version.txt`. Contract-tested against captured output — never a hand-written shape.

### D6 — Drive the real validator from `BuiltInValidatorRulesTest`
The demoted-rule assertions currently exercise an inline `validateSpec` mirror in the test, so they test a copy, not production. As part of this change the demoted-rule assertions call the real `BuiltInValidator` so the demotion is actually verified against shipping code.

## Risks / Trade-offs

- **Relaxing a core quality check for pre-1.8 (CLI-uninstalled) users** → mitigated by `--strict` re-promotion, the unchanged editor inspection, and parity-framed docs. It is the correct direction under the never-stricter-than-the-CLI invariant.
- **Subset-of-laxest could pass vacuously** if the 1.8-vs-1.6 divergence silently collapsed → the anti-vacuity pin asserts the exact three-item divergence, identical id key sets, and ≥2 generations, so a collapse fails loudly.
- **"Body prose" edge cases** (whitespace-only body, body that is only a fenced block) → resolved by matching the CLI's own body notion and locking both the empty-body-ERROR and body-present-WARNING cases against captured 1.8 fixtures.
- **A future generation relaxes further than 1.8** → surfaces as a parity-test failure prompting an anchor bump; by design, not a silent pass.
- **Atomicity** → capture + demotion + re-anchor + guard rework must land together; committing the 1.8 corpus without the guard rework reds the build. Keep them in one change/commit.

## Migration Plan

No runtime or data migration — the change is validator logic + tests + docs, with no IntelliJ Platform API, `plugin.xml`, threading, CLI-floor, or `VersionSupport` config-axis impact. Rollback is a plain revert. The prior persistent-strict migration notice is unaffected.
