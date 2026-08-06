# Recognize new 1.7 config fields (operations / rules / defaultStore) as tolerated

## Why

1.7 adds optional config surfaces: project `openspec/config.yaml` gains
`operations: {apply?/archive?: {guidance: string[]}}` and a real `rules: {<artifactId>: string[]}`
shape, and the machine-level global config gains `defaultStore`. The plugin's config reader is
already tolerant (untyped `Yaml().load` → Map → `fromMap` cherry-picks known keys), and neither
`BuiltInValidator.validateConfig` nor `ConfigValidationInspection` flags unknown keys — so these are
ignored cleanly today, with no parity issue. What is missing is a captured fixture and a test that
*lock* that no-false-flag behavior, so a future change can't silently start being stricter than the
client. This does the minimal recognition — it does **not** build a structured `operations.guidance`
reader ahead of any consuming UI surface (that would be speculative modeling); the new fields stay
optional and inert.

## What Changes

- **Capture real 1.7 config shapes** into `fixtures/cli/1.7.0/config-validation/`:
  `operations-and-rules.config.yaml` (proven CLI-accepted — `openspec validate --all` exits 0 with it
  in place) and `global-config-defaultstore.json` (the global config after `config set defaultStore`,
  the same shape `config list --json` returns).
- **Lock the tolerance with tests** (no production change): `ConfigServiceTest` asserts `fromMap`
  reads `schema` and skips the list-valued `rules` without crashing; `BuiltInValidatorTest` +
  `ConfigValidationInspectionTest` assert neither raises an ERROR or a plugin-invented WARNING on the
  operations config; `WorkflowProfileServiceTest` asserts `parseSnapshot` ignores `defaultStore`.
- **Docs:** note the recognition in `docs/cli-versions/1.7.md` and the support matrix.

## Capabilities

No capability spec changes. This conforms to the existing `validation` "Config validation" contract
(the plugin defers to the CLI and never reds a config the CLI accepts); it adds/removes no capability
and changes no production code. The config-format axis stays pinned at its single baseline — no new
axis enum value. `config-version-unknown` was already removed by the earlier validator-relaxation
change. Marked `skip_specs: true`.

## Impact

- Fixtures + tests only (`ConfigServiceTest`, `BuiltInValidatorTest`, `ConfigValidationInspectionTest`,
  `WorkflowProfileServiceTest`) + manifest + docs. No `src/main` change.
