## Context

See proposal.md. REST execution already passes through RestAiBackend and DirectApiService; settings enumerate AiProvider and retain provider-specific models. PasswordSafe names are provider-scoped. The settings field currently retains typed keys on provider changes, which must be corrected.

## Goals / Non-Goals

Goals: executable OpenRouter with catalog discovery, safe credential handling and the shared context/application boundary. Non-goals: OAuth, arbitrary endpoints, native tool execution, autonomous workspace writes or paid acceptance requests without a budget.

## Decisions

- Add OPENROUTER to the REST provider enum and preference canonicalization. Keep LOCAL_CODEX separate and retain all existing workflow routing.
- Use fixed https://openrouter.ai/api/v1/chat/completions and /models. No endpoint override or redirect forwarding of credentials. OpenRouter's normalized contract uses max_tokens, qualified model IDs and nonstreaming text. Prompt-based artifact envelopes remain validated locally; no unsupported structured-output claims.
- Put wire parsing in a standalone OpenRouterProtocol with captured API fixtures. Reject missing text, embedded errors, refusal and truncation. Status diagnostics use known status guidance without reflecting response bodies or credential-bearing exception text.
- Reuse the cancelable REST transport with injectable HttpClient for deterministic transport tests. Check cancellation before sending and cancel the future on interrupted/canceled waits. Catalog refresh is explicit and off EDT, with provider generation guards.
- Keep the editable selector, add Refresh models (no inference), and preserve manual selection. Catalog model descriptions retain exact decimal-string token/request/image/cache units plus source and fetch time, displayed in model tooltips. Clear keys on switching; apply a stored-key mask only to an untouched field for the current generation. Defaults use the documented free router; model availability still varies.

## Risks / Trade-offs

- Catalog and free model availability change → live refresh and manual IDs; no paid fallback in acceptance smoke.
- Provider error bodies may contain sensitive input → status-only diagnostics, no raw response/error logging.
- Nonstreaming output arrives at completion → transport cancellation is supported; no token-streaming claim.
- GUI acceptance is environment-dependent → distinguish unit/platform checks, live HTTP smoke and UI end-to-end evidence.

## Migration Plan

Extend the recognized provider set without changing schema version or existing fields. Existing models and credentials remain provider-scoped. Rollback ignores OPENROUTER without rewriting another provider's credentials.
