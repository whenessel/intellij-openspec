# Declare OpenSpec CLI 1.7.x a supported line

## Why

The functional, fixture, and tolerance work for CLI 1.7.x has merged (lenient change-metadata parse
with `skip_specs`, the `requires`-driven downstream reasoning, the parser-gap closures, and the
config-field recognition), each backed by captured real 1.7.0 output. What remains is the user-facing
and spec-level **declaration** that 1.7.x is supported. Today the `plugin-core` "Supported CLI versions"
requirement still enumerates only `1.3.x`–`1.6.x`, so the spec contradicts the shipped support. This
change makes the declaration truthful and consistent across every surface. It is sequenced so the
claim lands only after the behavior it asserts is real.

## What Changes

- **`plugin-core` spec (MODIFIED):** add `1.7.x` to the supported-version list and add a
  "The 1.7.x line is a supported generation" scenario mirroring the `1.6.x` one (additive semantics,
  no re-gating, per-generation contract coverage against captured 1.7.0 output). The `1.3.0` floor and
  the version-scoped self-retiring-capability wording are preserved verbatim.
- **Docs:** revise the canonical `openspec-support.md` spec-validator sentence to record the
  cross-version verdict-stability check (CLI verdicts byte-identical `1.6.0` → `1.7.0`) alongside the
  existing 1.6.0 parity test; add a concise `1.7.x` note to the README's per-version narrative. The
  `1.7.x` supported-line entry, per-line bullet, workflow matrix, and `docs/cli-versions/1.7.md`
  already landed with the fixture-corpus work.
- **CHANGELOG:** an `Added` entry — OpenSpec CLI 1.7.x support (additive; recognizes the new optional
  change-metadata and config fields; status shape tolerated), user-outcome-worded.
- **Test:** extend the supported-versions floor test to assert `1.6.0` and `1.7.0` clear the floor.

## Capabilities

- **plugin-core** (MODIFIED — "Supported CLI versions and capability preservation"): the supported set
  now includes `1.7.x`, with an explicit generation scenario.

In-product range strings need no change: they are floor-based (`1.3.0+`) or version-window-specific
(`1.4.x` coordination, `1.5.0+` store) and already include 1.7 with no upper cap — extended, never
narrowed. The config-format axis is untouched (no new enum value).

## Impact

- `openspec/specs/plugin-core/spec.md` (delta), `docs/openspec-support.md`, `README.md`,
  `CHANGELOG.md`, `CliVersionAtLeastTest`. Coverage floors re-measured (unchanged since the epic's
  test work already ratcheted them).
