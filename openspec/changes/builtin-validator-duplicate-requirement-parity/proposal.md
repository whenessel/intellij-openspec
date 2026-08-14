## Why

OpenSpec CLI 1.8 added a main-spec validation rule: a spec that declares the same `### Requirement:` name more than once is an ERROR (`valid: false`), because duplicate names let a spec update silently discard one block while editing another. The plugin's CLI-absent built-in validator does not detect this today, so when the CLI is not installed the fallback reports a duplicate-requirement spec as **valid** while the real CLI reports it **invalid**.

That gap is the safe direction — the fallback under-reports, it is never *more* restrictive than the client — so it did not block 1.8 support (Change A). But closing it brings the fallback's main-spec verdict into full parity with 1.8 for the one remaining rule Change A deliberately left out, and prevents a genuinely broken spec (a name collision that will drop a requirement on the next archive) from passing unnoticed in a CLI-less setup.

## What Changes

- The built-in validator's **main-spec path** gains duplicate-`### Requirement:`-name detection, matching OpenSpec CLI 1.8: an exact-name collision on a later requirement is an ERROR that fails the file's verdict, referencing the line of the first declaration.
- The rule mirrors the CLI's observed semantics exactly (captured from the real 1.8.0 CLI): the match is **case-sensitive** and exact on the requirement name; with N occurrences of one name the later N−1 are each flagged (each pointing back at the first); the message wording follows the CLI's.
- The rule fires **only on the main-spec path** (`openspec/specs/**`). Change **delta** specs — which legitimately repeat a name across `## ADDED`/`## MODIFIED` sections and are governed by the CLI's separate delta-consistency rules — are untouched, so the fallback never becomes more restrictive than the CLI there.
- A single-item fixture is captured from the real 1.8.0 CLI and locked behind a contract test, plus built-in-validator rule tests driving the real validator (no inline mirror).

## Capabilities

### Modified Capabilities
- `validation`: the "Spec format validation" requirement gains a duplicate-requirement-name ERROR on the main-spec fallback path, scoped away from change delta specs.

## Impact

- `BuiltInValidator.validateSpecContent` — additive detection inside the existing requirement loop; no change to any other rule's severity or verdict.
- Tests: a new captured 1.8.0 single-item fixture + contract-test assertion; new rule cases in the built-in validator tests. No production API surface changes, so no `verifyPlugin`/`uiSmoke` gate is triggered.
- No change to the CLI-present path (the plugin already surfaces the CLI's own duplicate-requirement ERROR verbatim when the CLI is installed).
