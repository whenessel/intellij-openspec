# AI Configuration

OpenSpec can generate artifacts using AI providers. This page covers setup and usage.

## Supported Providers

| Provider | Models | API Endpoint |
|----------|--------|-------------|
| **Claude** (Anthropic) | claude-sonnet-4-20250514, claude-haiku-4-20250414, claude-opus-4-20250514 | `https://api.anthropic.com/v1/messages` |
| **OpenAI** | gpt-4o, gpt-4o-mini, gpt-4-turbo | `https://api.openai.com/v1/chat/completions` |
| **Gemini** (Google) | Editable model preference | `https://generativelanguage.googleapis.com` |
| **None** | — | Clipboard/Editor modes only |

## Setup

Open **Settings → Tools → OpenSpec → AI generation**. Select **REST API (API billed)** for Claude, OpenAI, Gemini or OpenRouter and configure the provider's key/model. PasswordSafe owns REST credential storage. **Test (API billed)** sends a live REST request.

Select **Installed Codex CLI** to use your existing CLI login. Enter a native executable or PATH command, refresh status/models without inference, choose an account catalog entry or enter a manual model ID, and Apply. Blank uses the CLI default. The current adapter accepts Codex 0.160.0 on Linux/macOS with a verified restricted profile; unsupported versions, Windows, configured MCP/hooks/plugins or incompatible custom context fail visibly without REST fallback. Codex owns login/refresh; run `codex login` externally. The plugin never reads or copies auth files. ChatGPT inference is online and subject to subscription limits; CLI API key mode requires explicit billing opt-in.

REST and Codex preferences are independent. Existing installations retain REST until Codex is selected. No OpenRouter provider or autonomous workspace-writing Apply is implemented in this phase. Codex generation/Verify use one-shot requests. Explore resumes CLI-owned history in a fresh process per turn under a verifiable ChatGPT workspace/account identity and unchanged context/model/security scope. New conversation discards plugin reuse without deleting CLI history; presentation-only Clear does not erase conversation history. API key mode supports acknowledged one-shot generation/Verify but is blocked for persistent Explore.

## Delivery Modes

| Mode | Description |
|------|-------------|
| **Clipboard** | Copies a prompt for manual use in your chosen AI tool; no integrated inference. |
| **Editor Tab** | Opens a prompt for manual review/use; no integrated inference. |
| **Integrated generation** | Sends reviewed context to the selected REST or Codex backend, then previews validated artifact results. |

Explicit manual preferences take precedence for generation, Explore and Verify. Manual Fast-Forward scaffolds a change and delivers its first ready artifact prompt rather than calling a saved backend. Continue either generates the next selected-DAG artifact through integrated delivery or hands its prompt to Clipboard/Editor.

## How Generation Works

The orchestration service resolves the artifact DAG and dependency context. Integrated execution uses `AiExecutionService`, which presents an editable destination/context review and applies known-pattern secret redaction. The effective payload limit is the smaller of the configured limit and 48 KiB UTF-8. Oversized context fails before sending; review additional sensitive data yourself.

Codex receives reviewed prompt text through stdin with an empty execution-environment list, verified before inference. Native filesystem/execution tools, writes and extra context expansion are disabled; the named read profile is an additional check, not a kernel sandbox guarantee. Artifact responses contain versioned concrete create/replace operations. The common writer validates scope and paths, rejects symlinks/traversal/duplicates/stale targets, previews the batch and writes approved edits through the IDE. Explore/Verify return text. Cancel/timeout prevent result application.

Full build, Plugin Verifier and IDE acceptance are currently pending because SDK downloads are blocked (HTTP 403); fixture/mock tests are not live Codex inference evidence.

## AI Tool Detection

At startup, `AiToolDetectionService` scans the project root for:

| Directory | Tool |
|-----------|------|
| `.claude/` | Claude Code |
| `.github/copilot/` | GitHub Copilot |
| `.cursor/` | Cursor |
| `.windsurf/` | Windsurf |
| `.cline/` | Cline |

Detected tools are shown in the tool window status bar, helping users know which AI assistants are available in the project.

## Troubleshooting

- **Generation is unavailable** → Check delivery mode, workflow profile and selected backend status; REST requires a key, Codex requires supported CLI/configuration/login.
- **API errors** → Check the Console tab for HTTP status codes and error messages
- **Rate limiting** → The plugin does not retry; wait and try again
- **Wrong model** → Verify the model ID matches your API plan's available models

---

**Previous:** [[Menu-and-Actions-Reference]] | **Next:** [[Validation]]

## OpenRouter

Select REST → OpenRouter in Settings → Tools → OpenSpec. PasswordSafe stores its key separately; the editable model ID is retained independently of other providers. Refresh models makes a public catalog request without credentials or inference. Test sends a live request to the selected model. See [the OpenRouter guide](../../../docs/openrouter.md) for privacy, billing, cancellation and acceptance details.
