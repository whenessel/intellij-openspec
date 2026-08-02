# Add a Validate (Strict) smoke journey; reconcile the journey count

## Why

The `remove-strict-validation-setting` change added a per-run **Validate (Strict)** action (a "Validate ▾" toolbar dropdown + menu leaf + Find Action) and its own test plan committed to a release-gated uiSmoke journey asserting the strict disclosure — but that journey was never delivered. The strict action is a release-gated UI surface (the smoke suite's charter is "presence and wiring of rendered surfaces"), so it should have a journey; it currently has none.

Separately, the `ui-smoke-journeys` spec has drifted: it says the suite has "currently seven" journeys and enumerates seven, but three more shipped during the v0.5.0 Spec-Intelligence epic (preview-renders-selected-spec, preview-renders-change-deltas, grouped-formatted-validation-report) without their scenarios being added. The spec no longer matches the suite.

## What Changes

- Add uiSmoke **Journey 11** — invoking `OpenSpec.ValidateStrict` discloses the strict run in two rendered surfaces a default Validate never produces: the summary balloon title `Validate (strict)` and the Console's `openspec validate --strict` command echo. Both disclosures are verdict-independent, so the assertions target the strict-specific wiring, not the verdict (the strict semantics are unit-tested).
- Reconcile the `ui-smoke-journeys` spec to the real suite: update the count and add scenarios for the previously-undocumented journeys 8, 9, 10 plus the new strict journey 11.

## Capabilities

### Modified Capabilities
- **ui-smoke-journeys** — the suite-existence requirement is updated to the current journey count and gains scenarios for the spec-viewer preview, change-deltas preview, grouped validation report, and the new Validate (Strict) disclosure journeys.

## Impact

- Affected tests: `src/integrationTest/kotlin/.../OpenSpecUiSmokeTest.kt` (new journey + a `getTitle()` on the notification JMX stub).
- No production code change. uiSmoke is release-gated and manual-dispatch only — not a per-PR blocker.
- Validation of the new journey requires a headful run under `caffeinate -dimsu` (the CI tag job can't boot a headful IDE, per the known runner limitation); it compiles in CI but is asserted at the release gate.
