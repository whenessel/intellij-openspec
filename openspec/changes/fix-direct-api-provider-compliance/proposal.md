# Fix Direct API provider compliance

## Why

A static audit of the Direct API provider integration (which the plugin uses to call Claude / OpenAI / Gemini directly when a user configures a key) found two hard-failure defects on the Claude path that cannot be caught without live API keys — and the maintainer has none to test against:

1. **The default Claude model ID is fabricated.** The provider list leads with `claude-sonnet-4-5-20250514`, a snapshot date that does not exist (it grafts the Claude 4 launch date onto "4-5"; the real Sonnet 4.5 snapshot is `-20250929`). Because the first list entry is the default when no model override is set, **every fresh Claude Direct-API install with default settings sends a non-existent model and gets HTTP 404.**
2. **The `anthropic-version` header is pinned to an invalid value.** It sends `2024-06-01`; the required, published version string is `2023-06-01`. The header is required and enumerated, so this risks a hard failure on every Claude call.

Two lower-severity items surfaced alongside:

3. **OpenAI reasoning-model token routing is too narrow.** Only `o1*` is routed to `max_completion_tokens`; `o3`/`o4`/`gpt-5` reasoning models also require it and would 400 with `max_tokens`. Latent today (none are in the default list) but fires when a user types a custom reasoning-model ID.
4. **The response parsers are not contract-tested.** The three provider response parsers are exercised by no test against real provider output — the exact gap that let defect 1 ship (the existing model test only asserts a `claude-` prefix). This violates the repo's contract-test discipline.

## What Changes

- Correct the Claude model list to valid, current **dateless aliases** (`claude-sonnet-4-5`, `claude-opus-4-1`, `claude-haiku-4-5`) so the default resolves and the list does not re-stale on the next snapshot date.
- Correct the `anthropic-version` header to `2023-06-01`.
- Broaden OpenAI reasoning-model token-param routing to the whole reasoning family (`o1`/`o3`/`o4`/`gpt-5` → `max_completion_tokens`).
- Add **contract tests** for the three response parsers, using each provider's own published example response body as the fixture (the defensible substitute for a live capture when no keys are available), plus non-vacuous guards for the model default, the version header, and the token-param routing table.

## Capabilities

### Modified Capabilities
- **ai-integration** — the "AI providers" requirement gains explicit correctness guarantees: provider defaults must be valid current model identifiers, the Anthropic version header must be the current required value, reasoning-model token parameters must be routed correctly, and the response parsers must be contract-tested against captured provider example output.

## Impact

- Affected code: `ai/AiProvider.java` (model lists), `ai/DirectApiService.java` (version header, token-param routing; parsers unchanged in behavior but now covered).
- Affected tests: new parser contract fixtures under `src/test/resources/fixtures/ai/`, new/updated tests in `ai/`.
- User-visible: Claude Direct API works out of the box instead of 404-ing on defaults. No change for CLI-delegation users (Direct API is opt-in).
- Residual risk (documented, not hidden): without live keys we cannot prove end-to-end request/response against the live services; the contract tests pin parsing to the providers' *published* contract, which is the strongest guarantee available offline.
