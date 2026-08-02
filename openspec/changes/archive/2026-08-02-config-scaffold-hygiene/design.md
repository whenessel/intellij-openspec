## Context

`TemplateProvider.configYamlTemplate(schema, version)` writes a fresh `config.yaml` as `schema:` + `version: "<v>"` + `profile:\n  name: default` + `context: ""` + `rules: {}`. Upstream `openspec init` writes only `schema: spec-driven`. `version:` and `profile:` are not in upstream's Zod `ProjectConfigSchema` (it ignores them); the plugin reads them **only for cosmetic display** — `SpecTreeModel` renders a `version:`/`profile:` tree node (both guarded to omit when absent), and `ExploreContextService` appends an inert `- Version: <value>` AI-context line (unguarded → prints `Version: null` when absent). Neither drives behavior: `getEffectiveVersion` falls back to the single config-format baseline when `version:` is absent, and `profile:` (the config map) feeds nothing but the tree node.

The plugin-core "Project detection and initialization" requirement already specifies only `schema:` for the built-in init; the code writes more than the spec requires. This change aligns them.

## Goals / Non-Goals

**Goals:**
- A freshly-scaffolded `config.yaml` matches upstream `openspec init` (schema-only) — no plugin-invented fields in the user's upstream-owned file.
- Full back-compat: a legacy config carrying `version:`/`profile:` still parses and still shows its tree nodes.
- Fix the `Version: null` AI-context noise.
- Dogfood the clean shape in the plugin's own `config.yaml`.

**Non-Goals:**
- No rename of `VersionSupport`/`V1_2` → `ConfigFormat`/`BASELINE` — deferred to `rename-versionsupport-to-configformat` (it ripples into the validation spec). This change touches no type names.
- No removal of the `version:`/`profile:` *readers* — they stay for legacy configs. Only the *writer* (scaffolding) changes.
- No change to config *validation* (absence of `version:`/`profile:` is already accepted, per the `validation` capability).

## Decisions

**1. Scaffold `schema:`-only.** `configYamlTemplate` becomes `configYamlTemplate(schema)` returning just `schema: <schema>\n`. `ScaffoldingService` drops the `version` argument it computed from `VersionSupport`. Matching `openspec init` exactly (no empty `context`/`rules` either — those are upstream fields but empty values are noise; a user adds them if wanted).

**2. Keep the readers, guard the AI line.** `SpecTreeModel` is unchanged (already omits absent `version:`/`profile:` nodes). `ExploreContextService` omits the `- Version:` line entirely when `config.getVersion()` is null/empty (rather than printing `null`). `getEffectiveVersion`'s config fallback is unchanged — absent `version:` resolves to the baseline.

**3. Clean the plugin's own `config.yaml`.** Remove its `version: "1.2.0"` + `profile:` block + the explanatory comment. `getEffectiveVersion` then resolves the baseline via fallback — the plugin's own validation is unaffected (verified: `version:` absence is accepted and inert).

**4. Reframe the CLAUDE.md note.** The "Plugin-internal config fields — audit before aligning to upstream" section uses `version:`-in-config.yaml as its worked example. Since `version:` is no longer written into config.yaml, update the example (the general grep-before-removing rule stays; anchor it to a still-live plugin-internal field, and note `version:`'s retirement from the scaffolded config).

## Risks / Trade-offs

- **[A user/tool relied on the plugin writing `version:`/`profile:`]** → None do — they're cosmetic-only and upstream ignores them; and legacy configs that have them are still read. No behavior depends on them.
- **[The plugin's own `config.yaml` losing `version:` breaks its self-validation]** → Verified safe: `version:` absence resolves to the baseline via `getEffectiveVersion` fallback, and config validation accepts an absent `version:`. (This is the same field the `c34c7b2` incident restored — but it is now optional/inert, not required.)

## Migration Plan

None needed — pure scaffold/writer change. Existing projects are untouched (their configs are read as-is). Rollback is reverting the diff.
