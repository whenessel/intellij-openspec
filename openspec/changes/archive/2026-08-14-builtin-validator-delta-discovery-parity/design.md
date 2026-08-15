# Design

## Context

Two CLI errors about change-delta **discovery** are under-reported by the plugin's CLI-absent fallback:

- **Misplaced delta** — a regular file `specs/spec.md` at a change's `specs/` root. Shipped upstream in **1.7.0** (CHANGELOG credits PR #1392; dist code comments credit #1385): `validate` flags it, `archive` blocks on it, and `validate` reuses the merge/archive discovery rules. 1.8 only **reworded** the message ("in a capability folder" → "under a capability path"); it did not add or strengthen this rule. The recursive discovery layout itself is older still (1.6.0, #1182/#1280). So this is a 1.7-era parity gap, not 1.8 scope — it surfaced while wrapping up 1.8 support.
- **No deltas found** — a change that requires specs but has no delta `spec.md`. A long-standing fundamental check for spec-producing schemas.

The plugin's CLI-present path already surfaces the CLI's own errors. Only the CLI-absent fallback (`BuiltInValidator.validateDeltaSpecs`, and the change-level `validateSingleChange`) misses them — a false green in Verify.

## Exact CLI behavior (captured from the real 1.8.0 CLI + source-confirmed via openspec-guru)

- **Misplaced:** `level: ERROR`, `path: "spec.md"` (no `line`), `valid:false`. Message: `Delta spec found at specs/spec.md. Delta specs must live under a capability path (e.g. specs/<capability-path>/spec.md) — a file at the specs/ root is ignored when the change is applied or archived.` Detection is **path-based / content-independent** — `fs.stat(specs/spec.md).isFile()`; a root `spec.md` with plain prose is still flagged. If a valid delta *and* a misplaced root `spec.md` coexist, only the misplaced ERROR is reported (still `valid:false`).
- **Discovery boundary:** `specs/<cap>/spec.md`, `specs/<area>/<cap>/spec.md`, `specs/a/b/c/spec.md` — all **valid** (a `spec.md` counts when at least one directory sits below `specs/`; `id` = joined segments). A root `specs/spec.md` regular file is **misplaced**. A non-`spec.md` file, or a *directory* named `spec.md`, is not misplaced (the latter is walked as an ordinary capability folder). Match is exact lowercase `spec.md`; do not treat `SPEC.md` as misplaced (guru saw a case-insensitive-FS artifact on APFS, not intended CLI behavior).
- **No-deltas:** `level: ERROR`, `path: "file"`, `valid:false`. Message begins `Change must have at least one delta. No deltas found. Ensure your change has a specs/ directory with capability folders …` and ends with a `skip_specs` hint. Fires for an empty `specs/`, a missing `specs/`, or a `specs/` with only non-`spec.md` files.
- **`skip_specs` interaction (load-bearing for parity):** a change with `skip_specs: true` and no deltas is `valid:true` with an **INFO** note (`skip_specs is set in .openspec.yaml: change declares no spec-level behavior change …`), **not** a no-deltas ERROR. The fallback's no-deltas ERROR must therefore be suppressed under `skip_specs`.
- **Archive blocking:** `openspec archive` exits non-zero with no mutation on a misplaced/invalid structure (`archive_validation_failed`). The plugin's archive is CLI-delegated, so it inherits this — **no plugin change needed** for archive.
- **No machine rule-id** for either issue — freeform messages keyed by `level` + `path` + content. The plugin assigns its own internal rules `delta-spec-misplaced` and `delta-none-found`, both hard ERRORs.

## Decisions

### D1 — Recursive delta discovery replaces the one-level directory scan

`validateDeltaSpecs` currently iterates only *directory* children of `specs/` and looks exactly one level down for `spec.md` (`if (!domainDir.isDirectory()) continue;`), so a root `spec.md` is skipped and a nested `specs/<area>/<cap>/spec.md` is never validated. Replace this with a recursive walk of `specs/`:
- A regular file named exactly `spec.md` whose parent **is** `specs/` → misplaced ERROR (`delta-spec-misplaced`), path = that file, content-independent; it is **not** structurally validated (matching the CLI, which reports only the misplaced issue for it).
- A `spec.md` at depth ≥ 1 (parent is some directory under `specs/`) → `validateDeltaSpecStructure` (the existing structural rules), now applied at any depth. This also fixes the pre-existing nested-delta under-report.
- Track whether *any* `spec.md` was found (misplaced or valid) for the no-deltas gate (D2).

### D2 — `skip_specs`-aware no-deltas ERROR, gated on schema requiring specs

The no-deltas check needs the change's required-artifact set and its `skip_specs` flag, both available in `validateSingleChange` (which already computes `Set<String> required = version.getRequiredArtifacts()` and reads the change metadata). Emit `delta-none-found` (ERROR) when **all** hold: the change's schema requires specs (`required.contains("specs")`), the change does **not** declare `skip_specs`, and `validateDeltaSpecs` found **no** `spec.md` (neither misplaced nor valid). A misplaced root `spec.md` counts as "found", so a misplaced-only change gets the misplaced ERROR alone — matching the CLI. `validateDeltaSpecs` returns (or records) whether it found any `spec.md` so the gate can read it.

### D3 — Never more restrictive than the CLI (the boundary that matters)

The one place this change could over-report is the misplaced/no-deltas boundary. Guardrails, each pinned by a test: flag misplaced **only** for a regular file named exactly `spec.md` at the `specs/` root (never at depth ≥ 1, never a directory named `spec.md`, never content-gated); suppress no-deltas under `skip_specs` and when the schema does not require specs; and count a misplaced root file as "found" so no-deltas never piles on. Deeper-nested deltas are now *validated* (previously ignored) — that is parity, not new restriction, because it applies the same structural rules the CLI applies.

## Test strategy

Contract-test discipline, fixtures captured from the real 1.8.0 CLI (never hand-authored); test-engineer PLAN is consulted at apply start.

- **Fixtures** under `src/test/resources/fixtures/cli/1.8.0/`: `validate-single-change-misplaced-delta.json` (ERROR `path:"spec.md"`), `validate-single-change-no-deltas.json` (ERROR `path:"file"`), `validate-single-change-skip-specs.json` (valid + INFO), and a nested-valid capture. `root.path` sanitized to `/fixture`; added to the README manifest.
- **Contract tests** (`CliContractTest`): assert each shape at the raw-JSON level (`parseJsonOutput` discards `path`/`line`, so path assertions are raw-JSON only), keying off `level` + `path` + message content (no rule-id exists).
- **Rule/integration tests** driving the real validator (`BuiltInValidatorTest`, which can build a change + delta via the VFS fixture — several existing tests do, e.g. `testProposalLessChangeWithValidDeltaPasses`): misplaced root `spec.md` → `delta-spec-misplaced` ERROR + verdict fails; nested delta (incl. multi-segment) → discovered, structurally validated, **not** misplaced; no-deltas → `delta-none-found` ERROR; `skip_specs` change with no deltas → **no** error; misplaced-only → misplaced ERROR **without** `delta-none-found`; a directory literally named `spec.md` → not misplaced. Each negative paired with a positive control.
- **Parity guards** (`ValidatorVerdictParityTest` / `…VersionStabilityTest`) unaffected — no change/delta corpus item is added or moved.
- **No `verifyPlugin`/`uiSmoke`:** the discovery already uses `VirtualFile`/`LocalFileSystem`; no new `com.intellij.*` API. Coverage ratcheted after measuring.

## Risks / trade-offs

- **Recursion vs. the one-level scan** touches the delta path more than a minimal misplaced-only fix would, but it is required: a `skip_specs`-less no-deltas check that ignored nested deltas would falsely fire on a valid deeper delta — a *more-restrictive* regression. Recursion is the only way the no-deltas gate is safe.
- **The no-deltas gate is behavior-changing** (a spec-requiring change with zero deltas now fails the fallback verdict). Existing tests that create a change without deltas and assert a *passing* verdict must be audited; a change that only asserts `notNull` is unaffected. The `skip_specs` and requires-specs gates keep pure-tooling changes green.
- **Message drift** (as with the other fallback rules) — the plugin-generated messages mirror the CLI's wording but are not byte-identical; acceptable for a degraded-mode fallback.
