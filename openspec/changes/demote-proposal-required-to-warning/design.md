## Context

See proposal.md — Why. `BuiltInValidator.validateSingleChange` (`:187-190`) constructs `change-proposal-required` at `Severity.ERROR`; the change verdict is `passed = noneMatch(ERROR)` (`:228`), so a proposal-less change fails. Its sibling `change-artifact-missing` (`:195-203`) is already a non-failing WARNING with a comment noting it is a plugin-invented lint the CLI never checks. Verified live on 1.7.0: a change with a valid `specs/` delta and no `proposal.md`/`.openspec.yaml` validates `valid:true` in default and `--strict` (single-item and `--all`).

## Goals / Non-Goals

**Goals**
- The built-in (CLI-absent) verdict never fails a proposal-less-but-valid change, matching the CLI in both default and strict.

**Non-Goals**
- Touching the profile-switch / other validation rules; the CLI-present path (which already defers to the CLI verdict for changes).
- Regrowing the parity corpus (see Decision 2).

## Decisions

### Decision 1 — Demote to WARNING, don't remove
Make `change-proposal-required` a non-failing WARNING, symmetric with `change-artifact-missing`. Both are plugin-invented nudges the CLI never emits; keeping a non-failing hint is useful (a proposal is the first OpenSpec workflow step) without over-ruling the CLI. Removal would drop a helpful nudge for no benefit; the harm was only that it *failed the verdict*, which the demotion fixes. As a WARNING it is non-failing in default (`noneMatch(ERROR)`) and in strict (not in the empty `CLI_MIRRORING_STRICT_WARNINGS` allow-list from the strict-parity change).

### Decision 2 — Standalone fixture, parity corpus untouched
Capture a new `fixtures/cli/1.7.0/validate-single-change-no-proposal.json` (real single-item `openspec validate <id> --type change --json` on a proposal-less-but-valid change → `valid:true`, `issues:[]`), joining the existing single-item fixture family. Its distinct name is invisible to `ValidatorVerdictVersionStabilityTest`'s discovery (which globs the exact filenames `validate-parity-corpus.json` / `-strict.json`), so the 13-item anchor corpus is not regrown and the 1.6.0 anchor need not be re-captured — the reason this fix was split out of the parity-guard change. No 1.6.0 twin (that CLI isn't installed; cross-generation verdict stability is already guarded by the parity corpus).

## Risks / Trade-offs

- **A future CLI that starts requiring `proposal.md`** would make this demotion wrong → the captured contract fixture (`CliContractTest`) locks the current upstream fact and breaks loudly on re-capture if it ever changes.
- **The proposal-less change also emits two `change-artifact-missing` WARNINGs** (design/tasks) → expected and harmless; the plugin test asserts on rule presence + verdict, not "sole issue".

## Migration Plan

Not applicable — a severity change in a read-only validator; no persisted state.
