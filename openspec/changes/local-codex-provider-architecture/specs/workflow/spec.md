## MODIFIED Requirements

### Requirement: Workflow action panel

The plugin SHALL display the selected change's pipeline status with interactive artifact chips that serve as the primary action surface. Pipeline chips SHALL be clickable (READY → generate, DONE → open file) with right-click context menus. A compact icon action bar SHALL provide secondary actions (Fast-Forward, Verify, Archive, overflow menu). A single-line status strip SHALL show compliance, task progress, and delivery mode. The panel SHALL NOT use a horizontal button row for workflow actions. The content area SHALL use CardLayout to manage NO_CHANGES, FF_INPUT, and PIPELINE views. The Fast-Forward link in the "no changes" card SHALL be visible when an artifact-generation backend is ready or explicit manual FF mode is selected; when automated generation is unavailable and no manual FF mode is selected, the card SHALL show only the Propose link. The Explore tab context SHALL reflect the current change selection.

#### Scenario: Pipeline visualization
- **WHEN** a change is selected
- **THEN** the panel SHALL show interactive artifact status chips (DONE, READY, BLOCKED, GENERATING) with content-aware scaffolding detection, hover affordances, and click/right-click actions

#### Scenario: Generate via chip click
- **WHEN** the user clicks a READY chip
- **THEN** it SHALL deliver via the selected method (clipboard, editor tab, or backend execution) with a guidance popover showing post-delivery feedback

#### Scenario: Sync Specs in overflow menu
- **WHEN** all artifacts are complete and the change contains delta spec sections
- **THEN** the overflow menu SHALL include a "Sync Specs" item

#### Scenario: Sync Specs hidden from overflow
- **WHEN** the change has no delta spec sections
- **THEN** the overflow menu SHALL NOT include "Sync Specs"

#### Scenario: Compliance chip displayed
- **WHEN** the workflow action panel renders for a selected change
- **THEN** the status strip SHALL include compliance status alongside task progress and delivery mode

#### Scenario: FF link visible with Direct API
- **WHEN** the "no changes" card renders and an artifact-generation backend is ready
- **THEN** the card SHALL show "Propose or Fast-Forward" with both links active

#### Scenario: FF link hidden without Direct API
- **WHEN** the "no changes" card renders and automated artifact generation is unavailable and no manual FF mode is selected
- **THEN** the card SHALL show only the Propose link without the FF option

#### Scenario: FF input activation
- **WHEN** the user clicks the FF toolbar button or "Fast-Forward" hyperlink
- **THEN** the panel SHALL switch to the FF_INPUT card while keeping the tool selector visible

#### Scenario: Explore tab reflects change context
- **WHEN** a change is selected in the workflow panel
- **THEN** the Explore tab SHALL include that change's details in the assembled context

### Requirement: FF action requires Direct API

The FF action SHALL support automated backend generation when ready artifact-generation capabilities are negotiated and manual first-artifact handoff when clipboard/editor is explicitly selected. Availability SHALL retain existing profile/schema constraints and SHALL NOT require a REST API key for Codex.

#### Scenario: FF action disabled without Direct API
- **WHEN** execution mode is selected but backend is unready or lacks safe artifact generation
- **THEN** automated FF SHALL be disabled with a capability/settings explanation

#### Scenario: FF action enabled with Direct API
- **WHEN** Codex is ready and supports safe artifact generation
- **THEN** automated FF SHALL be enabled subject to profile/schema visibility

#### Scenario: Manual FF input
- **WHEN** clipboard/editor delivery is selected and FF is activated
- **THEN** the FF form SHALL allow change creation and first-ready-artifact handoff without claiming automatic completion

#### Scenario: FF input guard without Direct API
- **WHEN** integrated FF is requested but the selected backend is unready or lacks required artifact capabilities
- **THEN** the panel SHALL show actionable configuration/capability guidance instead of starting generation

### Requirement: Generate All

The plugin SHALL sequentially generate all remaining artifacts in dependency order via the selected ready execution backend with progress reporting and cancellation support.

#### Scenario: Orchestration
- **WHEN** Generate All is triggered
- **THEN** artifacts SHALL generate in DAG dependency order with real-time progress feedback

### Requirement: Apply action

The plugin SHALL assemble a full-context implementation prompt (proposal, specs, design, tasks) and deliver via the selected tool.

#### Scenario: Prompt assembly
- **WHEN** Apply is triggered
- **THEN** the plugin SHALL assemble reviewed bounded context from all required change artifacts and deliver via an explicitly chosen manual method in MVP

#### Scenario: MVP manual Apply boundary
- **WHEN** Apply is selected during the generation/Explore/Verify MVP
- **THEN** the implementation prompt SHALL be handed off manually and SHALL NOT start autonomous workspace execution
