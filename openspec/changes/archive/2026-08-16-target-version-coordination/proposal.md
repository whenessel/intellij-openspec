## Why

The plugin's "top-supported OpenSpec CLI version" is written down in several places that must be kept in agreement by hand, and one of them has already drifted. When 1.9.x support landed, the fixture corpus, the version-floor guards, and the supported-versions declaration all advanced to `1.9.0`, but the UI-smoke workflow still installs `@fission-ai/openspec@1.6.0` — three generations behind. A hardcoded pin that nobody is forced to update is a standing invitation to re-drift every generation, so the maintainer's authoritative release-gate `uiSmoke` run has been exercising the plugin against a CLI three lines older than the one it claims to support.

Fix it **by construction**: make one value the single source of truth for the top-supported CLI version, have the workflow derive its install from that value, and add a build tripwire so bumping the value without capturing that version's fixtures fails the build. Tracked in the project tracker.

## What Changes

- **Introduce a single source of truth** for the top-supported CLI version: `openspecTargetVersion` in `gradle.properties` (initial value `1.9.0`, matching the currently-declared top of the supported set).
- **Derive the UI-smoke CLI install from it.** `.forgejo/workflows/ui-smoke.yaml` reads `openspecTargetVersion` out of `gradle.properties` and installs `@fission-ai/openspec@<that version>` instead of the hardcoded `@1.6.0`, so the smoke journeys always run against the version the plugin actually targets. This closes the stale-pin gap permanently.
- **Add a build tripwire wired into `check`.** `./gradlew build` fails if no captured fixture corpus (`src/test/resources/fixtures/cli/<openspecTargetVersion>/version.txt`, whose content matches the version) exists for the single-sourced version — so bumping the target without capturing that generation's fixtures cannot merge green. This is the load-bearing coupling: the version the workflow will install and the version we have contract fixtures for are forced to agree.
- **Document the model.** Add a "CLI version targeting & local development" section to `CONTRIBUTING.md`: the two-axis model (declared support is a **range** carried in-repo by captured fixtures, floor `1.3.0` / no ceiling; the installed CLI is **one** version per machine; the two are bridged by the fixtures, which is why `./gradlew build` is version-agnostic), the single-source variable, and the local-dev disciplines (capture in an isolated `XDG`/`HOME` sandbox; use `npx --yes @fission-ai/openspec@<v>` to drive an off-target generation instead of downgrading the global; `openspec update` is adopt-sync-only because it rewrites the tracked skills/commands to the *installed* CLI).

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `ci`:
  - **Single-sourced top-supported CLI version** (new requirement) — the top-supported OpenSpec CLI version SHALL be declared in exactly one place that both the build and the UI-smoke workflow derive from; `check` SHALL fail when a captured fixture corpus for that version is absent; and the UI-smoke workflow's CLI install SHALL name the single-sourced version rather than a separately-maintained literal, so the smoke suite cannot drift behind the declared support set.

## Impact

- **`src/main`** — **none.** No production code changes; this is build-tooling, CI, and docs only. No IntelliJ Platform API surface is touched (no Plugin Verifier or 2024.2+ compatibility impact), and the CLI version floor and `VersionSupport` config-format axis are unchanged.
- **Build** — `gradle.properties` gains `openspecTargetVersion`; `build.gradle.kts` adds a `check`-wired verification that a fixture corpus exists for that version.
- **CI workflow** — `.forgejo/workflows/ui-smoke.yaml` derives the CLI pin from `gradle.properties` (`@1.6.0` → the single-sourced `1.9.0`). Because the smoke workflow is manual-dispatch-only and the authoritative gate is the maintainer's local `caffeinate -dimsu ./gradlew uiSmoke` run, the journeys are re-confirmed green under `1.9.0` as part of this change (the journey scenarios are written "on a host CLI at 1.6+", which `1.9.0` satisfies; a body-less missing-`SHALL` requirement still errors on 1.9, so the validate-results journeys are unaffected).
- **Docs** — `CONTRIBUTING.md` gains the version-targeting section. Public / vendor-neutral.
- **Tests** — the fixture-existence tripwire is itself the enforcing check; a unit test asserts it fires when a corpus is missing and passes when present. The existing `ValidatorVerdictVersionStabilityTest.FLOOR` and `CliVersionAtLeastTest` version lists are **retained as-is** — a durable historical *floor* is a distinct concept from the single "current target" and must not collapse into it.
- **Out of scope** — the `no_openspec_root` validate/list parser hardening (the remaining optional 1.9 follow-up, Class C); no change to any validation rule, severity, fallback, or parity anchor.
