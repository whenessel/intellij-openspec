## ADDED Requirements

### Requirement: Scheduled-for-removal API usage fails verification

The Plugin Verifier gate SHALL be configured so that usage of IntelliJ Platform API **scheduled for removal** (`@Deprecated(forRemoval = true)`) is a build failure, in addition to the binary-compatibility failures the verifier already fails on by default. This makes a call to a method the platform has announced it will delete unable to merge, since such a call becomes a runtime `NoSuchMethodError`-class incompatibility once the platform actually removes the method — and the plugin's compatibility range is open-ended (no upper bound), so it must remain callable on every future build.

Plain-deprecated API usage (`@Deprecated` without `forRemoval`) SHALL remain permitted (report-only), because a plain deprecation will not be removed without a further for-removal cycle, and a usage whose only non-deprecated replacement is unavailable at the plugin's compatibility floor cannot be fixed without raising the floor. Such usages SHALL be tracked for migration when the floor advances rather than blocked.

#### Scenario: Scheduled-for-removal usage fails the build

- **WHEN** the plugin sources call an IntelliJ Platform API annotated `@Deprecated(forRemoval = true)` against any IDE in the verified range
- **THEN** `verifyPlugin` SHALL report it at a failing level, failing the build, so the usage cannot merge

#### Scenario: Plain-deprecated usage does not fail the build

- **WHEN** the plugin sources call an IntelliJ Platform API that is `@Deprecated` but not `forRemoval`, and no non-deprecated replacement exists at the plugin's compatibility floor
- **THEN** `verifyPlugin` SHALL report it without failing the build, and the usage SHALL be tracked for migration when the floor advances

#### Scenario: The default binary-compatibility failures are preserved

- **WHEN** the scheduled-for-removal gate is added to the verification step
- **THEN** the severity levels that fail the verifier by default (binary incompatibilities / compatibility problems, internal-API and override-only usage) SHALL still fail, so adding the scheduled-for-removal gate does not weaken existing verification coverage
