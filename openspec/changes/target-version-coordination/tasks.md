## 1. Introduce the single source

- [x] 1.1 Add `openspecTargetVersion=1.9.0` to `gradle.properties`, with a short comment stating it is the single source of truth for the top-supported OpenSpec CLI version (consumed by the UI-smoke workflow and the `TargetVersionSingleSourceTest` tripwire; the durable historical floor lives separately in `ValidatorVerdictVersionStabilityTest.FLOOR` / `CliVersionAtLeastTest`).

## 2. Derive the UI-smoke CLI install from the single source

- [x] 2.1 In `.forgejo/workflows/ui-smoke.yaml`, replace the hardcoded `npm i -g @fission-ai/openspec@1.6.0` with a version read from `gradle.properties`:
  `OSV=$(grep -E '^openspecTargetVersion=' gradle.properties | cut -d= -f2)` then `npm i -g @fission-ai/openspec@"$OSV"`. Keep the workflow manual-dispatch-only; do not touch the journey code (the journeys gate on a `CLI 1.6+` floor, which 1.9 satisfies).
- [x] 2.2 Verify the shell extraction is correct at runtime (the one thing no Gradle tier can prove): ran `OSV=$(grep -E '^openspecTargetVersion=' gradle.properties | cut -d= -f2); test "$OSV" = "1.9.0"` locally → prints `OK`.

## 3. Add the by-construction tripwire (test)

- [x] 3.1 Add `src/test/java/com/johnnyblabs/openspec/integration/TargetVersionSingleSourceTest.java` (reuse the `repoRoot()` walk-up idiom from `DocumentationHygieneTest`). Assert, all as loud failures:
  - (a) `gradle.properties` contains `openspecTargetVersion=` matching `\d+\.\d+\.\d+` (absent/renamed key fails).
  - (b) `src/test/resources/fixtures/cli/<target>/version.txt` exists and its **trimmed** content equals `<target>`, and `validate-parity-corpus.json` + `validate-parity-corpus-strict.json` exist under that dir.
  - (c) `.forgejo/workflows/ui-smoke.yaml` references `openspecTargetVersion` **and** contains no `@fission-ai/openspec@<digit>` literal. **Scope this assertion to `ui-smoke.yaml` only** — do NOT generalize repo-wide (`.github/workflows/build.yml` `@latest` and `scripts/seed-lifecycle-demo.sh` `@1.3.1` are deliberate).
  - Include classpath/regex guards (`assertNotNull`/`assertTrue(matcher.find())`) so a broken glob/regex fails loudly rather than passing on nothing.
- [x] 3.2 Anti-vacuity evidence: confirmed the test is **RED** before the production edits (no `openspecTargetVersion` → (a) fails; `@1.6.0` → (c)-negative fails) and **GREEN** after (`@"$OSV"` → `@` then `"`, not a digit). Both runs recorded.
- [x] 3.3 Left `ValidatorVerdictVersionStabilityTest.FLOOR` and `CliVersionAtLeastTest`'s `@ValueSource` **unchanged** — the durable floor / boundary spread are independent, additive, hand-maintained lists (both already carry `1.9.0`); collapsing them into the single source would make the guard circular and non-monotonic.

## 4. Document the model

- [x] 4.1 Added a "CLI version targeting & local development" section to `CONTRIBUTING.md` (under "Making Changes", after "Version-support fidelity"): the two-axis model (declared support = a **range** carried in-repo by captured fixtures, floor `1.3.0`/no ceiling; installed CLI = **one** per machine; bridged by fixtures ⇒ `./gradlew build` is version-agnostic), the `openspecTargetVersion` single source, and local-dev disciplines (capture in an isolated `XDG`/`HOME` sandbox; `npx --yes @fission-ai/openspec@<v>` to drive an off-target generation; `openspec update` is adopt-sync-only). Includes the ordered **bump checklist**: capture `fixtures/cli/<new>/{version.txt,validate-parity-corpus.json,-strict}` **first** → bump `gradle.properties` → append `<new>` to `ValidatorVerdictVersionStabilityTest.FLOOR` and `CliVersionAtLeastTest` → `./gradlew build`.
- [x] 4.2 Kept `CONTRIBUTING.md`'s `Maintenance: Reference` label and introduced **no** `plugin vX.Y.Z` string (wrote "OpenSpec CLI 1.9.0") so `DocumentationHygieneTest` stays green — confirmed by the passing build.

## 5. Verify

- [x] 5.1 `./gradlew build` green — full suite (incl. the new `TargetVersionSingleSourceTest`) + JaCoCo coverage floor held. No production `src/main` change → coverage is flat → floor **not** ratcheted.
- [x] 5.2 `DocumentationHygieneTest` green after the `CONTRIBUTING.md` edit (part of the passing `build`).
- [ ] 5.3 **Deferred to the v0.9.0 release-prep local run (recorded per plan).** The headful UI-smoke journeys are re-confirmed under 1.9 via `caffeinate -dimsu ./gradlew uiSmoke` at release-prep, which the `ui-smoke-journeys` spec designates as the **authoritative** gate (CI's ui-smoke job cannot boot headful and is explicitly not the gate). Deferring is safe here because: the journeys gate on a `CLI 1.6+` floor that 1.9 satisfies (no journey edits needed); 1.9's default/strict validate verdicts are byte-identical to the already-passing generations (the committed `1.9.0` parity fixtures pass `ValidatorVerdictVersionStabilityTest` in 5.1), and a body-less missing-`SHALL` still errors on 1.9; the store/coordination JSON model is unchanged since 1.6; and the rendered-UI wiring the journeys assert is CLI-version-independent. The bump's only per-change runtime risk (that the workflow shell actually extracts `1.9.0`) is closed by 2.2.
- [x] 5.4 `openspec validate target-version-coordination --strict` clean; ran the CLAUDE.md homelab-identifier leak scan over the staged files (and the exact pre-push guard pattern) — no matches after rewording task 5.4 to not embed the raw token list.
