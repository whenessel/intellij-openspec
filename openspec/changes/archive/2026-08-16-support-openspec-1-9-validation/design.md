## Context

See proposal.md — Why. The plugin's CLI-version support is fixture-based: each supported generation has real captured `--json` output under `src/test/resources/fixtures/cli/<gen>/`, and a durable `ValidatorVerdictVersionStabilityTest` auto-discovers every generation's parity corpus and enforces "never more restrictive than any captured generation." The only open question for 1.9 was which branch applies:

- **Branch A** (additive): 1.9's default verdicts match 1.8 → the stability guard picks up the new corpus with no anchor move and no fallback change.
- **Branch B** (relaxation): 1.9 relaxes a default verdict further than 1.8 → the default anchor must advance to 1.9.0, the parity oracle re-points, and the `BuiltInValidator` fallback needs a matching relaxation (the 1.7/1.8 pattern).

The branch was resolved empirically, per this project's rule *"run the corpus, don't diff the dist"*: the real 1.9.0 CLI was installed in isolation and run over the committed `1.6.0` parity corpus.

## Goals / Non-Goals

**Goals:**
- Declare `1.9.x` a supported generation with per-generation contract coverage against captured real 1.9.0 output.
- Preserve the never-more-restrictive-than-the-CLI invariant across the new generation.
- Zero production-code change (this is capture-and-declare).

**Non-Goals:**
- Adopting 1.9's new client behaviors into the plugin (the `no_openspec_root` parser hardening, `validate --archived`, the task-numbering WARNING).
- Any change to the `VersionSupport` config-format axis or the version floor/ceiling math.

## Decisions

- **D1 — Branch A; the default verdict-parity anchor stays `1.8.0`.** The captured 1.9.0 parity twins are byte-identical (modulo per-run `durationMs`) to the committed 1.8.0 twins in *both* default (12/13) and strict (9/13). 1.9 is a strict additive superset of 1.8, so no relaxation exists to anchor on. *Alternative rejected:* advancing the anchor to 1.9.0 — pointless work that would assert the same map under a new name.
- **D2 — No `validation` spec delta.** The validation behavior contract (rules, severities, fallback, anchor) is unchanged. Declaring 1.9 there would be a no-op requirement edit — the "don't invent a requirement to satisfy validation" anti-pattern. The declaration lives solely in `plugin-core`'s supported-versions requirement. *Alternative rejected:* a validation delta restating the unchanged anchor.
- **D3 — Add `1.9.0` to `ValidatorVerdictVersionStabilityTest.FLOOR`.** The guard's vacuity floor makes a generation's corpus *mandatory* to discovery; adding 1.9.0 means a forgotten or dropped future 1.9 capture fails the guard loudly instead of silently reducing coverage. This is the anti-vacuity ratchet that makes the new fixtures load-bearing rather than decorative.
- **D4 — Defer the `no_openspec_root` hardening.** 1.9's one behaviorally-material change (root-less `validate`/`list --json` now return a `no_openspec_root` error envelope + exit 1 instead of empty + exit 0) is not reachable through the plugin today: CLI invocation is gated on the on-disk `openspec/` root, `OpenSpecListAction` reads specs from the VFS, and `CliOutputParser` already guards on `root.has("items")` so the new shape reads as "no failures," not a crash. It is captured/described here and hardened separately, keeping this change zero-production-code.

## Risks / Trade-offs

- **The 1.9 fixtures look identical to 1.8's.** That is the *correct* Branch-A outcome, not redundancy: they are a committed forward tripwire. If a *future* 1.9.x patch (or the next generation captured against this corpus) ever diverges, the stability guard flags it — the value is the invariant, not novelty in the bytes.
- **The deferred `no_openspec_root` gap is latent.** Exposure is low (see D4), but a future non-VFS-gated call path could meet the new shape. Tracked as a separate hardening follow-up so it isn't lost.
