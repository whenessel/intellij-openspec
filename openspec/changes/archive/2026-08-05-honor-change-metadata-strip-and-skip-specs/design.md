# Design — tolerant `.openspec.yaml` parse + read-only newer fields

## Root cause

`ChangeService.getChangesFromDir` parses each change's `.openspec.yaml` with a strict typed constructor in both threading branches:

```java
Yaml yaml = new Yaml(new Constructor(ChangeMetadata.class, new LoaderOptions()));
metadata = yaml.loadAs(is, ChangeMetadata.class);
```

`ChangeMetadata` has only `schema`/`status`/`created`. An unknown property makes `loadAs` throw a `ConstructorException` (subclass of `MarkedYAMLException`), which the surrounding `catch (MarkedYAMLException)` turns into a `WARNING` notification and leaves `metadata == null`. Upstream's real change metadata carries `goal`/`affected_areas`/`initiative`/`skip_specs`, so a CLI-valid file trips this on every refresh.

## Approach — reuse the config reader's lenient idiom (not `setSkipMissingProperties`)

The plugin already parses `config.yaml` leniently: `new Yaml().load(text)` (untyped) → `Map` → `OpenSpecConfig.fromMap` cherry-picks known keys with `instanceof` guards. We adopt the **same** idiom for change metadata rather than `Constructor.setSkipMissingProperties(true)`, because:

1. It reuses the codebase's single leniency pattern instead of introducing a second.
2. `setSkipMissingProperties` would **drop** the very fields we now want to read unless we also register `TypeDescription`s for the list and the nested `initiative` object.
3. A pure `parse(String)` / `fromMap(Map)` seam is unit- and contract-testable off-platform (like `CliOutputParser`), covering both the ok and malformed branches — important for the thin BRANCH coverage floor.

### New pure seam: `ChangeMetadataParser.parse(String) -> ParseResult`

```
new Yaml().load(text)            // untyped
  catch MarkedYAMLException  -> ParseResult.malformed(problem)   // genuinely broken YAML only
  Map                        -> ChangeMetadata.fromMap(map)
  null / non-Map top-level   -> empty (non-null, all-null-fields) ChangeMetadata, no warning
  (non-marked RuntimeException propagates — preserves today's log-only path)
```

`ParseResult { ChangeMetadata metadata; boolean malformed; String problem; }`.

### `ChangeMetadata.fromMap(Map)` cherry-pick

Mirrors `OpenSpecConfig.fromMap`, one `instanceof`-guarded key at a time:

| Key | Type handling |
|---|---|
| `schema`, `status`, `goal` | `String` |
| `created` | **`String` OR `java.util.Date`** — untyped SnakeYAML resolves a bare `created: 2026-08-05` scalar to a `Date`; format any `Date` to `yyyy-MM-dd` **in UTC** (the resolved value is UTC-midnight; local-tz formatting day-shifts to the previous day). A naive `instanceof String`-only check would silently drop `created`. |
| `affected_areas` | YAML sequence → `List<String>` filtered to String elements (default empty list) |
| `initiative` | YAML mapping → `Map<String,String>` (`store`/`id`) — a nested object, **not** a String |
| `skip_specs` | boxed `Boolean` (`null` = not declared) |

All four new fields are **read-only** (display/labeling). No validation gate is added — adding a `skip_specs`- or delta-count rule would recreate the very over-restriction this change removes.

### `ChangeService` rewiring (threading preserved)

Keep the existing branch structure exactly: perform the VFS read to a `String` **inside** the EDT branch and **inside** `ReadAction.compute` for the off-EDT branch (do **not** hoist the VFS read next to the pure parser). Then call `ChangeMetadataParser.parse(text)`. `malformed` → the existing `WARNING` balloon with `result.problem()`; otherwise `change.setMetadata(result.metadata())`. The generic `catch (Exception) { LOG.warn }` (no balloon) path is preserved for non-marked failures, keeping today's warn-vs-silent split exact. `getStatus()` and the `archiveChange` `setStatus` call are untouched (the status reader stays; retiring the status write/surface and guarding the archive NPE are a separate change).

## Not-more-restrictive scope

The parse is version-agnostic — no version branch anywhere. The lenient cherry-pick never looks up unmodeled keys, so it can never error on them, inherently mirroring upstream's strip. **Nuance:** making metadata non-null re-activates `BuiltInValidator`'s existing `change-schema-incompatible` lint (guarded by `getMetadata() != null`). So the "no warning" guarantee is scoped to a **resolvable schema value**; a file whose `schema:` value is genuinely unknown is still linted by the existing rule (that rule's own relaxation belongs to the validator-parity work, not here). A real CLI file (`schema: spec-driven`) is unaffected.

## Backward compatibility

`schema`/`status`/`created` readers and properties are retained. Legacy plugin-scaffolded files carrying `status: proposed` still parse to non-null metadata with `getStatus() → PROPOSED`; the existing `ChangeServiceIntegrationTest#testDetectsChangeStatus` is the regression anchor. A `schema`/`created`-only file parses identically to today.

## Testing

- **Contract** (plain JUnit5 over `ChangeMetadataParser.parse`, against captured real 1.7.0 fixtures): the real `new change --goal` file → non-null, `created == "2026-08-05"` exact (catches the Date trap), `goal` set, `status == null`; a rich file → `affectedAreas == ["services","model"]` (a List), `skipSpecs == true` (boolean), `initiative.store/id` (nested object); a `skip_specs`-only file; the legacy `status: proposed` file → `PROPOSED`; a genuinely malformed file → `malformed` result.
- **Tolerance/forward-compat** (labeled policy test): real baseline bytes + a synthetic `zzz_future_key:` line → still non-null, no malformed classification. Guards against a fix that models only today's keys strictly.
- **Negative control fixture**: a deliberately wrong-shaped `initiative` (string instead of object) captured in the same isolated session, to prove the `openspec status --json` reader-acceptance check actually discriminates good from bad derived fixtures.
- **Integration** (`BasePlatformTestCase`, `MockedStatic<OpenSpecNotifier>`): valid `--goal` file → non-null metadata + `notify(...)` never called; malformed file → null metadata + `notify(... WARNING)` exactly once (positive control).
- **Bean**: getter/setter round-trip for the four new fields.
- `verifyPlugin`/`uiSmoke` are **not** load-bearing here (pure service/model/SnakeYAML change; `OpenSpecNotifier.notify` is unchanged; no new `com.intellij.*` reference).

## Decisions

- `initiative` as `Map<String,String>` (single lenient pattern) over a small record — display-only either way.
- `created` normalized by UTC `yyyy-MM-dd` formatting in `fromMap` (over disabling implicit-timestamp resolution).
- Non-Map top-level YAML → empty non-null metadata, no warning (favor not-more-restrictive).
- Model-only; surfacing `goal`/`initiative` in the tree and honoring `skip_specs` in the plugin's own archive path are out of scope. The `1.7.x`-supported doc line is deferred to the support-declaration change; this change adds only a scoped `fixtures/cli/1.7.0/change-metadata/` dir.
