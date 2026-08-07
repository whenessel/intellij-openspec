## 1. Re-source the Config Profile display read

- [x] 1.1 Rewire `OpenSpecSettingsPanel.refreshConfigProfileSection` (~:308-341): keep the CLI-unavailable branch (`showProfileFallback()`); on the pooled thread call `WorkflowProfileService.refresh()` then read `getActiveProfileName()` + `getActiveWorkflows()`; render name + workflow bullets on the EDT via `invokeLater`. Remove the `config profile … --json` `CliRunner.run` block and the `ConfigProfileDetail` usage
- [x] 1.2 Remove the description row: delete the `profileDescriptionLabel` field (~:98) and its build/write sites (~:280-281, :292, :317, :345, :366); keep the "Active profile:" label, the workflow list panel, and `profileFallbackLabel`
- [x] 1.3 Keep `updateProfileDisplay`'s workflow-bullet rendering and its empty-list guard ("No workflow information available") as a defensive fallback; adapt its signature to take name + workflows (no `ConfigProfileDetail`)
- [x] 1.4 Confirm the profile *switch* path is untouched — General-section combo, `applyProfileChange`, `WorkflowProfileSwitchService`, and "Customize workflows…" (they already call `refreshConfigProfileSection` after a switch)

## 2. Retire the dead `config profile --json` path

- [x] 2.1 Delete `src/main/java/com/johnnyblabs/openspec/model/ConfigProfileDetail.java` (no consumer after §1) and `src/test/java/com/johnnyblabs/openspec/model/ConfigProfileDetailTest.java`; remove the now-unused import in `OpenSpecSettingsPanel`
- [x] 2.2 Keep the `1.7.0/config-profile-json-rejected.txt` fixture as the tombstone; add a one-line note to `fixtures/cli/README.md` that it documents why `config profile --json` is not used (so the model isn't reintroduced)

## 3. Test

- [x] 3.1 In the existing `OpenSpecSettingsPanelProfileTest` harness, add a test that stubs `WorkflowProfileService` (profile `core` + a workflow set from the committed `1.7.0/config-list.json` fixture) and asserts the Config Profile section renders the profile name + each workflow as a read-only item, and shows NO description row
- [x] 3.2 Add a test that with the CLI unavailable, the section shows the fallback label (not a stale/blank render)
- [x] 3.3 Confirm no test still references `ConfigProfileDetail`

## 4. Documentation fidelity

- [x] 4.1 Update the CHANGELOG `## Unreleased` — a user-facing **Fixed** note that the Settings Config Profile section now shows the active profile's real workflows again (sourced from `openspec config list --json`) instead of a blank fallback, and no longer shows a fabricated description

## 5. Verify

- [x] 5.1 `openspec validate source-config-profile-from-config-list --strict` is clean (both MODIFIED requirements restate all scenarios, drop none)
- [x] 5.2 `./gradlew build` green (suite + JaCoCo `jacocoTestCoverageVerification`); confirm the coverage floor holds (deleting the `ConfigProfileDetail` path removes covered lines but also its tests — verify no regression below the floor; ratchet only if it rises)
- [x] 5.3 Run the tracker/host leak-guard grep on staged files before commit (pattern per the repo's contributor guide)
