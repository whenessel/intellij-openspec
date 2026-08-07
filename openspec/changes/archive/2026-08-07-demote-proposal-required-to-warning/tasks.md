## 1. Demote the rule (src/main)

- [x] 1.1 In `BuiltInValidator.validateSingleChange` (`:187-190`), change the `change-proposal-required` issue from `Severity.ERROR` to `Severity.WARNING` and reword to nudge tone (`Change '<name>' should have proposal.md`); add a comment noting it is a plugin-invented lint the CLI never checks (CLI resolves changes by directory existence, upstream #1182), symmetric with `change-artifact-missing`
- [x] 1.2 Confirm no other production site emits `change-proposal-required` (grep) and the verdict logic (`passed = noneMatch(ERROR)`) needs no change

## 2. Capture the contract fixture (parity corpus untouched)

- [x] 2.1 Capture `fixtures/cli/1.7.0/validate-single-change-no-proposal.json` from real `openspec validate <id> --type change --json` on a temp change with a valid `specs/` ADDED delta but no `proposal.md`/`.openspec.yaml` (isolated HOME/XDG, `OPENSPEC_TELEMETRY=0`, sanitize the project root incl. the macOS `/private` prefix → `/fixture`); verify `valid:true`, `issues:[]`
- [x] 2.2 Add a provenance row to `fixtures/cli/README.md` (1.7.0 single-item family) noting it locks the "CLI validates a proposal-less change valid" fact behind this demotion; confirm the new filename is NOT matched by the version-stability guard's globs

## 3. Tests

- [x] 3.1 Update `BuiltInValidatorTest.testMissingProposalTriggersError` → assert `change-proposal-required` is a non-failing WARNING (severity WARNING, `result.passed()==true`, no ERROR-severity issue); rename accordingly
- [x] 3.2 Add `BuiltInValidatorTest` test: a proposal-less change with a valid delta (materialized, no `proposal.md`) → `validateChange(name)` passes, carries the `change-proposal-required` WARNING, and has no ERROR
- [x] 3.3 Add a `CliContractTest.SingleItemValidateContractV17` assertion parsing the new fixture → `result.passed()==true`, `issues` empty (locks the upstream fact; bites a future CLI that starts requiring proposal.md)

## 4. Verify

- [x] 4.1 `openspec validate demote-proposal-required-to-warning --strict` clean (MODIFIED requirement restates all scenarios, drops none)
- [x] 4.2 `./gradlew build` green (suite + JaCoCo `jacocoTestCoverageVerification`); confirm the coverage floor holds (severity flip on an already-covered branch — no ratchet)
- [x] 4.3 Run the tracker/host leak-guard grep on staged files before commit (pattern per the repo's contributor guide)
