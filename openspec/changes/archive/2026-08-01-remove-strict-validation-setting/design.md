## Context

`strictValidation` is a persisted boolean in `OpenSpecSettings` (`@Storage("openspec.xml")`), surfaced as a Settings checkbox. Three code paths read it: `BuiltInValidator.validateSingleChange` (escalates `change-artifact-missing` WARNING→ERROR), `SpecSyncService.applyModified` (blocks a sync when a MODIFIED delta targets a missing requirement), and the settings UI. Upstream OpenSpec has no durable strict state at all — `--strict` is a per-invocation `validate` flag (`valid = strictMode ? errors===0 && warnings===0 : errors===0`). This change was designed with the plugin-ui-specialist (UX) and jetbrains-platform-guru (feasibility) after openspec-guru ruled the persistent setting off-model.

## Goals / Non-Goals

**Goals:**
- Model strict the way the client does: a per-run choice, threaded per-invocation, stored nowhere.
- Never let strict silently change a verdict — every strict run discloses itself in the balloon and console.
- Keep the default Validate a frictionless one-click; make strict discoverable (toolbar dropdown, menu leaf, Find Action).
- Strict means the same thing whether or not the CLI is present.
- Untangle the two non-Validate behaviors currently mislabeled "strict."

**Non-Goals:**
- No persistent strict preference in any form (settings, config.yaml, env) — that's the off-model thing being removed.
- No scoped (per-spec/per-change) strict in the Project-View menu — niche; whole-project is the CI-parity case. (Revisit only on real demand.)
- Not changing the *outcome* of strict (warnings fail) — only its home (per-run, not a hidden mode).

## Decisions

**1. Two registered actions, not a split-button (platform-guru's call, chosen by the owner).** `OpenSpec.Validate` (normal) + a new `OpenSpec.ValidateStrict` (`OpenSpecValidateStrictAction extends OpenSpecValidateAction`, `actionPerformed` → `runValidation(project, wholeProject, strict=true)`) — mirroring the existing `OpenSpecValidateFromProjectViewAction` subclass pattern. Toolbar: a `popup="true"` group ("Validate ▾") holding both. Menu: two adjacent leaves. This renders identically in toolbar, menu, Project View, and Find Action, uses only long-stable `AnAction`/`ActionGroup` API (no verifier risk), and is language-agnostic. `SplitButtonAction` was considered (the owner's original instinct) but rejected: it's a `platform-impl` class needing a subclass wrapper + a `verifyPlugin` gate, and it degrades to a plain submenu in the main menu — an inconsistent affordance between toolbar and menu.

**2. Strict is threaded per-invocation, never stored.** `boolean strict` flows through `runValidation` → `cliArgs`/`commandLine` (append `--strict`) and into the built-in path. The choice is read at action-invocation time (a literal passed by the action), not from persisted state — strictly better than today (no shared mutable state, no races). `BuiltInValidator` is a shared project service, so strict is a **method parameter** (`validateAll(strict)`, `validateChange(name, strict)`, `validateSingleChange(change, strict)`), never a field.

**3. CLI-present vs CLI-absent both honor strict.** CLI present: `--strict` on the invocation; the CLI's `--json` `valid` field already reflects `errors==0 && warnings==0` (the plugin's parser reads `valid` since the parity change; the `validate-strict-warning-only.json` fixture covers it). CLI absent: the built-in fallback flips the top-level verdict — `passed = strict ? (errorCount==0 && warningCount==0) : (errorCount==0)` — as a pure verdict flip on `ValidationResult`, **not** by re-severity-ing issues. Warning rows stay labeled WARNING (honest); the disclosure explains the "0 errors yet FAILED" verdict.

**4. Non-silent disclosure, test-token-preserving.** The uiSmoke journeys and screenshot tour assert on the literal `passed (` / `failed (` balloon substrings and the `Validation FAILED` console substring. Disclose strict *around* those tokens: balloon title `Validate (strict)`, and on a warnings-only failure append `— strict: warnings count as failures` after the paren group; console echoes `openspec validate --all --strict` and its verdict line names strict. Existing UI tests invoke normal Validate, so they see no change.

**5. `change-artifact-missing` → always-on non-failing WARNING.** Drop the strict-escalation. It's a plugin-invented lint (the CLI never checks `tasks.md`/`design.md` presence), so an honest WARNING is on-model; under a strict run it flips the verdict via Decision 3's warnings-fail rule (correctly attributed to strict, not to a phantom ERROR). No signal is lost.

**6. SpecSync guard decoupled and renamed.** `SpecSyncService` blocking a sync when a MODIFIED delta targets a missing requirement is a **data-safety** concern (the delta genuinely can't be applied), not validation-strictness — and it was a third reader of the removed flag. Make it an always-on guard (`sync-modified-target-missing`) surfaced in the Sync **preview** (where the user approves the mutation), with the "strict mode" framing removed. There is one honest behavior; the strict/lenient branch goes away.

**7. Migration: notice only for the opted-in minority.** Removing the field is platform-safe (`XmlSerializer` ignores the orphaned `<option>`). But a user who had it ON would silently lose strictness — the "invisible divergence" we're eliminating, in reverse. So keep `state.strictValidation` as a **migration-only read** for one release plus a `migratedStrictNotice` guard: on first run, if it was `true`, fire one sticky notification ("Strict validation is now per-run — use Validate (Strict)") and clear it. Default (off) users see nothing.

## Risks / Trade-offs

- **[A dev relied on always-on strict and now gets lenient by default]** → Mitigated by the one-time migration notice pointing them to the per-run action; and strict-as-policy belongs in CI (`openspec validate --strict`), not a per-dev IDE toggle.
- **[Toolbar dropdown is one click to reach strict vs. a split-button's arrow]** → Accepted: the default (normal) stays one click; strict is a deliberate secondary action. Consistency across surfaces + zero platform risk outweighs the split-button's marginal toolbar affordance.
- **[CLI `--strict` is now sent — a real behavior change on the CLI path]** → Covered by the captured `validate-strict-warning-only.json` contract fixture; the parser already reads the `valid` field.
- **[Removing a config field the CLAUDE.md rule guards]** → Explicitly handled: all three readers are re-homed (Validate → parameter, SpecSync → own guard, UI → removed), per the grep-before-removing rule.

## Migration Plan

No data migration required (orphaned `<option>` ignored). The one-time notice is the only upgrade-time behavior. Rollback is reverting the diff. Behavior is outcome-preserving (strict still fails on warnings); it moves home from a hidden mode to a visible per-run action.
