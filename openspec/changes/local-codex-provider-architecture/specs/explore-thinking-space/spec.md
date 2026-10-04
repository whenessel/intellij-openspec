## MODIFIED Requirements

### Requirement: Direct API routes through panel input

Explore actions SHALL route through the shared choice and use the existing Explore input/result surface for integrated backends. Explicit manual mode SHALL remain prompt handoff.

#### Scenario: Direct API delivery activates panel
- **WHEN** the Explore menu is invoked with a ready backend selected
- **THEN** the existing Explore tab SHALL activate its inline topic input

#### Scenario: Non-Direct-API delivery shows modal dialog
- **WHEN** the Explore menu is invoked with clipboard/editor selected
- **THEN** the plugin SHALL collect the topic and deliver the reviewed prompt without inference

### Requirement: Loading and error states

The Explore panel SHALL display appropriate visual feedback during API calls and on errors.

#### Scenario: Loading state display
- **WHEN** an explore request is sent to the AI provider
- **THEN** the response area SHALL display a loading message in muted foreground color and the topic header SHALL show the topic being explored

#### Scenario: Error state display
- **WHEN** the AI provider returns an error
- **THEN** the response area SHALL display the error message in error styling (red foreground) and the input area SHALL be re-enabled

#### Scenario: Error state allows retry
- **WHEN** an error is displayed and the user submits a new topic
- **THEN** the panel SHALL clear the error and initiate a new explore request

#### Scenario: Cancel or timeout recovery
- **WHEN** Explore is interrupted or times out
- **THEN** input controls SHALL recover and late responses SHALL NOT modify a new conversation
