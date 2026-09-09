## Context

See proposal.md — Why. This mirrors the 1.9/1.10/1.11 support changes: verify the new CLI generation against the plugin's contracts, capture real output, and declare support. The evaluation was done against the real 1.12.0 binary (isolated `HOME`/`XDG_*`) plus a source diff of the published tarball's `dist/core/validation/`.

Verified facts this rests on:
- The validation **verdict logic is byte-identical** 1.11→1.12 (`createReport` unchanged: default `valid = errors===0`, strict also requires `warnings===0`; `INFO` is counted but never feeds either verdict). Over the shared parity corpus, default 12/13 and strict 9/13 — identical totals to the 1.11 fixtures.
- 1.12's only validation-engine change is a new `findArchiveBlockers` producing a **verdict-neutral `INFO`** on a change whose delta is a MODIFIED/RENAMED operation against a main spec that does not yet exist ("Archive would refuse this delta…"). Over the shared corpus the normalized diff vs the 1.11 twins is exactly **one added `INFO`** — on `nameless-change`, in both the default and strict twin (`info-change`'s ADDED-only delta trips no blocker). Not a `durationMs`-only copy.
- The plugin's existing `CliOutputParser.parseJsonOutput` already maps `"INFO"` to the INFO severity and derives the verdict from the CLI-authoritative `valid` field. So the new diagnostic flows through **without a plugin change**.
- Config/schema surfaces the plugin parses are byte-identical 1.11→1.12; version comparison is numeric and already orders `1.12.0 > 1.11.0`.

## Goals / Non-Goals

**Goals:**
- Declare `1.12.x` supported and lock its one new plugin-observable behavior (the archive-blocker `INFO`) with a positive-control fixture, so a future change can't silently start mishandling it.
- Keep the version guards and target-version single-source coherent with the committed corpus.

**Non-Goals:**
- Any `src/main` change — none is warranted (safe-direction; the parser already handles `INFO`).
- Adopting 1.12's off-model/inert additions: the `validate --report full|findings` projection, the `codeassistant` adapter, IDE-restart wording. (The `--report findings` JSON contract is noted for the deferred write/read-surface `--help` sweep, not built here.)
- Any change to the validation verdict anchors — they stay default `1.8.0` / strict `1.6.0`.

## Decisions

**1. Capture-and-declare, no `src/main` change.** The verdict math is byte-identical and the parser already surfaces `INFO`. Support is therefore fixtures + guards + docs, exactly like 1.10/1.11. *Alternative — add explicit INFO handling:* rejected; the parser already does it correctly (guru-verified), so new code would be redundant.

**2. Positive-control the archive-blocker `INFO` via a constructed two-cap capture.** On the parity corpus the new `INFO` rides only `valid:true` items — and `parseJsonOutput` extracts issues only from `valid:false` items, so the corpus alone would not exercise the INFO through the parser. The lock therefore uses a deliberately constructed `valid:false` change carrying **both** an `ERROR` and the archive-blocker `INFO` on **different** delta paths (the two dedupe when they share a path). This is the 1.12 analog of the 1.11 `PURPOSE_IS_PLACEHOLDER` lock.
- *Test shape:* two-arm, mirroring `ValidatePurposePlaceholderContractV1_11`. `parseJsonOutput` overwrites the issue `path` with `type/id`, so assert the surfaced `INFO` severity and the preserved `valid:false` verdict **through the parser**, and assert the raw `INFO` level/message/path off the committed capture.

**3. Couple the target-version bump to the corpus.** `openspecTargetVersion` 1.11.0 → 1.12.0 lands in the same change as the `1.12.0/` fixtures, enforced by `TargetVersionSingleSourceTest`; `version.txt` must equal it.

## Risks / Trade-offs

- **A vacuous positive-control** (a fixture where the INFO rides a `valid:true` item, so `parseJsonOutput` drops it) → would pass without proving anything. Mitigation: the fixture MUST be the two-cap `valid:false` shape; the test asserts the INFO actually surfaces through the parser. Verified reproducible by the evaluation.
- **Parity twins mistaken for a `durationMs`-only re-capture** → the 1.12 twins legitimately differ from 1.11 by one added `INFO` (the archive-blocker on `nameless-change`, both twins); the fixtures README must say so, or a future re-capture audit could "correct" a real difference.
- **Under-declaring** (treating 1.12 as a pure byte-identical passthrough like 1.10) → would miss the INFO lock. Mitigation: this change is scoped as 1.11-weight (one behavioral delta to lock), not 1.10-weight.

## Migration Plan

Additive — no migration. Rollback is reverting the fixtures, guard edits, target-version bump, and docs.
