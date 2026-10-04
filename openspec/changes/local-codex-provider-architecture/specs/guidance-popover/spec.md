## MODIFIED Requirements

### Requirement: Guidance popover after generation
The plugin SHALL display a lightweight popover near the generated artifact's chip after generation completes. The popover SHALL show delivery confirmation, save-path hint, and a "Copy again" link. The popover SHALL NOT affect the panel's layout or height.

#### Scenario: Popover appears after generation
- **WHEN** an artifact generation completes (clipboard, editor tab, or backend execution)
- **THEN** a popover SHALL appear near the artifact's chip showing delivery confirmation and save-path hint

#### Scenario: Popover auto-dismisses
- **WHEN** the guidance popover is displayed
- **THEN** it SHALL auto-dismiss after 8 seconds or when the user clicks outside the popover

#### Scenario: Copy again link
- **WHEN** the popover is displayed after clipboard delivery
- **THEN** it SHALL include a "Copy again" link that re-copies the prompt to clipboard

#### Scenario: No layout shift
- **WHEN** the popover appears or dismisses
- **THEN** the WorkflowActionPanel SHALL NOT change height or reflow its layout

#### Scenario: Accepted multi-file output
- **WHEN** integrated generation applies a validated accepted batch
- **THEN** feedback SHALL identify the actual backend and concrete saved files, and SHALL NOT claim a glob or unaccepted partial output was saved

### Requirement: Guidance popover content
The popover content SHALL vary by delivery mode to provide contextually relevant guidance.

#### Scenario: Clipboard delivery guidance
- **WHEN** the delivery mode is clipboard
- **THEN** the popover SHALL show "Copied to clipboard" with the save-path hint and tool-specific paste guidance

#### Scenario: Direct API delivery guidance
- **WHEN** the delivery mode is integrated backend execution
- **THEN** the popover SHALL show "Generated via [backend]" with the concrete accepted file paths, or "Ready for review" when no files have yet been accepted

#### Scenario: Editor tab delivery guidance
- **WHEN** the delivery mode is editor tab
- **THEN** the popover SHALL show "Opened in editor" with the save-path hint
