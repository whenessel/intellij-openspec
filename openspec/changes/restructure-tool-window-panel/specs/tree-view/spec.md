# tree-view (delta)

## MODIFIED Requirements

### Requirement: Tree view display

The plugin SHALL display a tool-window tree showing the **Changes** section — changes with per-artifact status. Navigation to spec files, archived changes, and `config.yaml` is owned by the **Project View** (which already lists `openspec/**` and opens files into the editor), not a second file-navigation tree in the tool window. The Changes tree auto-refreshes on filesystem changes.

#### Scenario: Tree structure
- **WHEN** the tool window opens on an initialized project
- **THEN** it SHALL show the Changes section (changes with per-artifact status); spec, archived-change, and config files are browsed in the Project View, not a tool-window file tree

#### Scenario: Actionable hints
- **WHEN** no changes exist
- **THEN** a hint node SHALL appear under Changes that triggers Propose on double-click

#### Scenario: Change-node visual hierarchy
- **WHEN** a change node renders in the Changes tree
- **THEN** the change name SHALL be shown in the primary (default) color, the `[status]` tag in its meaning color, and the `X/Y` task-completion count as dimmed secondary text — so the count does not compete with the name
- **AND** a complete count (`N/N`) SHALL NOT be colored as "done": the apply-ready state is conveyed by the node's status icon badge (driven by the artifact DAG), a signal distinct from the tasks.md task count

### Requirement: Search and filtering

The plugin SHALL provide content search over spec requirement bodies and scenario text via a **Search Everywhere contributor** — so a term occurring only inside a requirement's prose surfaces that spec/requirement and, on selection, opens it in the editor at the requirement. The search SHALL run off the UI thread over the local OpenSpec files without persisting a search index, and SHALL be available during indexing (dumb-aware). The former always-on tool-window tree filter over spec content is retired in favor of this surface.

#### Scenario: Content search via Search Everywhere
- **WHEN** the user enters, in Search Everywhere, a term that appears in a requirement's body or scenario text but not in any label
- **THEN** the contributor SHALL surface that requirement, and selecting it SHALL open its spec in the editor at that requirement

#### Scenario: Off-thread, index-free content matching
- **WHEN** content search runs
- **THEN** it SHALL scan the local spec files off the UI thread without persisting an index, and SHALL function during indexing

## REMOVED Requirements

### Requirement: Config node types

**Reason**: The Config section is removed from the tool-window tree; `config.yaml` is browsed and opened via the Project View. The plugin's config *reading* (tree-independent) is unaffected.
**Migration**: Users open `openspec/config.yaml` from the Project View instead of a Config tree node.
