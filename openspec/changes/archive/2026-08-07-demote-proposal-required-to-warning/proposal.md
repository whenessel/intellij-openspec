## Why

The built-in validator emits `change-proposal-required` as an **ERROR** when a change lacks `proposal.md`, so a proposal-less change **fails** the built-in (CLI-absent) verdict in both default and strict modes. But the real OpenSpec CLI validates a proposal-less change that has a valid delta as `valid:true` — it resolves changes by directory existence, not by requiring `proposal.md` (upstream #1182; verified live on 1.7.0). So the plugin is **more restrictive than the client it wraps** — the exact invariant the parity work protects. It only bites in the CLI-absent fallback (when the CLI is present, the plugin defers to the CLI verdict for changes), but that fallback path reds a project the CLI reports clean.

This is the larger never-more-restrictive gap deferred out of the parity-guard change (it was kept separate to avoid regrowing the 13-item anchor parity corpus). Tracked in the project tracker.

## What Changes

- **Demote `change-proposal-required` from ERROR to a non-failing WARNING**, symmetric with its sibling `change-artifact-missing` (missing `design.md`/`tasks.md`) — both are plugin-invented lints the CLI never checks. As a WARNING it neither fails the default verdict (`passed = noneMatch(ERROR)`) nor flips a strict run (it is not in the empty CLI-mirroring strict-warning set), so a proposal-less-but-valid change now passes the plugin's fallback in both modes, matching the CLI. The message shifts to nudge tone (`should have proposal.md`).
- The nudge is retained (not removed): a proposal is the first step of the OpenSpec workflow, so a non-failing hint remains useful — it just no longer over-rules the CLI.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `validation`: the **Change validation** requirement — `change-proposal-required` becomes an always-non-failing WARNING (like `change-artifact-missing`), because the CLI never requires `proposal.md`; its "Proposal required" scenario changes from ERROR to a non-failing WARNING.

## Impact

- **`src/main` (one line):** `BuiltInValidator.validateSingleChange` (`:187-190`) — `Severity.ERROR` → `Severity.WARNING` + nudge message. No other production site emits the rule; no inspection twin.
- **Tests + fixture:** update `BuiltInValidatorTest.testMissingProposalTriggersError` to assert a non-failing WARNING (+ a new test that a proposal-less-but-valid change passes); add a captured `1.7.0/validate-single-change-no-proposal.json` fixture + a `CliContractTest` assertion locking the upstream fact (CLI verdicts such a change `valid`). The 13-item parity corpus is **untouched** (the new fixture's distinct name is invisible to the version-stability guard's exact-filename glob).
- **No `plugin.xml` / platform-API change** — no IntelliJ 2024.2+ compatibility impact, nothing for the Plugin Verifier.
- **JaCoCo coverage floor HOLD** — a one-line severity flip on an already-covered branch.
