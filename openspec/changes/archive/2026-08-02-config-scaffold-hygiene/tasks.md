## 1. Scaffold schema-only

- [x] 1.1 Change `TemplateProvider.configYamlTemplate` to `configYamlTemplate(String schema)` returning just `schema: <schema>\n` (drop `version:`, `profile:`, `context: ""`, `rules: {}`), matching upstream `openspec init`.
- [x] 1.2 Update `ScaffoldingService`'s call site to drop the `version` argument (it computed it from `VersionSupport`); remove any now-dead version computation local to that path.

## 2. Fix the Version:null AI-context bug

- [x] 2.1 In `ExploreContextService`, only append the `- Version:` line when `config.getVersion()` is non-null/non-empty (currently it prints `- Version: null` when absent). Mirror the existing null-guard on the adjacent `Schema:` line.

## 3. Keep the readers (back-compat)

- [x] 3.1 Verify `SpecTreeModel` still renders `version:`/`profile:` nodes from a legacy config (it already guards `!= null && !isEmpty`) — no change needed; add/keep a test.
- [x] 3.2 Verify `getEffectiveVersion` resolves the config-format baseline when `version:` is absent — no change; the scaffold-omission is behavior-safe.

## 4. Clean the plugin's own config.yaml + CLAUDE.md

- [x] 4.1 Remove `version: "1.2.0"`, the `profile:` block, and the explanatory comment from this repo's `openspec/config.yaml` (leave `schema: spec-driven` + the `context`/`rules` the repo actually uses). Confirm `openspec validate --all` stays clean and the plugin's self-validation is unaffected.
- [x] 4.2 Update `CLAUDE.md` "Plugin-internal config fields" — the `version:`-in-config.yaml worked example is now moot (no longer written into config.yaml). Keep the grep-before-removing rule; re-anchor its example on a still-live plugin-internal field and note `version:`'s retirement from the scaffolded config.

## 5. Tests

- [x] 5.1 Scaffold test: a freshly-generated `config.yaml` (built-in init) contains `schema:` and does NOT contain `version:`, `profile:`, or empty `context:`/`rules:`. Cover both the default-schema and chosen-schema paths (the existing plugin-core init scenarios).
- [x] 5.2 Back-compat test: a legacy `config.yaml` string carrying `version:`/`profile:` still parses (`OpenSpecConfig`) and `SpecTreeModel` still surfaces its version/profile nodes.
- [x] 5.3 `ExploreContextService` test: with a config that has no `version:`, the assembled AI context does NOT contain `Version: null` (and omits the line entirely).
- [x] 5.4 `./gradlew build` (suite + JaCoCo floor). No `verifyPlugin` risk (no platform-API surface touched).

## 6. Docs

- [x] 6.1 `CHANGELOG.md` (`## Unreleased`): "New projects get a cleaner `openspec/config.yaml` — the plugin now scaffolds only the upstream `schema:` field, no longer writing plugin-internal `version:`/`profile:` fields the OpenSpec CLI ignores. Existing configs are unaffected."
- [x] 6.2 Grep docs for references to the plugin writing `version:`/`profile:` into config.yaml and update (feature-reference / getting-started, if any).
