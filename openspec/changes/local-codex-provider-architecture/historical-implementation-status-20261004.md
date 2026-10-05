# Historical implementation checkpoint — 2026-10-04

This preserves an earlier execution record. SDK blockage and pending checks here do not describe the current environment; see [current status](implementation-status.md).


The following snapshot is retained for provenance. Its SDK-blocked results, 10/51 task count and future-only OpenRouter statements describe that earlier execution, not the current branch. Use the dated evidence above for current completed checks.

### Original implementation verification status — 2026-10-04

Branch: `plan/local-codex-provider-architecture`. Planning baseline: `27adb08d7ce197e998bfb897ecce6d7d49a2a609`; planning commit: `7e2b4ca4c044c6a1a23fa66f74bff57ce5c3c3f9`. Previously published implementation checkpoints: `4036029`, `9d5fa93`, `7ecb3f6`, `16480cb`, `31d0b07`. Implementation and publication to the user's fork were subsequently authorized. This document describes the next reviewed checkpoint; **SDK-dependent acceptance and release are incomplete**.

No PR, merge, upstream push, dependency/SDK/CI/hook or network-permission change, credential copy, auth-file read or paid inference was performed. Full task criteria determine checkboxes; source presence alone does not close platform-dependent acceptance.

## Stages, dependencies and acceptance

| Stage | Dependencies | Delivered | Acceptance / remaining evidence |
| --- | --- | --- | --- |
| Contracts and shared routing | P1 | Immutable capability-aware requests; discriminated text/artifact results and delta/completed/cancelled/failed events; serialized terminal dispatch; captured per-run route overrides with manual precedence and unavailable-backend rejection | Pure negotiation/event/routing tests pass; actual IDE service lookup, persisted settings and all action bindings still need SDK tests. |
| Reviewed context | P1–P2 | Selected-change manifest; ordered essential instructions/template/dependencies; explicit additional changes/source; hashes and opaque resolved-root identity; bounded reads, exclusions, redaction, byte/file/conservative token budgets and visible omissions | Pure filesystem/adversarial tests pass. The explicit file picker and editable review bind the final payload/destination; their SDK/UI acceptance remains open. |
| Safe artifact results | P1–P2 | Versioned create/replace/patch envelope, exact snapshot hashes and UTF-16 old-text edits, strict JSON and full-batch concrete path checks; shared preview and recoverable undoable writer | Pure patch/parser/path tests pass. No fuzzy patching or deletion. IDE document conflicts, VFS, recovery, undo and cancellation/commit races require SDK execution. |
| Installed Codex | P1–P3 | Typed schema-backed handshake/thread/turn/account/rate/usage messages; validated argv/stdin launch and controlled environment; bounded framing/readers/correlation; cancellation/fake-clock deadlines and cleanup; truthful auth/billing and model catalog | Pure mock subprocess and actual no-inference captures pass. CLI 0.160.0 is pinned. Linux no-inference probe succeeded previously; kernel profile and supported OS runtime acceptance remain pending. |
| Persistent Explore | Shared routing/context and Codex | Scoped CLI-owned thread resume, New/Clear semantics; topic-only follow-up reuse while binding all reviewed source including later additions; correlated history usage admission with output/safety reserves; unknown/inconsistent history requires New | Pure resume/scope/usage fixtures pass. Actual IDE controls, successful live disk resume, UI progress/focus and project disposal remain unverified. No paid turn was run. |
| Settings and all workflows | P1–P4 | Provider-scoped models/migration; executable/auth/status/catalog/budget/timeout; shared generation/Continue/FF/Explore/Verify snapshots; manual Apply; full required DAG and accepted-file completion | Production source and SDK source tests delivered. Actual IDE serialization/typecheck/runtime acceptance remains open. Clipboard Verify and explicit manual overrides have no transport path in the policy/source; SDK mock acceptance is still required. |
| UI/manual gates and docs | P4–P5 | Mock-backed Settings → context → streamed Stop/New → two-file preview/save journey; disposable-project acceptance checklist; user docs; distribution decision record | Offline Python fixture protocol checks pass. Kotlin/Driver compilation, screenshots, manual Linux/macOS checks and release-owner decision remain pending. |
| Future OpenRouter / autonomous Apply | Separate implementation authorization | Architecture/specifications only | No OpenRouter transport or workspace-writing Apply implementation. Artifact patch support above belongs to generation and is delivered in the MVP scope. |

## Safety and compatibility decisions

- Official Codex **0.160.0** and experimental generated schemas are pinned. Linux was captured/probed locally. macOS runtime acceptance remains pending; Windows and other versions are rejected visibly.
- `thread/start` selects `environments: []`; start/resume responses must confirm it. Unsupported tool/approval/escalation requests are denied. A restricted process-scoped profile is additional protection; the host kernel sandbox canary was blocked by socket-directory restrictions and is not claimed verified. User security files are unchanged.
- Authentication and refresh stay in the CLI. ChatGPT inference is online and subject to plan limits. Acknowledged API-key mode supports one-shot generation/Verify; persistent Explore requires verifiable CLI-reported ChatGPT workspace identity. No implicit refresh during readiness checks, hidden REST fallback or new auth grant occurs.
- Context review uses a default 12,000 conservative input-token cap, one UTF-8 byte per token when tokenizer bounds are unknown, and a 48 KiB effective byte ceiling. This is admission policy, not an exact tokenizer count or context-window guarantee. CLI cumulative correlated usage, reasoning and next-prompt budget must fit before continuation; absent/inconsistent statistics require New.
- Explore history is CLI-owned. New discards plugin reuse, not CLI history; Clear changes presentation only. Failure/cancellation, changed routing/account/project/source/model/security/budget or incompatible usage requires New. Typed topic boundaries permit only reviewed topic changes to reuse a thread.
- Patch offsets/hashes bind exact raw disk bases. Logical document contents use canonical LF; normal IDE save preserves a consistent existing separator. Mixed/lone-CR bases or output, stale/redacted bases, invalid Unicode, unsorted/overlapping edits and mismatching old text reject the full batch. This is not a raw byte-for-byte output guarantee.
- Full-batch preview precedes one bounded EDT command with recovery copies. This is recoverable application, not atomic filesystem or per-file model approval. Autonomous source Apply remains a separate phase.

## Executed validation

| Check | Outcome and limits |
| --- | --- |
| Actual Java 21 compiler + standalone JUnit | **221/221 passed** in the final integrated pure suite. Existing cached Gson/CommonMark/JUnit; no platform stubs, live inference or SDK dependencies. Does not typecheck IntelliJ adapters/services. |
| Independent mutation audit | Disabling routing override, patch old-text guard, file inclusion, typed topic normalization/suffix binding, unknown-notification handling, strict JSON framing, shared event dispatch lock or fake-clock deadline causes targeted assertion failures. One intentionally hung deadline-mutant run was terminated and checked with the finite fake-clock case; baseline suites were unaffected. |
| Real no-inference CLI captures | Official version/help/generated schema/handshake/account/catalog provenance; earlier isolated signed-out adapter probe, 8 models / 1 default; no `turn/start`, existing auth files or paid calls. Synthetic inference/usage/resume and HTTP fixtures are labeled as such. |
| Captured fixture manifest | 21 SHA-256 entries verified against captured/generated files. |
| Strict OpenSpec / format | Strict CLI validation: valid, zero issues. Syntax-only parse: 99 Java files. Relative Markdown links: 38 files / 67 links. XML/JSON/Python syntax and `git diff --check` pass. |
| UI mock scripts | Python syntax and deterministic fixture handshake/model/turn/interrupt/OpenSpec status protocol checks pass; not a Kotlin compile, Driver UI run or screenshot result. |
| Gradle build / buildPlugin / verifyPlugin / uiSmoke | Earlier real attempts each exited 1 before SDK compilation/tests: envoy proxy CONNECT **403 before TLS**. No usable target SDK was cached. Access-denied downloads were not retried or bypassed. No coverage or platform/API compatibility claim. |
| CI / push hook | Visible exact prior implementation SHA `31d0b07` had `workflow_runs: []`. Existing build workflow triggers main push / PR-to-main, without dispatch. The hook classifies GitHub remotes as mirrors and skips tests/verifier; push success is not test evidence. No CI/hook change. |

Reproduction/evidence in this execution workspace: `/tmp/intellij-mvp-final-command.txt`, `/tmp/intellij-mvp-final-pure.log`, `/tmp/openspec-pure-reports/`, `/tmp/openspec-mvp-audit/`, `/tmp/intellij-mvp-format-check.py`, `/tmp/intellij-mvp-format-check.log`, `/tmp/intellij-mvp-ui-mock-check.py`, `/tmp/intellij-mvp-source-sha256.json`, `/tmp/intellij-gradle-{build,buildPlugin,verifyPlugin,uiSmoke}.log`. These are execution evidence, not plugin distribution artifacts.

## Remaining mandatory acceptance and decisions

The independently implementable protocol, routing, context, patch and test-source gaps from the prior bounded audit are now addressed. Remaining SDK criteria are actual type/API compilation, IDE persistence/service/disposal checks, VFS/document conflicts and recovery, all workflow/UI integration, Kotlin/Driver smoke execution, coverage/build/Plugin Verifier and operator-supported OS/runtime acceptance. Source and pure tests do not substitute for them.

The [manual acceptance checklist](../../../docs/local-codex-acceptance.md) records every operator outcome as pending. [SDK setup research](sdk-setup.md) confirms the existing Gradle connection and documents permitted provisioning options; no SDK/dependency/network change is made. A permitted environment with the matching SDK and required artifacts is needed to run these gates.

[Distribution decision](distribution-decision.md) records the bounded local/open-source design; release-owner eligibility review and any separate commercial/hosted SIWC assessment remain open. Authorized tracker association/custom skills and CI/review topology remain unresolved; no public tracker IDs or private infrastructure references are introduced. These are operator/release decisions, not missing pure implementation or authorization to create a PR/change CI.

OpenRouter and workspace-writing Apply remain their planned future phases. This checkpoint does not declare the MVP accepted or ready for release.

OpenSpec progress: **10/51 complete**. Only pure criteria 1.1, 2.2, 2.4 and 3.3–3.6 were closed in this continuation; 2.5, 3.1 and 3.10 were already complete. Remaining checkboxes retain their full SDK/operator/release/future-phase criteria. Final independent source/test audit passed; the shared-dispatch concurrency mutant is detected by the bounded callback test.

## Subsequent OpenRouter implementation

This file records the earlier Codex MVP checkpoint. OpenRouter REST generation and catalog support were subsequently implemented in [add-openrouter-provider](../add-openrouter-provider/proposal.md); see that change’s validation for current build, tests and real HTTP acceptance. Its nonstreaming REST contract does not mark the original future SSE, advanced provider routing-policy or autonomous Apply tasks complete.
