## Context

See proposal.md — Why. `AiProvider` holds three static model lists (default = `models.get(0)`); `DirectApiService` holds `MAX_TOKENS` (8192, global) and `REASONING_MODEL_PREFIXES` (`o1`/`o3`/`o4`/`gpt-5`). The `ai-integration` spec's "AI providers" requirement already mandates a valid, current contract and a valid-current default per provider, contract-tested parsers, and the reasoning-model token routing — all already satisfied by the code except the drifted model lists.

**The load-bearing constraint:** the prior `update-direct-api-providers` audit shipped only the item it could verify and deferred the model-list refresh precisely because **guessed model IDs 401 on fresh installs**. This change must not repeat that — exact IDs are verified before they are written.

## Goals / Non-Goals

**Goals:**
- Each provider's list and default is a valid, current model ID at ship time.
- The verification is done from an authoritative source, not recalled/guessed.

**Non-Goals:**
- Any change to the request/response contract, auth headers, `anthropic-version` (stays `2023-06-01`), token-routing logic, or response parsers — audited conformant.
- A live model-discovery feature (querying `GET /v1/models` at runtime to populate the dropdown) — out of scope; a curated static list is the intended design.
- Hardcoding specific model IDs into the `ai-integration` spec — deliberately avoided so the spec doesn't drift (it stays abstract: "valid current identifier").

## Decisions

**1. Verify exact IDs before writing them (per provider).**
- **Anthropic:** authoritative from Anthropic's current model documentation — `claude-opus-5`, `claude-sonnet-5`, `claude-opus-4-8`, `claude-haiku-4-5` (dateless aliases, no fabricated date suffix, per the spec).
- **OpenAI / Gemini:** confirm exact strings at apply time via `GET /v1/models` (key-authenticated) or the provider's models docs page. Web-search names (e.g. GPT-5.x/GPT-6, Gemini 3.x) are directional only — do not commit them unverified. If a key isn't available, fetch the docs page; if neither can be verified for a provider, narrow scope to the providers that can (the `secure-gemini-auth-header` precedent for shipping only the verifiable part).

**2. Curate a short list per provider; pick a balanced default.**
Keep ~3 IDs per provider (a flagship, a mid-tier, a cheap/fast option). The **default** (`models.get(0)`) is the fresh-install first-call model, so it should be a capable **but cost-reasonable** mid-tier rather than the most expensive flagship. Proposed defaults (confirm at apply): Claude `claude-sonnet-5`, OpenAI a current mid-tier GPT-5-class chat model, Gemini a current mid-tier Flash. *Alternative — default to the flagship:* rejected as the default because it silently raises fresh-install cost/latency; the flagship stays in the list for explicit selection.

**3. Raise `MAX_TOKENS` 8192 → 16000 (global).**
Modern models allow far more output, and large specs/designs truncate at 8192. The service is non-streaming with a 5-minute timeout, so 16000 is safe (the Claude API reference's non-streaming default is ~16000). *Alternative — per-provider caps:* deferred; a single conservative global value is simpler and sufficient until real artifact-length data justifies more.

**4. Keep `REASONING_MODEL_PREFIXES` current.**
`o1`/`o3`/`o4`/`gpt-5` is correct today. Extend it only if verification (Decision 1) surfaces a new OpenAI reasoning family that rejects `max_tokens` — additive, guarded by the existing `OpenAiTokenParam` test.

## Risks / Trade-offs

- **Guessed IDs → 401 on fresh installs** → the whole reason for verify-first; unit tests can't catch it (no live API call), so verification is the only guard. Mitigation: Decision 1, and a test asserting the Claude default is a dateless alias.
- **Rapid provider churn** (OpenAI/Gemini rename often) → the list will drift again. Mitigation: keep the spec abstract (no hardcoded IDs), accept a periodic-refresh cadence, prefer dateless aliases where a provider offers them (Anthropic does).
- **Higher default cost/latency** if a flagship is chosen as default → Decision 2 picks a mid-tier default; flagship remains selectable.

## Open Questions

- Final **default-model** choice per provider (mid-tier vs flagship) — a cost/quality product call; resolve with the user at apply. Does not change the approach or task breakdown.
- Exact OpenAI/Gemini ID strings — resolved by Decision 1's verification at apply (needs a key or the docs page).

## Migration Plan

Additive/maintenance — no migration. Existing users' saved `aiModel` setting is preserved; only fresh-install defaults change. Rollback is reverting the constants.
