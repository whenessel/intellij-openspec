# Design — recognize 1.7 config fields

## Why no production change is needed

Three paths could in principle false-flag an unknown config key; none do:

| Path | Behavior on `operations`/`rules`/`defaultStore` |
|---|---|
| `OpenSpecConfig.fromMap` | cherry-picks `schema`/`version`/`context`/`profile`/`rules`; unknown keys ignored, no unknown-key detection. `rules` reader takes only String values, so 1.7's list-shaped `rules` entries are **skipped**, not crashed on. |
| `BuiltInValidator.validateConfig` | keys off `schema` only (INFO if missing; WARNING only for a CLI-authoritative unknown schema). No unknown-key rule. |
| `ConfigValidationInspection` | checks `schema` presence (INFORMATION nudge); the `profile` nag was already removed. No unknown-key rule. |

So the fields are tolerated today. The card's mandate is to **lock** that, not change it — building a
structured `operations.guidance` reader with no consuming UI surface would be speculative modeling the
card explicitly rules out.

## Fixtures (proven real)

- `operations-and-rules.config.yaml` — authored to the exact 1.7 Zod shapes
  (`OperationConfigSchema = {guidance: string[]}`, `operations: {apply?, archive?}`,
  `rules: record<string, string[]>`, from the installed CLI's `dist/core/project-config.js`) and
  **proven CLI-accepted**: with it in place `openspec validate --all --json` exits 0 (the CLI parses
  the config on load, so a bad shape would error there). This is the contract-test discipline for a
  config the CLI validates on read rather than emits.
- `global-config-defaultstore.json` — captured from the global config after `openspec config set
  defaultStore <id>`; the same `{featureFlags, profile, delivery, defaultStore}` shape
  `config list --json` returns once a default store is set.

## Tests

- **Parse layer (pure):** `ConfigServiceTest` — `fromMap` on the operations config reads `schema` and
  yields empty `rules` (list values skipped), no throw.
- **Validation surfaces (platform):** `BuiltInValidatorTest.validateConfig` and
  `ConfigValidationInspection` raise no ERROR and no plugin-invented WARNING on the operations config.
- **`config list` reader (pure):** `WorkflowProfileServiceTest.parseSnapshot` ignores `defaultStore`
  (reads only `profile`/`workflows`).

## Non-goals

No `operations.guidance` reader/injection (an AI-bridge concern, no IDE surface), no `defaultStore`
modeling (machine-level store routing, out-of-model), no config-format axis change (single baseline
retained, no new enum value), no `rules` list-shape modeling (inert until a consumer exists).
