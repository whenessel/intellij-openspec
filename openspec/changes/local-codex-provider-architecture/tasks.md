## 1. P1 — Execution contracts and migration (after implementation authorization)

- [ ] 1.1 Define immutable requests, discriminated results/events, capability states, cancellation and deadlines; verify unit tests reject incompatible required capabilities and complete terminal state exactly once.
- [ ] 1.2 Introduce shared routing and readiness snapshots; verify precedence tests for run override, explicit manual preference, legacy REST, detected-tool suggestion and unavailable backend with zero fallback calls.
- [ ] 1.3 Adapt existing Claude/OpenAI/Gemini transport behind backend contracts while retaining PasswordSafe identities and request semantics; verify captured existing provider fixtures and header/token-limit regression tests.
- [ ] 1.4 Version settings migration for provider-scoped models/backend/delivery while preserving legacy rollback fields and independent OpenSpec CLI settings; verify old persisted-state fixtures including NONE, blank, invalid and clipboard-with-saved-key cases.
- [ ] 1.5 Register new project I/O services and disposal consistently with repository conventions; verify service lookup/disposal tests and concrete API availability against IDE 2024.2 before selecting new APIs.

## 2. P2 — Context privacy and safe results (depends on P1)

- [ ] 2.1 Build selected-change context manifests with explicit inclusion of other changes/source; verify filesystem fixture tests preserve required instruction order and exclude unselected changes.
- [ ] 2.2 Add byte/file/token budgets, exclusions and redaction with essential-content overflow errors; verify boundary tests for large files, unknown tokenizer/model bounds, secrets, binary/ignored files and visible omissions.
- [ ] 2.3 Implement shared context preview and acceptance bound to action/backend/model/account/security/content; verify UI/controller tests invalidate approval on expanded scope or changed billing destination and do not send before review.
- [ ] 2.4 Define result envelope v1 for concrete create/replace/patch operations with allowed root/pattern and base versions; verify parser fixtures reject malformed/unknown envelopes and two-domain specs result passes without treating specs/**/*.md as a filename.
- [x] 2.5 Implement full-batch path/scope validation; verify adversarial fixtures for ../, absolute/UNC/drive paths, separators/streams/NUL, wildcard concrete names, symlink escapes, case collisions, duplicates and targets outside resolved store roots.
- [ ] 2.6 Add base-hash/document-conflict checks and diff preview; verify edits after preview, unsaved documents, forbidden deletion and changed parent symlinks block writes and require re-review.
- [ ] 2.7 Replace both orchestration and workflow-panel artifact writers with one recoverable undoable safe writer; verify multi-file acceptance, rejection/no writes, injected mid-batch failure/recovery and VFS/document refresh behavior in IDE tests.
- [ ] 2.8 Prevent partial/canceled/timed-out output from reaching application and serialize concurrent target mutations; verify deterministic cancellation/commit-race tests and duplicate-run isolation.

## 3. P3 — Codex compatibility and subprocess transport (depends on P1–P2)

- [x] 3.1 Capture sanitized real no-inference version/help/app-server generated schema, initialization/account/model events for candidate CLI versions in isolated directories; verify provenance/version manifest and select a supported minimum/range from actual fixtures rather than hand-invented contracts.
- [ ] 3.2 Define supported OS read-scope/tool restrictions from verified CLI schemas/config controls; verify no-inference permission captures and document unsupported profiles that must be blocked; do not read/copy auth files.
- [ ] 3.3 Implement validated executable resolution and argument-array/stdin process launch with controlled environment; verify mock executable records exact args/stdin for spaces, Unicode, metacharacters, missing paths and unsupported wrappers without shell evaluation or secret logging.
- [ ] 3.4 Implement concurrent bounded stdout/stderr readers and bidirectional JSON framing/correlation; verify mock subprocess partial lines, interleaved IDs, notifications, server requests, EOF, huge stderr, oversized/malformed frames and process crash.
- [ ] 3.5 Implement initialize/initialized and typed thread/start/resume and turn/start events using captured schema codecs; verify captured valid fixtures plus mock event ordering/duplicate/unknown optional events and incompatible required-field errors.
- [ ] 3.6 Implement terminal-status assembly and incremental response updates; verify failed/interrupted terminal states, delta-before-terminal, missing completion, zero exit without completion and wrong-thread/turn isolation.
- [ ] 3.7 Implement interruption/deadline/fake-clock lifecycle and project/executable-change cleanup; verify delayed completion after cancel, no interrupt ACK, hung descendant, stream closure and project disposal with no file writes.
- [ ] 3.8 Enforce reviewed read-only context/tool policy for MVP and deny unsupported approvals/escalation; verify malicious tool requests and unsupported permission-profile fixtures cannot broaden access or create workspace files.
- [ ] 3.9 Add auth/account/rate-limit status without plugin credential ownership; verify managed ChatGPT, API-key, signed-out, unknown/new mode and unavailable-limit fixtures show truthful billing/status and invalidate stale sessions.
- [x] 3.10 Add provider/account-scoped catalog pagination/cache/refresh/manual model ID/effort; verify default selection, expired cache, offline discovery, absent models, account changes and unknown capabilities without silent model replacement.
- [ ] 3.11 Implement visible scoped Explore thread reuse, New conversation and presentation-only Clear; verify resume after changed project/account/model/security context is rejected and new conversation excludes late old deltas.

## 4. P4 — Settings and every workflow entry point (depends on P1–P3)

- [ ] 4.1 Extend existing Settings → Tools → OpenSpec with backend, distinct Codex executable, status, catalog/override, budget and timeout controls; verify labeled keyboard-accessible UI, reset/apply semantics and async probing without paid Test Connection calls for Codex.
- [ ] 4.2 Distinguish integrated Codex from existing clipboard Codex tool selection and display route/model/auth/billing/context links; verify migrated detected-tool preference remains manual and action update() performs no I/O.
- [ ] 4.3 Route chip generation/regeneration and Continue through shared backend/context/result contracts; verify mock Codex and REST selections, multi-file previews and declined results leave target files unchanged.
- [ ] 4.4 Route automated Generate All/FF through capability gates and full required artifact closure, retaining skipped/conditional status and progress; verify non-default DAG fixtures, skip_specs, glob outputs, cancellation preserving accepted artifacts and no optimistic completion.
- [ ] 4.5 Preserve explicit manual FF change creation and first-ready-artifact handoff with accurate labels; verify clipboard/editor mode does not execute a backend or claim all artifacts generated.
- [ ] 4.6 Replace Explore tab and lazy-creation Direct API gates with readiness/capabilities and shared inline/menu routing; verify manual inline mode never calls saved REST and tab/results persist when switching delivery.
- [ ] 4.7 Add streaming Explore progress/Stop/error/timeout recovery with safe rendered output; verify UI updates/focus, failed rendering inputs, session reset and no external content fetch from provider markup.
- [ ] 4.8 Route optional semantic Verify and all Archive preflight paths through shared routing, keeping deterministic checks/schema gates and existing archive states; verify clipboard with configured REST issues zero AI calls and unavailable/canceled semantics is not a false pass or hard validation block.
- [ ] 4.9 Keep MVP Apply as reviewed manual prompt handoff and expose later autonomous capability as unavailable; verify every Apply entry point starts no Codex workspace-write turn.
- [ ] 4.10 Update guidance/status feedback for concrete accepted multi-file outputs and sanitized errors; verify no success/save claim for invalid/unaccepted/partial results or literal glob paths.

## 5. P5 — Integration tests, documentation and release gates (depends on P4)

- [ ] 5.1 Add a fixture-backed mock subprocess integration suite and provider HTTP fixtures with captured real contract provenance; verify test isolation, no live paid inference/secrets/network dependency, and negative tests fail when handshake/routing/path checks are broken.
- [ ] 5.2 Add IDE tests for project lifecycle, document/VFS conflicts, EDT updates, undo/recovery and stale-result suppression; verify the fixture-backed suite runs with the supported test harness.
- [ ] 5.3 Add a compact mock-backed uiSmoke journey: settings → context review → streamed cancellation → two-file result preview/save; verify journey screenshots/assertions on supported sandbox IDE and retain existing manual/release gating.
- [ ] 5.4 Document manual acceptance in a disposable lifecycle-testdrive project: executable with spaces/missing executable, auth/billing modes, catalog refresh/override, Generate/Continue/FF, two-turn Explore/Stop/New conversation, Clipboard Verify zero calls, denial/timeout/project-close cleanup and unavailable integrated Apply; verify an operator records each outcome on supported OS/IDE versions without paid inference or secret capture.
- [ ] 5.5 Update README, docs/feature-reference.md, docs/feature-comparison-matrix.md, docs/marketplace-page.md, docs/openspec-support.md as applicable, and relevant internal scripts/docs/wiki surfaces; verify documented routing/privacy/subscription/model/version/manual FF behavior matches the implementation and no tracker/private infrastructure leaks.
- [ ] 5.6 Run ./gradlew build for tests/coverage, ./gradlew verifyPlugin for platform compatibility and planned uiSmoke/manual gates; verify exit status and evidence are recorded per implementation SHA, separately reporting unavailable checks and visible CI runs without inferred green status.
- [ ] 5.7 Confirm local/open-source distribution eligibility and record any separate commercial/hosted SIWC assessment before release; verify a reviewed decision record exists and no new auth grant/token-copy mechanism has entered implementation.
- [ ] 5.8 Resolve authorized tracker association and review topology using available custom skill/ignored sidecar; verify no IDs in published artifacts and explicit local checks are required because current GitHub-remote hook classification skips test/verifier gates; do not change hook/CI as part of this change without separate scope.

## 6. P6 — Future OpenRouter adapter (separate implementation authorization; depends on P1–P2)

- [ ] 6.1 Implement HTTPS chat/SSE transport with PasswordSafe Bearer key, fixed endpoint and redirect/key-leak protection; verify recorded HTTP request/response fixtures and header/redaction tests without live requests.
- [ ] 6.2 Add /models catalog and optional /key status with decimal-string pricing/units/timestamps and provider-qualified model IDs; verify absent/zero price, request/cache/image units, context/output bounds and manual-ID fixtures.
- [ ] 6.3 Add route-level capability requirements and explicit provider order/allow/fallback/privacy/ZDR policy; verify unsupported required parameters or no eligible route fail without weakening privacy or changing plugin backend.
- [ ] 6.4 Normalize HTTP/body/SSE errors, credit/rate limits and Retry-After with bounded cancellation-aware retry; verify 400/401/402/403/408/429/5xx, HTTP-200 error, disconnect/truncation and no duplicate accepted output.
- [ ] 6.5 Expose router/downstream billing/privacy/actual-provider detail through shared UX and run adapter-conformance workflows; verify workflow code needs no provider-specific branching and existing mock Generate/Explore/Verify tests work with the new adapter.

## 7. P7 — Future workspace-writing Apply (separate authorization; depends on P4–P5)

- [ ] 7.1 Finalize trusted-workspace, isolated execution and command/network/write policy with platform expert review; verify approved permission design and supported-OS sandbox matrix before enabling autonomous Apply.
- [ ] 7.2 Implement explicit Apply context/run-permission preview and typed server approval handling; verify command/path/scope IDs, approve/deny, unknown requests, approval timeout, project close and cancellation never silently escalate.
- [ ] 7.3 Execute in disposable worktree/isolated scope and review final patches through shared conflict-safe application; verify external effects are disclosed, denied writes are absent, stale-base patches are blocked and original workspace remains reviewable before acceptance.
- [ ] 7.4 Add Apply acceptance/docs explaining scope grants rather than per-file guarantees; verify mock/UI/manual denial, cancellation, conflict and recovery journeys before any release enablement.
