# Tasks: make-uismoke-gate-local

## Implementation Tasks

- [x] 1.1 Remove the `push: tags: ['v*']` trigger from `.forgejo/workflows/ui-smoke.yaml`; keep `workflow_dispatch`. The suite becomes on-demand only.
- [x] 1.2 Update the workflow's header comment: it is manual-dispatch-only because the self-hosted runner cannot boot a headful IDE; the release gate is the local `caffeinate -dimsu ./gradlew uiSmoke` run in release-prep.

## Testing Tasks

- [x] 2.1 `openspec validate make-uismoke-gate-local` clean; whole spec set validates.
- [ ] 2.2 No plugin/code test (CI-workflow + spec change). Workflow YAML validity is exercised on the next manual dispatch / by inspection; the retired tag trigger is confirmed by the next release tag NOT spawning a ui-smoke run.
- [ ] 2.3 `./gradlew build` still green (no source touched — sanity only).
