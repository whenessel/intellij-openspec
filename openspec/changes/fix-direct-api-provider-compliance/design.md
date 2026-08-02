# Design: fix-direct-api-provider-compliance

## Approach

Four targeted corrections to the Direct API layer, each backed by a test that would fail if the defect returned.

### C1 — valid Claude model defaults (dateless aliases)

`AiProvider.CLAUDE` becomes `List.of("claude-sonnet-4-5", "claude-opus-4-1", "claude-haiku-4-5")`. Dateless aliases resolve server-side to the current snapshot, so `getDefaultModel()` (`models.get(0)`) is always a live model and the list does not need a date bump each release. This is the maintainer's chosen curation (aliases over pinned snapshots) precisely to avoid re-introducing this class of bug.

### H1 — correct `anthropic-version`

`DirectApiService` sends `anthropic-version: 2023-06-01` — the single published, required production value. Feature opt-ins ship via `anthropic-beta`, not new version dates, so this value is stable.

### M1 — reasoning-family token routing

`openAiTokenParam` routes the whole reasoning family to `max_completion_tokens`. Replace the `startsWith("o1")` check with a prefix set (`o1`, `o3`, `o4`, `gpt-5`); everything else keeps `max_tokens`. Chat models (`gpt-4o`, etc.) are unaffected.

### M2 — parser contract tests without live keys

The parsers read `content[0].text` (Claude), `choices[0].message.content` (OpenAI), and `candidates[0].content.parts[0].text` (Gemini). We cannot capture live responses (no keys), so the fixtures are each provider's **own published example response body** from their API reference — the provider's stated contract, not our guess. Fixtures live under `src/test/resources/fixtures/ai/<provider>/` with a provenance manifest recording the doc source and capture date (mirroring `fixtures/cli/README.md`). The exact test surface is set by the test-engineer plan; the non-negotiable is that each parser is driven by a fixture, and the model-default/version/token-param guards are assertion-meaningful (not prefix-only).

## Components Affected

- `ai/AiProvider.java` — model lists.
- `ai/DirectApiService.java` — `anthropic-version` header, `openAiTokenParam`.
- `src/test/resources/fixtures/ai/` — new response fixtures + manifest.
- `ai/DirectApiServiceTest.java`, `ai/AiProviderTest.java` — contract tests and non-vacuous guards.

## Trade-offs

- **Aliases vs pinned snapshots:** aliases can shift the exact model under a user without a plugin update. Accepted — it is strictly better than a guaranteed-404 stale pin, and it is what keeps the list from re-staling.
- **Doc-example fixtures vs live capture:** a provider could change its live envelope without updating docs, so the contract tests guarantee "we parse the published contract," not "we parse today's live bytes." The residual gap is documented in the proposal; it is the strongest offline guarantee and still far exceeds the prior hand-authored/no coverage.
- **Model curation is not pinned in the test:** asserting an exact ID would re-break the test on every refresh, so the guard asserts structural validity (well-formed provider ID, non-empty default, no fabricated-date shape) rather than a literal string.
