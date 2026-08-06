# plugin-core (delta)

## MODIFIED Requirements

### Requirement: Configuration parsing

The plugin SHALL parse `openspec/config.yaml` and each change's `.openspec.yaml`, **ignoring unrecognized or newer keys rather than treating them as errors** (matching upstream OpenSpec's strip contract). For a change's `.openspec.yaml`, genuinely malformed YAML SHALL surface a clear warning notification with the change name and parse error. The plugin SHALL support reading the active profile's workflow configuration when the CLI is available. The plugin SHALL persist a default schema preference in `OpenSpecSettings.State` and expose it via getter/setter methods.

#### Scenario: Valid config
- **WHEN** a valid `config.yaml` exists
- **THEN** all config values SHALL be accessible via ConfigService

#### Scenario: Malformed YAML
- **WHEN** a change's `.openspec.yaml` contains genuinely invalid YAML
- **THEN** the plugin SHALL show a warning notification with the change name and parse error
- **AND WHEN** `config.yaml` contains invalid YAML
- **THEN** the plugin SHALL degrade to empty configuration without blocking (the parse failure is logged, not surfaced as a notification)

#### Scenario: Profile config retrieval via CLI
- **WHEN** the CLI is detected and `openspec config profile --json` succeeds
- **THEN** the plugin SHALL make profile name, description, and active workflows available to the Settings panel

#### Scenario: Profile config retrieval without CLI
- **WHEN** the CLI is not detected
- **THEN** the plugin SHALL fall back to the locally-stored profile name in OpenSpecSettings without workflow details

#### Scenario: Default schema persistence
- **WHEN** the user selects a default schema in Settings
- **THEN** the value SHALL be persisted in `OpenSpecSettings.State.defaultSchema` and available via `OpenSpecSettings.getDefaultSchema()`

#### Scenario: Unknown or newer change-metadata keys parse without warning
- **WHEN** a change's `.openspec.yaml` carries upstream-valid keys the plugin does not model (`goal`, `affected_areas`, `initiative`, `skip_specs`) or any other unknown or newer key
- **THEN** the plugin SHALL parse it to non-null metadata (retaining `schema`, `status`, and `created`) with no warning notification
- **AND** the unmodeled keys SHALL be ignored, so no validation gate treats them as errors
