# Tasks

## 1. Stop writing + fix scaffold shape
- [x] 1.1 `TemplateProvider.openspecYamlTemplate` — signature `(status)` → `(schema)`; emit `schema: %s`, an unquoted `created: %s`, and no `status:`.
- [x] 1.2 `ScaffoldingService` — pass `OpenSpecSettings.getEffectiveSchema(project)` (as `config.yaml` scaffolding already does).

## 2. Stop surfacing the [status] tag
- [x] 2.1 `SpecTreeModel` — `buildChangeLabel(name, counts)` (drop status); `ChangeLabelParts(name, counts)`; drop the `getStatus` call.
- [x] 2.2 `SpecTreeCellRenderer` — drop the status fragment + `statusAttributes` + the PROPOSED/APPLIED color constants + the `ChangeStatus` import.
- [x] 2.3 `OpenSpecListAction` — drop the `[status]` suffix from the active-changes list.

## 3. Delete dead writers + the archive NPE
- [x] 3.1 `ChangeService` — delete the dead `updateStatus` (zero callers) and the `archiveChange` `getMetadata().setStatus("archived")` line (the live latent NPE on null metadata after the move committed).

## 4. Retire the reader (safe now that tolerant parsing shipped)
- [x] 4.1 `ChangeMetadata` — remove `status` field + `getStatus`/`setStatus` + the `fromMap` `status` extraction.
- [x] 4.2 `ChangeService.getStatus` removed; delete the `ChangeStatus` enum + `ChangeStatusTest`.

## 5. Tests
- [x] 5.1 `ScaffoldingContractTest.scaffoldChangeMetadata_matchesRealNewChangeShape` (vs captured `baseline-schema-created.openspec.yaml`); rewrite the schema/YAML tests; `TemplateProviderTest` for the new signature.
- [x] 5.2 `ChangeServiceIntegrationTest` — delete `testDetectsChangeStatus`; add `testArchivesChangeWithNullMetadata` + malformed variant (red on pre-change code).
- [x] 5.3 `BuiltInValidatorTest.testScaffoldSchemaProducesNoChangeSchemaWarning` (self-inflicted warning gone).
- [x] 5.4 Adapt `SpecTreeModelLabelTest` / `SpecTreeCellRendererTest` (2-arg label/parts; delete status-tag tests, keep name-first + dimmed-count); `ModelTest` / `ChangeMetadataContractTest` (drop status assertions; legacy file tolerates the ignored `status:` key).

## 6. Docs, spec, build
- [x] 6.1 `tree-view` delta — MODIFIED *Changes tree display* (Change-node visual hierarchy scenario drops the `[status]` tag). `openspec validate --strict` passes.
- [x] 6.2 `CHANGELOG.md` `## Unreleased` (Changed) — vendor-neutral, plugin-user-facing.
- [x] 6.3 `./gradlew build` green; coverage held (no ratchet).
- [x] 6.4 Pre-commit leak scan; confirm vendor-neutral for the public mirror.
