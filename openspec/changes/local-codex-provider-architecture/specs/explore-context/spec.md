## MODIFIED Requirements

### Requirement: Context assembly

The plugin SHALL assemble a Markdown-formatted project context from the current OpenSpec project state, including config summary with context and rules, detected AI tools, selected changes with reviewed bounded artifact content, and spec domain listings with requirement summaries.

#### Scenario: Full context assembly
- **WHEN** the user triggers context assembly
- **THEN** the service SHALL produce a Markdown document with sections for Project Config, Detected AI Tools, Active Changes, and Specs

#### Scenario: Config section includes context and rules
- **WHEN** context is assembled and `config.yaml` has `context` and `rules` fields
- **THEN** the Project Config section SHALL include the schema, version, context description, and rules as a bulleted list

#### Scenario: Active changes with full artifacts
- **WHEN** an active change has artifact files (proposal.md, design.md, tasks.md, delta specs)
- **THEN** the Active Changes section SHALL include only the reviewed content of selected artifacts within effective budget under the change heading

#### Scenario: Active changes with missing artifacts
- **WHEN** an active change is missing some artifact files
- **THEN** the Active Changes section SHALL include only the artifacts that exist, silently skipping missing ones

#### Scenario: Spec domain with requirement summaries
- **WHEN** a spec domain has a `spec.md` with `### Requirement:` blocks
- **THEN** the Specs section SHALL list each requirement name with its description text (not scenarios)

#### Scenario: No active changes
- **WHEN** the project has no active changes
- **THEN** the Active Changes section SHALL indicate no changes are in progress

#### Scenario: Unselected change exclusion
- **WHEN** context is assembled for the selected change
- **THEN** other active changes SHALL be excluded by default, and exclusions, redactions and budget omissions SHALL be shown before delivery

### Requirement: Explore panel

The plugin SHALL provide an Explore tab in the OpenSpec tool window when an execution backend is configured with ready Explore capability or an existing Explore conversation surface is being reused. The tab SHALL display AI explore responses rendered as HTML from markdown, with an inline input area for submitting topics and toolbar actions for copy and clear.

#### Scenario: Tab present when Direct API configured
- **WHEN** the tool window content is created and an execution backend is ready with Explore capability and its required authentication
- **THEN** the Explore tab SHALL be added to the tool window

#### Scenario: Tab absent when no Direct API configured
- **WHEN** the tool window content is created and no execution backend with Explore capability is configured
- **THEN** the Explore tab SHALL NOT be added to the tool window

#### Scenario: Panel displays rendered response
- **WHEN** the AI provider returns an explore response
- **THEN** the panel SHALL render the markdown response as styled HTML in the response area

#### Scenario: Copy button
- **WHEN** the user clicks the Copy Response button in the Explore toolbar
- **THEN** the panel SHALL copy the raw markdown response text to the system clipboard with a notification

#### Scenario: Clear button
- **WHEN** the user clicks the Clear button in the Explore toolbar
- **THEN** the panel SHALL reset to the invitation empty state and clear the input area

#### Scenario: Manual mode switch preserves tab
- **WHEN** the user switches an existing Explore tab to manual delivery
- **THEN** existing results SHALL remain and inline submission SHALL hand off a prompt without hidden inference

### Requirement: Lazy Explore tab creation

The plugin SHALL lazily create the Explore tab when `ExplorePanelService.getAndActivate()` is called and the tab does not yet exist but a ready Explore-capable execution backend is now configured. This supports users who configure a provider after project open.

#### Scenario: Lazy creation on first Direct API explore
- **WHEN** `getAndActivate()` is called and no Explore tab exists and an authenticated backend with ready Explore capability is selected
- **THEN** the service SHALL create the ExplorePanel, add the Explore content tab to the tool window, register the panel, and activate the tab

#### Scenario: No lazy creation without Direct API
- **WHEN** `getAndActivate()` is called and no Explore tab exists and no backend with required Explore capability/readiness is available
- **THEN** the service SHALL return null without creating the tab

#### Scenario: Existing tab reused
- **WHEN** `getAndActivate()` is called and the Explore tab already exists
- **THEN** the service SHALL activate the existing tab without creating a new one

#### Scenario: Manual mode switch preserves tab
- **WHEN** the user switches an existing Explore tab to manual delivery
- **THEN** existing results SHALL remain and inline submission SHALL hand off a prompt without hidden inference

### Requirement: Direct API submit from panel

Inline Explore SHALL use the same explicit routing policy as menu Explore. Integrated execution SHALL use the selected ready backend; manual mode SHALL deliver the prompt without inference.

#### Scenario: Panel submit uses Direct API
- **WHEN** the user submits in an integrated execution mode
- **THEN** the plugin SHALL build reviewed context and call the resolved backend

#### Scenario: Inline manual submit
- **WHEN** the user submits with clipboard or editor selected
- **THEN** the plugin SHALL deliver the prompt with guidance and SHALL NOT invoke saved REST

#### Scenario: Menu action retains delivery mode routing
- **WHEN** Explore is triggered from the menu
- **THEN** it SHALL use the same route and context policy as inline Explore

### Requirement: Delivery mode routing

The plugin SHALL route the assembled explore prompt through the user's configured delivery mode, supporting backend execution, Editor Tab, and Clipboard delivery.

#### Scenario: Direct API delivery
- **WHEN** the resolved delivery mode is backend execution and a ready Explore-capable backend is selected
- **THEN** the plugin SHALL send the explore prompt to the configured AI provider on a background thread and display the response in the Explore tab

#### Scenario: Editor Tab delivery
- **WHEN** the resolved delivery mode is Editor Tab
- **THEN** the plugin SHALL write the explore prompt to a temporary file on a pooled thread, perform the VFS `refreshAndFindFileByNioFile` lookup on the same pooled thread, and open the file in an editor tab via `invokeLater` on the EDT

#### Scenario: Clipboard delivery
- **WHEN** the resolved delivery mode is Clipboard
- **THEN** the plugin SHALL copy the explore prompt to the system clipboard and show a notification

#### Scenario: Direct API not configured
- **WHEN** the resolved delivery mode is backend execution but the selected backend is unavailable
- **THEN** the plugin SHALL show the unavailability and offer explicit alternative selection without automatically invoking another provider or copying content

### Requirement: Explore panel rework

The plugin SHALL rework the Explore tab from a passive read-only context viewer into an explore results panel displaying the topic, AI response, and toolbar actions for re-exploring and copying.

#### Scenario: Display explore response
- **WHEN** the AI provider returns a successful response via the selected ready execution backend
- **THEN** the plugin SHALL activate the Explore tab, display the topic as a header, and display the AI response in the content area

#### Scenario: Display API error
- **WHEN** the AI provider returns an error
- **THEN** the plugin SHALL display the error in the Explore tab with error styling and show a notification

#### Scenario: Background execution with progress
- **WHEN** the explore prompt is sent via the selected ready execution backend
- **THEN** the API call SHALL execute on a background thread without blocking the UI, with a progress indicator in the Explore tab

#### Scenario: Toolbar actions
- **WHEN** the Explore tab displays a response
- **THEN** the toolbar SHALL provide actions for: New Explore (re-open topic dialog), Copy Response (copy to clipboard), and Refresh (re-run the last explore)
