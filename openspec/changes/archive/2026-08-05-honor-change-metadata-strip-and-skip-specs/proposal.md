# Honor the `.openspec.yaml` strip contract; model newer change-metadata fields read-only

## Why

Each change's `.openspec.yaml` is parsed by `ChangeService` with a **strict** typed SnakeYAML `Constructor(ChangeMetadata.class)`, and `ChangeMetadata` models only `schema` / `status` / `created`. Upstream OpenSpec's real `.openspec.yaml` also carries optional `goal` / `affected_areas` / `initiative` (since 1.4) and `skip_specs` (1.7), and its schema simply **strips** unknown keys. Any key the plugin does not model makes the strict parse throw a `ConstructorException` (a `MarkedYAMLException`), which fires a user-facing "`.openspec.yaml` parse error" **warning balloon on every change-list refresh** and leaves the change's metadata `null` (it renders as `UNKNOWN`).

This is reachable today: `openspec new change x --goal "…"` writes a CLI-valid file the plugin rejects. It breaks the invariant the plugin holds itself to — **the plugin must never be more restrictive than the OpenSpec CLI.**

## What Changes

- Parse a change's `.openspec.yaml` **leniently** — unrecognized or newer keys are ignored, not errored — matching upstream's strip contract, by adopting the same untyped `Yaml().load` → `Map` → `fromMap` cherry-pick idiom the plugin's own `config.yaml` reader already uses.
- Model `goal` / `affected_areas` / `initiative` / `skip_specs` on `ChangeMetadata` as **read-only** (for display/labeling only — no validation gate is added).
- Reserve the warning balloon for **genuinely malformed** `.openspec.yaml`.
- Retain the `schema` / `status` / `created` readers unchanged, so a legacy plugin-scaffolded file that still carries `status:` parses identically.

## Capabilities

- **plugin-core** — MODIFIED: the *Configuration parsing* requirement now guarantees lenient change-metadata parsing (unknown/newer keys ignored; a valid file parses to non-null metadata with no warning; the warning is reserved for genuinely malformed `.openspec.yaml`).

## Impact

- Fixes the spurious parse-error warning and `UNKNOWN` status on any change whose `.openspec.yaml` uses `goal` / `affected_areas` / `initiative` / `skip_specs` (or any future key).
- Removes the primary trigger of a separate archive-path null dereference (retirement of that path is a follow-up change; its guard is not folded in here).
- Read-only modeling only; `skip_specs` is **not** honored in the plugin's own archive path (deferred until a real case appears).
- New read-only fields are covered by captured-real-CLI contract fixtures under `fixtures/cli/1.7.0/change-metadata/`.
