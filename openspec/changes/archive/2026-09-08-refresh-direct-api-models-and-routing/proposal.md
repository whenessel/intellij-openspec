## Why

The plugin's Direct API request/response **contract** is conformant and current (endpoints, auth headers, `anthropic-version: 2023-06-01`, the `o1`/`o3`/`o4`/`gpt-5` → `max_completion_tokens` routing, and the response parsers are all correct). But the offered **model catalogs** in `AiProvider` have drifted roughly two generations behind across all three providers — Claude `claude-sonnet-4-5` / `claude-opus-4-1`, OpenAI `gpt-4o` / `o1-mini`, Gemini `gemini-2.5-pro` / `gemini-2.5-flash`. The `ai-integration` spec already requires each provider's default to be **"a valid current model identifier"**; a stale default risks a model-not-found (HTTP 401/404) on a fresh install's very first generation once a provider retires it. Refresh the lists and defaults to current, valid IDs.

## What Changes

- Refresh the per-provider model lists and defaults in `AiProvider` to current, valid identifiers — verified against each provider's live models endpoint or model documentation **before** they are written (never guessed).
- Extend `REASONING_MODEL_PREFIXES` only if verification surfaces a new reasoning family that requires `max_completion_tokens` (the existing `o1`/`o3`/`o4`/`gpt-5` set is already correct).
- Optionally raise `MAX_TOKENS` (currently 8192, global, non-streaming) so large specs/designs don't truncate on modern models — decided in design.
- **No change** to the request/response contract, auth headers, `anthropic-version` (stays `2023-06-01`), the token-parameter routing logic, or the response parsers — those are already conformant.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
<!-- none — skip_specs: true -->
The `ai-integration` "AI providers" requirement already mandates a valid, current API contract and a valid-current default model per provider, and does so **abstractly** (it deliberately does not hardcode model IDs, so it never drifts). This change re-satisfies that existing requirement by refreshing the implementation's model constants; it introduces no new or changed spec-level behavior, so it carries no delta.

## Impact

- **Code:** `AiProvider.java` (model lists + defaults); possibly `DirectApiService` (`REASONING_MODEL_PREFIXES`, `MAX_TOKENS`). No contract/header/parser change.
- **Tests:** the existing `DirectApiServiceTest` (`OpenAiTokenParam`, `BuildClaudeRequest` anthropic-version, provider response contract tests) stays green; add coverage that each provider default is non-blank and — for Claude — a dateless alias (no fabricated date suffix).
- **Users:** fresh installs get current defaults; existing users' saved model settings are preserved.
- **Verification prerequisite (load-bearing):** exact OpenAI/Gemini model-ID strings MUST be confirmed against `GET /v1/models` (key-authenticated) or the providers' model docs before implementation — guessed IDs 401 on fresh installs (the reason these items were deferred originally). Current Anthropic IDs are authoritative from Anthropic's current model documentation.
