# Implementation checkpoint — 2026-10-04

Branch: `plan/local-codex-provider-architecture`. Verified foundation commit: `4036029` (transport/contracts/results/captures). Planning commit: `7e2b4ca4c044c6a1a23fa66f74bff57ce5c3c3f9`, based on `27adb08d7ce197e998bfb897ecce6d7d49a2a609`.

Implementation and publication to the user's fork were subsequently authorized. No PR, merge, upstream push, dependency/CI/hook change, credential copy or paid inference was performed. This checkpoint is work in progress, **not completed MVP or release acceptance**. Task checkboxes cover their full stated verification criteria; source code alone does not complete IDE-dependent tasks.

## Stages, dependencies and acceptance

| Stage | Depends on | Delivered behavior | Evidence / remaining acceptance |
| --- | --- | --- | --- |
| Backend/contracts and Codex transport | P1 interface seam, captured CLI contract | Installed CLI argument-array/stdin execution, CLI-owned authentication, billing/account guards, model pagination, cancellation/deadline/cleanup, successful terminal output only | 21 pure subprocess tests; real Java adapter signed-out probe/catalog against official 0.160.0 without inference; actual handshake/schema/help/version captures with SHA manifest. |
| Reviewed context and result foundation | Backend contracts | Redaction, 48 KiB effective payload cap, dependency path checks, envelope v1 concrete create/replace, full-batch path/scope/conflict checks, safe provider Markdown, Explore scope hashing | 28 pure tests. No arbitrary patch/delete implementation. Concrete specs globs resolve to multiple validated files. |
| Settings and shared workflow integration | Both foundations | Separate Codex executable/model/status/API-billing acknowledgement; REST migration defaults; shared generation/Continue/FF/Explore/Verify routing; manual preference precedence; unified diff preview and recoverable undoable writer | Code and SDK-backed tests added; actual IDE compilation, service/UI behavior, VFS/undo and writer recovery tests remain blocked. |
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
| Actual Java 21 compiler + JUnit standalone | **88/88 passed**: Codex transport/conversation 21, safety/context/render/scope 28, existing provider/delivery 19, ArtifactStatus 20. IntelliJ-dependent adapters/services/tests were excluded; no fake platform stubs. |
| Real adapter probe with official CLI, isolated empty `CODEX_HOME`, API-key environment absent | signedOut, version 0.160.0; catalog 8 models / 1 default; exit 0; no `turn/start`, paid calls or existing auth files. |
| External parser audit | Successful decoder paths use captured real handshake/catalog/schema shapes. Synthetic paid-turn/resume cases are explicitly labeled. Negative tests assert the intended guard/stage so earlier unrelated failures cannot falsely satisfy them. |
| Fixture manifest SHA-256 | All 17 capture/schema/provenance file hashes match. |
| `openspec validate ... --strict --no-interactive --json` | valid=true, zero issues. |
| Java `JavacTask.parse()` | 61 changed Java files parsed successfully; parsing is not IntelliJ type/API verification. |
| `git diff --check`, XML/Markdown local format and links | plugin.xml parsed; 15 changed Markdown files / 55 local relative links checked; no formatting/link errors. |
| Gradle `build`, `buildPlugin`, `verifyPlugin`, `uiSmoke` | All actually attempted, exit 1 before SDK compilation/test execution. Official metadata/download URLs receive **envoy proxy CONNECT 403 before TLS**, so this is environment outbound policy, not an upstream license/login failure. No access-denial bypass attempted. |
| GitHub CI for planning SHA | `workflow_runs: []` for planning SHA and verified foundation SHA `40360297e0dcb5d891c8502938adb25df8762df1`. Existing build workflow triggers only push to main / PR targeting main, with no dispatch trigger. Publishing this branch alone cannot produce that workflow run; CI/hook configuration was not altered. |

Temporary supporting evidence in this execution workspace: `/tmp/intellij-pure-final.log`, `/tmp/intellij-pure-final-command.txt`, `/tmp/openspec-pure-reports/`, `/tmp/codex-actual-adapter-probe.log`, `/tmp/intellij-gradle-{build,buildPlugin,verifyPlugin,uiSmoke}.log`. These paths are execution evidence, not distributable plugin artifacts.

## Remaining work / decisions

Full completion needs an authorized environment with the supported IntelliJ SDK (or permitted access to its official endpoints) and the SDK/UI/manual gates above. The current GitHub workflow needs a separately authorized trigger/topology decision to validate this branch; creating a PR or changing CI was not authorized. The existing hook classifies every GitHub URL as a mirror and skips source/verifier gates, so its successful push is not test evidence.

Remaining unchecked tasks include richer capability/catalog cache/effort negotiation, comprehensive old-state migration/provider transport fixtures, schema/platform concurrency and lifecycle acceptance, the mock UI smoke journey, manual Linux/macOS acceptance, distribution/auth eligibility review and tracker association. Persistent Explore implementation is present in this scope; its unexecuted live/IDE acceptance is not described as a deferred feature. OpenRouter and autonomous Apply retain their separately planned later phases.
