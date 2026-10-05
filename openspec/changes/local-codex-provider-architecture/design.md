## Context

See [proposal.md](proposal.md) for motivation and capability inventory. This preserves the original design decisions. Implementation was subsequently authorized; completed baseline evidence and outstanding combined acceptance criteria are tracked in [implementation-status.md](implementation-status.md) and [tasks.md](tasks.md).

### Verified baseline and discovery limits

The clean checkout began on `work` at `27adb08d7ce197e998bfb897ecce6d7d49a2a609`; `git ls-remote` confirmed origin/main at that SHA and no remote branch-name collision. The planning branch was created from that main-equivalent HEAD without inventing local main or changing remotes. Only origin (GitHub) is configured; repository conventions describe a different upstream review/mirror topology. No push/PR is authorized.

| Observed source at this HEAD | Consequence for design |
| --- | --- |
| `ai/DirectApiService.java:68,94` returns String; final class switches CLAUDE/OPENAI/GEMINI REST | Add composition-based adapters and typed results; extending this class or adding one more switch arm is insufficient. |
| `services/DeliveryMethodResolver.java:25` and `toolwindow/WorkflowActionPanel.java:563` resolve separately; named tools become clipboard targets | Separate manual delivery from executable backend selection; detection of `.codex` is not proof of CLI availability/authentication. |
| `services/VerificationService.java:217–227` calls saved DirectApiService directly | Route optional semantic verification consistently, including Archive preflight; manual mode must not cause an implicit paid call. |
| `services/ExploreContextService.java` reads full active-change artifacts | Introduce selected scope, explicit omissions/redactions, and bounded snapshots before sending. These are static findings, not runtime measurements. |
| `services/ArtifactOrchestrationService.java:344` and `toolwindow/WorkflowActionPanel.java:1688` independently write String to outputPath | Replace both writers; outputPath may be a pattern and provider text is untrusted. |
| Workflow panel comments acknowledge blocking HTTP is not interruptible | Cancellation must reach transport and prevent late result application, rather than merely update UI. |
| `OpenSpecSettings.State` stores aiProvider, aiModel, preferredDeliveryMethod, preferredTool, cliPath and cliTimeoutSeconds | Migrate old provider/model/preference explicitly; keep OpenSpec CLI settings separate from Codex executable/timeouts. |
| Java 21 / Gradle 9.0.0 / platform plugin 2.18.1 / SDK 2024.2 | Retain 242+ compatibility and existing project-service registration conventions. |

No AGENTS.md or `.agents/skills` exists in the project or readable workspace skill directory. Read CLAUDE.md, `.claude/skills/openspec-propose/SKILL.md`, relevant main specs, and project expert instructions. The OpenSpec expert file contains stale version claims; current CLI/fixtures/docs govern instead. Custom tracker-mirroring skill is absent. Tracker association remains pending outside this documentation-only delivery; IDs belong only in the ignored sidecar. No auth file was inspected.

### Source review (2026-10-04)

Official pages were opened during planning. These establish external contracts, not a tested installed Codex version:

- [Codex app-server](https://learn.chatgpt.com/docs/app-server): stdio handshake, threads/turns, streaming terminal events, interrupt, account/model/limits surfaces and version-specific generated schemas. Its [authentication section](https://learn.chatgpt.com/docs/app-server#authentication) permits continued local/open-source use and excludes commercial/hosted use of app-server authentication; evaluate SIWC separately before changing distribution scope.
- [Codex authentication](https://learn.chatgpt.com/docs/auth): saved CLI login may be ChatGPT or API-key based; API mode uses Platform billing. The plugin delegates credential persistence and refresh to Codex.
- [Non-interactive mode](https://learn.chatgpt.com/docs/non-interactive-mode): `exec --json` supplies JSONL events, supports schema-shaped final output and explicit session resume, and reuses CLI auth. It is an alternative adapter, not the app-server protocol.
- [OpenRouter authentication](https://openrouter.ai/docs/api_reference/authentication) and [API overview](https://openrouter.ai/docs/api_reference/overview): HTTPS API with Bearer key; chat completions and optional streaming are the future initial transport.
- [Model catalog](https://openrouter.ai/docs/api/api-reference/models/list-all-models-and-their-properties): identifiers, context/output bounds, modalities, supported parameters and decimal-string pricing. Catalog facts are time-dependent.
- [Provider routing](https://openrouter.ai/docs/guides/routing/provider-selection): routing constraints and `require_parameters`; parameter support must be checked rather than assumed from OpenAI-shaped requests.
- [Structured outputs](https://openrouter.ai/docs/guides/features/structured-outputs): model/provider support varies; schema conformance still needs client validation.
- [Data collection](https://openrouter.ai/docs/guides/privacy/data-collection) and [provider logging](https://openrouter.ai/docs/guides/privacy/provider-logging): router retention and downstream retention/training policies differ.
- [Errors](https://openrouter.ai/docs/api_reference/errors-and-debugging), [limits](https://openrouter.ai/docs/api_reference/limits), and [streaming](https://openrouter.ai/docs/api_reference/streaming): handle body/stream failures as well as HTTP status, rate limits and credit exhaustion.

## Goals / Non-Goals

**Goals:** one observable routing decision, interchangeable adapters, bounded data disclosure, user-owned installed Codex and login, validated artifact results, and future OpenRouter without rebuilding workflows.

**Non-Goals:** new OAuth/token grant, copied ChatGPT credentials, bundled CLI, server-hosted subscription proxy, implementation in this branch, and autonomous workspace-writing Apply in MVP. No promise of offline inference, unlimited subscription usage, or guaranteed dollar cost. Codex subprocess is local; inference remains online unless a separately supported local-model configuration is explicitly chosen.

## Decisions

### 1. Separate the execution contracts

Proposed names are design vocabulary, not a selected public Java API:

| Boundary | Responsibility |
| --- | --- |
| `AiExecutionRouter` | Resolve action + persisted preference + explicit per-run override into manual delivery or backend execution; return reason/availability, never hidden provider fallback. |
| `AiBackend` | Run immutable `AiRequest` and emit typed events/terminal `AiResult`; composition of capabilities, auth status and catalog. |
| `BackendCapabilities` | Known/supported/unsupported/unknown for text, streaming, conversations, structured files, patch generation, interruption, tool approvals, workspace write and modalities; intersection with model, transport, policy and installed version. |
| `ProcessTransport` / `HttpTransport` | Framing, request correlation, stream reading, cancellation and cleanup; no prompt/workflow or file-write policy. |
| `AuthStatus` | Mode, masked account/plan where provided, auth-required/error state and billing category; never credential material in execution metadata. |
| `ModelCatalog` | Provider-scoped stable IDs, labels, optional effort/modality/bounds/pricing, timestamp/source and refresh status. |
| `ContextSnapshot` | Reviewed bytes/content manifest, exclusions, budgets, hashes and disclosure policy. |
| `ArtifactResultValidator` / `SafeArtifactWriter` | Validate intended paths/content/patches and apply a reviewed batch; adapters do not write generated artifacts. |

AiRequest identifies action (artifact/Explore/Verify/later Apply), project and resolved planning home, change/artifact, model selection, context snapshot, permissions, output contract, deadline and cancellation. AiResult is a discriminated union: text, structured files, validated patch proposal, semantic findings, or terminal error/cancel; metadata contains backend/model/usage and provenance without secrets. Partial deltas are display-only, never write-ready. Existing REST remains behind adapters; DirectApiService can be a temporary compatibility facade with no new consumers. No subclassing final services; no provider enums switch in workflow code.

All new I/O services remain project-scoped and registered consistently in plugin.xml during implementation. Providers, catalogs and sessions are plugin configuration/runtime state, never new OpenSpec lifecycle states or spec coverage fields. Resolve actual planning home/changeRoot/actionContext from CLI; do not hardcode repo-local paths or add credentials/settings to `.openspec.yaml`.

### 2. Preferred transport and honest alternatives

| Choice | Initial implementation cost | Generation/Explore/Verify | Future Apply and lifecycle | Decision |
| --- | --- | --- | --- | --- |
| Codex app-server stdio | Higher: bidirectional protocol, event routing, server requests, compatibility fixtures | Native ongoing sessions, streaming, account/model status | Approval requests and turn interruption fit interactive IDE | Recommended MVP transport; invest once in boundary. |
| Codex exec --json | Lower for one-shot process adapter; still requires framing, timeouts, final schema validation and safe writer | Suitable restricted single-shot MVP; resume exists, UX/account discovery needs extra CLI queries | Not equivalent to interactive server approval and event control; later adapter work required | Optional explicitly scoped alternative if schedule requires; do not silently auto-switch. |
| Direct REST to OpenAI using extracted ChatGPT tokens | Superficially simple, wrong credential ownership and billing contract | Does not deliver installed CLI semantics | Authentication and policy risk | Rejected. |
| Add CODEX/OPENROUTER arms to current service | Low immediate cost | Preserves inconsistent callers, cancellation and String output | Workflows repeatedly redesigned | Rejected. |
| SDK bridge/remote server | Additional runtime/distribution/network management | Possible but unnecessary for installed executable | Broadens deployment/auth surface | Deferred. |

These are relative engineering costs, not numerical estimates or subscription prices. App-server is selected in this plan; exec requires a recorded scope amendment and its reduced capability matrix before implementation. Existing clipboard/editor delivery remains useful but is not an automatic substitute after an executed request fails.

### 3. Codex process and session lifecycle

Use executable path and argument array, with prompt and JSON on stdin. Never shell interpolation, command-string execution, or prompts/tokens in argv. Resolve explicit absolute executable or background PATH discovery, show resolved path/version before use, reject invalid/untrusted launcher paths and unsupported wrappers. Windows quoting, spaces, Unicode, process descendants and actual executable formats require supported-OS acceptance. Never execute an executable supplied by project content without user selection. Preserve CLI environment needed for normal login while removing unintended secret injection; do not print the environment or alter the user's CLI config/login.

Protocol mapping: `initialize` → `initialized`; `thread/start` or explicit `thread/resume`; `turn/start`; accumulate `item/agentMessage/delta`; finalize only on successful `turn/completed`; cancel via `turn/interrupt`. Probe `account/read`, `model/list`, and `account/rateLimits/read` when supported. [App-server reference](https://learn.chatgpt.com/docs/app-server).

Own one lazily started connection per project/backend configuration; serialize turns per thread and cap project-wide concurrency (MVP one active turn, queue visible). JSON-RPC request IDs, thread IDs, turn IDs and plugin run IDs are distinct. Readers continuously drain stdout and bounded stderr concurrently; preserve request/response/server-request distinction and partial-line framing. Unknown noncritical notifications can be skipped; malformed/oversized messages, unknown required server requests or missing required fields fail visibly. Do not concatenate all stdout as model text or treat exit zero/interrupt ACK as completed output.

New generation/Verify requests get fresh threads by default to avoid hidden carry-over. Explore conversation can reuse an explicitly visible project-local thread; New Explore clears association. Clear removes presentation/input only and does not silently erase the CLI conversation; expose New conversation separately. Resume requires same project/planning home/backend/account/model/security context and explicit user intent; reject stale IDs after account/config changes and never use global `--last`. Rebuild approved context per turn, show history contributes to budget, and reset if history cannot be safely bounded. Persist only necessary opaque associations locally, never in published OpenSpec files; expose session clearing and disclose CLI-side history retention separately.

Proposed configurable defaults: process/handshake/catalog 10 seconds; generation/Explore/Verify 180 seconds total; 60 seconds without protocol activity; interrupt grace 3 seconds then terminate, 2 seconds later force-kill descendants. Approval waits use a visible separate deadline in the later phase. Tune with fake clocks and manual OS runs, not paid performance benchmarks. Closed project/reconfigured executable disposes connection, completes pending futures once, closes streams and kills owned descendants; never kill unrelated user Codex processes. Canceled/deadline-expired run IDs cannot apply late responses. Preserve already accepted artifacts during canceled FF and report remaining work.

### 4. Authentication, model catalogs, compatibility and UX

Settings → Tools → OpenSpec is the durable home: execution backend (`REST`, `LOCAL_CODEX`; OpenRouter is a REST provider), manual delivery (`Clipboard`, `Editor Tab`), executable browse/detect/version, effective auth/account/billing, model picker/refresh/manual ID, optional effort, context limits and timeouts. Keep OpenSpec executable distinct. Workflow tool selector gains an explicit Local Codex execution option alongside `Codex [CLI]` paste guidance; discovering `.codex` alone must not enable execution. Show selected backend/model with concise auth/billing detail and View Context/Cancel in existing Workflow/Explore surfaces, with detail on demand rather than a new tool window. Use textual statuses, labeled controls, keyboard access and predictable focus; color alone is insufficient.

Codex owns login and refresh. MVP offers status check and instructions to run `codex login` outside the plugin, without logging in/out automatically. Never read/copy `auth.json`, OS credential entries or tokens, and do not implement external-token refresh or a new SIWC grant. Distinguish ChatGPT plan usage, API billing, signed out, unsupported/unknown auth and any non-goal auth modes returned by newer CLI. API mode requires visible acknowledgement when first choosing Local Codex expecting subscription; do not reject intentional API use or label unknown mode as subscription. Account change invalidates catalog/session caches and reviewed billing state. Limits are advisory and may be absent; show unknown, avoid automatic credit purchases/emails/reset redemption. Local/open-source versus commercial/hosted eligibility is a release gate, with SIWC feasibility evaluated independently. [Auth contract](https://learn.chatgpt.com/docs/app-server#authentication), [CLI billing](https://learn.chatgpt.com/docs/auth).

Catalogs are lazy/background, cached per backend/account/config (proposed TTL 15 minutes), paginated, with explicit refresh and timestamp/stale labels. Codex uses returned default; do not freeze a model ID from docs. Existing REST defaults and exact user model IDs remain intact in migration. Manual override is permitted but marked unverified; unknown capability cannot satisfy workspace-write or strict structured-output requirements. If model disappears, retain its ID and prompt re-selection; never silently choose a different paid model. Subscription catalog is not the OpenAI Platform catalog. Catalog failure does not erase selection; execution surfaces precise validation/provider errors.

Record tested CLI version range plus schema fingerprint in compatibility fixtures; generate app-server JSON schema from each supported installed version in isolated temporary output, not automatically on every action. Preserve protocol variants in adapter codecs. Feature detection and required-field validation supplement version range; fail closed on incompatible required methods/security controls. Preserve unknown harmless optional fields. Do not use OpenSpec VersionSupport for Codex protocol versions. Track separately settings-format version, result-envelope version, OpenSpec CLI/schema version, backend protocol/CLI version and HTTP contracts. Minimum Codex version must be selected from a real captured compatibility matrix before shipping, not guessed here. Unsupported CLI shows actionable version/path guidance and manual delivery option only through explicit selection.

### 5. Unified routing and context disclosure

One router governs chip generation/regeneration, Generate All, Continue, FF, menu/inline Explore, Verify, semantic Archive preflight and later Apply. Selection precedence: explicit run override → saved manual/backend preference → legacy configured REST only when no explicit preference → detected tool paste suggestion → generic clipboard. Never override an explicit clipboard/editor choice because REST credentials exist. Readiness/capability snapshots make action update() fast; status probes run asynchronously.

Automated FF/Generate All require ready artifact-generation capability and safe structured results; no DirectApiService.isConfigured gate. Manual FF input can create a change and deliver only its first ready artifact, matching the existing ff-panel scenario; label it manual FF and do not imply full automatic generation. Continue/manual chip generation retain prompt delivery. Automated FF closes transitively over applyRequires and requires edges, observes skipped/conditional artifacts and refreshes actual status after accepted writes. Existing glob-valued *dependency* prompt handling is preserved; multi-file output is a separate contract. OpenSpec 1.12 strict delta validation requires existing scenario identities to be retained; historical Direct API scenario headings therefore remain while their normative bodies explicitly cover backend execution/manual routing.

Explore menu and inline input obey the same choice; manual inline submit copies/opens prompt with guidance and does not fabricate an AI response. Keep/reuse the Explore tab when switching mode, and do not discard existing results. Verify always runs deterministic schema/completeness/tasks checks locally; optional AI semantics uses resolved execution only after context review. Manual mode reports semantic assessment not performed and offers explicit prompt delivery; it never invokes saved REST. Unavailable/failed AI is suggestion/not-assessed, not hard validation BLOCK. Keep existing READY/IN PROGRESS/BLOCKED meanings, single Verify surface and schema-specific modes.

Context review shows action, backend/model/auth/billing category, destination(s), included files/snippets, sizes, estimated tokens, redactions and exclusions before first send and any changed scope. Default selected change only; other active changes, workspace source and full artifacts require explicit inclusion. Proposed limits: 64 files, 32 KiB per file, 256 KiB total, estimated input 12k tokens capped further by known model context less output reserve and safety margin. For unknown tokenizer/bounds label estimates and use conservative byte caps; never silently truncate required instructions. Require scope reduction on essential-content overflow; optional omissions are visible.

Exclude credential/config secret files, VCS internals, ignored/binary/generated content by default; configurable allowlist and secret-pattern redaction are defense in depth, not guarantees. A secret exclusion override requires a visible review; never include the CLI's own credentials. Bound filesystem traversal and snapshot consistent contents/hashes off EDT. REST sees reviewed payload; Codex can also inspect permitted local data/tools/config. For MVP restrict Codex reads to a temporary reviewed context root using supported sandbox controls, disable project-config/MCP/plugin tool additions where enforceable, and deny escalation/write requests. If effective permissions cannot guarantee reviewed scope on a CLI/OS, block that backend profile instead of claiming preview exhaustiveness; offer explicit scope choice only after a separate permission design. Do not confuse tool network restriction with inference connectivity. No raw prompts, outputs, credentials or stderr in normal logs/telemetry; opt-in diagnostics are sanitized and bounded.

### 6. Structured results and the safe writer

Result envelope v1 carries `schemaVersion`, artifact ID, and file operations (`relativePath`, create/replace or patch, content/base hash); request supplies resolved root, allowed output pattern, limits and expected base hashes. Providers that cannot produce safe structured output can serve text Explore or findings Verify, but are not advertised as automatic multi-file artifact generation. Single-file legacy text can be wrapped only when the instruction denotes exactly one concrete allowed file. No prose/code-fence guessing for glob results, no splitting by model headings.

Validate all operations before any write: schema/version, UTF-8, count/bytes, unique normalized destinations, legal operation, requested artifact scope and resolved planning roots. Reject absolute/drive/UNC paths, traversal, NUL, path wildcard characters in concrete destinations, alternate separators/streams, case collisions, symlink-parent escape and non-regular targets. Resolve existing parents against canonical approved roots, account for absent parents, and recheck containment/hash/unsaved document state immediately before applying. External store planning roots must be explicitly displayed and permitted, not inferred from workspace sandbox access. Glob pattern `specs/**/*.md` constrains permitted concrete files such as `specs/ai-integration/spec.md`; never create a file named with a glob.

Show all-file diff preview including additions, replacements and removals (removals restricted to separately approved Apply); require accepting the artifact batch, not approve each generated token. Bind acceptance to content digest/path/base snapshot; edits/conflicts invalidate it. Apply through one shared writer using IDE undoable write commands and VFS/document synchronization, with heavy preparation off EDT and bounded mutation on EDT. Preflight entire batch; stage recoverable content/backups and report partial failure with recovery paths. Do not claim cross-filesystem atomicity; cancellation before commit writes nothing, cancellation during bounded commit finishes/reports the batch consistently. Successful accepted writes refresh DAG based on disk/CLI, not optimistic response completion.

MVP Codex is read-only/no escalation, returns artifacts to plugin writer, Explore/Verify write no workspace files. Artifact acceptance is separate from AI execution permission. Workspace-writing Apply is later: project trust check, implementation prompt/context preview, explicit permission/run grant, disposable worktree or isolated workspace first, capability-gated commands/patch approvals, final diff review and conflict-safe merge/application. Approval requests use typed IDs, action/path/command/scope and response validation; unknown or unsupported approval is denied/canceled. Reject/timeout means no escalation. A workspace-write sandbox grants a scope; it does not guarantee an approval prompt for every file or command. Preview cannot undo a command's external side effect, so tool execution needs a distinct reviewed policy. Preserve existing manual Apply prompt delivery in MVP; REST generation never implies autonomous Apply capability.

### 7. OpenRouter adapter (separately authorized P6 implementation)

Use `https://openrouter.ai/api/v1/chat/completions`, Bearer key in PasswordSafe, `GET /api/v1/models` catalog and optional `GET /api/v1/key` status. No dependency on Codex auth; no redirect of ChatGPT subscription entitlement. Pin public HTTPS endpoint initially and prevent key leakage to arbitrary bases/redirect hosts. Headers and optional app attribution follow official docs, not copied provider defaults. [API](https://openrouter.ai/docs/api_reference/overview), [auth](https://openrouter.ai/docs/api_reference/authentication), [limits](https://openrouter.ai/docs/api_reference/limits).

Model descriptors preserve provider-qualified IDs and decimal-string pricing with currency/unit/timestamp; render per-million-token estimates using decimal arithmetic, keep missing costs unknown, zero distinct from missing, and account for request/image/cache/other units separately. Context/output bounds and supported parameters come from metadata; model support alone does not establish route-level tools/schema support. [Catalog](https://openrouter.ai/docs/api/api-reference/models/list-all-models-and-their-properties).

Proposed user routing policy: explicit model, optional provider allow/order, `allow_fallbacks` visible, `require_parameters=true` for required schema/tools, data-collection/ZDR constraints when selected. Never relax privacy/capability requirements on retry or automatically switch the plugin backend. Provider fallback inside an explicitly selected OpenRouter policy is different from backend fallback; show actual provider where available. No route meeting constraints produces an actionable error. [Routing](https://openrouter.ai/docs/guides/routing/provider-selection), [structured output](https://openrouter.ai/docs/guides/features/structured-outputs).

Show router and possible downstream recipients and their retention/training policies in data review; do not claim universal no retention from router policy. [Router privacy](https://openrouter.ai/docs/guides/privacy/data-collection), [provider policy](https://openrouter.ai/docs/guides/privacy/provider-logging).

Normalize 400/401/402/403/408/429/5xx, no-content/truncation, SSE errors even after HTTP 200, and disconnect into shared terminal results. Honor Retry-After and cancellation; bounded retry only where no accepted/generated result or side effect can be duplicated, no blind retry of completed paid inference, auth or exhausted-credit errors. Report possible incurred usage after canceled calls, not guaranteed refunds. [Errors](https://openrouter.ai/docs/api_reference/errors-and-debugging), [streaming](https://openrouter.ai/docs/api_reference/streaming).

## Stages, Dependencies and Acceptance Criteria

| Stage | Dependencies | Deliverable | Acceptance criteria |
| --- | --- | --- | --- |
| P0 Planning review | Current HEAD, official docs | This proposal/design/deltas/tasks | Strict OpenSpec validation; only planning files changed; source facts separated from proposals. |
| P1 Contracts + existing REST | P0 approved for implementation | Router/capabilities/results; REST adapters; migration | Every action resolves same preferences; existing provider contracts/keys/models preserved; Clipboard Verify sends zero requests. |
| P2 Safe context + artifact application | P1 | Snapshot review/budget/redaction; structured result validator/writer | Malicious paths/globs/symlinks/conflicts rejected before writes; multi-domain specs preview/apply; late canceled results cannot write. |
| P3 Codex transport + settings | P1–P2; captured supported-version matrix | App-server lifecycle, auth/model/limits UX | Mock event contract passes; status checks do not generate; real CLI auth remains CLI-owned; unsupported sandbox/schema/version blocks visibly. |
| P4 Generation/Explore/Verify MVP | P1–P3 | All entry points, DAG and response UI | Same chosen backend across menu/panel/Continue/FF/Archive semantic preflight; generation artifacts only; Explore/Verify no writes; deterministic Verify retained. |
| P5 Release validation/docs | P4; local/open-source eligibility confirmed | Regression checks, UI journeys, manual supported-OS evidence | Build/coverage + verifier + planned uiSmoke/manual checks recorded by SHA; no live paid calls/secrets in automated tests; no inferred green CI. |
| P6 OpenRouter implementation | P1–P2, separate authorization; P4 reference tests | HTTP adapter/catalog/privacy/routing | Swap adapter without workflow edits; model/provider capabilities negotiated; error/pricing/privacy fixtures pass. |
| P7 Workspace-writing Apply | P4–P5; separate permission design/authorization | Isolated execution, typed approvals and final diff review | Untrusted projects blocked; denials/timeouts honored; no promise of per-file approval; conflict-safe controlled apply. |

P6 is authorized, implemented and automated criteria passed: SSE, routing/privacy controls, key status, bounds and retries share the existing backend boundary. [P6 validation](p6-validation.md) records full checks and the free synthetic capture; GUI/operator criteria remain open. P7 remains a separate future phase. OpenRouter does not gain agent/workspace capabilities.

## Risks / Trade-offs

- [Protocol/security controls evolve] → fixture/schema matrix, capability probing, fail closed on required controls; select tested minimum before release.
- [CLI config/tools may expose more than prompt preview] → enforce restricted context and tool policy or block profile; do not rely on read-only meaning limited reads.
- [Subscription exhaustion/API charges/unknown limits] → visible effective auth/billing, no hidden REST fallback, no automatic paid status-test prompt.
- [Generated file traversal/overwrite or stale results] → common validator/writer, snapshot hashes, all-file preview and adversarial tests.
- [Different platforms sandbox/process cleanup behavior] → mock tests plus supported-OS manual verification and platform verifier; record limitations honestly.
- [Settings migration accidentally reroutes manual users] → preserve explicit manual choice and require opt-in Codex; reversible versioned migration.
- [Hook classifies any github.com remote as mirror and skips test/verifier gates] → require explicit local checks for future implementation and separately resolve review topology; leave hook untouched here.
- [Prior CI had no visible runs] → no green claim; future release checks require observed run/check evidence for the relevant SHA. No CI mutation in planning.
- [Inherited main-spec inconsistencies on compliance/icon surfaces] → modify only routing-relevant blocks, preserve unrelated behavior and flag debt rather than silently broadening scope.
- [Tracker mirror skill unavailable] → mark pending association; no fabricated IDs, private infrastructure text, or external tracker mutation.

## Migration Plan

Version plugin settings independently. Map aiProvider/model to matching REST backend and provider-scoped model without changing PasswordSafe identity; map DIRECT_API to execute-selected-REST, preserve CLIPBOARD/EDITOR_TAB and preferredTool, and retain legacy fields during rollback window. NONE/blank stays manual; invalid values get visible recovery, not a new paid default. Codex detection never auto-migrates a paste user to execution. New Codex path, timeout and context policy use separate keys; OpenSpec CLI settings remain unchanged.

Deploy feature-gated contract/router first, then writer/context, then Codex and workflow wiring. Before enabling automated generation require safe output capability. Existing provider keys remain only in PasswordSafe; Codex stores its own auth. Disable new execution feature to rollback routing to retained legacy configuration; never logout Codex or delete user sessions to rollback. Already accepted artifacts remain ordinary files, undo/recovery available. Document intentional Verify/Explore explicit-routing changes and manual FF semantics.

## Release Decisions to Confirm

The architectural default is app-server MVP with read-only generation/Explore/Verify, no automatic workspace Apply and future OpenRouter. Subsequent implementation authorization permits current P6 work, but does not close the remaining release gates:

1. Confirm local/open-source distribution eligibility; any commercial/hosted evolution requires separate SIWC and terms review, without reusing this auth design unchanged.
2. Accept app-server engineering investment or explicitly re-scope to limited exec MVP and revise capabilities/tasks before implementation.
3. Select minimum tested Codex version and supported OS/sandbox profiles from real schema/status/security captures; current online documentation is insufficient proof.
4. Tune conservative context/timeouts/catalog TTL from UX acceptance; preserve review and security guarantees when tuning.
5. Associate an authorized tracker issue through the repository's ignored sidecar when its custom skill is available; no inline IDs.
