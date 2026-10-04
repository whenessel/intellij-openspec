## Purpose

Make AI context selection, disclosure, redaction and budgets visible and enforceable before execution or manual prompt delivery.

## ADDED Requirements

### Requirement: Reviewed bounded context

The plugin SHALL assemble a bounded immutable context snapshot and show included data, exclusions, redactions, destination, budget estimates and effective execution permissions before sending. Other changes/source content SHALL require explicit scope inclusion.

#### Scenario: Explore default scope
- **WHEN** Explore context is assembled for a selected change
- **THEN** unselected active changes SHALL be excluded by default and omissions SHALL be visible

#### Scenario: Budget exceeded
- **WHEN** essential context exceeds effective input budget
- **THEN** the plugin SHALL request scope reduction instead of silently truncating required instructions

#### Scenario: Secrets
- **WHEN** excluded credential files or secret patterns occur in candidate context
- **THEN** they SHALL be omitted or redacted before preview and delivery and SHALL NOT appear in normal diagnostics

### Requirement: Data and usage disclosure

The plugin SHALL distinguish local subprocess execution from model inference destinations and disclose subscription/API billing and any backend tool-read scope beyond the submitted text. Privacy constraints SHALL NOT be weakened on retry.

#### Scenario: Local Codex
- **WHEN** the user previews a Codex request
- **THEN** the UI SHALL disclose online inference and the enforced local read/tool scope

#### Scenario: Router backend
- **WHEN** a future router backend is selected
- **THEN** the preview SHALL disclose router and downstream-provider privacy policy constraints

#### Scenario: Scope changed
- **WHEN** a request has new data destinations or expanded included files/permissions
- **THEN** the reviewed approval SHALL be invalidated and a new preview SHALL be required
