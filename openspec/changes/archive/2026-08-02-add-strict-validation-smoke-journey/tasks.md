# Tasks: add-strict-validation-smoke-journey

## Implementation Tasks

- [x] 1.1 Add `getTitle()` to the `NotificationRef` JMX stub and a `notificationTitles()` helper (the balloon title carries the strict disclosure; the existing content helper reads only the body)
- [x] 1.2 Add Journey 11 (`validateStrictActionDisclosesStrictRun`): show the tool window, invoke `OpenSpec.ValidateStrict`, assert the balloon title `Validate (strict)` and the Console `--strict` echo
- [x] 1.3 Confirm the integrationTest source compiles (`compileIntegrationTestKotlin`)

## Testing Tasks

- [x] 2.1 Reconcile `openspec/specs/ui-smoke-journeys/spec.md` — update the journey count and add scenarios for journeys 8 (spec preview), 9 (change deltas), 10 (grouped report), 11 (Validate Strict)
- [ ] 2.2 `./gradlew build` green (unit suite + JaCoCo floor unaffected — integrationTest is a separate source set)
- [ ] 2.3 Headful validation of Journey 11 under `caffeinate -dimsu ./gradlew uiSmoke` is a release-gate step (recorded in release-prep), not a per-PR gate
