## Purpose

Provide consistent execution and lifecycle behavior across AI backends without changing OpenSpec workflow semantics.

## ADDED Requirements

### Requirement: Backend-independent execution

The plugin SHALL resolve every AI action through the same explicit delivery and backend policy. It SHALL negotiate action requirements against backend, model, transport, installed version and security policy, and SHALL explain unsupported or unknown required capabilities.

#### Scenario: Shared route
- **WHEN** generation, Continue, FF, Explore, Verify or Archive semantic assessment is requested
- **THEN** the chosen delivery/backend/model SHALL match the explicit run choice or saved preference

#### Scenario: Unknown capability
- **WHEN** a required security or output capability is unknown
- **THEN** execution SHALL be blocked with an actionable reason rather than assumed supported

#### Scenario: No fallback
- **WHEN** an executed backend fails
- **THEN** the plugin SHALL show that failure without invoking another backend or billing source

### Requirement: Observable terminal lifecycle

Each executed run SHALL have isolated identity, bounded lifetime, progress, cancellation and exactly one terminal outcome. Failed, partial, canceled and timed-out outputs SHALL NOT be applied as successful results.

#### Scenario: Late completion
- **WHEN** a canceled run later returns output
- **THEN** its output SHALL neither modify files nor replace results for another run

#### Scenario: Project disposal
- **WHEN** the project closes during execution
- **THEN** owned processes, requests and pending approvals SHALL be canceled and cleaned up

#### Scenario: Responsive UI
- **WHEN** I/O or streaming is active
- **THEN** the IDE SHALL remain responsive and UI updates SHALL occur on its UI thread

### Requirement: Versioned interoperability

The plugin SHALL distinguish backend protocol compatibility from OpenSpec schema and plugin settings versions. It SHALL tolerate harmless optional additions and reject incompatible required contracts visibly.

#### Scenario: Incompatible version
- **WHEN** the installed backend lacks a required supported contract
- **THEN** execution SHALL stop with version guidance and an explicit manual-delivery alternative

#### Scenario: Future adapter
- **WHEN** a new backend satisfies the existing request/result/capability contracts
- **THEN** workflows SHALL use it without provider-specific routing behavior

### Requirement: Future router model and policy boundary

When a future router backend is enabled, the plugin SHALL retain provider-qualified model identities, source/timestamp and exact price units; negotiate required parameters at model and route level; honor explicit routing/privacy constraints; and normalize credit/rate/auth/stream failures without hidden fallback.

#### Scenario: Pricing metadata
- **WHEN** a router catalog supplies decimal-string token prices or omits a cost
- **THEN** the UI SHALL preserve decimal precision, show currency/unit/timestamp and distinguish zero cost from unknown cost

#### Scenario: Required structured output
- **WHEN** no allowed downstream route supports the requested structured-output or privacy requirements
- **THEN** execution SHALL fail with a constraint explanation rather than drop the requirement

#### Scenario: Error after successful HTTP status
- **WHEN** the router stream reports an error after HTTP 200
- **THEN** the run SHALL end as failure and SHALL NOT apply partial output
