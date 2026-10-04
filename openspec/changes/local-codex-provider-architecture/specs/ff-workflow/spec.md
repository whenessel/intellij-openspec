## MODIFIED Requirements

### Requirement: Fast-Forward change creation

The plugin SHALL provide a Fast-Forward action that creates a new change from a description. In automated mode it SHALL generate all required artifacts via the selected ready execution backend; in explicit manual mode it SHALL hand off only the first ready artifact prompt.

#### Scenario: FF from description
- **WHEN** the user triggers automated Fast-Forward and enters a description
- **THEN** the plugin SHALL derive a kebab-case name, create the change via `openspec new change`, and begin generating artifacts

#### Scenario: FF artifact generation
- **WHEN** the change is created in automated FF mode
- **THEN** the plugin SHALL walk the artifact DAG in dependency order, generating each artifact via the selected ready execution backend with progress feedback in the dialog

#### Scenario: FF completion
- **WHEN** automated FF finishes with the transitive required artifact closure complete or explicitly skipped
- **THEN** the plugin SHALL refresh the tool window, select the new change, and display a summary with a prompt to run Apply


#### Scenario: Manual FF handoff
- **WHEN** the user submits FF with clipboard/editor selected
- **THEN** the plugin SHALL create the change and deliver only the first ready artifact prompt with manual completion guidance
