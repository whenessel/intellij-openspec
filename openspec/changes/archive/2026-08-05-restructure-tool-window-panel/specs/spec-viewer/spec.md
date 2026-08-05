# spec-viewer (delta)

## MODIFIED Requirements

### Requirement: Source-faithful rendering per node type

The tool-window preview pane SHALL render the source markdown of the selected **change** node — a change artifact (`proposal.md`, `design.md`, or `tasks.md`) or a change's delta spec (`changes/<change>/specs/<capability>/spec.md`) — and SHALL NOT reconstruct the document from the CLI's curated JSON. A **main spec** (`specs/<capability>/spec.md`) SHALL open in the **editor** (with the platform's Markdown preview) rather than the tool-window preview pane: the tool-window preview is for change surfaces, not plain-spec viewing. A delta spec SHALL be interpreted according to its own structure and never conflated with a main spec.

#### Scenario: Change artifact rendered
- **WHEN** a change's proposal, design, or tasks node is selected
- **THEN** the preview SHALL render that artifact file's markdown

#### Scenario: Delta spec rendered as a delta
- **WHEN** a change's delta-spec node is selected
- **THEN** the preview SHALL render the delta-spec markdown, treated as a change's proposed deltas and never as a main spec

#### Scenario: Main spec rendered
- **WHEN** a main spec node is selected
- **THEN** it SHALL render in the editor (with the platform Markdown preview), not in the tool-window preview pane
