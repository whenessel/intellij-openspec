## Why

The Settings → **Config Profile** section reads `openspec config profile --json` to show the active profile's name, description, and workflows. That command option **never existed** — it was plugin-assumed from the start (the introducing change even hedged it as a risk), and the current CLI rejects it outright (`error: unknown option '--json'`). So the section always falls into its name-only fallback: empty description, "No workflow information available". The section is effectively dead on every supported CLI. The `description` it tried to show is also plugin-invented — OpenSpec has no profile-description concept in any command or schema.

Tracked in the project tracker (OpenSpec CLI 1.7.x support epic).

## What Changes

- **Re-source the section from the working command.** `refreshConfigProfileSection` will read the active profile name and workflow list from the plugin's existing `WorkflowProfileService`, which resolves them from `openspec config list --json` (`profile` + `workflows[]`) — the same source the status-bar profile widget already uses, so the two surfaces can no longer disagree. When `workflows[]` is absent (the CLI omits it until a profile is explicitly written), the service's existing core-default fallback applies.
- **Drop the invented `description`.** The row and its data have no upstream source; the section shows the profile name and its active workflows only.
- **Retire the dead `config profile --json` path:** delete the `ConfigProfileDetail` model and its test (the only consumer is the removed read). The captured `config-profile-json-rejected.txt` fixture stays as the tombstone documenting why the command isn't used.
- **The profile *switch* is untouched.** Switching still delegates to `openspec config profile <preset>` (a write, no `--json`) via the General-section combo and the "Customize workflows…" picker; only the broken display *read* changes. The existing post-switch refresh wiring already re-renders the re-sourced section.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `config-profile`: two requirements change.
  - **Config profile display in Settings** — the section sources the active profile name and workflow list from `openspec config list --json` (not the non-existent `openspec config profile --json`), and no longer shows a profile description.
  - **Settings panel surfaces use CLI runtime data** — drop the dead `openspec config profile <name> --json` from the list of runtime sources; `openspec config list --json` is the sole structured source.

## Impact

- **`src/main`:** `OpenSpecSettingsPanel.refreshConfigProfileSection` rewired to `WorkflowProfileService` (off the EDT, as today); the description label/rows removed. Delete `model/ConfigProfileDetail.java`.
- **Tests:** delete `ConfigProfileDetailTest`; add a section-render assertion to the existing `OpenSpecSettingsPanelProfileTest` harness (stubbed `WorkflowProfileService` → workflows render; CLI-unavailable → fallback label). Uses the committed `1.7.0/config-list.json` fixture.
- **No `plugin.xml` / extension-point change; no platform-API change** — an internal rewire on APIs already used in this method, so no IntelliJ 2024.2+ compatibility impact and nothing for the Plugin Verifier to catch.
- **Deferred (noted, not done):** retitling the section "Config Profile" → "Active workflows" to disambiguate it from the General section's "Workflow profile:" switch combo — a genuine UX improvement, but it ripples into the `documentation` spec and README, out of proportion for this low-priority fix. Left as a follow-up.
