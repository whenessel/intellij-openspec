## Purpose

Allow a user-selected installed Codex to execute reviewed OpenSpec requests using CLI-owned authentication and explicit safety limits.

## ADDED Requirements

### Requirement: Installed executable ownership

The plugin SHALL use a user-approved installed Codex executable with safe argument and stdin handling. It SHALL report path/version compatibility and SHALL NOT infer executable readiness from a project tool-marker directory.

#### Scenario: Marker only
- **WHEN** a project contains .codex but no validated executable
- **THEN** integrated execution SHALL remain unavailable while manual Codex handoff remains selectable

#### Scenario: Special characters
- **WHEN** the executable path or prompt contains spaces or shell metacharacters
- **THEN** the intended executable and input SHALL be used without shell evaluation

### Requirement: CLI-owned authentication and billing

The plugin SHALL delegate login storage and refresh to Codex, SHALL NOT read or copy auth files or credentials, and SHALL show effective auth and billing category before inference. Unknown auth SHALL NOT be represented as subscription use.

#### Scenario: ChatGPT login
- **WHEN** Codex reports managed ChatGPT authentication
- **THEN** the UI SHALL identify plan-based usage and available limits without promising unlimited or offline inference

#### Scenario: API key login
- **WHEN** Codex reports API-key mode
- **THEN** the UI SHALL identify API billing and require acknowledgement when the user first selected Codex expecting subscription use

#### Scenario: Signed out
- **WHEN** Codex needs authentication
- **THEN** the plugin SHALL offer CLI login guidance without silently invoking saved REST credentials

### Requirement: Interactive session correctness

The local backend SHALL honor the supported connection handshake, explicit thread ownership, streamed events and terminal turn status. Resume SHALL be scoped to the same approved project/account/security context.

#### Scenario: Streamed turn
- **WHEN** a supported session emits text deltas then successful terminal completion
- **THEN** the plugin SHALL display incremental text and make only the validated final result eligible for acceptance

#### Scenario: Interrupt
- **WHEN** the user stops an active turn
- **THEN** the plugin SHALL request interruption and enforce bounded cleanup if terminal confirmation does not arrive

#### Scenario: Account changed
- **WHEN** a user attempts to resume a thread after account or security scope changes
- **THEN** the plugin SHALL require a new explicitly scoped conversation

### Requirement: Read-only MVP scope

Integrated generation, Explore and Verify SHALL enforce approved context read scope, deny workspace writes and escalation, and return results for plugin-controlled review. The plugin SHALL block a profile unable to enforce that scope.

#### Scenario: Unsupported sandbox
- **WHEN** an installed CLI cannot enforce the reviewed MVP permissions
- **THEN** the backend SHALL report the unsupported safety profile and SHALL NOT run with broader permissions

#### Scenario: Approval in MVP
- **WHEN** a tool or write escalation request arrives during MVP execution
- **THEN** it SHALL be denied or canceled without granting broader permissions

### Requirement: Backend model discovery

The plugin SHALL obtain available models and defaults from the selected Codex account/backend when supported, with visible refresh/staleness and an explicit manual model override.

#### Scenario: Missing model
- **WHEN** a saved model is absent from refreshed catalog
- **THEN** its ID SHALL be retained with a visible warning and SHALL NOT silently switch to another model

#### Scenario: Catalog failure
- **WHEN** model discovery fails
- **THEN** the selection SHALL be preserved and discovery failure SHALL be distinguished from successful validation
