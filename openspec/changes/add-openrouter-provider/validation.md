# OpenRouter validation

Validated on 2026-10-05 in the cloud workspace, starting from `b6f2f8b79c70d099e269ec6cc00995362f11220f` on `plan/local-codex-provider-architecture`. Java 21, Gradle 9.0.0, IntelliJ Platform Gradle plugin 2.18.1, build SDK 2024.2. No coverage threshold, verifier failure level or security gate was reduced.

| Stage | Result | Evidence |
|---|---|---|
| Compilation | passed | Full Gradle validation |
| Unit/mock/platform suite | passed | 1716 tests, 0 failures/errors, 4 skipped; includes existing Codex/other REST regressions |
| check / JaCoCo report / coverage verification | passed | Instruction 51.29%, line 47.69%, branch 46.53%; floors unchanged |
| buildPlugin | passed | Local ZIP below; integrity checked |
| verifyPluginStructure / verifyPluginProjectConfiguration | passed | Full Gradle validation |
| verifyPlugin | passed | Four default recommended IDEs, no excluded checks |
| OpenSpec strict validation | passed | `openspec validate add-openrouter-provider --strict --json` |
| Real public catalog HTTP | passed | `openrouter.ai/api/v1/models`, HTTP 200, 466 catalog models |
| Real authenticated completion HTTP | passed | `liquid/lfm-2.5-2.6b:free`, synthetic "Reply with exactly OK.", 256 tokens, HTTP 200, finish stop, nonempty text, reported cost 0; production Java request/response codec also passed |
| Truncated real completion | safely rejected by contract tests | Same free model at 32 tokens: HTTP 200, finish length, null content, reported cost 0 |
| IDE UI end-to-end | not run | DISPLAY unset and no Xvfb/xvfb-run; Swing callback/controller tests and Plugin Verifier do not establish UI acceptance |
| Repository secret scan | passed | Added environment token was not found in tracked/untracked deliverable files; value never printed or embedded in argv |

Verifier matrix:

| IDE | Build | Verdict |
|---|---|---|
| 2024.2.6 | IC-242.26775.15 | Compatible |
| 2024.3.7.1 | IC-243.28141.41 | Compatible; 5 pre-existing deprecated usages |
| 2025.1.7.2 | IC-251.29188.72 | Compatible; 5 pre-existing deprecated usages |
| 2025.2.6.3 | IC-252.28539.97 | Compatible; 5 pre-existing deprecated usages |

The deprecated-usage counts match the prior branch validation. Configured compatibility/internal/override-only failure gates and the scheduled-for-removal canary remained enabled.

ZIP: `/workspace/intellij-openspec/build/distributions/intellij-openspec-0.11.0.zip`, 1,328,847 bytes, SHA256 `cfde245dc8291d48a9606b85d7d829116c99cd11af4055486a80d2307cfb795a`. It is local only, with no Library upload or release publication.

Logs and result files are under `/workspace/openrouter-validation-20261005/`: `validation-final.log`, `http-smoke.log`, `openspec-validation.json`, and final `result.json`. Test and verifier reports are under `build/reports/`. Captured sanitized contract fixtures are committed under `src/test/resources/fixtures/ai/openrouter/`.

Network access used the normal environment proxy and supported sandbox network permission. Successful OpenRouter requests and cached JetBrains verifier SDKs do not prove unrestricted internet. The headless searchable-options IDE emitted a nonfatal background `Connection refused` diagnostic while its task still completed; this was not an OpenRouter inference failure.

Acceptance limits: real HTTP smoke sent no project/personal content and used only a free model. It does not establish generation from a running IDE, PasswordSafe behavior on every OS or GUI workflow acceptance. The adapter exposes nonstreaming text REST, local artifact validation and cancelable HTTP; advanced downstream policy controls, SSE streaming and autonomous workspace Apply remain outside this change, as recorded separately in the original architecture plan.

Implementation commit `c3ea9b671b4219b879afaf92aa620e99057a472c` was pushed to `whenessel/intellij-openspec`, branch `plan/local-codex-provider-architecture`. Publication used the normal pre-push hook; no bypass flag, PR, main merge, tag or release was created. The follow-up acceptance commit changes documentation only.
