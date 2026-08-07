## MODIFIED Requirements

### Requirement: Config profile display in Settings

The plugin SHALL display the active OpenSpec config profile in a dedicated "Config Profile" section within the Settings panel, showing the profile name and its active workflow list. The section SHALL source these from `openspec config list --json` (the `profile` and `workflows` fields, resolved through the plugin's shared active-profile resolution — the same source the status-bar profile widget uses), NOT from `openspec config profile --json`, which OpenSpec does not provide (that option never existed and the current CLI rejects it). The section SHALL NOT display a profile description: OpenSpec models no profile-description concept in any command or schema, so there is nothing to source.

#### Scenario: Profile section with CLI available
- **WHEN** the user opens OpenSpec Settings and the CLI is detected
- **THEN** the Config Profile section SHALL display the active profile name and a list of active workflows resolved from `openspec config list --json`; when that output omits `workflows` (the CLI does not write it until a profile is explicitly set), the section SHALL show the CLI's core-default workflow set for the resolved profile

#### Scenario: Profile section without CLI
- **WHEN** the user opens OpenSpec Settings and the CLI is not detected
- **THEN** the Config Profile section SHALL display the locally-stored profile name with a label indicating that the CLI is required for profile details

#### Scenario: Profile section updates on selection change
- **WHEN** the user switches the active profile — applying a new selection in the workflow profile combo, or clicking "I'm done" after the "Customize workflows…" picker
- **THEN** the Config Profile section SHALL refresh to show the newly-resolved active profile and its workflows from `openspec config list --json` (the section reflects the applied CLI state, not an unapplied combo preview)

### Requirement: Settings panel surfaces use CLI runtime data

The Settings panel's workflow profile combo entries, active profile readout, and Config Profile section SHALL be sourced from the OpenSpec CLI's runtime output (`openspec config list --json`) rather than from detected CLI version or hardcoded preset assumptions. The plugin SHALL NOT depend on `openspec config profile --json`, which OpenSpec does not provide. Inline copy that describes profile semantics (e.g., the ContextHelpLabel) SHALL NOT enumerate specific workflow names; it SHALL link to the canonical workflow profiles documentation page for current workflow lists.

#### Scenario: Active profile readout from CLI
- **WHEN** the Settings panel renders the "Active profile:" label in the Config Profile section
- **THEN** the value SHALL come from the `profile` field of `openspec config list --json`, not from a plugin-side preset map

#### Scenario: ContextHelpLabel copy avoids workflow enumeration
- **WHEN** a user hovers the workflow profile `?` icon to read the ContextHelpLabel
- **THEN** the copy SHALL describe profiles in general terms (core ships an essential set; Customize workflows… picks additional workflows the CLI offers) without listing specific workflow names like `verify`, `ff`, `sync`, etc.

#### Scenario: Version detection scope
- **WHEN** the plugin needs to decide whether to expose a profile-related Settings panel surface
- **THEN** version detection SHALL be used only for gating availability of CLI features (e.g., a CLI subcommand existing in version X+), not for inferring preset names, workflow membership, or default profile behavior
