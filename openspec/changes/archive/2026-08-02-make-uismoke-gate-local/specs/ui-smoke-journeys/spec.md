# ui-smoke-journeys (delta)

## MODIFIED Requirements

### Requirement: Smoke journeys never gate ordinary PRs

The UI smoke workflow SHALL run on **manual dispatch only** and SHALL NOT be a required check on ordinary pull requests, and SHALL NOT run automatically on a release tag push. Because the self-hosted CI runner cannot boot a headful IDE, the release gate is the maintainer's **local `caffeinate -dimsu ./gradlew uiSmoke` run** performed during release preparation: a release SHALL require those journeys green (real, rendered-frame passes — not environmental black-frame failures) before the release tag is pushed. The CI ui-smoke workflow SHALL NOT be relied upon as the release gate.

#### Scenario: PR unaffected
- **WHEN** an ordinary pull request runs CI
- **THEN** the UI smoke job SHALL NOT run and SHALL NOT block the merge

#### Scenario: Release gated by the local run
- **WHEN** a release is prepared
- **THEN** release preparation SHALL require the local headful `caffeinate -dimsu ./gradlew uiSmoke` journeys to pass for the release commit before the release tag is pushed

#### Scenario: No CI ui-smoke on a tag push
- **WHEN** a release tag is pushed
- **THEN** no CI ui-smoke job SHALL run automatically — the workflow is manual-dispatch-only, since the self-hosted runner cannot boot a headful IDE and would otherwise report a permanent false-red
