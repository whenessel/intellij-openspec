## Why

The plugin's scaffolding writes **plugin-invented fields into the user's `openspec/config.yaml`** — a file that belongs to upstream OpenSpec. `openspec init` writes only `schema: spec-driven`, but the plugin's `TemplateProvider.configYamlTemplate` writes `schema` + `version:` + `profile:` + empty `context: ""`/`rules: {}`. `version:` and `profile:` are not in upstream's Zod schema (it ignores them), and both are read by the plugin only for **cosmetic display** (a tree node; `version:` also an inert, unguarded AI-context line that prints `Version: null` when absent). So the plugin pollutes every fresh config it generates with fields the CLI never reads and that drive no behavior.

## What Changes

- **Scaffolded `config.yaml` is `schema:`-only**, matching `openspec init`. `TemplateProvider.configYamlTemplate` stops writing the plugin-invented `version:` and `profile:` and the empty `context: ""`/`rules: {}`. `ScaffoldingService` drops the now-unused version argument it passed in.
- **The plugin keeps *reading* `version:`/`profile:` for back-compat** — an existing config that has them still parses and still shows its tree-view nodes; only new scaffolding omits them. `version:` remains the read-only config-format-axis fallback (`getEffectiveVersion` → default baseline when absent), so validation behavior is unchanged.
- **Fix the `Version: null` bug** in `ExploreContextService`: a config with no `version:` currently appends `- Version: null` to the AI context. Omit the line when the value is absent/empty (the field is inert, so a null line is pure noise).
- **Clean the plugin's own `openspec/config.yaml`** — remove its `version: "1.2.0"` and `profile:` block (and the now-moot explanatory comment) so the plugin dogfoods the schema-only shape it now scaffolds. Validation still defaults to the baseline via the `getEffectiveVersion` fallback.
- **Update `CLAUDE.md`** — the "Plugin-internal config fields" section's `version:`-in-config.yaml example is now moot (the field is no longer written into config.yaml). Keep the general grep-before-removing rule but reframe it around a still-live plugin-internal field, and note `version:`'s retirement from the scaffolded config.

**Out of scope (separate follow-up `rename-versionsupport-to-configformat`):** renaming the misnamed config-format-axis type `VersionSupport`/`V1_2` (which reads as a CLI-version claim it isn't) to `ConfigFormat`/`BASELINE`. That rename ripples into the `validation` spec's many `VersionSupport.V1_2` references, so it is split out to keep this change low-risk and focused on the scaffold pollution.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `plugin-core`: The built-in init's `config.yaml` is made explicitly `schema:`-only (matching upstream `openspec init`) — the plugin no longer writes the plugin-invented `version:`/`profile:` or empty `context`/`rules`. This tightens the existing "Project detection and initialization" requirement, which already only specified `schema:`.

## Impact

- **Code:** `TemplateProvider.configYamlTemplate` (schema-only shape) + `ScaffoldingService` (drop the version argument); `ExploreContextService` (guard the `Version:` line). No rename, no public API change, no upstream contract change.
- **Tests:** a test asserting a freshly-scaffolded `config.yaml` contains `schema:` and NOT `version:`/`profile:`; a regression that a legacy config carrying `version:`/`profile:` still parses and still surfaces its tree nodes; `ExploreContextService` no longer emits `Version: null` when the field is absent.
- **Docs:** `CLAUDE.md` (plugin-internal-config-fields section) and `CHANGELOG`.
- **Behavior:** no functional change — `version:`/`profile:` were cosmetic-only. New projects get a cleaner, upstream-faithful `config.yaml`; legacy configs are fully back-compatible.
- **Platform compatibility:** no platform-API surface touched; continues to support 2024.2+.
