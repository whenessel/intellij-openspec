# Validation evidence — 2026-10-05

Current completed baseline is `062f454c6c2a10681f58cbfc368b90abde689d47`. Compilation, 1716 tests (four skipped, zero failures/errors), `check`, JaCoCo, local ZIP build and four-version Plugin Verifier passed. Nine SDK-backed artifact writer tests passed; actual GUI/Undo/service lifecycle and live installed Codex generation remain unverified. The baseline OpenRouter HTTP smoke used a free model and synthetic text, with reported cost zero; it does not validate subsequent P6 streaming/policy changes. Current P6 source `343452b8aad4cde3b81b00128c839e229773d531` additionally passed explicit `build`, 1761 tests, check/coverage/buildPlugin/four-version verifier and integration Kotlin compilation; see [P6 validation](p6-validation.md).

See [implementation-status.md](implementation-status.md) for stage-specific evidence and limits. P6 automated validation passed and tasks 6.1–6.5 are checked; GUI/manual/release criteria remain open. The earlier SDK CONNECT403 failure below is historical, not a current compilation blocker. No network bypass was used.

## Historical planning-only snapshot

The following record preserves the original planning execution. Claims of untracked-only files, no implementation/publication and no SDK tests apply solely to that checkpoint.

### Planning validation — 2026-10-04

This records the original planning-only checkpoint before later implementation/publication authorization. For current implementation evidence and blockers, see [implementation-status.md](implementation-status.md).

Branch: `plan/local-codex-provider-architecture`.
Planning baseline HEAD: `27adb08d7ce197e998bfb897ecce6d7d49a2a609`.

## Executed checks

| Check | Observed result |
| --- | --- |
| `git status --short` before editing | Clean checkout on `work`. |
| `git branch -a`, `git worktree list`, `git ls-remote --heads origin main plan/local-codex-provider-architecture` | No local/worktree/remote branch collision; origin/main equals baseline HEAD. Repeated remote check after planning still matched. |
| Repository instruction/skill discovery | CLAUDE.md and relevant `.claude/skills`/expert files read; AGENTS.md, project `.agents/skills`, and custom tracker mirroring skill absent. |
| Source/spec inspection | Static code findings at current HEAD informed design; no runtime behavior is claimed. |
| Official Codex/OpenRouter web documentation review | Relevant pages opened, references recorded in design. Some old OpenRouter URLs failed and were replaced with canonical accessible pages; Markdown download via shell returned 403, so browser-readable official pages were used. External reference availability is session evidence, not a permanent link guarantee. |
| Isolated CLI installation | OpenSpec 1.12.0 installed under `/tmp/intellij-openspec-plan-tools`; npm cache also under `/tmp` after default cache-path failure. Repository dependencies/lockfiles untouched. |
| `openspec context --json` | Root resolved to this repo; no store override. |
| `openspec new change local-codex-provider-architecture` | Standard spec-driven metadata created. |
| `openspec instructions proposal/specs/design/tasks --change local-codex-provider-architecture --json` | Actual schema templates/rules and artifact paths read in dependency order. |
| `openspec status --change local-codex-provider-architecture --json` | proposal/specs/design/tasks all done; `isPlanningComplete: true`. Status is artifact-existence evidence, not implementation completion. |
| `openspec validate local-codex-provider-architecture --strict --no-interactive --json` | Final result valid=true, zero issues. Initial validation detected omitted historical scenario identities; identities restored while bodies changed to intended backend-neutral behavior. |
| `openspec show local-codex-provider-architecture --json` | Change parses, 38 requirement deltas exposed. |
| Python planning checks (`/tmp/check-openspec-plan.py`) | Capability inventory matches all 14 delta files; 142 scenarios have WHEN/THEN; 51 future tasks have IDs/verification and remain unchecked; Markdown links have valid syntax and local targets exist; no whitespace/conflict-marker/private-identifier or out-of-scope changes found. |
| `git diff --check` and per-file `git diff --no-index --check /dev/null <file>` | No whitespace errors, including new untracked planning files. |
| Project-expert review | OpenSpec and UI specialists consulted under CLAUDE.md conventions. Manual FF, soft Verify incomplete-work semantics and Explore readiness gates aligned after review. |

CLI commands above use `/tmp/intellij-openspec-plan-tools/node_modules/.bin/openspec`; subsequent validation commands set `OPENSPEC_TELEMETRY=0`. Temporary check scripts/reference outputs are supporting workspace tooling, not repository implementation.

## Deliverables

All repository changes are untracked documentation in `openspec/changes/local-codex-provider-architecture/`:

- `.openspec.yaml`
- `proposal.md`
- `design.md` (architecture, source review, alternatives, phases/dependencies/acceptance criteria, migration and release decisions)
- `tasks.md` (51 unchecked future tasks, including separate OpenRouter/Apply phases)
- `validation.md` (this evidence record)
- `specs/ai-backend-execution/spec.md`
- `specs/local-codex/spec.md`
- `specs/ai-context-privacy/spec.md`
- `specs/ai-result-application/spec.md`
- `specs/ai-integration/spec.md`
- `specs/workflow/spec.md`
- `specs/continue-workflow/spec.md`
- `specs/ff-workflow/spec.md`
- `specs/ff-panel/spec.md`
- `specs/explore-context/spec.md`
- `specs/explore-thinking-space/spec.md`
- `specs/verify-workflow/spec.md`
- `specs/pipeline-interaction/spec.md`
- `specs/guidance-popover/spec.md`

## Not executed / remaining gates

No Java implementation, Gradle build/test, Plugin Verifier, IDE/UI runtime, live Codex inference or paid provider calls were run. These are future implementation acceptance checks. No auth files/secrets were read. No source/dependency/CI/hook files changed; no staging, commit, push or PR was performed. Existing CI has no new run evidence from this work and is not claimed green.

Planning is complete. Tracker association is pending because the custom skill/connector is unavailable and external tracker mutation is outside the authorized scope. Release decisions remain: distribution/auth eligibility, confirmation of recommended app-server scope (or explicit exec re-scope), tested CLI minimum/OS/security matrix, and tuning conservative defaults. The hook's GitHub-remote mirror classification and current review topology remain separately documented risks, unchanged by this branch.
