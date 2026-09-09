## 1. Verify exact model IDs FIRST (never guess — guessed IDs 401 on fresh installs)

- [x] 1.1 **Anthropic:** confirmed current dateless aliases from Anthropic's model reference — `claude-opus-5`, `claude-sonnet-5`, `claude-opus-4-8`, `claude-haiku-4-5` (no date suffix).
- [ ] 1.2 **OpenAI:** DEFERRED — no key for `GET /v1/models` and the docs pages returned fast-moving marketing/variant names (GPT-5.x/6) with no commit-ready exact strings. Per 1.4, OpenAI is left unchanged this pass rather than shipping a guessed ID. Revisit with a key.
- [x] 1.3 **Gemini:** confirmed against the Google AI models docs — Gemini 2.5 (pro/flash/flash-lite) is retiring 2026-10-16 (flash already deprecated); current stable Flash line: `gemini-3.5-flash`, `gemini-3.8-flash`, `gemini-3.5-flash-lite` (free-tier friendly).
- [x] 1.4 Narrowed scope to the verifiable providers (Claude + Gemini); OpenAI excluded and recorded (the `secure-gemini-auth-header` precedent).

## 2. Refresh implementation

- [x] 2.1 `AiProvider` refreshed: Claude → `claude-sonnet-5` (default), `claude-opus-5`, `claude-haiku-4-5`; Gemini → `gemini-3.5-flash` (default), `gemini-3.8-flash`, `gemini-3.5-flash-lite`. OpenAI left unchanged (deferred, 1.2) with an in-code note.
- [x] 2.2 `REASONING_MODEL_PREFIXES` unchanged — `o1`/`o3`/`o4`/`gpt-5` is current and correct; no new reasoning family verified, so nothing added.
- [x] 2.3 `DirectApiService.MAX_TOKENS` 8192 → 16000 (non-streaming; under the 5-min timeout).

## 3. Tests

- [x] 3.1 Added `AiProviderTest$Models.geminiDefaultIsNotARetiringModel` — the default must not be a retiring `gemini-2.5*` (fails against the prior `gemini-2.5-pro` default). Discovered the existing guards (`claudeDefaultIsDatelessAlias`, `defaultModelIsFirstInList`, per-provider `*HasModels`, and `AiProviderModels.*`) already cover default-non-blank / Claude dateless-alias, so only the Gemini guard was added (avoided duplicating them). `OpenAiTokenParam` + `BuildClaudeRequest` (`anthropic-version == 2023-06-01`) stay green.
- [x] 3.2 N/A — `REASONING_MODEL_PREFIXES` unchanged, so the existing `OpenAiTokenParam` `@ValueSource` (`o1`/`o3`/`o4-mini`/`gpt-5`/`gpt-5-mini` + `gpt-4o` negatives) is untouched and green.
- [x] 3.3 `./gradlew build` green (test + JaCoCo floor); response-parser/contract tests untouched.

## 4. Documentation

- [x] 4.1 CHANGELOG `## Unreleased → Fixed` entry (Gemini/Claude model refresh; contract/headers/routing unchanged; OpenAI deferred noted).
- [x] 4.2 Grepped user-facing docs for stale IDs — none hardcode provider models, so nothing to change.

## Deferred (follow-up, needs a key)

- **OpenAI list refresh** — verify exact current chat + reasoning IDs against `GET /v1/models` (key) and refresh `AiProvider.OPENAI` + extend `REASONING_MODEL_PREFIXES` if a new reasoning family appears. Update `DirectApiServiceTest.AiProviderModels.openAiModelsAreCurrent` (it currently pins `gpt-4o`).
