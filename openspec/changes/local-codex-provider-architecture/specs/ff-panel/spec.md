## MODIFIED Requirements

### Requirement: Delivery-aware FF generation

After creating a change, the panel SHALL trigger generation using the currently selected delivery method from the tool selector.

#### Scenario: Direct API delivery
- **WHEN** the selected delivery method is backend execution
- **THEN** the panel SHALL automatically trigger GenerateAll for the new change with progress bar and pipeline chips

#### Scenario: Clipboard delivery
- **WHEN** the selected delivery method is Clipboard
- **THEN** the panel SHALL trigger generation of the first ready artifact via clipboard copy with tool-specific guidance

#### Scenario: Editor Tab delivery
- **WHEN** the selected delivery method is Editor Tab
- **THEN** the panel SHALL trigger generation of the first ready artifact by opening the prompt in an editor tab

#### Scenario: Automated mode capability guard
- **WHEN** backend execution is selected without ready safe artifact-generation capability
- **THEN** the panel SHALL explain unavailability rather than invoke a saved REST provider
