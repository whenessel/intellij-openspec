# Implementation checkpoint — 2026-10-04

Branch: `plan/local-codex-provider-architecture`. Verified foundation commit: `4036029` (transport/contracts/results/captures). Planning commit: `7e2b4ca4c044c6a1a23fa66f74bff57ce5c3c3f9`, based on `27adb08d7ce197e998bfb897ecce6d7d49a2a609`.

Implementation and publication to the user's fork were subsequently authorized. No PR, merge, upstream push, dependency/CI/hook change, credential copy or paid inference was performed. This checkpoint is work in progress, **not completed MVP or release acceptance**. Task checkboxes cover their full stated verification criteria; source code alone does not complete IDE-dependent tasks.

## Stages, dependencies and acceptance

| Stage | Depends on | Delivered behavior | Evidence / remaining acceptance |
| --- | --- | --- | --- |
| Backend/contracts and Codex transport | P1 interface seam, captured CLI contract | Installed CLI argument-array/stdin execution, CLI-owned authentication, billing/account guards, model pagination, cancellation/deadline/cleanup, successful terminal output only | 41 pure subprocess/catalog tests plus 13 negotiation/cache tests; real Java adapter signed-out probe/catalog against official 0.160.0 without inference; actual handshake/schema/help/version captures with SHA manifest. |
| Reviewed context and result foundation | Backend contracts | Redaction, 48 KiB effective payload cap, dependency path checks, envelope v1 concrete create/replace, full-batch path/scope/conflict checks, safe provider Markdown, Explore scope hashing | 29 pure tests. No arbitrary patch/delete implementation. Concrete specs globs resolve to multiple validated files. |
| Settings and shared workflow integration | Both foundations | Separate Codex executable/model/effort/status/API-billing acknowledgement; versioned provider-scoped REST migration; shared generation/Continue/FF/Explore/Verify routing; manual preference precedence; unified diff preview and recoverable undoable writer | Pure migration/provider selection/application seams verified by 5 tests; SDK-backed settings/service tests added. Actual IDE serialization, compilation, service/UI behavior, VFS/undo and writer recovery tests remain blocked. |
| Persistent Explore | Shared routing and reviewed context | CLI-owned persistent thread, fresh process plus `thread/resume` per turn; account/workspace routing/model/default/context guards; New versus display-only Clear; stale UI suppression | Mock subprocess resume tests pass. Real successful disk resume requires an existing inference turn and was not run. Empty no-inference thread resume failure is captured honestly. IDE/UI tests remain blocked. |
| DAG completion and skipped status | Shared generation/result application | Full transitive required closure; authoritative `SKIPPED`; blocked/missing dependencies are errors; accepted output must actually change completion state | 20 pure ArtifactStatus tests pass; real CLI fixture-backed orchestration/contract tests added but SDK harness unavailable. |
| Release gates | All integration stages | Build, Plugin Verifier, IDE/UI journey and manual supported-platform acceptance | Not passed. Proxy CONNECT 403 blocks official JetBrains SDK downloads before compilation. No coverage floor or green CI claim. |
| OpenRouter / autonomous workspace-writing Apply | Separate implementation authorization and release gates | Architecture and specifications only; Apply remains manual handoff | No provider implementation, new auth grant, hosted flow or autonomous workspace execution in this checkpoint. |

## Current safety and compatibility choices

- Pin official Codex **0.160.0** and its generated experimental schemas. Linux was captured and probed locally; macOS is a source/schema target requiring runtime acceptance; Windows and other versions fail visibly.
- `thread/start` selects `environments: []`; responses for start/resume must confirm an empty environment list. Prompts travel through stdin. Native filesystem/execution tools cannot access a project; a restricted process-scoped named profile remains defense in depth. User security/config files are not changed. The local kernel sandbox canary was blocked by host socket-directory restrictions and is not claimed verified.
- Authentication/refresh remain in the CLI. ChatGPT usage is online and subject to plan limits. API-key mode requires acknowledgement and supports one-shot generation/Verify; persistent Explore requires CLI-reported ChatGPT workspace identity. Unknown identity/capabilities are not guessed; no hidden REST fallback occurs.
- Persistent Explore stores reviewed conversation text in CLI-owned history. New discards plugin reuse, not the CLI's persisted history. Clear only clears display. Failed/cancelled turns require New before another turn. Changes in reviewed context, project, executable, model/default, account/routing or security scope cannot silently resume an old conversation.
- Artifact target snapshots are taken before inference and checked after review. The whole batch is reviewed; writes use one bounded EDT command with explicit recovery copies on partial failure. This is not an atomic filesystem guarantee or per-file model approval promise. Source Apply/patch execution remains later work.

## Executed validation

| Check | Result |
| --- | --- |
| Actual Java 21 compiler + JUnit standalone | **150/150 passed**: Codex transport/conversation/catalog 41, negotiation/cache 13, safety/context/render/scope 29, existing provider/delivery 19, ArtifactStatus 20, existing REST protocol 23, migration 5. IntelliJ-dependent adapters/services/tests were excluded; no fake platform stubs. |
| Earlier foundation adapter probe with official CLI, isolated empty `CODEX_HOME`, API-key environment absent | signedOut, version 0.160.0; catalog 8 models / 1 default; exit 0; no `turn/start`, paid calls or existing auth files. |
| External parser audit | Successful decoder paths use captured real handshake/catalog/schema shapes. Synthetic paid-turn/resume cases are explicitly labeled. Negative tests assert the intended guard/stage so earlier unrelated failures cannot falsely satisfy them. |
| Fixture manifest SHA-256 | All 17 capture/schema/provenance file hashes match. |
| `openspec validate ... --strict --no-interactive --json` | valid=true, zero issues. |
| Java `JavacTask.parse()` | 77 changed Java files parsed successfully; parsing is not IntelliJ type/API verification. |
| `git diff --check`, XML/Markdown local format and links | plugin.xml parsed; 33 changed Markdown files / 60 local relative links checked; no formatting/link errors. |
| Gradle `build`, `buildPlugin`, `verifyPlugin`, `uiSmoke` | All actually attempted, exit 1 before SDK compilation/test execution. Official metadata/download URLs receive **envoy proxy CONNECT 403 before TLS**, so this is environment outbound policy, not an upstream license/login failure. No access-denial bypass attempted. |
| GitHub CI for planning SHA | `workflow_runs: []` for planning SHA and verified foundation SHA `40360297e0dcb5d891c8502938adb25df8762df1`. Existing build workflow triggers only push to main / PR targeting main, with no dispatch trigger. Publishing this branch alone cannot produce that workflow run; CI/hook configuration was not altered. |

Temporary supporting evidence in this execution workspace: `/tmp/intellij-pure-final.log`, `/tmp/intellij-pure-final-command.txt`, `/tmp/openspec-pure-reports/`, `/tmp/codex-actual-adapter-probe.log`, `/tmp/intellij-gradle-{build,buildPlugin,verifyPlugin,uiSmoke}.log`. These paths are execution evidence, not distributable plugin artifacts.

## Remaining work / decisions

Full completion needs an authorized environment with the supported IntelliJ SDK (or permitted access to its official endpoints) and the SDK/UI/manual gates above. The current GitHub workflow needs a separately authorized trigger/topology decision to validate this branch; creating a PR or changing CI was not authorized. The existing hook classifies every GitHub URL as a mirror and skips source/verifier gates, so its successful push is not test evidence.

Capability/catalog cache/effort negotiation and pure migration/provider protocol coverage are delivered in the independent checkpoint below. Remaining unchecked criteria include typed external protocol events and further lifecycle/adversarial cases, actual captured IDE settings serialization, SDK-backed REST/service wiring, platform concurrency and lifecycle acceptance, the mock UI smoke journey, manual Linux/macOS acceptance, distribution/auth eligibility review and tracker association. Persistent Explore implementation is present in this scope; its unexecuted live/IDE acceptance is not described as a deferred feature. OpenRouter and autonomous Apply retain their separately planned later phases.

## Independent MVP checkpoint after `7ecb3f6`

The four SDK-independent work areas were implemented and checked without live inference or network-dependent tests:

| Work area | Delivered | Verification and limits |
| --- | --- | --- |
| Capabilities and request lifecycle | Tri-state capability evidence, required-capability rejection without fallback, immutable output schema/requirements, exactly-once terminal outcomes integrated into transport | 9 pure negotiation/terminal tests plus mock transport rejection/duplicate/cancellation cases. Typed external event decoding remains a separate unchecked requirement. |
| Catalog and effort | Bounded pagination, separate catalog/wire IDs, advertised efforts/modalities; five-minute TTL, sixteen-entry cache; fresh account check before reuse, force-refresh, explicit stale suggestions, no default/effort selection from stale metadata | 4 injected-clock cache tests and captured-schema-backed transport cases. Missing workspace identity is not cached even when a fallback email fingerprint exists. Explicit manual IDs are preserved; unknown required capabilities are rejected. |
| Settings migration | Version 1 pure persisted-field projection, provider-scoped model selection/update/apply, legacy rollback and independent delivery/CLI values retained; explicit effort setting | 5 pure tests with NONE/blank/invalid/manual-with-provider specimens, idempotence/future values, provider switching and edited-draft application. XML specimens are synthetic, explicitly labeled; actual IDE serialization and UI binding are unverified. |
| Existing REST protocols | SDK-independent exact existing request/response codecs, headers and token parameters; SDK adapter now sends captured provider/model and uses captured-provider readiness | 23 pure request/documented-provider-fixture tests. Provider fixtures are existing provider-published examples, not live paid captures. Added SDK mock adapter/service tests were not run. |

The shared execution source binds the resolved wire model and explicit effort to context preview, request and Explore scope; changed settings require review again. REST no longer re-reads a different provider/model after preview. Explore installation/reset is serialized; a canceled initial conversation does not retain its temporary root. These platform bindings have source tests and static review, but no IntelliJ typecheck/runtime claim.

Final pure-suite command/source manifest: `/tmp/intellij-pure-checkpoint-command.txt`; compiler/test output: `/tmp/intellij-pure-checkpoint.log`; XML reports: `/tmp/openspec-pure-reports/`. These tests use actual Java 21, existing cached Gson/CommonMark and JUnit, with no IntelliJ stubs, real subprocess inference or secret access. Additional final checks: strict OpenSpec validation (zero issues), Java syntax parse of all 76 changed Java files, 32 Markdown files / 59 relative links, settings/plugin XML parsing, 17 fixture SHA-256 hashes and `git diff --check` all passed. Task 3.10 is complete for the pure catalog contract; task 4.1 UI acceptance and settings/service persistence criteria remain open.

The preceding failed compile exposed a missing brace in the new subprocess test; it was corrected before the successful final suite.

## Discovery cancellation checkpoint after `16480cb`

Probe and catalog now accept the request cancellation token through launch, handshake, account/rate-limit RPC, pagination, cached/stale returns and cache publication. Cancellation propagates as a typed cancellation result, never backend-unavailable or a successful catalog. Late cancellation retracts a published catalog. Session cleanup is idempotent and records a terminal outcome once; blocked stdin and interrupted callers clean owned descendants before terminating the parent, preserving the caller interrupt flag. Read/write waits observe cancellation at 50/25 ms intervals; parent cleanup waits up to 200 ms before forcing termination. These are polling/cleanup bounds, not a hard real-time guarantee.

Shared execution binds cancellation before discovery. Source tests cover Stop and executable changes without review/inference. The settings worker observes replacement/disposal cancellation; executable edits cancel the old worker. Background status discovery observes service disposal and executable changes. Those IntelliJ bindings have source/static-review evidence only; their SDK tests were not executed.

Final actual Java 21 compiler/JUnit suite: **150/150 passed**, including 41 Codex tests and 13 negotiation/cache tests. Added mock cases cover pre-launch cancellation, blocked handshake/account/rates/models/stdin, late valid replies, second-page cancellation, cached/offline/launch races, cache retraction during cleanup and interrupted blocked stdin with a child visible only before parent exit. No real inference, OS process termination, secrets or platform stubs are involved. Evidence: `/tmp/intellij-cancellation-command.txt`, `/tmp/intellij-cancellation.log`, `/tmp/intellij-cancellation-restored.log`, `/tmp/openspec-pure-reports/`. After environment reconnection, the same saved compiler/JUnit command was executed again and passed 150/150; strict OpenSpec validation, 77 Java syntax parses, 33 changed Markdown files / 60 relative links, XML parsing, 17 fixture hashes and `git diff --check` passed. Full task 3.7 remains unchecked because IDE/project lifecycle acceptance is incomplete.

## Bounded audit of mandatory remaining MVP criteria

| Category | Criteria | Acceptance still required |
| --- | --- | --- |
| Independent of SDK | 1.1 / 3.5 typed external protocol events; 1.2 explicit per-run override precedence; 2.1–2.2 context manifest and explicit inclusion policy; 3.3–3.7 / 5.1 adversarial framing/interleaving/EOF/stderr/Unicode and fake-clock tests | Implement and exercise the pure seams. In particular, an unknown optional notification with missing/null params currently reaches a null dereference; it needs typed dispatch and safe unknown-notification handling while server requests remain fail closed. Existing fixed byte caps/redaction are not the complete manifest/model-aware budget criterion. |
| Planned artifact result criterion | 2.4 create / replace / patch | Current envelope handles create/replace only. Artifact patch is distinct from autonomous workspace Apply; implement its safe contract or obtain an explicit scope decision. Do not silently mark it deferred under Apply. |
| Source possible without SDK, execution requires SDK | 5.3 new mock UI smoke journey | The Codex review/stream/multi-file journey is not yet authored. Writing it is possible independently; compilation, execution and screenshots require SDK/IDE. |
| SDK / IDE verification | 1.5 services/disposal; 2.3 preview; 2.6–2.8 VFS/documents/undo/recovery; 4.1–4.10 settings/actions/workflow/Explore/Verify; 5.2–5.3 integration/UI; 5.6 build/coverage/verifier | Actual API typecheck, persisted IDE settings captures and platform/threading/lifecycle/runtime acceptance. Pure tests and syntax parsing do not satisfy these gates. |
| Separate operator / user decision | 3.2 / 5.4 supported Linux/macOS runtime and kernel-profile acceptance; 5.7 distribution/auth eligibility; 5.8 tracker/review/CI topology | Use a permitted SDK/runtime environment and resolve release eligibility/topology. No new auth grant, permission change or CI mutation is authorized here. |

Only the bounded discovery-cancellation defect was implemented in this pass. Other findings above remain open. OpenSpec task progress remains **3/51**, reflecting complete criteria rather than source presence. OpenRouter and autonomous Apply retain their separately planned future phases. SDK/IDE/Plugin Verifier/uiSmoke gates remain blocked by the earlier proxy denial; unchanged endpoints were not retried. SDK connection research is recorded in [sdk-setup.md](sdk-setup.md). No dependency, SDK, CI, hook or network permission changes were made.
