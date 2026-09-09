## Why

OpenSpec CLI **1.12.0** is the current `latest` on npm. The plugin should declare the `1.12.x` line supported and lock its one new plugin-observable behavior with captured contract coverage, keeping the per-version support contract current. 1.12 is an **additive, safe-direction superset of 1.11** — its validation verdict logic is byte-identical to 1.11, and nothing a 1.12 user does today breaks — so this is a lean capture-and-declare with one positive-control lock, no `src/main` change.

## What Changes

- Declare `1.12.x` a supported generation (floor stays `1.3.0`, no ceiling); bump the plugin's target version `1.11.0` → `1.12.0`.
- Capture real 1.12.0 CLI output — the parity-corpus twins (`validate --all --json`, default and `--strict`), `version.txt`, and a **positive-control** for 1.12's one new diagnostic.
- Lock the one behavioral delta: 1.12 adds a **verdict-neutral INFO diagnostic** on `validate` — an "Archive would refuse this delta…" merge-conflict warning on change items (upstream's new `findArchiveBlockers`). It never affects the verdict, and the plugin's existing validate-output parser already surfaces `INFO` at the correct severity, so **no `src/main` change is required**. The cross-generation verdict-parity anchors stay default `1.8.0` / strict `1.6.0`.
- Record 1.12's other client-side additions as off-model or inert (built nothing): a `validate --report full|findings` selector (a new opt-in JSON projection the plugin never requests), a `codeassistant` AI-tool adapter, and per-surface IDE-restart hint wording.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `plugin-core`: extend "Supported CLI versions and capability preservation" — add `1.12.x` to the declared version list and a "The 1.12.x line is a supported generation" scenario (the verdict-neutral archive-blocker INFO surfaced at INFO severity with the verdict preserved; anchors unchanged; off-model/inert additions; version comparison orders `1.12.0` above `1.11.0`).

## Impact

- **Code:** no `src/main` change (safe-direction, additive; the existing parser already maps `INFO`). `openspecTargetVersion` 1.11.0 → 1.12.0 in `gradle.properties` (coupled to the corpus via `TargetVersionSingleSourceTest`).
- **Tests/fixtures:** new `src/test/resources/fixtures/cli/1.12.0/` (parity twins + version.txt + archive-blocker positive-control); version guards extended (`ValidatorVerdictVersionStabilityTest.FLOOR`, `CliVersionAtLeastTest`, `CliVersionTest.twoDigitMinor_*` with `1.12.0 > 1.11.0`).
- **Docs (vendor-neutral, public mirror):** `openspec-support.md` (1.12.x line + matrix heading), README, CHANGELOG, fixtures README.
- **Platforms/CLI:** no change to IntelliJ 2024.2+ compatibility; floor stays 1.3.0, no ceiling.
