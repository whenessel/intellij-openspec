## Context

See proposal.md — Why. The plugin's CLI-version support is fixture-based: each supported generation has real captured `--json` output under `src/test/resources/fixtures/cli/<gen>/`, and a durable `ValidatorVerdictVersionStabilityTest` auto-discovers every generation's parity corpus and enforces "never more restrictive than any captured generation." The top-supported CLI version is single-sourced as `openspecTargetVersion` in `gradle.properties`, consumed by the ui-smoke workflow's CLI install and coupled to the fixture corpus by `TargetVersionSingleSourceTest`. The only open question for 1.10 was which branch applies:

- **Branch A** (additive): 1.10's verdicts match 1.9/1.8 → the stability guard picks up the new corpus with no anchor move and no fallback change.
- **Branch B** (relaxation): 1.10 relaxes a default verdict further than 1.8 → the default anchor must advance, the parity oracle re-points, and the `BuiltInValidator` fallback needs a matching relaxation (the 1.7/1.8 pattern).

The branch was resolved empirically, per this project's rule *"run the corpus, don't diff the release notes"*: the real 1.10.0 CLI was installed in isolation and run over the committed `1.6.0` parity corpus, and the 1.10 validation-engine source was diffed against 1.9.

## Goals / Non-Goals

**Goals:**
- Declare `1.10.x` a supported generation with per-generation contract coverage against captured real 1.10.0 output.
- Advance the single-sourced `openspecTargetVersion` to `1.10.0` so the ui-smoke gate and the fixture-coupling guard track the new top.
- Preserve the never-more-restrictive-than-the-CLI invariant across the new generation.
- Confirm the first two-digit minor version orders correctly everywhere version strings are compared.
- Zero production-code change (this is capture-and-declare).

**Non-Goals:**
- Adopting 1.10's new client surfaces into the plugin (`init --language`, the Zed adapter target, completion-tip-to-stderr, `completionTipSeen`).
- The `no_openspec_root` parser hardening (still deferred from 1.9).
- Any change to the `VersionSupport` config-format axis (pinned `1.2.0`) or the version floor/ceiling math.

## Decisions

- **D1 — Branch A; anchors stay `1.8.0` default / `1.6.0` strict.** The 1.10 validation engine source is byte-identical to 1.9, and the captured 1.10.0 parity twins are byte-identical (modulo per-run `durationMs`) to the committed 1.9.0/1.8.0 twins in *both* default (12/13) and strict (9/13). 1.10 is a strict additive superset — no relaxation exists to anchor on. *Alternative rejected:* advancing the anchor to 1.10.0 — pointless work asserting the same map under a new name.
- **D2 — No `validation` spec delta.** The validation behavior contract (rules, severities, fallback, anchor) is unchanged. Declaring 1.10 there would be a no-op requirement edit — the "don't invent a requirement to satisfy validation" anti-pattern. The declaration lives solely in `plugin-core`'s supported-versions requirement. *Alternative rejected:* a validation delta restating the unchanged anchor.
- **D3 — Add `1.10.0` to `ValidatorVerdictVersionStabilityTest.FLOOR`.** The guard's vacuity floor makes a generation's corpus *mandatory* to discovery; adding 1.10.0 means a forgotten or dropped future 1.10 capture fails the guard loudly instead of silently reducing coverage. This is the anti-vacuity ratchet that makes the new fixtures load-bearing rather than decorative.
- **D4 — The `openspecTargetVersion` bump is in-scope and atomically coupled to the fixture capture.** Unlike the 1.9 cycle (which deferred single-sourcing to a companion change), the mechanism now exists. `TargetVersionSingleSourceTest.targetVersionHasACapturedFixtureCorpus()` fails if the property names a version with no committed corpus, so the property bump to `1.10.0` and the `fixtures/cli/1.10.0/` capture must land together. *Alternative rejected:* bumping the property in a later change — it would ship a red build in the interim.
- **D5 — Explicitly verify two-digit-minor ordering; do not assume.** 1.10 is the first minor whose numeral is two digits. The shared comparator `CliVersion.compare` splits on `.` and compares each segment numerically with `Integer.compare`, so `1.10.0 > 1.9.0` holds — but this is the first time that path matters in practice. The change adds `1.10.0` to `CliVersionAtLeastTest`'s supported-versions cases (which clears the `1.3.0` floor and exercises the two-digit segment) and sweeps for any ad-hoc lexical version ordering elsewhere, adding a targeted assertion if one is found. The stability guard is unaffected: it looks up anchors by explicit key and treats `FLOOR` as set-membership, so its `TreeMap` string-sort of generation dirs is immaterial to correctness.
- **D6 — Defer the off-model 1.10 client surfaces.** `init --language`, the Zed adapter (`--tools zed`), and the completion-tip-to-stderr change are CLI-UX / AI-tool-target surfaces with no spec/change/coordination state the plugin reads — the same off-model class as the 1.8 agent-support ruling. `completionTipSeen` is a runtime-managed global-config field the lenient config reader already tolerates. All are footnoted, not built.

## Risks / Trade-offs

- **The 1.10 fixtures look identical to 1.9's/1.8's.** That is the *correct* Branch-A outcome, not redundancy: they are a committed forward tripwire. If a *future* 1.10.x patch (or the next generation captured against this corpus) ever diverges, the stability guard flags it — the value is the invariant, not novelty in the bytes.
- **Two-digit-minor ordering is a real edge, verified not assumed.** A lexical compare would rank `1.10.0` below `1.9.0`; the numeric comparator is correct, and the sweep + the `CliVersionAtLeastTest` case make that a tested property rather than a lucky default. Any surface that does string-sort version identifiers would surface here.
- **The deferred `no_openspec_root` gap is still latent.** Exposure is low and unchanged by 1.10 (byte-identical on that shape); it remains a separate hardening follow-up so it isn't lost.
