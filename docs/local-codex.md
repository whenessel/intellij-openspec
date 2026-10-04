# Integrated local Codex

> **Maintenance: Living** — update with changes to the installed-Codex backend and its compatibility checks.

This branch adds artifact generation, Explore and Verify through the Codex CLI installed on your machine. Inference is online. Codex handles your existing login and credential refresh; the plugin does not read or copy authentication files or create a new login grant. ChatGPT authentication uses your plan's limits. API key authentication uses API billing and requires an explicit acknowledgement in Settings.

The implementation is not a verified Marketplace release. SDK download failures (HTTP 403) currently block the full plugin build and IDE checks in the execution environment. Captured protocol fixtures and mocked processes do not prove a live inference session; no paid inference calls were run.

## Compatibility

The current adapter accepts **Codex 0.160.0 on Linux or macOS**, with its supported restricted permission profile. Other versions and Windows are rejected visibly. An incompatible executable, authentication mode, configuration or permission profile stops the request without switching to a saved REST provider.

The plugin supplies process-scoped configuration overrides and verifies the effective profile. It does not edit your persisted Codex configuration. Reviewed text is supplied in the turn prompt through stdin. Codex runs with an empty execution-environment list (`environments: []`), so native filesystem and execution tools are unavailable. The adapter verifies this before inference; a named restricted read profile provides additional checks rather than a kernel sandbox guarantee. Workspace writes, automatic project instructions, skills and context expansion are disabled. Configured MCP servers, hooks, enabled plugins or custom instruction/context files that cannot be safely excluded cause a compatibility failure. The CLI remains responsible for its own authentication and online model access.

Generation and Verify use one-shot requests. Integrated Explore starts a persistent CLI-owned conversation and resumes its thread in a fresh app-server process for each turn. Reuse requires a verifiable CLI-reported ChatGPT workspace/account identity and unchanged project/context/model/security scope; a change, cancellation or failed turn requires **New conversation**. API key authentication can run acknowledged one-shot generation and Verify, but persistent Explore is blocked because it has no verifiable account identity. Broader version/OS support still requires separate compatibility checks.

## Configure

1. Install the supported native Codex executable and run `codex login` in your terminal. Confirm your intended authentication method there.
2. Open **Settings → Tools → OpenSpec → AI generation** and choose **Installed Codex CLI**.
3. Enter `codex` if it is available on the IDE process's PATH, or browse to the executable. This field accepts an executable, not a shell command or extra arguments. An absolute path is useful when the desktop IDE does not inherit your terminal's PATH.
4. Click **Refresh status and models (no inference)**. Inspect the version, authentication mode and compatibility status. Refresh reads CLI account and model information; it does not send a generation request.
5. Choose a model ID from the refreshed catalog or enter an ID manually. Blank selects the catalog's verified account default. The request pins the resolved model before context review. Availability is account-dependent; a manual override does not make an unavailable model usable.
6. Leave reasoning effort blank for the CLI default, or choose an effort advertised for the selected model. An explicit effort requires fresh verified metadata; it is never silently replaced with another effort. Changing effort requires a new Explore conversation.
7. Set the timeout and context limit as needed. Allow API billed Codex requests only if you intend to use the CLI's API key mode. Click **Apply**.

Existing settings continue to select REST unless you choose Codex. Versioned migration retains legacy rollback fields and manual delivery preferences, and remembers each REST provider's model separately. API keys and OpenSpec CLI settings stay independent of Codex settings. Selecting a detected Codex tool for clipboard delivery is a manual handoff; it does not activate this backend.

The model catalog is a bounded in-memory metadata cache scoped to executable, authentication mode, verified account fingerprint and protocol version. The TTL is five minutes, with at most sixteen account/executable entries. Refresh bypasses the TTL. When discovery fails, cached entries are visibly stale and cannot authorize a default model, explicit effort or required model capability. With a fresh verified account, a literal manual model ID and blank effort can still be submitted for CLI validation. Accounts without a verifiable fingerprint are not cached. Absent capability metadata remains unknown; required unknown or unsupported capabilities block execution without another backend being called.

## Routing, context and results

Choose integrated generation in the workflow delivery control to execute through the selected backend. An explicit **Clipboard** or **Editor Tab** choice takes precedence, including for Explore and Verify. Fast-Forward in a manual delivery mode scaffolds the change and delivers the first ready artifact prompt; it does not silently generate the remaining artifacts. Continue advances the selected artifact DAG: integrated delivery generates the next ready artifact, while Clipboard/Editor delivers its prompt for manual use.

Before integrated inference, a dialog shows the destination, billing indication, payload size and editable context. Known secret patterns are redacted, but you must review additional sensitive content yourself. The effective limit respects configured byte and input-token caps, with a maximum **48 KiB of UTF-8 text**. With an unknown tokenizer, admission uses one UTF-8 byte per token as a conservative estimate; the default input cap is 12,000. This is not an exact token count or a guarantee of a model's context window. Known context bounds subtract output and safety reserves. Oversized context fails before sending rather than being silently truncated. Dependency/context reads reject hidden credential paths and symlinks. The prompt is supplied through stdin; the Codex execution environment has no native filesystem/execution tools and does not automatically add workspace files.

Context includes an immutable manifest of origins, sizes, hashes, redactions and omissions. Other changes and source content require explicit inclusion through the context dialog; hidden, credential, ignored, binary and unsafe files remain excluded. Changing destination, billing, settings or included scope requires review again.

Artifact generation returns a versioned structured envelope containing concrete create/replace/patch file operations. A glob such as `specs/**/*.md` defines allowed destinations; it is never treated as a literal filename. The plugin validates the whole batch, shows a preview and applies approved changes through a common writer. Absolute paths, traversal, paths outside the artifact scope, symlinks, duplicate destinations and stale target snapshots are rejected. The current limits are 64 files, 32 KiB per file and 256 KiB total file content. Artifact patches require the exact captured base hash and sorted, nonoverlapping UTF-16 ranges with exact old text; no fuzzy matching or rebasing occurs. Redacted bases cannot be patched. Reviewed results are LF-normalized logical document text; normal IDE saving preserves supported existing separators. Mixed or lone-CR text is rejected. Deletes and arbitrary workspace patches are outside this scope.

Explore and Verify return text rather than applying workspace edits. Explore context review also discloses that Codex stores conversation history and may reuse prior reviewed turns. **New conversation** discards the plugin's thread reuse and stops an active turn; it does not delete history stored by the CLI. Any presentation-only Clear action only hides displayed text, not retained conversation history. Permanent history deletion is not implemented. Before a continued Explore turn, correlated CLI token-usage reports must fit the reviewed input cap together with the next prompt. Missing or inconsistent usage, a changed context window or budget, or insufficient space requires **New conversation**; visible response text is not used to guess history size. Cancellation stops the active request and prevents result application; timeout and protocol failures are visible. These safeguards do not imply an approval dialog for every model filesystem operation: tools and autonomous workspace writes are disabled in this phase, and the plugin reviews an artifact batch separately.

## Later phases and acceptance

OpenRouter has an architectural extension seam but no executable provider, key flow or hosted authorization in this phase. Autonomous workspace-writing Apply is also deferred; the existing Apply action retains its existing OpenSpec lifecycle role.

Release acceptance must include the supported CLI on Linux and macOS, both subscription and explicitly acknowledged API billing modes, unsupported-version/configuration errors, model catalog/manual override, every delivery mode, cancellation/timeout, persistent Explore thread resume and account/scope guards, New-conversation semantics, context reduction and multi-file preview/stale-edit rejection. Use fixtures and mocks for automated tests; a deliberate live inference check requires separate authorization. Full build, Plugin Verifier and IDE/UI checks must pass before claiming release readiness.

See [feature reference](feature-reference.md#ai-generation) and the [OpenSpec support matrix](openspec-support.md#integrated-ai-backends).

Manual/SDK acceptance evidence is recorded separately in the [acceptance checklist](local-codex-acceptance.md).
