# Local Codex acceptance record

> **Maintenance: Living** — retain evidence per implementation SHA; source and mock checks do not replace IDE/runtime acceptance.

Use a disposable lifecycle-testdrive project and mock transports. No live paid inference or existing authentication-file capture is part of this checklist. Record IDE/build, OS/architecture, Java, installed CLI version, implementation SHA, outcome and sanitized evidence for each row. **All operator outcomes below are pending** until recorded in a permitted SDK/runtime environment.

The self-contained UI journey uses mock OpenSpec and Codex executables with spaces in their paths, captured no-inference metadata and explicitly synthetic account/turn events:

```sh
./gradlew uiSmoke --tests 'com.johnnyblabs.openspec.uismoke.LocalCodexUiSmokeTest.settingsContextCancelAndTwoFilePreviewSave'
```

Its source and offline Python protocol harness can be reviewed independently. Kotlin compilation, screenshots, real IDE behavior and actual supported CLI runtime acceptance require the SDK gates. Do not substitute a successful mock run for those outcomes.

| Case | Expected outcome | Operator outcome / evidence |
| --- | --- | --- |
| Executable path with spaces/Unicode/metacharacters | One executable and exact argument array; prompt via stdin; no shell evaluation | Pending |
| Missing executable, wrapper, unsupported version/OS | Visible compatibility error; no REST fallback/inference | Pending |
| Settings reset/apply and provider switching | Independent CLI/backend/model/effort/budget values persist; rollback fields retained | Pending |
| Signed out / managed ChatGPT / API key / unknown auth | Truthful mode and online billing; API key requires acknowledgement; unknown blocks | Pending |
| No-inference refresh, pagination, stale/default/manual model | Status/catalog only; stale metadata cannot choose default/effort; explicit ID retained | Pending |
| Changed account/executable/backend/model after review | Approval invalidated; original destination remains frozen; no request applied | Pending |
| Clipboard/Editor, explicit per-run copy, saved REST credentials | Manual delivery wins; zero backend calls | Pending |
| Selected change and explicit additional source/change files | Unselected contents excluded; provenance, redactions, exclusions and omissions visible | Pending |
| Ignored/binary/credential/symlink/oversized file | Omitted or essential-context error; no secret in diagnostics | Pending |
| Byte/file/token limit and unknown tokenizer/model bounds | Effective conservative limits disclosed; essential instructions never silently truncated | Pending |
| Generate/regenerate/Continue | Context review before inference; validated artifact preview before save | Pending |
| Generate All / FF non-default DAG and skipped artifacts | Full required closure; skipped retained; accepted status refreshed; no optimistic completion | Pending |
| Manual FF | Scaffolds change and hands off first-ready prompt; no backend/all-artifacts claim | Pending |
| Two-file specs create/replace/patch | Concrete scoped paths, full-batch preview, exact-base patch validation; no literal glob file | Pending |
| Declined/invalid/partial/cancelled result | No generated file saved; no success claim | Pending |
| Stale disk base / unsaved document / changed symlink parent | Whole batch blocked and re-review required | Pending |
| CRLF artifact and mixed/lone-CR text | Exact raw base hash/offsets; reviewed LF document projection; normal IDE save preserves supported separators; mixed/lone-CR rejected | Pending |
| Undo and injected mid-batch failure | One undoable command; honest partial-failure/recovery report | Pending |
| Two-turn Explore, topic changes and explicit added files | History scope remains stable for topic-only changes; expanded context requires New | Pending |
| Explore history budget, unknown usage, failed turn | Admission bound visible; unsafe/unknown continuation requires New | Pending |
| Stream / Stop / timeout / late completion / New / Clear | Safe incremental rendering; old deltas ignored; Stop prevents writes; Clear changes presentation only | Pending |
| Wrong-thread/turn, server approval/tool request, incompatible permissions | Fail closed; no workspace tool grant or write | Pending |
| Clipboard Verify and Archive semantic preflight | Zero AI calls; deterministic gates remain; unavailable semantics shown as not assessed | Pending |
| Project close/executable change during discovery and inference | Own process/descendants and streams cleaned; no late UI/file application | Pending |
| Every Apply entry point | Reviewed manual handoff; no autonomous workspace-writing turn | Pending |
| Linux and macOS supported native CLI profile | No-inference permission/runtime acceptance recorded separately per platform | Pending |
| Full build/coverage, verifier and UI journey | Successful exit/logs for exact SHA; visible CI checked separately | Pending |

The execution environment's earlier SDK attempts received proxy CONNECT 403 before compilation. Repeated blocked downloads and access-policy workarounds are excluded; use a permitted SDK environment. The current GitHub-remote hook skips test/verifier gates, so push success is not evidence. CI topology and release eligibility remain separate decisions.

See [setup and behavior](local-codex.md), [implementation evidence](../openspec/changes/local-codex-provider-architecture/implementation-status.md) and [distribution decision](../openspec/changes/local-codex-provider-architecture/distribution-decision.md).
