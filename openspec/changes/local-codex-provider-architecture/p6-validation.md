# P6 OpenRouter validation — 2026-10-05

Tested implementation: `343452b8aad4cde3b81b00128c839e229773d531`, branch `plan/local-codex-provider-architecture`, parent `062f454c6c2a10681f58cbfc368b90abde689d47`. Final evidence updates change documentation only. Java 21, Gradle 9.0.0, IntelliJ Platform plugin 2.18.1 and build SDK 2024.2 retained; no test, coverage, verifier or security gate disabled.

| Stage | Result | Evidence |
| --- | --- | --- |
| Explicit `build`, tests, `check` | passed | **1761 tests, 0 failures/errors, 4 skipped** |
| JaCoCo report / coverage verification | passed | Instruction **52.28%**, line **48.64%**, branch **47.41%**; floors unchanged |
| SDK writer suite | passed | Nine `SafeArtifactServiceIdeTest` tests; actual Undo and runtime service lifecycle remain unverified |
| `buildPlugin`, structure/configuration verification | passed | ZIP below |
| `verifyPlugin` | passed | All four default recommended IDEs compatible, gates unchanged |
| `compileIntegrationTestKotlin` | passed | First dependency-backed compile 5 minutes; final aggregate compile up-to-date |
| Strict OpenSpec validation / diff formatting / secret scan | passed | No token value in tracked or untracked repository deliverables |
| Real free SSE capture | passed | `openrouter.ai/api/v1/chat/completions`, HTTP 200; `liquid/lfm-2.5-2.6b:free`, actual provider Liquid, stop/DONE, reported cost 0 |
| Real optional key-status capture | passed | `openrouter.ai/api/v1/key`, HTTP 200; sanitized fixture |
| IDE GUI `uiSmoke` / operator acceptance | not run | DISPLAY unset; no Xvfb/xvfb-run; harness compilation and mocks do not establish GUI acceptance |
| Live authenticated installed Codex / native OS profile / real Undo / service lifecycle | not run | Separate operator/release criteria; no live Codex inference performed |
| Autonomous workspace Apply | not enabled | P7 remains deferred; manual reviewed handoff retained |

## P6 results

- **6.1 passed:** fixed HTTPS/Bearer SSE; bounded UTF-8 and multiline/comment framing, stop/DONE validation, incremental deltas, cancellation/deadline and redirect protection. Positive contract uses a real capture; partial/error output cannot become an accepted result.
- **6.2 passed:** exact price/unit/time provenance, catalog context/output admission bounds, user output cap, conservative unknown context/manual IDs and optional safe key status. Unsaved Test/status keys are not persisted.
- **6.3 passed:** immutable provider only/order/fallback/data-collection/ZDR snapshot shown in context review and checked again before send. `require_parameters=true` delegates eligible-route negotiation to OpenRouter; explicitly required native schemas additionally require known catalog support. No eligible route fails without relaxation or backend fallback.
- **6.4 passed:** sanitized HTTP/body/SSE diagnostics; at most three attempts only after explicit pre-stream 429/503 rejection with valid Retry-After <=10 seconds and within the original deadline. No retry after HTTP 200, partial output or ambiguous network failure. Localhost tests cover retry/cancel/timeout/no-duplicate behavior; live retries were not induced.
- **6.5 passed (automated scope):** actual completed provider/model reaches the shared Console with operation/cancel/disposal guards. Mock Generate/safe-writer handoff, two Explore turns and Verify use the shared adapter; their GUI journeys remain unverified. No workflow-specific provider routing added.

Settings and wizard connection tests use the captured route/privacy policy and SSE with at most 256 output tokens. A positive inference smoke is limited to synthetic `Reply with exactly OK.` on the pinned free variant with zero price ceiling; no project/personal content or paid inference. Key reads use process memory/PasswordSafe; no credential in argv, logs or fixtures.

Verifier: IC-242.26775.15 Compatible; IC-243.28141.41, IC-251.29188.72 and IC-252.28539.97 Compatible with the same five pre-existing deprecated usages. New code adds no deprecated API usage.

Local ZIP: `/workspace/intellij-openspec/build/distributions/intellij-openspec-0.11.0.zip`, **1,353,223 bytes**, SHA256 `d61822a62bef13341dfb1e372cddacc62d5d3bb96bb59c0e68d6edcc3a61545a`. No Library upload, release, PR or merge.

Workspace logs: `/workspace/openrouter-p6-validation-20261005/final-checks.log`, `integration-compile.log`, `openspec-validation.json`. Initial failing attempts were corrected (HTTP mock compatibility and stale test stubs/guards); the final aggregate command passed in **1m47s**. Reports: `build/reports/tests/test`, `build/reports/jacoco/test`, `build/reports/pluginVerifier`. Captured sanitized fixtures and provenance: `src/test/resources/fixtures/ai/openrouter/`.

Normal environment proxy and supported sandbox network permission were used. SDK and integration dependencies are available in this instance; the old CONNECT403 SDK blockade is historical. Successful particular requests/cache use do not establish unrestricted internet. Headless searchable-options emitted a nonfatal background `Connection refused` diagnostic in the final log (domain not recorded there); build tasks succeeded and this was not an OpenRouter request failure.

Independent test-engineer AUDIT passed; platform review found no remaining concrete violation. No CI success is inferred from this local run or push hook; current GitHub hook classification skips test/verifier, so explicit checks above provide the evidence.
