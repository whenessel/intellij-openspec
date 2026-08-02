# ai-integration (delta)

## MODIFIED Requirements

### Requirement: AI providers

The plugin SHALL support Claude, OpenAI, and Gemini as Direct API providers with secure credential storage via IntelliJ PasswordSafe. API keys SHALL be transmitted via provider-specific headers (`x-api-key` for Claude, `Authorization: Bearer ...` for OpenAI, `x-goog-api-key` for Gemini) and SHALL NOT appear in request URLs or query strings.

The plugin SHALL call each provider using a valid, current API contract. Specifically: the default model for each provider SHALL be a valid current model identifier (Claude defaults SHALL be dateless aliases so they resolve to the current snapshot and do not carry a fabricated release date); the Anthropic requests SHALL send the current required `anthropic-version` value (`2023-06-01`); and OpenAI requests SHALL send the token-limit parameter the target model requires (`max_completion_tokens` for the reasoning family — `o1`/`o3`/`o4`/`gpt-5` — and `max_tokens` otherwise). The provider response parsers SHALL be contract-tested against captured provider example responses.

#### Scenario: API generation
- **WHEN** a user configures a Direct API provider with a valid key
- **THEN** the plugin SHALL generate artifacts by calling the provider's API directly

#### Scenario: Test connection
- **WHEN** the user clicks "Test Connection"
- **THEN** the plugin SHALL send a test prompt and display success or a provider-specific error message

#### Scenario: Gemini auth header
- **WHEN** the plugin builds a Gemini API request
- **THEN** the API key SHALL be set as the `x-goog-api-key` request header AND the request URL SHALL NOT contain a `?key=` query parameter (or any other form of the key)

#### Scenario: Valid provider defaults
- **WHEN** a user selects a provider without choosing an explicit model
- **THEN** the default model SHALL be a valid current model identifier (for Claude, a dateless alias with no fabricated release-date suffix) so the first API call does not fail with a model-not-found error

#### Scenario: Anthropic version header
- **WHEN** the plugin builds a Claude API request
- **THEN** the request SHALL carry `anthropic-version: 2023-06-01`

#### Scenario: OpenAI reasoning-model token parameter
- **WHEN** the plugin builds an OpenAI request for a reasoning-family model (`o1`/`o3`/`o4`/`gpt-5`)
- **THEN** the request SHALL send `max_completion_tokens` rather than `max_tokens`; for non-reasoning chat models the request SHALL send `max_tokens`

#### Scenario: Response parser contract
- **WHEN** a provider returns its documented response shape
- **THEN** the plugin SHALL extract the generated text from that shape, verified by a contract test that drives each provider's parser from a captured provider example response
