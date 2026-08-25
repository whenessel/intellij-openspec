## MODIFIED Requirements

### Requirement: Store registration outcome semantics across CLI generations

The plugin SHALL treat `openspec store register --json` outcomes according to the CLI's own parsed report rather than plugin-side assumptions about root health. A registration the CLI reports as successful SHALL be treated as success even when the root lacks the planning directories (`openspec/specs`, `openspec/changes`, `openspec/changes/archive`) — the CLI 1.6+ fresh/config-only-root case. A registration refusal SHALL be surfaced from the parsed uniform `status[]` envelope with the entry's `fix` remediation verbatim; this includes the 1.6 refusal codes `invalid_store_pointer` and `store_root_pointer_declared` (registering a root whose `openspec/config.yaml` declares `store:`) and the 1.5-generation refusal code `store_register_root_unhealthy`. The plugin SHALL NOT special-case any refusal code in a way that breaks when a code is absent on another supported CLI generation.

The initial `store register` invocation SHALL NOT pass `--yes` (a probe), so a root that needs no confirmation behaves as before and the plugin never silently writes store-identity metadata. When the CLI reports `store_register_identity_confirmation_required` — a healthy OpenSpec root that is not yet a store — the plugin SHALL present an actionable confirmation (Yes/Cancel) carrying the CLI's own `message` and `fix` text verbatim, and SHALL NOT surface it as a dead-end failure. On confirmation the plugin SHALL re-invoke `store register <path> --yes --json`, creating the store-identity metadata (`.openspec-store/store.yaml`) non-interactively, and SHALL refresh the surface on success. On cancellation the plugin SHALL leave the root unregistered and SHALL NOT show an error dialog. This mirrors the confirm-plus-`--yes` idiom the plugin already applies to the destructive removals, adapted to a non-destructive metadata creation.

#### Scenario: Fresh root registers successfully on CLI 1.6+
- **WHEN** `store register` succeeds for a root that lacks the planning directories
- **THEN** the plugin SHALL treat the registration as a success and refresh the store listing, with the new store presented without any unhealthy or error marker

#### Scenario: Healthy not-yet-a-store root prompts, then registers on confirmation
- **WHEN** Register Existing Store is invoked on a healthy OpenSpec root that does not yet carry `.openspec-store/store.yaml`, and the probe `store register <path> --json` returns `store_register_identity_confirmation_required`
- **THEN** the plugin SHALL present a confirmation carrying the CLI's `message` and `fix`, and on confirmation SHALL invoke `store register <path> --yes --json`, the CLI SHALL create the store-identity metadata and report success, and the plugin SHALL treat it as success and refresh the surface

#### Scenario: Cancelling the identity confirmation registers nothing
- **WHEN** the user declines the identity confirmation
- **THEN** the plugin SHALL NOT invoke `store register` with `--yes`, SHALL leave the root unregistered, and SHALL NOT display an error dialog

#### Scenario: Pointer-root refusal surfaced with its fix
- **WHEN** `store register` fails with `invalid_store_pointer` or `store_root_pointer_declared`
- **THEN** the plugin SHALL surface the parsed `status[]` message and its `fix` string verbatim, and SHALL NOT display raw CLI stderr

#### Scenario: 1.5-generation unhealthy-root refusal still parsed
- **WHEN** a 1.5-generation CLI fails `store register` with `store_register_root_unhealthy`
- **THEN** the plugin SHALL surface the parsed failure message and its `fix` remediation exactly as before
