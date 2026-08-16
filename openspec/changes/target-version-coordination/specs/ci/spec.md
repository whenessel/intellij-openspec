## ADDED Requirements

### Requirement: Single-sourced top-supported CLI version

The top-supported OpenSpec CLI version SHALL be declared in exactly one place in the repository (the `openspecTargetVersion` property in `gradle.properties`), and every other build or CI consumer of that version SHALL derive it from that single source rather than restating it as an independently-maintained literal. The build SHALL enforce, wired into `check`, that a captured contract-fixture corpus exists for the single-sourced version, so that advancing the target without capturing that generation's fixtures fails the build. The UI-smoke workflow's OpenSpec CLI installation SHALL name the single-sourced version, so the smoke suite cannot drift behind the declared support set.

This does not replace the durable version **floor** (the set of historical generations whose captured corpora must be retained, enforced by the cross-generation parity guard): the floor is a lower bound on retained coverage and is a distinct concept from the single "current target". The two SHALL remain separate.

#### Scenario: Target version without a fixture corpus fails the build

- **WHEN** `openspecTargetVersion` names a version for which no captured fixture corpus (`src/test/resources/fixtures/cli/<version>/`, with a `version.txt` whose content is that version) exists
- **THEN** the `check`-wired verification SHALL fail, and because it is wired into `check`, `./gradlew build` (and CI) SHALL fail

#### Scenario: Target version with a fixture corpus passes

- **WHEN** `openspecTargetVersion` names a version whose captured fixture corpus is present and whose `version.txt` matches
- **THEN** the verification SHALL pass without touching any other build output

#### Scenario: UI-smoke workflow installs the single-sourced version

- **WHEN** the UI-smoke workflow installs the OpenSpec CLI before running the smoke journeys
- **THEN** it SHALL install the version named by `openspecTargetVersion` (read from `gradle.properties`), not a version literal maintained separately in the workflow file, so the smoke suite runs against the plugin's declared top-supported CLI

#### Scenario: The historical floor is not collapsed into the target

- **WHEN** the single-sourced target version advances to a newer generation
- **THEN** the durable version floor enforced by the cross-generation parity guard SHALL still require the previously-covered generations' corpora, so advancing the target SHALL NOT drop coverage of an older still-supported generation
