## Why

The OpenSpec CLI reports two change-delta **discovery** errors that the plugin's CLI-absent built-in validator misses, so when the CLI isn't installed the Verify / validate surface shows a false green:

1. **A delta spec written at the change's `specs/` root** (`specs/spec.md`, no capability directory) is an ERROR — the merge/apply/archive path only reads capability folders, so a root-level delta is **silently dropped** and its requirements never reach `openspec/specs/`. This is the exact silent-drop bug the CLI hardened against; `archive` blocks on it. The plugin's fallback skips the file entirely (it iterates only directory children of `specs/`), so a CLI-less author sees Verify pass while their delta will vanish.
2. **A change with no delta specs at all** (empty `specs/`, missing `specs/`, or only non-`spec.md` files) is an ERROR. The plugin's fallback has no such check, so a spec-requiring change with zero deltas passes.

Both are false greens: the fallback under-reports where the CLI errors. Closing them keeps the fallback faithful to the client it wraps — a user without the CLI gets the same verdict — and catches a real, silent authoring mistake. (The misplaced-delta rule shipped upstream in 1.7.0; the no-deltas check is older. Neither is 1.8-new — they surfaced while wrapping up 1.8 support.)

## What Changes

- The built-in validator's change-delta discovery becomes **recursive**, matching the CLI: a `spec.md` at **any depth ≥ 1** under `specs/` (e.g. `specs/<cap>/spec.md` or `specs/<area>/<cap>/spec.md`) is a valid delta and is structurally validated (this also fixes a pre-existing under-report where nested deltas were never validated).
- A **regular file named exactly `spec.md` at the `specs/` root** is flagged as a misplaced-delta ERROR (rule `delta-spec-misplaced`), mirroring the CLI's message. Detection is path-based and content-independent, exactly matching the CLI boundary — so the fallback is never more restrictive (only a root-level `spec.md` is flagged; anything at depth ≥ 1 is valid).
- A change that requires specs but has **no delta `spec.md` anywhere** is flagged as a no-deltas ERROR (rule `delta-none-found`), gated to fire only when the change's schema requires specs **and** it does not declare `skip_specs` — matching the CLI, which suppresses the error (emitting an informational note instead) under `skip_specs`. A misplaced root `spec.md` counts as "found", so it yields only the misplaced ERROR, never a spurious no-deltas one.
- Both shapes are captured from the real CLI and locked behind contract tests.

## Capabilities

### Modified Capabilities
- `validation`: the "Delta spec validation" requirement gains recursive delta discovery, a misplaced-delta ERROR, and a `skip_specs`-aware no-deltas ERROR on the CLI-absent fallback path.

## Impact

- `BuiltInValidator.validateDeltaSpecs` (recursive discovery + misplaced detection) and `validateSingleChange` (the no-deltas gate, which needs the required-artifact set and `skip_specs`). Additive; no change to any existing delta structural rule.
- Tests: fixtures captured from the real CLI (misplaced, no-deltas, `skip_specs`-valid, nested-valid) + contract assertions + rule/integration tests. No new `com.intellij.*` API (the discovery already uses `VirtualFile`/`LocalFileSystem`), so no `verifyPlugin`/`uiSmoke` gate.
- No change to the CLI-present path (the plugin already surfaces the CLI's own errors) or to archive behavior (archive is CLI-delegated and already inherits the CLI's block).
