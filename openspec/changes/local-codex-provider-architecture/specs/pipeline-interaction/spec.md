## MODIFIED Requirements

### Requirement: Chip right-click context menu
The plugin SHALL display a context menu when the user right-clicks a pipeline chip. The menu items SHALL be appropriate to the chip's current state.

#### Scenario: DONE chip context menu
- **WHEN** the user right-clicks a DONE pipeline chip
- **THEN** the context menu SHALL show "Open file", "Regenerate", and "Copy prompt"

#### Scenario: READY chip context menu
- **WHEN** the user right-clicks a READY pipeline chip
- **THEN** the context menu SHALL show "Generate" and "Copy prompt"

#### Scenario: READY chip context menu with multiple ready
- **WHEN** the user right-clicks a READY pipeline chip and two or more artifacts are in READY state with ready artifact-generation capability
- **THEN** the context menu SHALL include "Generate All Remaining" as an additional item

#### Scenario: GENERATING chip context menu
- **WHEN** the user right-clicks a GENERATING pipeline chip
- **THEN** the context menu SHALL show "Cancel"

### Requirement: Status strip
The plugin SHALL display a single-line status strip below the icon bar showing compliance status, task progress (if tasks exist), and delivery mode. The status strip SHALL span the full width of the panel. During Generate All, the status strip SHALL show generation progress and elapsed time.

#### Scenario: Steady-state status
- **WHEN** a change is selected and not generating
- **THEN** the status strip SHALL display compliance status, task progress (if tasks.md exists), and current delivery mode in one line

#### Scenario: Generation progress
- **WHEN** Generate All is in progress
- **THEN** the status strip SHALL display "Generating N/M... Xs" with the current delivery mode

#### Scenario: Effective execution identity
- **WHEN** integrated execution is selected
- **THEN** the strip SHALL identify backend/model and make auth/billing details and context preview discoverable
