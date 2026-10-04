## MODIFIED Requirements

### Requirement: Continue artifact generation

The plugin SHALL provide a Continue action that detects the next ready artifact in the selected change and generates it via the configured delivery method.

#### Scenario: Next artifact detection
- **WHEN** the user triggers Continue on a change with incomplete artifacts
- **THEN** the plugin SHALL query the artifact DAG status, find the first artifact with status "ready", and generate it

#### Scenario: All artifacts complete
- **WHEN** Continue is triggered and all artifacts are already complete
- **THEN** the plugin SHALL display a message indicating all artifacts are done and suggest running Apply or Archive

#### Scenario: No change selected
- **WHEN** Continue is triggered with no active change selected
- **THEN** the plugin SHALL prompt the user to select a change from the active changes list

#### Scenario: Shared artifact acceptance
- **WHEN** integrated Continue returns a final artifact result
- **THEN** it SHALL use common structured validation and concrete-file preview before applying files
