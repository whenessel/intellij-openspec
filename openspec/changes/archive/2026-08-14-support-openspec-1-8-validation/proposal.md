## Why

OpenSpec CLI 1.8.0 **demoted the "requirement must contain SHALL/MUST" rule from ERROR to WARNING in default mode**: a requirement whose body has prose but no `SHALL`/`MUST` is now `valid` in a default `validate` run (it stays a WARNING; `--strict` flips only the verdict, not the level), while a requirement with *no body at all* remains an ERROR. The plugin's CLI-absent built-in validator still emits missing-SHALL as a blanket ERROR, so on a 1.8 target the fallback reds a spec the CLI reports clean — the plugin is **more restrictive than the client it wraps**, the exact invariant the validation-parity work exists to protect. Adopting 1.8.x is therefore a real parity fix, not a capture-and-declare. (Verified by running the real 1.8.0 CLI over the parity corpus, not by diffing the dist.) Tracked in the project tracker.

## What Changes

- **Demote the missing-SHALL/MUST rule to WARNING in default mode, conditional on the requirement having a body.** In the built-in validator's main-spec path, a requirement with body prose but no `SHALL`/`MUST` becomes a non-failing WARNING (matching 1.8 default); a requirement with **no body prose at all** stays an ERROR (1.8 still errors on that); the header-only-keyword targeted diagnostic and quick-fix are retained at the new severity.
- **Add the missing-SHALL warning to the CLI-mirroring strict-warning set** so a strict fallback run flips the verdict to failed when that warning is present — mirroring 1.8, where `--strict` counts the warning as a failure. This is a verdict change only; no issue is relabeled.
- **Re-anchor the cross-generation verdict-parity invariant.** The plugin's fallback now matches the **1.8** default map exactly (intentionally laxer than 1.6/1.7), so the "anchor generation" the plugin is proven against moves 1.6.0 → 1.8.0 for the default map. The strict map is unchanged (1.8 strict = the 1.6.0 map). The stability guard's default arm relaxes from *map-equality* to *subset-of-the-laxest-anchor* (no generation may be valid on an item the plugin rejects), keeping the strict-arm equality invariant and its anti-vacuity guards.
- **Capture a real 1.8.0 CLI fixture corpus** under `src/test/resources/fixtures/cli/1.8.0/` (default + strict parity twins reusing the existing 1.6.0 corpus markdown as input; plus current-generation contract twins), sanitized of machine-specific paths and telemetry ids. Everything is contract-tested against captured real output, never hand-authored shapes.
- **Declare 1.8.x a supported CLI generation** — extend the plugin-core supported-versions contract to include `1.8.x` (floor unchanged at `1.3.0`, no ceiling) and add a 1.8-generation scenario; add a version-floor/at-least test assertion and a captured `1.8.0/version.txt`.
- **Refresh the user-facing docs** (vendor-neutral): `docs/openspec-support.md` (its "semantics unchanged / byte-identical 1.6→1.7" parity claim goes false at 1.8 and is rewritten; a 1.8.x line/column is added), the `docs/feature-reference.md` fallback-severity example, the `docs/feature-comparison-matrix.md` review stamp, and a `CHANGELOG` entry framed as honoring "never stricter than the CLI" (pointing to `--strict` as the escape hatch), not as weakened validation.
- **Footnote that 1.8's agent-support additions are deliberately not built.** 1.8's "more agents" (new AI-tool targets, the skills + `opsx` commands split, Copilot cloud) is CLI-side file generation into other tools' config directories — off-model for a wrapper. No plugin feature; only a docs note that `openspec update` now also regenerates `.claude/commands/opsx/` (CLI-managed).

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `validation`:
  - **Spec format validation** — the missing-SHALL/MUST rule severity changes from a blanket ERROR to a body-conditional WARNING in default mode (ERROR retained when the requirement has no body); the "SHOULD-only requirement is flagged" and fenced-keyword scenarios move ERROR → WARNING, with a new no-body-still-ERROR scenario.
  - **Per-run strict validation** — the CLI-mirroring strict-warning set is no longer empty; it now contains the missing-SHALL warning, so a strict fallback run fails on it (matching the CLI).
  - **CLI verdict is authoritative when the CLI is available** — the cross-generation stability invariant relaxes from default-map *equality* to *subset-of-the-laxest-anchor* with the anchor re-set to 1.8.0; the strict-map equality invariant and anti-vacuity guards are retained.
- `plugin-core`:
  - **Supported CLI versions and capability preservation** — the supported set gains the `1.8.x` line (floor `1.3.0` unchanged), with a new "The 1.8.x line is a supported generation" scenario stating the 1.8 validator semantics (default missing-SHALL is a non-failing WARNING; strict re-promotes) and that per-generation contract coverage against captured 1.8.0 output exists.

## Impact

- **`src/main`** — `BuiltInValidator` only: the missing-SHALL severity decision (body-conditional WARNING/ERROR) and the strict-warning-set membership. No other production site emits the rule; the editor inspection (`SpecFormatInspection`) already uses WARNING, so no on-the-fly inspection change.
- **Tests + fixtures** — capture `src/test/resources/fixtures/cli/1.8.0/` (parity twins + current-generation contract twins + `version.txt`); re-anchor `ValidatorVerdictParityTest` (default oracle 1.6→1.8, strict oracle unchanged); rework `ValidatorVerdictVersionStabilityTest`'s default arm to subset-of-laxest with anti-vacuity pins; split `BuiltInValidatorTest` into default-WARNING / strict-flip arms; fix the vacuous inline `validateSpec` mirror in `BuiltInValidatorRulesTest` so the demoted-rule assertions drive the real validator; add the 1.8.0 assertion to the version-floor test.
- **Docs** — `docs/openspec-support.md` (primary; parity claim rewrite + 1.8.x line), `docs/feature-reference.md`, `docs/feature-comparison-matrix.md`, `README`/`CHANGELOG`. All public/vendor-neutral.
- **No IntelliJ Platform API surface touched** — no 2024.2+ compatibility impact, nothing for the Plugin Verifier; the CLI version floor and `VersionSupport` config-format axis are unchanged.
- **Out of scope** — the new main-spec duplicate-requirement ERROR fallback parity (separate change); the scenario-loss authoring check (documented as a fallback limitation, not reimplemented); any agent-support feature.
