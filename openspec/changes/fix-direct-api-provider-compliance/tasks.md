# Tasks: fix-direct-api-provider-compliance

## Implementation Tasks

- [x] 1.0 Extract testable seams (prerequisite) — pull the buried parsers and the Claude request builder into `static` package-private methods mirroring `buildGeminiRequest`: `parseClaudeResponse`, `parseOpenAiResponse`, `parseGeminiResponse`, and `buildClaudeRequest` (so the `anthropic-version` header is assertable). The private `call*` methods delegate to these; no behavior change.
- [x] 1.1 C1 — set `AiProvider.CLAUDE` model list to dateless aliases (`claude-sonnet-4-5`, `claude-opus-4-1`, `claude-haiku-4-5`)
- [x] 1.2 H1 — change the `anthropic-version` header in `DirectApiService` from `2024-06-01` to `2023-06-01`
- [x] 1.3 M1 — broaden `openAiTokenParam` to route the reasoning family (`o1`/`o3`/`o4`/`gpt-5`) to `max_completion_tokens`; update the javadoc

## Testing Tasks

- [x] 2.1 M2 — add captured provider example-response fixtures under `src/test/resources/fixtures/ai/{claude,openai,gemini}/` with a provenance manifest (doc source + date), per the test-engineer plan
- [x] 2.2 M2 — add a per-provider parser contract test that drives each parser from its fixture and asserts the extracted text
- [x] 2.3 Replace the vacuous `claude-`-prefix model assertion with a non-vacuous default guard (default resolves, well-formed provider ID, no fabricated-snapshot-date shape)
- [x] 2.4 Add guards for the `anthropic-version` value and the `openAiTokenParam` routing table (chat vs each reasoning-family prefix)
- [x] 2.5 `./gradlew build` green — suite passes and the JaCoCo coverage floor holds (ratchet if it rises)
