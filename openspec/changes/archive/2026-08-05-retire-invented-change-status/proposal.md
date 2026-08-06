# Retire the invented `.openspec.yaml` change-status field + fix the scaffold shape

## Why

OpenSpec has **no change-status concept** — a change is *active* or *archived* purely by directory location (`changes/` vs `changes/archive/`). The plugin invented a `status:` field on a change's `.openspec.yaml`: it **wrote** `status: proposed` when scaffolding, **surfaced** a `[proposed]`/`[applied]` tag in the tree and the list, and stamped an invented `schema: openspec-change` value the CLI doesn't recognize. That off-model field also carried a **live latent bug**: `ChangeService.archiveChange` did an unguarded `getMetadata().setStatus("archived")` on possibly-null metadata *after* the directory move had already committed, NPE-ing into a half-succeeded archive (the change moved, the error was swallowed, the tool window never refreshed). Now that tolerant `.openspec.yaml` parsing has shipped, a legacy file still carrying `status:` parses fine as an ignored key — so the reader can be retired safely.

## What Changes

- **Stop writing** `status:` — the scaffolded `.openspec.yaml` matches real `openspec new change`: the project's **effective schema** (not the invented `openspec-change`), an **unquoted** `created:` date, and no `status:`.
- **Stop surfacing** the `[proposed]`/`[applied]` tag — the change node reads *name-first* with just the dimmed `X/Y` task count; active vs archived is the tree's Changes-vs-Archive location.
- **Delete** the dead `ChangeService.updateStatus` and the archive-path `setStatus` NPE.
- **Retire the reader** — `ChangeMetadata.status`/`getStatus`/`setStatus`, `ChangeService.getStatus`, and the `ChangeStatus` enum, all now unused.

## Capabilities

- **tree-view** — MODIFIED: the *Changes tree display* requirement's change-node hierarchy no longer includes a `[status]` tag; the node is name (primary) + dimmed `X/Y` count.

## Impact

- Removes the self-inflicted `change-schema-incompatible` warning the invented `openspec-change` scaffold value produced, and removes the primary trigger (now the full cause) of the archive NPE.
- A legacy `.openspec.yaml` carrying `status:` still parses (the key is ignored — strip contract); a compile-time break forced a conscious decision at every retired reference.
- No headful uiSmoke change is required (no headful surface asserts the `[status]` tag; the demo fixture already has no `status:` line); the scaffold shape is contract-tested against captured real `openspec new change` output, and the archive NPE has a regression test that is red on the pre-change code.
