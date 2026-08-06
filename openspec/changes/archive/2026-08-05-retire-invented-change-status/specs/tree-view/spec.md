# tree-view (delta)

## MODIFIED Requirements

### Requirement: Changes tree display

The plugin SHALL display a tool-window tree showing the **Changes** section — changes with per-artifact status. Navigation to spec files, archived changes, and `config.yaml` is owned by the **Project View** (which already lists `openspec/**` and opens files into the editor), not a second file-navigation tree in the tool window. The Changes tree auto-refreshes on filesystem changes.

#### Scenario: Tree structure
- **WHEN** the tool window opens on an initialized project
- **THEN** it SHALL show the Changes section (changes with per-artifact status); spec, archived-change, and config files are browsed in the Project View, not a tool-window file tree

#### Scenario: Actionable hints
- **WHEN** no changes exist
- **THEN** a hint node SHALL appear under Changes that triggers Propose on double-click

#### Scenario: Change-node visual hierarchy
- **WHEN** a change node renders in the Changes tree
- **THEN** the change name SHALL be shown in the primary (default) color and the `X/Y` task-completion count as dimmed secondary text — so the count does not compete with the name. No change-status tag is shown: OpenSpec has no change-status concept (active vs archived is directory location), and the invented `[proposed]`/`[applied]` tag is retired.
- **AND** a complete count (`N/N`) SHALL NOT be colored as "done": the apply-ready state is conveyed by the node's status icon badge (driven by the artifact DAG), a signal distinct from the tasks.md task count
