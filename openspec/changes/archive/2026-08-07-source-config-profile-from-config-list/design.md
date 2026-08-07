## Context

See proposal.md — Why. Key facts (source-verified against the installed 1.7.0 CLI + dist):

- `refreshConfigProfileSection` (`OpenSpecSettingsPanel.java:308-341`) runs `config profile [<preset>] --json` on a pooled thread → `ConfigProfileDetail.fromJson`. The `--json` option was **never registered** on `config profile` (dist `commands/config.js` declares only `profile [preset]` with a description + action — no `--json`), so the run always fails → `ConfigProfileDetail.fallback()` → name-only display.
- `WorkflowProfileService` already resolves the active profile name + workflows from `config list --json` (`getActiveProfileName()`, `getActiveWorkflows()`, cached, `refresh()`), and already handles the caveat that `config list --json` **omits `workflows[]` until a profile is explicitly written** (falls back to `CORE_DEFAULTS` while preserving the parsed profile name).
- The profile *switch* is in the **General** section (combo + "Customize workflows…"), writing via `config profile <preset>` (no `--json`); it already calls `refreshConfigProfileSection()` after a switch. Unaffected by the 1.7 breakage.

## Goals / Non-Goals

**Goals**
- Make the Config Profile section show real data again, from the one working structured source, sharing it with the status-bar widget.
- Remove the plugin-invented `description` and the dead `config profile --json` read.

**Non-Goals**
- Touching profile switching (General combo, Customize picker, `WorkflowProfileSwitchService`) — only the display read changes.
- Retitling the section (see Decisions) or surfacing `delivery`/`featureFlags` — scope creep for a low-priority fix.

## Decisions

### Decision 1 — Re-source from `WorkflowProfileService`, don't remove the section
The section is not redundant with the status-bar widget: the widget shows the workflow list only in a transient tooltip, off in the status bar, while the Settings section is the only inline, persistent view of the resolved workflow set, co-located with the switch control — and it answers "what does the `""`/default target actually resolve to?" that the General combo can't. Re-sourcing through `WorkflowProfileService` also makes the section and the widget share one source of truth. Rewire `refreshConfigProfileSection`: keep the CLI-unavailable branch (`showProfileFallback()`); on the pooled thread call `WorkflowProfileService.refresh()` then read `getActiveProfileName()` + `getActiveWorkflows()`, and render name + workflow bullets on the EDT. Keep reads off the EDT — `getActiveWorkflows()` can trigger a lazy CLI resolve.

### Decision 2 — Drop the description; retire `ConfigProfileDetail`
No OpenSpec command or schema emits a profile description (verified across the config Zod schema, `profiles.js`, and both `config list` forms). Remove `profileDescriptionLabel` and its rows. After re-sourcing, `ConfigProfileDetail` has no remaining consumer, so delete the model and `ConfigProfileDetailTest`. Keep the `config-profile-json-rejected.txt` fixture (and a one-line README note) as the tombstone documenting why `config profile --json` is not used — so the model isn't reintroduced.

### Decision 3 — Defer the section retitle
`plugin-ui-specialist` recommends retitling "Config Profile" → "Active workflows" to disambiguate it from the General section's "Workflow profile:" switch combo. It's a real improvement, but the title is a documented settings-section name (`documentation` spec + README), so renaming ripples beyond this capability. For a low-priority fix, keep the "Config Profile" title (matches the capability name and all docs) and leave the retitle as a noted follow-up.

### Decision 4 — Section reflects the applied active profile, not an unapplied preview
`config list --json` reports the applied global state, so the section shows the *active* profile's workflows and refreshes *after* a switch (Apply / "I'm done"), via the existing wiring. Previewing an unapplied combo selection is not restored — it was already impossible once `config profile --json` was rejected, so this is no regression.

## Risks / Trade-offs

- **`workflows[]` absent on a fresh config** → handled: `WorkflowProfileService` falls back to `CORE_DEFAULTS` while keeping the profile name, so the section still shows the core set rather than "no workflows".
- **`config list --json` carries extra keys** (`telemetry`, optional `defaultStore`) → the existing parser reads only `profile`/`workflows` and tolerates extras (Gson); no change needed.
- **Section/widget divergence** → eliminated by both reading the same `WorkflowProfileService`.

## Migration Plan

Not applicable — internal UI rewire, no persisted state or data migration. The switch path and its persistence are untouched.
