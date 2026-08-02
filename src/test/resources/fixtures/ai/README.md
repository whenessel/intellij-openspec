# AI provider response fixtures — provenance

These JSON files are the **providers' own published example response shapes**, transcribed from
each provider's public API reference. They back the parser contract tests in
`DirectApiResponseContractTest` so the response parsers in `ai/DirectApiService.java` are verified
against the providers' *stated contract* rather than a shape we assume.

**Why doc-sourced, not tool-captured (important — read before editing):** the repo's usual
contract-test discipline captures **real** output from the live tool (see `fixtures/cli/README.md`).
That is impossible here: exercising the live Anthropic / OpenAI / Gemini APIs requires paid API
keys, which the maintainer does not hold. A provider's *documented* example response is the
strongest available substitute — it is the provider's own stated contract, categorically better
than a hand-authored shape (which would merely encode our assumption, the exact failure mode this
test class exists to prevent). It can nonetheless lag the live API.

**When API credentials become available:** re-capture a live response per provider (isolate the key
via an env var, never commit it), diff it against these files, and fix any parser mismatch by
updating the parser — treat the committed fixture as the contract of record until then.

| File | Provider | Documented shape source | Key path the parser reads |
|------|----------|-------------------------|---------------------------|
| `claude/messages-response.json` | Anthropic | Messages API reference — response object example (`platform.claude.com/docs`, API → Messages) | `content[0].text` |
| `openai/chat-completion-response.json` | OpenAI | Chat Completions reference — "The chat completion object" example (`platform.openai.com/docs/api-reference/chat/object`) | `choices[0].message.content` |
| `gemini/generatecontent-response.json` | Google Gemini | `generateContent` reference — response example (`ai.google.dev/api/generate-content`) | `candidates[0].content.parts[0].text` |

Transcribed 2026-08-02 for change `fix-direct-api-provider-compliance`. Structural fidelity (field
nesting and the read paths above) is what the contract tests assert; the exact text strings are read
from these files, so refreshing a fixture to a newer documented example is safe as long as the shape
is preserved.
