## Why

OpenSpec CLI 1.10.0 shipped, and the plugin's supported set currently tops out at 1.9.x. Verified by running the real 1.10.0 CLI over the plugin's parity corpus (not by diffing release notes), **1.10 is a strict additive superset of 1.9**: the entire validation engine source is byte-identical 1.9 → 1.10, and `validate` (default and `--strict`), `list`, `spec show`, `schemas`, and `status --json` produce byte-identical output and exit codes over the shared corpus. Its client-side additions (`init --language`, a Zed adapter, a first-run completion tip moved to stderr, and a runtime-only `completionTipSeen` global-config field) are off-model or inert to the plugin and change no contract the plugin parses. Adopting 1.10.x is therefore a **capture-and-declare**, not a parity fix. Tracked in the project tracker.

## What Changes

- **Capture a real 1.10.0 CLI fixture corpus** under `src/test/resources/fixtures/cli/1.10.0/`: the `validate --all --json` default and `--strict` parity twins (reusing the existing `1.6.0/parity-corpus` markdown as capture input) plus `version.txt`, sanitized of machine-specific paths and telemetry ids. The captures are identical (modulo per-run `durationMs`) to the committed `1.9.0`/`1.8.0` twins and are committed as a forward tripwire that the auto-discovering `ValidatorVerdictVersionStabilityTest` picks up with no code change.
- **Wire the durable version guards (tests only).** Add `1.10.0` to `ValidatorVerdictVersionStabilityTest.FLOOR` so the new corpus is a *mandatory* discovery that fails loudly if a future capture is dropped, and add `1.10.0` to the supported-versions assertion in `CliVersionAtLeastTest`. The default verdict-parity **anchor stays `1.8.0`** and the strict anchor stays `1.6.0` — 1.10 is not laxer than 1.8, so no parity-guard rework and no re-anchor.
- **Advance the single-sourced target version** `openspecTargetVersion` from `1.9.0` to `1.10.0` in `gradle.properties`. This is the top-supported-CLI single source that the ui-smoke workflow derives its install from and that `TargetVersionSingleSourceTest` couples to the fixture corpus — so the property bump and the 1.10.0 fixture capture land together (bumping the property without the corpus reddens `targetVersionHasACapturedFixtureCorpus`).
- **Verify the first two-digit minor sorts correctly.** 1.10 is the first minor version whose numeral is two digits, so a naive lexical string compare would misorder `1.10.0` as below `1.9.0`. Confirm the shared numeric comparator (`CliVersion.compare`) and every version-ordering surface place `1.10.0` above `1.9.0`, adding a targeted assertion if any ad-hoc lexical ordering is found.
- **Declare 1.10.x a supported CLI generation** — extend the plugin-core supported-versions contract to include the `1.10.x` line (floor unchanged at `1.3.0`, no ceiling) and add a 1.10-generation scenario stating its additive semantics.
- **Refresh the user-facing docs** (vendor-neutral): `docs/openspec-support.md` (add a 1.10.x line and extend the workflow-availability matrix heading/omit note), `README` (supported-lines sentence), and a `CHANGELOG` entry framed as additive 1.10 support (not weakened validation).
- **Footnote the deliberately-unbuilt 1.10 items.** `init --language`, the Zed adapter target, the completion-tip-to-stderr behavior, and the runtime-only `completionTipSeen` field are described but not adopted into plugin behavior — they are off-model AI-tool / CLI-UX surfaces (agent/tool-target enumeration was ruled off-model in the 1.8 cycle) or inert config the lenient parsers already tolerate.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `plugin-core`:
  - **Supported CLI versions and capability preservation** — the supported set gains the `1.10.x` line (floor `1.3.0` unchanged), with a new "The 1.10.x line is a supported generation" scenario stating that the plugin delivers the full 1.9.x capability set with no re-gating; the validation engine source is byte-identical to 1.9 so the default verdict-parity anchor stays `1.8.0` and the strict anchor stays `1.6.0`; the new `init --language` flag, Zed adapter target, completion-tip-to-stderr behavior, and runtime-only `completionTipSeen` field are off-model or inert and change no parsed contract; and the regenerated skills / `opsx` commands are tolerated. It requires per-generation contract coverage against captured 1.10.0 output and a version-floor/at-least assertion including `1.10.0`.

_The `validation` capability is intentionally **not** modified: no rule, severity, fallback, or anchor changes — 1.10's validation engine is byte-identical to 1.9's, and its default and strict verdicts on the corpus are identical, so the validation behavior contract is unchanged._

## Impact

- **`src/main`** — **none.** This is a pure capture-and-declare; no production code changes.
- **Build config** — `gradle.properties` `openspecTargetVersion` `1.9.0` → `1.10.0` (single source; `TargetVersionSingleSourceTest` and the ui-smoke workflow consume it).
- **Tests + fixtures** — add `src/test/resources/fixtures/cli/1.10.0/` (parity default+strict twins + `version.txt`) and a `1.10.0/` section to the fixtures `README.md`; add `1.10.0` to `ValidatorVerdictVersionStabilityTest.FLOOR`; add `1.10.0` to `CliVersionAtLeastTest`'s supported-versions assertion.
- **Docs** — `docs/openspec-support.md` (primary; 1.10.x line + matrix heading/note), `README`, `CHANGELOG`. All public / vendor-neutral. `docs/feature-comparison-matrix.md` and other `plugin vX.Y.Z` restatements are **not** touched here — they bump at release-cut (bumping a restatement ahead of `build.gradle.kts` drifts `DocumentationHygieneTest`).
- **No IntelliJ Platform API surface touched** — no 2024.2+ compatibility impact and nothing for the Plugin Verifier; the CLI version floor and the `VersionSupport` config-format axis (pinned at `1.2.0`) are unchanged.
- **Out of scope** — the `no_openspec_root` validate/list parser hardening (still low-exposure and deferred, tracked separately); adopting any of the off-model 1.10 client surfaces; and optional `validate --archived` / task-numbering fixture locks.
