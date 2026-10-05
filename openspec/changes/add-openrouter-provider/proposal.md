## Why

The installed Codex architecture leaves OpenRouter as an extension seam, so users cannot yet generate artifacts through their OpenRouter account. Implement a complete REST provider using the existing reviewed-context and safe artifact application pipeline.

## What Changes

- Add OpenRouter routing with fixed HTTPS endpoints, provider-scoped PasswordSafe credentials and per-provider model preferences.
- Add a live text-model catalog refresh and editable model IDs; refresh makes no inference request.
- Handle HTTP errors, embedded errors, malformed/empty/truncated responses and cancellation without fallback or partial application.
- Cover real API contracts, existing workflows and provider isolation; document billing and acceptance evidence.

## Capabilities

### New Capabilities

- `openrouter`: OpenRouter connection, catalog, response validation and cancellation through the shared REST backend.

### Modified Capabilities

None. Existing lifecycle and reviewed-context application contracts remain in force.

## Impact

Touches AI REST transport, settings, tests and documentation. Retains Java 21 and IntelliJ IDEA 2024.2+ compatibility, existing provider behavior and coverage gates. No new runtime dependency or external tracker publication is needed for this user-requested fork change.
