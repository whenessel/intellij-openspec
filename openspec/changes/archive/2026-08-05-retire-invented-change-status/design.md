# Design — retire the invented change-status field

## Why it's safe now

The `status:` reader was previously kept because a strict `.openspec.yaml` parser would throw on a legacy file carrying `status:`. Tolerant parsing has shipped, so `status:` is now just an ignored unknown key (strip contract) — the reader is inert and removable.

## The retirement is a compile-time break

Removing `ChangeStatus`, `ChangeMetadata.status`, `ChangeService.getStatus`, and the status params makes every reference fail to compile. That is the safety net: git forces a conscious decision at each site (delete the status-specific assertion, don't restore the symbol). Anything that still compiles but fails at runtime is a genuine regression.

## Production changes

| Area | Change |
|---|---|
| **Scaffold** (`TemplateProvider.openspecYamlTemplate`, `ScaffoldingService`) | Signature `(status)` → `(schema)`; emit `schema: <effective>` (via `OpenSpecSettings.getEffectiveSchema`, as `config.yaml` already does), an **unquoted** `created:`, and **no** `status:`. Matches real `openspec new change`; also kills the self-inflicted `change-schema-incompatible` warning the invented `openspec-change` value produced. |
| **Surface** (`SpecTreeModel`, `SpecTreeCellRenderer`, `OpenSpecListAction`) | `buildChangeLabel` and `ChangeLabelParts` drop the status arg; the renderer drops the status fragment + its color constants; the list output drops the tag. Change node = name (REGULAR) + dimmed `X/Y`. |
| **Archive NPE** (`ChangeService`) | Delete the unguarded `getMetadata().setStatus("archived")` at the end of `archiveChange` — archived-ness is directory location, and this NPE'd on null metadata *after* the move committed. Delete the dead `updateStatus` (zero callers). |
| **Reader** (`ChangeMetadata`, `ChangeService`, `ChangeStatus`) | Remove `status` field + `getStatus`/`setStatus` + the `fromMap` `status` extraction; remove `ChangeService.getStatus`; delete the `ChangeStatus` enum. The other read-only fields (goal/affected_areas/initiative/skip_specs) are untouched. |

## Testing

- **Scaffold contract** (`ScaffoldingContractTest.scaffoldChangeMetadata_matchesRealNewChangeShape`): parse the scaffolded template AND the committed captured `openspec new change` fixture (`baseline-schema-created.openspec.yaml`) through the real `ChangeMetadataParser` — same schema, no status, unquoted created. No new capture needed.
- **Archive NPE regression** (`ChangeServiceIntegrationTest.testArchivesChangeWithNullMetadata` + a malformed variant): archive a change with null metadata (no `.openspec.yaml` / malformed) and assert no exception + the change is archived. Red on the pre-change `setStatus` line.
- **Self-inflicted warning gone** (`BuiltInValidatorTest.testScaffoldSchemaProducesNoChangeSchemaWarning`): a `spec-driven` scaffold does not trip `change-schema-incompatible` even with an authoritative known-set.
- **Legacy tolerance** (`ChangeMetadataContractTest`): a legacy `status:`-carrying file parses non-malformed (status ignored) — the reason retirement is safe.
- **Surface** (`SpecTreeCellRendererTest`/`SpecTreeModelLabelTest`): the name-first + dimmed-count contract stays covered; the status-tag tests are deleted.
- **uiSmoke NOT load-bearing**: neither headful surface asserts `[status]`; the demo fixture already has no `status:` line. `verifyPlugin` will pass (fragment removal only, no new API).
