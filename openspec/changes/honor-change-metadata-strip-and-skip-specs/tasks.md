# Tasks

## 1. Fixtures — captured real 1.7.0 CLI output
- [x] 1.1 Under an isolated env (`HOME`/`XDG_DATA_HOME`/`XDG_CONFIG_HOME` in a temp dir, `OPENSPEC_TELEMETRY=0`, `openspec init --tools none`), capture into `src/test/resources/fixtures/cli/1.7.0/change-metadata/`: `baseline-schema-created.openspec.yaml` (verbatim `openspec new change`), `new-change-goal.openspec.yaml` (verbatim `openspec new change … --goal "…"`), and derived-but-reader-accepted `rich-all-fields.openspec.yaml` (schema+created+goal + `affected_areas: [services, model]` + `initiative: {store, id}` + `skip_specs: true`) and `skip-specs-only.openspec.yaml`.
- [x] 1.2 Capture a **negative-control** in the same session: a deliberately wrong-shaped `initiative-as-string.openspec.yaml`; prove `openspec status --change <name> --json` returns an error block for it but exit-0/no-error for the rich/skip-specs files — so the derived-fixture acceptance check is shown to discriminate. Record the acceptance command in `fixtures/cli/README.md`.
- [x] 1.3 Copy the archived on-disk `openspec/changes/archive/2026-03-18-config-yaml-viewer/.openspec.yaml` → `legacy-status-proposed.openspec.yaml` (authentic legacy `schema: openspec-change` / `status: proposed` shape). Author `malformed.openspec.yaml` as a deliberate corrupt file (labeled — a user typo, not a CLI shape).
- [x] 1.4 Non-dot basenames (loaded by explicit resource path); grep the new fixtures + README vendor-neutral before commit.

## 2. Model + parser
- [x] 2.1 Broaden `ChangeMetadata`: add read-only `goal` (String), `affectedAreas` (`List<String>`), `initiative` (`Map<String,String>`), `skipSpecs` (`Boolean`) with getters (+ setters for bean round-trip). Add static `fromMap(Map<String,Object>)` cherry-pick — `created` accepts `String` OR `java.util.Date` (format `Date`→`yyyy-MM-dd` in UTC); `affected_areas` filtered `List`; `initiative` nested `Map`; `skip_specs` boxed `Boolean`. Keep `schema`/`status`/`created` + `getStatus`.
- [x] 2.2 Add `ChangeMetadataParser.parse(String) -> ParseResult` (untyped `Yaml().load` in try; `MarkedYAMLException` → `malformed(problem)`; `Map` → `fromMap`; null/non-Map → empty non-null metadata, no warning; non-marked exceptions propagate).

## 3. Wire ChangeService (threading preserved)
- [x] 3.1 Rewire `getChangesFromDir` — in BOTH the EDT and `ReadAction.compute` branches read the `.openspec.yaml` to a `String` **inside** that branch, then `ChangeMetadataParser.parse(...)`. `malformed` → the existing `WARNING` balloon with `result.problem()`; else `setMetadata`. Keep the generic `catch (Exception){ LOG.warn }` (no balloon) path; drop now-unused `Constructor`/`LoaderOptions` imports. Leave `getStatus` and the `archiveChange` `setStatus` call untouched.

## 4. Tests
- [x] 4.1 `ChangeMetadataContractTest` (plain JUnit5 over `ChangeMetadataParser.parse`): goal file → non-null + `created=="2026-08-05"` EXACT + `goal` set + `status==null`; baseline → schema/created intact, new fields absent-safe; rich → `affectedAreas==["services","model"]`, `skipSpecs==true`, `initiative` store/id; skip-specs-only → `skipSpecs==true`; legacy → `status=="proposed"`/`PROPOSED`; malformed → `malformed` result. (Assert parsed values only — the model tier emits no validation issues.)
- [x] 4.2 `ChangeMetadataParserToleranceTest#toleratesUnknownFutureKey` — real baseline bytes + synthetic `zzz_future_key:` line → non-null, schema/created intact, not malformed (labeled forward-compat policy test).
- [x] 4.3 `ChangeServiceMetadataToleranceTest` (`BasePlatformTestCase`, `MockedStatic<OpenSpecNotifier>`): valid goal file → non-null metadata + `notify` never; malformed file → null metadata + `notify(... WARNING)` once (positive control). Loads the same fixture bytes into a temp-project change dir.
- [x] 4.4 Extend `ModelTest.ChangeMetadataTest` with getter/setter round-trip for the four new fields. Confirm `ChangeServiceIntegrationTest#testDetectsChangeStatus` stays green (backward-compat anchor).

## 5. Docs, spec, build
- [x] 5.1 `plugin-core` delta authored (MODIFIED *Configuration parsing* — whole requirement restated + lenient clause + new scenario; Malformed YAML scenario corrected to actual behavior). `openspec validate --strict` passes.
- [x] 5.2 `CHANGELOG.md` `## Unreleased` (Fixed) — vendor-neutral, plugin-user-facing: change metadata using newer OpenSpec keys (e.g. `goal`) no longer triggers a spurious parse-error warning or shows as UNKNOWN.
- [x] 5.3 `./gradlew build` green (unit + `verifyPlugin` unaffected); run `jacocoTestReport`; if the counters rise, ratchet the JaCoCo floors ~0.006 below the measured values with a recorded justification comment.
- [x] 5.4 Pre-commit leak scan on staged files for internal-tracker identifiers; confirm fixtures, spec, and changelog are vendor-neutral for the public mirror.
