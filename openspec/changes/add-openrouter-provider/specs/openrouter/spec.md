## Purpose

Enable generation, exploration and verification through a configured OpenRouter account using the plugin's existing reviewed-context and artifact safety boundaries.

## ADDED Requirements

### Requirement: OpenRouter configuration and routing

The plugin SHALL offer OpenRouter as a REST provider, store its key only in provider-scoped PasswordSafe, remember its model independently, and route integrated generation, Continue, Fast-Forward, Explore and Verify through the captured OpenRouter selection. Manual delivery and installed Codex SHALL retain their existing behavior. Credentials SHALL NOT appear in URLs, settings state, diagnostics or another provider's field.

#### Scenario: Provider isolation
- **WHEN** a user switches to OpenRouter from another provider
- **THEN** its own model and stored-key mask SHALL be loaded without retaining the other provider's typed key

#### Scenario: Reviewed request
- **WHEN** an integrated workflow captures OpenRouter and a model
- **THEN** the request SHALL use that selection and the existing reviewed context even if settings subsequently change

### Requirement: OpenRouter model catalog

The plugin SHALL allow an explicit background refresh of the OpenRouter text-input/text-output model catalog without inference. Users SHALL be able to enter a model ID manually and keep their selection when refresh succeeds or fails. Stale refreshes SHALL NOT mutate another provider's settings. No model SHALL be assumed to support structured outputs merely because it is on OpenRouter.

#### Scenario: Catalog refresh
- **WHEN** the user refreshes OpenRouter models
- **THEN** available text model IDs SHALL populate the editable selector, preserving the current selection and displaying refresh status

### Requirement: OpenRouter completion and cancellation

The plugin SHALL send credentials only in the Authorization header to the fixed OpenRouter HTTPS API. It SHALL accept only nonempty completed assistant text, report actionable sanitized status errors, reject embedded errors and truncated/refused responses, and cancel pending transport requests when cancellation is requested. Failure SHALL NOT invoke another backend or apply partial results.

#### Scenario: Truncated completion
- **WHEN** HTTP 200 contains a length-limited completion or no assistant text
- **THEN** the operation SHALL fail without applying the response

#### Scenario: Cancel request
- **WHEN** cancellation occurs before or during the request
- **THEN** transport SHALL be canceled and no result SHALL be delivered or applied
