# Design — declare 1.7.x supported

## The spec delta is the load-bearing part

The `plugin-core` "Supported CLI versions and capability preservation" requirement is the spec-level
support declaration; it enumerates the supported lines and carries a per-generation scenario for each
recent line. Declaring 1.7.x supported therefore means a **MODIFIED** delta: the whole requirement is
restated (both paragraphs + both existing scenarios verbatim — the no-scenario-drop rule) with `1.7.x`
added to the version list and a new "The 1.7.x line is a supported generation" scenario appended,
mirroring the `1.6.x` scenario. The 1.7 scenario asserts what the epic actually delivered: the full
`1.6.x` capability set (no re-gating — coordination unchanged), the additive 1.7 semantics (lenient
metadata parse incl. `skip_specs`, tolerated `operations`/`defaultStore`, id-keyed `requires` edges),
and per-generation contract coverage against captured 1.7.0 output. Validated clean with
`openspec validate --strict` before archive.

## What was already done (by earlier epic cards)

`docs/openspec-support.md`'s `1.7.x` supported-line entry + per-line bullet + the workflow-availability
matrix, and `docs/cli-versions/1.7.md` + its index row, all landed with the fixture-corpus work. This
change only revises the one stale spec-validator sentence and adds the README note — it does not
re-add what exists.

## Parity-sentence wording (dependency-aware)

`openspec-support.md` said the spec validator is "verified by a verdict-parity contract test against
captured 1.6.0 CLI output." The card asked to reflect a *generalized 1.3–1.7 parity guard* — but that
generalization is a separate, not-yet-merged card. So the sentence is revised to reflect **what exists
today**: the 1.6.0 parity test **plus** the cross-version stability check that asserts the CLI's own
verdicts are byte-identical `1.6.0` → `1.7.0` over the same corpus (so the 1.6-verified verdicts hold
on 1.7 by transitivity). When the version-agnostic guard lands, it will further generalize this
sentence — the claim here is accurate and non-anticipatory.

## In-product strings: extended, never narrowed — nothing to change

Every user-facing version string is a floor (`Schema management requires OpenSpec CLI v1.3.0+`,
`install OpenSpec CLI 1.3+`) or a version-window statement (`1.4.x` coordination window, `OpenSpec CLI
1.5.0+ is required for write actions`). None caps at 1.6, and there is no upper bound, so 1.7 is
already included. Auditing them for a 1.6-cap found none — the correct action is to leave them.

## Test

`CliVersionAtLeastTest.allSupportedVersions_meetFloor` gains `1.6.0` and `1.7.0` (asserting each
declared line clears the 1.3.0 floor); `2.0.0` stays as the no-upper-cap assertion. The behavioral
contract coverage the 1.7 scenario references already exists (the change-metadata, downstream,
parser-gap, and config-recognition contract tests from the earlier cards).

## Coverage

The epic's test work already ratcheted the floors (to 0.388/0.366/0.364); this change adds only a
value to an existing parameterized test, so aggregate coverage is unchanged — floors are re-measured
and left as-is.
