## Why

Codex is currently detected as a paste target, so users cannot run their installed Codex with an existing ChatGPT login from OpenSpec workflows. A backend-neutral execution boundary is needed now so local Codex, existing REST providers, and a later OpenRouter adapter share model selection, privacy controls, cancellation, and safe artifact application.

## What Changes

- Introduce separate backend, transport, authentication status, model catalog, capability negotiation, request lifecycle, and result-application contracts.
- Recommend installed Codex `app-server` over stdio for the generation/Continue/FF/Explore/Verify MVP; compare `exec --json` as a constrained alternative in [design.md](design.md). Reuse CLI-managed login and refresh without reading credentials or silently falling back to REST.
- Add provider/executable/model settings and visible subscription-versus-API billing state, dynamic catalogs, manual model override, migration, context preview, redaction, and budgets.
- Route every AI action through one policy, including Verify and inline Explore; retain deterministic local Verify and manual clipboard/editor delivery.
- Replace unrestricted single-string artifact writes with validated multi-file results, scoped previews, conflict checks, and one safe writer. OpenSpec output globs remain patterns rather than literal filenames.
- Reserve workspace-writing Apply, tool approvals, and OpenRouter implementation for subsequent independently gated phases. No Java/UI/provider implementation, dependency, CI, or hook edits are part of this planning delivery.

## Capabilities

### New Capabilities

- `ai-backend-execution`: backend-neutral requests, capabilities, lifecycle, compatibility, and future OpenRouter boundary.
- `local-codex`: installed CLI transport, authentication visibility, model discovery, and read-only generation.
- `ai-context-privacy`: bounded previewable context and disclosure of execution/data/billing boundaries.
- `ai-result-application`: validated structured artifact files and patches, scoped application, and the later Apply gate.

### Modified Capabilities

- `ai-integration`: backend-aware delivery resolution and configuration while retaining existing REST contracts.
- `workflow`: execution-capability-based FF availability, generation, and visible routing.
- `continue-workflow`: backend-neutral Continue with shared safe result application.
- `ff-workflow`: backend-neutral DAG generation and complete dependency closure.
- `ff-panel`: automatic execution versus manual delivery behavior.
- `explore-context`: shared routing, bounded context, and backend-neutral Explore availability/results.
- `explore-thinking-space`: inline Explore honors explicit delivery selection.
- `verify-workflow`: explicit AI routing and no hidden invocation during local/manual verification.
- `pipeline-interaction`: backend-aware generation menu and visible execution status.
- `guidance-popover`: accepted multi-file results and backend-aware delivery feedback.

## Impact

Baseline: `27adb08d7ce197e998bfb897ecce6d7d49a2a609`, also current origin/main on 2026-10-04. Future changes touch `ai/DirectApiService`, `AiProvider`, `DeliveryMode`, `services/DeliveryMethodResolver`, `ArtifactOrchestrationService`, `VerificationService`, `ExploreContextService`, action entry points, `toolwindow/WorkflowActionPanel`, Explore panels, settings, PasswordSafe integration, and project-service registration. Preserve Java 21, Gradle 9, IntelliJ Platform plugin 2.18.1 and IDEA 2024.2+ compatibility; no new platform API or runtime dependency is selected by this plan.

Tracker reference is pending: the clone lacks the custom tracker-mirroring skill and its connector. CLAUDE.md's sidecar/no-inline-ID convention takes precedence over the stale config rule asking for inline references. Do not invent an issue or create external tracker content in this documentation-only request.

[design.md](design.md) contains stages, dependencies, acceptance criteria, alternatives, sources, and release decisions. [tasks.md](tasks.md) is future implementation work and remains unchecked. Planning does not authorize implementation, commit, push, or PR.
