# Make the uiSmoke release gate the local run, not a false-red CI tag job

## Why

The `ui-smoke.yaml` workflow runs the headful journey suite on every `v*` tag push, but the self-hosted CI runner **cannot boot a headful IDE** (no virtual display) — so the tag run **fails on every release**, a permanent false-red. The workflow's own comment already acknowledges the fallback: *"If the runner cannot satisfy these, the documented fallback applies: run `gradle uiSmoke` locally as part of release-prep."*

The false-red is also **misleading**: it gates nothing real. The actual publish runs from GitHub's `release.yml`; the self-hosted CI ui-smoke tag run never blocked publishing. But it reads as a red release, and — with the v0.7.0 panel restructure about to rewrite every uiSmoke ready-gate — leaving a broken CI ui-smoke signal in place means that rewrite would land with no trustworthy gate.

The spec currently claims the release *pipeline* enforces green journeys on tag. In reality the enforcement is the **local `caffeinate -dimsu ./gradlew uiSmoke` run in release-prep** — which already exists and is the honest gate. This change makes reality and the spec agree.

## What Changes

- **Retire the tag trigger** on `ui-smoke.yaml` — keep `workflow_dispatch` only, so the suite runs on demand (e.g. if the runner ever gains a display) but never auto-false-reds a release.
- **Formalize the local caffeinate run as the authoritative release gate** in the `ui-smoke-journeys` spec: the self-hosted runner can't do headful, so a release requires the journeys green from the local `caffeinate -dimsu ./gradlew uiSmoke` run (release-prep), not a CI tag job.

## Capabilities

### Modified Capabilities
- **ui-smoke-journeys** — the release-gating requirement is corrected: the gate is the maintainer's local headful run (release-prep), and the CI ui-smoke workflow is manual-dispatch-only (the self-hosted runner cannot boot a headful IDE); a tag push no longer runs a CI ui-smoke job.

## Impact

- Affected: `.forgejo/workflows/ui-smoke.yaml` (drop the `push: tags` trigger), `openspec/specs/ui-smoke-journeys/spec.md` (release-gating requirement).
- No plugin/code change. Resolves the ui-smoke tag-job permanent false-red (tracker linkage in the change's gitignored sidecar).
- **Enabler for the v0.7.0 flagship** (the panel restructure): with the false-red retired and the local run formalized, the panel restructure's wholesale uiSmoke-journey rewrite lands against an honest gate instead of a permanently-red CI signal.
- release-prep already performs the local run (step 4b) and now also asserts both-remotes CI green (step 1c, added this cycle); this change makes the spec name that local run as *the* gate.
