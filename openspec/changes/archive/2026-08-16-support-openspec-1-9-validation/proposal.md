## Why

OpenSpec CLI 1.9.0 shipped, and the plugin's supported set currently tops out at 1.8.x. Verified by running the real 1.9.0 CLI over the plugin's parity corpus (not by diffing the dist), **1.9 is a strict additive superset of 1.8**: the default (12/13 valid) and strict (9/13 valid) verdict maps are byte-identical to the captured 1.8.0 fixtures. Unlike the 1.7 and 1.8 cycles, 1.9's two validate changes make the CLI *stricter*, never laxer — a new task-numbering WARNING, and an "honest root resolution" error envelope when run outside a root — so the plugin's lenient CLI-absent built-in fallback cannot become more restrictive than 1.9. Adopting 1.9.x is therefore a **capture-and-declare**, not a parity fix. Tracked in the project tracker.

## What Changes

- **Capture a real 1.9.0 CLI fixture corpus** under `src/test/resources/fixtures/cli/1.9.0/`: the `validate --all --json` default and `--strict` parity twins (reusing the existing `1.6.0` corpus markdown as capture input) plus `version.txt`, sanitized of machine-specific paths and telemetry ids. The captures are identical (modulo per-run `durationMs`) to the `1.8.0` twins and are committed as a forward tripwire that the auto-discovering `ValidatorVerdictVersionStabilityTest` picks up with no code change.
- **Wire the durable version guards (tests only).** Add `1.9.0` to `ValidatorVerdictVersionStabilityTest.FLOOR` so the new corpus is a *mandatory* discovery that fails loudly if a future capture is dropped, and add `1.9.0` to the supported-versions assertion in `CliVersionAtLeastTest`. The default verdict-parity **anchor stays `1.8.0`** (1.9 is not laxer than 1.8) — no parity-guard rework and no re-anchor.
- **Declare 1.9.x a supported CLI generation** — extend the plugin-core supported-versions contract to include the `1.9.x` line (floor unchanged at `1.3.0`, no ceiling) and add a 1.9-generation scenario stating its additive semantics.
- **Refresh the user-facing docs** (vendor-neutral): `docs/openspec-support.md` (add a 1.9.x line), the `docs/feature-comparison-matrix.md` review stamp, `README`, and a `CHANGELOG` entry framed as additive 1.9 support (not weakened validation).
- **Footnote the deliberately-unbuilt 1.9 items.** The new `validate --archived` flag, the task-numbering WARNING, and the `no_openspec_root` error envelope are captured/described but not adopted into plugin behavior in this change — the first two are CLI-stricter-only (the fallback stays safe by being laxer), and the third is a separate, low-exposure parser-hardening follow-up (the plugin gates CLI invocation on the on-disk `openspec/` root, so it never runs a root-less validate today).

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `plugin-core`:
  - **Supported CLI versions and capability preservation** — the supported set gains the `1.9.x` line (floor `1.3.0` unchanged), with a new "The 1.9.x line is a supported generation" scenario stating that the plugin delivers the full 1.8.x capability set with no re-gating; the parity corpus is byte-identical to 1.8 so the default verdict-parity anchor stays `1.8.0`; the new `validate --archived` flag and task-numbering WARNING are CLI-stricter-only (the lenient fallback remains never-more-restrictive); the `no_openspec_root` error envelope is low-exposure because CLI invocation is gated on the on-disk `openspec/` root; and the regenerated skills / `opsx` commands are tolerated. It requires per-generation contract coverage against captured 1.9.0 output and a version-floor/at-least assertion including `1.9.0`.

_The `validation` capability is intentionally **not** modified: no rule, severity, fallback, or anchor changes — 1.9's default and strict verdicts on the corpus are identical to 1.8's, so the validation behavior contract is unchanged._

## Impact

- **`src/main`** — **none.** This is a pure capture-and-declare; no production code changes. (The `no_openspec_root` parser hardening is deliberately out of scope, as a separate follow-up.)
- **Tests + fixtures** — add `src/test/resources/fixtures/cli/1.9.0/` (parity default+strict twins + `version.txt`); add `1.9.0` to `ValidatorVerdictVersionStabilityTest.FLOOR`; add `1.9.0` to `CliVersionAtLeastTest`'s supported-versions assertion; optionally add `1.9.0` to `VersionSupportTest`'s "unknown/future config-format string routes to the single baseline" case (no new enum — the `VersionSupport` config-format axis is unchanged).
- **Docs** — `docs/openspec-support.md` (primary; 1.9.x line), `docs/feature-comparison-matrix.md` (review stamp), `README`, `CHANGELOG`. All public / vendor-neutral.
- **No IntelliJ Platform API surface touched** — no 2024.2+ compatibility impact and nothing for the Plugin Verifier; the CLI version floor and the `VersionSupport` config-format axis are unchanged.
- **Out of scope** — the `no_openspec_root` validate/list parser hardening; optional `validate --archived` and task-numbering fixture locks; and the target-version single-sourcing + stale uiSmoke CLI pin bump (all separate follow-ups).
