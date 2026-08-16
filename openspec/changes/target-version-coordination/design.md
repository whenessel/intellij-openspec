## Context

The top-supported OpenSpec CLI version is currently restated in four independent places: the fixture corpus dir name (`src/test/resources/fixtures/cli/1.9.0/`), `ValidatorVerdictVersionStabilityTest.FLOOR`, `CliVersionAtLeastTest`'s `@ValueSource`, and — the one that drifted — the UI-smoke workflow's `npm i -g @fission-ai/openspec@1.6.0`. The workflow pin has no forcing function to keep it current, so it silently fell three generations behind.

## Goals

- One source of truth for the top-supported CLI version.
- The UI-smoke workflow derives its CLI install from that source (no hand-maintained literal).
- A `check`-wired guard that fails the build if the target version has no captured fixture corpus.
- Do it without adding production code (this is build/CI/docs infrastructure) and without disturbing the durable historical floor.

## Decisions

### Single source: `openspecTargetVersion` in `gradle.properties`

`gradle.properties` is the right home: it is Gradle-native (readable from the build with `providers.gradleProperty(...)`) and a plain `key=value` line that a workflow shell step can read with `sed`/`grep` without a YAML/TOML parser. Adding it here keeps the value where the build already looks for configuration and avoids inventing a new file.

### Enforcement is a JUnit hygiene test, not a bespoke Gradle task

The repo already enforces repository-file invariants through JUnit tests that walk up to the repo root and read the working-tree files — `DocumentationHygieneTest` (doc labels, version-restatement drift) and `MarketplaceListingHygieneTest`. A new `TargetVersionSingleSourceTest` (in the `integration` package, sibling to `ValidatorVerdictVersionStabilityTest`) fits that established pattern exactly and needs no `systemProperty` plumbing or a new production/build class.

The decisive reason to prefer a JUnit test over a `check`-wired Gradle verification task is **gate surface**: the local pre-push hook runs `./gradlew test`, not `check`, so a Gradle task wired into `check` is invisible to pre-push and would only fail in CI. A JUnit test is caught by **both** the pre-push gate and CI (`check` → `test`), and it fails loudly with the repo's existing idiom. The test asserts, reading the real working tree:

1. **Source exists and parses.** `gradle.properties` contains `openspecTargetVersion=X.Y.Z` matching `\d+\.\d+\.\d+`; an absent or renamed key fails.
2. **Fixtures exist for the target.** For `<target>`: `src/test/resources/fixtures/cli/<target>/version.txt` exists and its **trimmed** content equals `<target>` (the file carries a trailing newline), and `validate-parity-corpus.json` + `-strict.json` exist. Bumping the target without capturing that generation's fixtures fails here — the same fixtures already consumed downstream by `CliDetectionServiceTest` (`version.txt`) and `ValidatorVerdictVersionStabilityTest` (the parity twins).
3. **The workflow derives from the source, scoped to `ui-smoke.yaml` only.** Read `.forgejo/workflows/ui-smoke.yaml`; assert it references `openspecTargetVersion` and contains **no** `@fission-ai/openspec@<digit>` literal. **This guard is scoped to `ui-smoke.yaml` alone — never repo-wide** — because two other CLI installs are deliberate and must not be flagged: `.github/workflows/build.yml` pins `@fission-ai/openspec@latest` on purpose (the Windows integration leg wants newest), and `scripts/seed-lifecycle-demo.sh` pins `@1.3.1` on purpose (it seeds a *legacy* demo project). Generalizing the negative assertion would produce false failures on both.

Both file-reading assertions test real invariants against real files — neither passes on a vacuous input, and the target version is always read from the single source, never hardcoded in the test, so the test cannot silently agree with a drifted value. Standard classpath/regex guards (`assertNotNull` on the resolved dir, `assertTrue(matcher.find())`) keep a broken glob from passing on nothing.

### The historical floor stays separate — do not collapse it into the target

`ValidatorVerdictVersionStabilityTest.FLOOR` and `CliVersionAtLeastTest`'s `@ValueSource` are **not** the current-target value; they are a durable lower bound on which *historical* generations must keep captured corpora and clear the version floor. Collapsing them into `openspecTargetVersion` would erase the "these older still-supported generations must remain covered" guarantee the moment the target advances. They are deliberately left as explicit lists, and the spec's fourth scenario states this separation as a contract.

### Workflow shell derivation

`.forgejo/workflows/ui-smoke.yaml`'s prerequisite step reads the value from the checked-out `gradle.properties`:

```sh
OSV=$(grep -E '^openspecTargetVersion=' gradle.properties | cut -d= -f2)
npm i -g @fission-ai/openspec@"$OSV"
```

The workflow remains manual-dispatch-only; this only changes *which* CLI version a dispatched (or local) run installs. Because no Gradle tier can prove the shell extraction actually yields the target at runtime, the same one-liner is run once locally against `gradle.properties` and asserted to print `1.9.0` — closing the shell-correctness sliver without an IDE boot. The uiSmoke journeys themselves gate on a `CLI 1.6+` floor (not an exact pin), so installing 1.9 keeps them green with no journey edits.

## Risks / trade-offs

- **The pin bump changes the CLI the journeys run against (1.6.0 → 1.9.0).** The journey scenarios are written "on a host CLI at 1.6+", which 1.9.0 satisfies, and a body-less missing-`SHALL` requirement still errors on 1.9 (1.8's default-mode demotion applies only to body-carrying requirements), so the validate-results journeys are unaffected. The authoritative gate is the maintainer's local `caffeinate -dimsu ./gradlew uiSmoke` run; the journeys are re-confirmed green under 1.9 as part of verifying this change. CI's ui-smoke job is manual-dispatch-only and cannot boot a headful IDE, so it is not the gate.
- **`sed` parse of `gradle.properties`.** Kept to a single unambiguous `key=value` line with no interpolation; the anti-drift test guards the workflow side.

## Out of scope

- The `no_openspec_root` validate/list parser hardening (the remaining optional 1.9 follow-up).
- Any validation rule, severity, fallback, or parity-anchor change (there is none — 1.9 parity is byte-identical to 1.8).
