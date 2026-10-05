# OpenRouter

> **Maintenance: Living** — updated with provider behavior and acceptance evidence.

The setup wizard also offers OpenRouter with an editable model ID; catalog refresh is available in Settings.

Select **Settings → Tools → OpenSpec → REST API → REST provider: OpenRouter**. Enter your OpenRouter API key and click Apply. The key is stored in IntelliJ PasswordSafe, separately from OpenAI and other providers; it is never saved in project settings. OpenRouter uses its own account and credits, independently of Codex login or a ChatGPT subscription.

Click **Refresh models (no inference)** to load the public text-model catalog. Refresh uses no API key and sends no project content. The model field remains editable: paste a provider-qualified ID such as `vendor/model`, or a free variant ending in `:free`. Refresh preserves your selected or typed ID. Hover the selected model or a catalog row to see exact decimal-string USD prices with their units, catalog source and refresh timestamp. Missing prices remain unknown. The default `openrouter/free` routes to an available free model; availability and free-tier rate limits vary. Confirm current prices on OpenRouter before selecting another model. **Test (API billed)** sends a short live request using the selected model and key; testing an unsaved key does not save it.

Generation, Continue, Fast-Forward, Explore and Verify use the same reviewed-context boundary and artifact preview/application as other REST providers. Clipboard and Editor delivery retain their precedence. Explore is a one-shot REST operation with the plugin-supplied context; it does not acquire Codex-owned persistent conversation history. Autonomous workspace Apply and native tool execution are not part of this provider.

Context review identifies both OpenRouter and its downstream model provider as recipients. Their retention and training policies apply; no universal zero-retention claim is made. Requests use a fixed HTTPS endpoint, with the key only in the Authorization header. There is no automatic switch to Codex or another plugin provider. OpenRouter's own downstream routing is controlled by the selected model and account policy.

Requests are nonstreaming, with a 4096-token output limit (256 for connection tests) and a five-minute request timeout. OpenRouter's normalized `max_tokens` field is used for all qualified model IDs. Responses must contain completed assistant text; truncation, blocked/refused responses, embedded errors and malformed output fail without partial application. Artifact schemas are enforced locally; the plugin does not assume every model supports native structured output. Cancel stops waiting and cancels pending HTTP transport; cancellation cannot guarantee refund of usage already processed by OpenRouter.

Errors identify the status and next action without displaying raw provider bodies: 401 key, 402 credits/spending limit, 403 permissions/privacy restrictions, 404 model availability, 429 rate limits and 5xx provider availability. Catalog failures leave manual model entry available.

Official contracts: [Quickstart](https://openrouter.ai/docs/quickstart), [model catalog](https://openrouter.ai/docs/api/api-reference/models/list-all-models-and-their-properties), [free variants](https://openrouter.ai/docs/guides/routing/model-variants/free).

Acceptance evidence is recorded in `openspec/changes/add-openrouter-provider/validation.md`. Automated mock/platform tests, real HTTP smoke and IDE UI end-to-end are reported separately.
