package com.johnnyblabs.openspec.util;

import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.model.ArtifactInstruction;
import com.johnnyblabs.openspec.model.ArtifactStatus;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests that parse real CLI JSON output captured as fixtures.
 * If the CLI output format changes, update the fixtures under
 * src/test/resources/fixtures/cli/ with fresh output and fix any failures.
 *
 * <p>Fixtures are per CLI generation: the versionless root files are frozen legacy
 * captures (see {@code fixtures/cli/README.md}), and the {@code 1.6.0/} twins are
 * asserted by the {@code ...V16} nests below. Legacy assertions stay untouched while
 * their generation remains supported.
 */
class CliContractTest {

    private static String loadFixture(String name) {
        String path = "/fixtures/cli/" + name;
        try (InputStream is = CliContractTest.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fixture: " + path, e);
        }
    }

    private static String fixture16(String name) {
        return loadFixture("1.6.0/" + name);
    }

    private static String fixture17(String name) {
        return loadFixture("1.7.0/" + name);
    }

    private static String fixture18(String name) {
        return loadFixture("1.8.0/" + name);
    }

    /** Look an artifact up by id — 1.7 reorders {@code artifacts[]} to schema order, so index-based
     * status assertions would be fragile; keying by id makes the reorder inert. */
    private static ArtifactInfo byId(ChangeArtifactDag dag, String id) {
        return dag.getArtifacts().stream().filter(a -> id.equals(a.id())).findFirst().orElseThrow();
    }

    @Nested
    class StatusContract {

        @Test
        void parsesRealStatusOutput() {
            String json = loadFixture("status.json");
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(json);

            assertNotNull(dag);
            assertEquals("ensure-ai-component-exist-in-plugin", dag.getChangeName());
            assertEquals("spec-driven", dag.getSchemaName());
            assertFalse(dag.isComplete());
        }

        @Test
        void parsesApplyRequires() {
            String json = loadFixture("status.json");
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(json);

            assertEquals(1, dag.getApplyRequires().size());
            assertEquals("tasks", dag.getApplyRequires().get(0));
        }

        @Test
        void parsesAllArtifacts() {
            String json = loadFixture("status.json");
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(json);

            assertEquals(4, dag.getArtifacts().size());

            // Verify each artifact has id, outputPath, and status
            dag.getArtifacts().forEach(a -> {
                assertNotNull(a.id(), "artifact id must not be null");
                assertNotNull(a.outputPath(), "outputPath must not be null");
                assertNotNull(a.status(), "status must not be null");
                assertNotEquals(ArtifactStatus.UNKNOWN, a.status(),
                        "status '" + a.id() + "' must deserialize to a known value");
            });
        }

        @Test
        void artifactStatusesDeserializeCorrectly() {
            String json = loadFixture("status.json");
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(json);

            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().get(0).status());   // proposal
            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().get(1).status());   // design
            assertEquals(ArtifactStatus.READY, dag.getArtifacts().get(2).status());  // specs
            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().get(3).status());   // tasks
        }

        @Test
        void getReadyArtifactsWorksOnRealData() {
            String json = loadFixture("status.json");
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(json);

            assertEquals(1, dag.getReadyArtifacts().size());
            assertEquals("specs", dag.getReadyArtifacts().get(0).id());
        }
    }

    /**
     * Contract for the status shape Verify's completeness gate consumes: a captured
     * status with a mix of done/ready/blocked artifacts and a populated actionContext
     * block (the pre-existing status.json fixture predates actionContext).
     */
    @Nested
    class StatusWithContextContract {

        @Test
        void parsesMixedArtifactStatuses() {
            String json = loadFixture("status-with-context.json");
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(json);

            assertNotNull(dag);
            assertEquals("demo-change", dag.getChangeName());
            assertEquals("spec-driven", dag.getSchemaName());
            assertFalse(dag.isComplete());

            assertEquals(4, dag.getArtifacts().size());
            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().get(0).status());    // proposal
            assertEquals(ArtifactStatus.READY, dag.getArtifacts().get(1).status());   // design
            assertEquals(ArtifactStatus.READY, dag.getArtifacts().get(2).status());   // specs
            assertEquals(ArtifactStatus.BLOCKED, dag.getArtifacts().get(3).status()); // tasks
        }

        @Test
        void parsesMissingDepsOnBlockedArtifact() {
            String json = loadFixture("status-with-context.json");
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(json);

            ArtifactInfo tasks = dag.getArtifacts().get(3);
            assertEquals("tasks", tasks.id());
            assertEquals(List.of("design", "specs"), tasks.missingDeps());
        }

        @Test
        void parsesApplyRequires() {
            String json = loadFixture("status-with-context.json");
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(json);

            assertEquals(List.of("tasks"), dag.getApplyRequires());
        }

        @Test
        void parsesActionContext() {
            String json = loadFixture("status-with-context.json");
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(json);

            ChangeArtifactDag.ActionContext ac = dag.getActionContext();
            assertNotNull(ac, "captured 1.3+ status must surface actionContext");
            assertEquals("repo-local", ac.getMode());
            assertEquals("repo", ac.getSourceOfTruth());
            assertEquals(List.of("/home/user/demo-project"), ac.getAllowedEditRoots());
            assertFalse(ac.isRequiresAffectedAreaSelection());
        }
    }

    @Nested
    class InstructionContract {

        @Test
        void parsesProposalWithNoDependencies() {
            String json = loadFixture("instructions-proposal.json");
            ArtifactInstruction inst = CliOutputParser.parseArtifactInstruction(json);

            assertNotNull(inst);
            assertEquals("proposal", inst.artifactId());
            assertEquals("proposal.md", inst.outputPath());
            assertNotNull(inst.dependencies());
            assertTrue(inst.dependencies().isEmpty());
            assertEquals(2, inst.unlocks().size());
            assertTrue(inst.unlocks().contains("design"));
            assertTrue(inst.unlocks().contains("specs"));
        }

        @Test
        void parsesSpecsWithOneDependency() {
            String json = loadFixture("instructions-specs.json");
            ArtifactInstruction inst = CliOutputParser.parseArtifactInstruction(json);

            assertEquals("specs", inst.artifactId());
            assertEquals(1, inst.dependencies().size());

            ArtifactInstruction.Dependency dep = inst.dependencies().get(0);
            assertEquals("proposal", dep.id());
            assertTrue(dep.done());
            assertEquals("proposal.md", dep.path());
            assertNotNull(dep.description());
        }

        @Test
        void parsesTasksWithMultipleDependencies() {
            String json = loadFixture("instructions-tasks.json");
            ArtifactInstruction inst = CliOutputParser.parseArtifactInstruction(json);

            assertEquals("tasks", inst.artifactId());
            assertEquals(2, inst.dependencies().size());

            // First dep: specs (not done)
            ArtifactInstruction.Dependency specsDep = inst.dependencies().stream()
                    .filter(d -> "specs".equals(d.id())).findFirst().orElseThrow();
            assertFalse(specsDep.done());
            assertEquals("specs/**/*.md", specsDep.path());

            // Second dep: design (done)
            ArtifactInstruction.Dependency designDep = inst.dependencies().stream()
                    .filter(d -> "design".equals(d.id())).findFirst().orElseThrow();
            assertTrue(designDep.done());
            assertEquals("design.md", designDep.path());
        }

        @Test
        void parsesEmptyUnlocksAsEmptyList() {
            String json = loadFixture("instructions-tasks.json");
            ArtifactInstruction inst = CliOutputParser.parseArtifactInstruction(json);

            assertNotNull(inst.unlocks());
            assertTrue(inst.unlocks().isEmpty());
        }

        @Test
        void parsesAllStringFields() {
            String json = loadFixture("instructions-specs.json");
            ArtifactInstruction inst = CliOutputParser.parseArtifactInstruction(json);

            assertEquals("ensure-ai-component-exist-in-plugin", inst.changeName());
            assertNotNull(inst.changeDir());
            assertNotNull(inst.instruction());
            assertFalse(inst.instruction().isEmpty());
            assertNotNull(inst.template());
            assertFalse(inst.template().isEmpty());
        }

        @Test
        void buildPromptIncludesDependencies() {
            String json = loadFixture("instructions-specs.json");
            ArtifactInstruction inst = CliOutputParser.parseArtifactInstruction(json);

            String prompt = inst.buildPrompt();
            assertTrue(prompt.contains("Dependencies:"));
            assertTrue(prompt.contains("### proposal"));
        }
    }

    @Nested
    class ValidateContract {

        @Test
        void parsesRealValidateOutput() {
            String json = loadFixture("validate.json");
            ValidationResult result = CliOutputParser.parseJsonOutput(json);

            assertNotNull(result);
            assertFalse(result.passed(), "should fail when items have errors");
        }

        @Test
        void capturesErrorsFromInvalidItems() {
            String json = loadFixture("validate.json");
            ValidationResult result = CliOutputParser.parseJsonOutput(json);

            assertTrue(result.errorCount() > 0, "should have at least one error");
        }

        @Test
        void ignoresWarningsOnValidItems() {
            // The parser only extracts issues from items with valid:false.
            // Warnings on valid:true items (like the "validation" spec) are skipped.
            String json = loadFixture("validate.json");
            ValidationResult result = CliOutputParser.parseJsonOutput(json);

            assertEquals(0, result.warningCount(),
                    "warnings on valid items are not extracted by parseJsonOutput");
        }
    }

    /**
     * 1.6-generation status contract. The capture (a seeded {@code demo-change} with
     * proposal/design/tasks written, no specs delta) reproduces the legacy per-index
     * statuses while carrying the additive 1.6 keys ({@code planningHome},
     * {@code changeRoot}, {@code artifactPaths}, {@code nextSteps}) — parsing it with
     * exact-value assertions proves the parser tolerates the 1.6 envelope.
     */
    @Nested
    class StatusContractV16 {

        @Test
        void parsesRealStatusOutputWithAdditive16Keys() {
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(fixture16("status.json"));

            assertNotNull(dag);
            assertEquals("demo-change", dag.getChangeName());
            assertEquals("spec-driven", dag.getSchemaName());
            assertFalse(dag.isComplete());
            assertEquals(List.of("tasks"), dag.getApplyRequires());
        }

        @Test
        void artifactStatusesDeserializeCorrectly() {
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(fixture16("status.json"));

            assertEquals(4, dag.getArtifacts().size());
            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().get(0).status());   // proposal
            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().get(1).status());   // design
            assertEquals(ArtifactStatus.READY, dag.getArtifacts().get(2).status());  // specs
            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().get(3).status());   // tasks
        }

        @Test
        void getReadyArtifactsWorksOnRealData() {
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(fixture16("status.json"));

            assertEquals(1, dag.getReadyArtifacts().size());
            assertEquals("specs", dag.getReadyArtifacts().get(0).id());
        }

        @Test
        void parsesMixedStatusesAndMissingDeps() {
            ChangeArtifactDag dag =
                    CliOutputParser.parseChangeStatus(fixture16("status-with-context.json"));

            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().get(0).status());    // proposal
            assertEquals(ArtifactStatus.READY, dag.getArtifacts().get(1).status());   // design
            assertEquals(ArtifactStatus.READY, dag.getArtifacts().get(2).status());   // specs
            assertEquals(ArtifactStatus.BLOCKED, dag.getArtifacts().get(3).status()); // tasks

            ArtifactInfo tasks = dag.getArtifacts().get(3);
            assertEquals("tasks", tasks.id());
            assertEquals(List.of("design", "specs"), tasks.missingDeps());
        }

        @Test
        void parsesActionContext() {
            ChangeArtifactDag dag =
                    CliOutputParser.parseChangeStatus(fixture16("status-with-context.json"));

            ChangeArtifactDag.ActionContext ac = dag.getActionContext();
            assertNotNull(ac, "captured 1.6 status must surface actionContext");
            assertEquals("repo-local", ac.getMode());
            assertEquals("repo", ac.getSourceOfTruth());
            assertEquals(List.of("/fixture/demo-project"), ac.getAllowedEditRoots());
            assertFalse(ac.isRequiresAffectedAreaSelection());
        }

        /**
         * The apply-ready path: a captured status from a change whose proposal/design/specs/tasks
         * are all done, so {@code isComplete} is true. The other status fixtures only carry
         * {@code isComplete:false}, leaving the done rollup ({@link ChangeArtifactDag#isComplete()},
         * which drives the CHANGE_DONE tree badge) otherwise unproven against real CLI output.
         */
        @Test
        void parsesCompleteStatusWithIsCompleteTrue() {
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(fixture16("status-complete.json"));

            assertNotNull(dag);
            assertEquals("demo-change", dag.getChangeName());
            assertTrue(dag.isComplete(), "a fully-complete change must report isComplete=true");

            assertEquals(4, dag.getArtifacts().size());
            dag.getArtifacts().forEach(a ->
                    assertEquals(ArtifactStatus.DONE, a.status(),
                            "every artifact in a complete change is done: " + a.id()));
            assertTrue(dag.getReadyArtifacts().isEmpty(), "nothing is merely ready when all are done");
        }
    }

    /** 1.6-generation instructions contract (staged DAG states of the same seed). */
    @Nested
    class InstructionContractV16 {

        @Test
        void parsesProposalWithNoDependencies() {
            ArtifactInstruction inst =
                    CliOutputParser.parseArtifactInstruction(fixture16("instructions-proposal.json"));

            assertNotNull(inst);
            assertEquals("proposal", inst.artifactId());
            assertEquals("proposal.md", inst.outputPath());
            assertTrue(inst.dependencies().isEmpty());
            assertEquals(List.of("design", "specs"), inst.unlocks());
        }

        @Test
        void parsesSpecsWithOneDependency() {
            ArtifactInstruction inst =
                    CliOutputParser.parseArtifactInstruction(fixture16("instructions-specs.json"));

            assertEquals("specs", inst.artifactId());
            assertEquals("demo-change", inst.changeName());
            assertEquals(1, inst.dependencies().size());

            ArtifactInstruction.Dependency dep = inst.dependencies().get(0);
            assertEquals("proposal", dep.id());
            assertTrue(dep.done());
            assertEquals("proposal.md", dep.path());
            assertNotNull(dep.description());
        }

        @Test
        void parsesTasksWithMultipleDependencies() {
            ArtifactInstruction inst =
                    CliOutputParser.parseArtifactInstruction(fixture16("instructions-tasks.json"));

            assertEquals("tasks", inst.artifactId());
            assertEquals(2, inst.dependencies().size());

            ArtifactInstruction.Dependency specsDep = inst.dependencies().stream()
                    .filter(d -> "specs".equals(d.id())).findFirst().orElseThrow();
            assertFalse(specsDep.done());
            assertEquals("specs/**/*.md", specsDep.path());

            ArtifactInstruction.Dependency designDep = inst.dependencies().stream()
                    .filter(d -> "design".equals(d.id())).findFirst().orElseThrow();
            assertTrue(designDep.done());
            assertEquals("design.md", designDep.path());
        }

        @Test
        void parsesEmptyUnlocksAsEmptyList() {
            ArtifactInstruction inst =
                    CliOutputParser.parseArtifactInstruction(fixture16("instructions-tasks.json"));

            assertNotNull(inst.unlocks());
            assertTrue(inst.unlocks().isEmpty());
        }

        @Test
        void buildPromptIncludesDependencies() {
            ArtifactInstruction inst =
                    CliOutputParser.parseArtifactInstruction(fixture16("instructions-specs.json"));

            String prompt = inst.buildPrompt();
            assertTrue(prompt.contains("Dependencies:"));
            assertTrue(prompt.contains("### proposal"));
        }
    }

    /**
     * 1.6-generation validate contract. The capture was seeded to exercise every issue
     * class 1.6 emits: a valid spec, a valid spec with a WARNING, a missing-SHALL spec
     * (the re-pathed {@code requirements[0]} ERROR with the reworded message), a
     * delta-less change (ERROR), and a valid change whose delta carries a non-canonical
     * level-3 header (the new INFO-level issue with a {@code line} field — 1.6 emits it
     * on {@code valid: true} items, so it must never surface from the parser).
     */
    @Nested
    class ValidateContractV16 {

        @Test
        void parsesRealValidateOutput() {
            ValidationResult result = CliOutputParser.parseJsonOutput(fixture16("validate.json"));

            assertNotNull(result);
            assertFalse(result.passed(), "capture contains two invalid items");
            assertEquals(2, result.errorCount(),
                    "missing-SHALL spec + delta-less change are the only invalid items");
        }

        @Test
        void extractsRepathedMissingShallError() {
            ValidationResult result = CliOutputParser.parseJsonOutput(fixture16("validate.json"));

            assertTrue(result.issues().stream().anyMatch(i ->
                            i.severity() == ValidationIssue.Severity.ERROR
                                    && i.message().contains("must contain SHALL or MUST")),
                    "the 1.6 requirements[0] missing-SHALL error must surface with its reworded message");
        }

        @Test
        void extractsDeltaLessChangeError() {
            ValidationResult result = CliOutputParser.parseJsonOutput(fixture16("validate.json"));

            assertTrue(result.issues().stream().anyMatch(i ->
                            i.severity() == ValidationIssue.Severity.ERROR
                                    && i.message().contains("Change must have at least one delta")),
                    "the delta-less change error must surface");
        }

        @Test
        void skipsWarningAndInfoIssuesOnValidItems() {
            // The seeded capture has a WARNING on a valid spec and the new 1.6 INFO
            // (with a line field) on a valid change. parseJsonOutput only extracts
            // issues from valid:false items, so neither may appear.
            ValidationResult result = CliOutputParser.parseJsonOutput(fixture16("validate.json"));

            assertEquals(0, result.warningCount(),
                    "warnings on valid items are not extracted by parseJsonOutput");
            assertTrue(result.issues().stream()
                            .noneMatch(i -> i.severity() == ValidationIssue.Severity.INFO),
                    "the 1.6 INFO issue rides a valid:true item and must be skipped");
        }

        @Test
        void warningOnlyInvalidItemReadsAsFailingFromValidField() {
            // Captured real 1.6.0 `validate --all --strict --json`: a spec whose only issue is a
            // WARNING (Purpose too brief) is valid:false under --strict. The verdict must come from
            // the item's `valid` field, not from re-deriving pass/fail off issue severities — the
            // latter would mis-read this warning-only item as passing (no ERROR present). This is the
            // captured-output proof of the CLI-authoritative verdict derivation.
            ValidationResult result = CliOutputParser.parseJsonOutput(fixture16("validate-strict-warning-only.json"));

            assertNotNull(result);
            assertFalse(result.passed(),
                    "a valid:false item with only a WARNING must be read as failing");
        }
    }

    /**
     * Single-item validate contract — {@code openspec validate <id> --type spec|change --json}.
     * The single-item envelope ({@code {items:[{id,type,valid,issues,durationMs}], summary,
     * version, root}}) differs from the bulk {@code --all} shape: it carries top-level
     * {@code summary} and {@code root} objects and a one-element {@code items} array. The
     * Project-View scoped Validate parses this shape with the same {@link CliOutputParser#parseJsonOutput}.
     * Captures are real 1.6.0 output (isolated env, {@code root.path} sanitized to {@code /fixture}).
     */
    @Nested
    class SingleItemValidateContractV16 {

        @Test
        void parsesValidSpecEnvelope() {
            // A valid spec whose only issue is a WARNING (Purpose too brief) — valid:true,
            // so parseJsonOutput extracts nothing and the result passes.
            ValidationResult result = CliOutputParser.parseJsonOutput(fixture16("validate-single-spec.json"));

            assertNotNull(result);
            assertTrue(result.passed(), "a valid spec's single-item envelope must parse as passed");
            assertEquals(0, result.warningCount(),
                    "the WARNING rides a valid:true item and must be skipped");
            assertTrue(result.issues().isEmpty(), "no issues extracted from a valid item");
        }

        @Test
        void parsesValidChangeEnvelope() {
            ValidationResult result = CliOutputParser.parseJsonOutput(fixture16("validate-single-change.json"));

            assertNotNull(result);
            assertTrue(result.passed(), "a valid change's single-item envelope must parse as passed");
            assertTrue(result.issues().isEmpty());
        }

        @Test
        void extractsErrorFromInvalidChangeEnvelope() {
            // A delta-less change: valid:false with one ERROR issue.
            ValidationResult result = CliOutputParser.parseJsonOutput(
                    fixture16("validate-single-change-invalid.json"));

            assertNotNull(result);
            assertFalse(result.passed(), "an invalid change must parse as failed");
            assertEquals(1, result.errorCount(), "the single delta-missing ERROR must surface");
            assertTrue(result.issues().stream().anyMatch(i ->
                            i.severity() == ValidationIssue.Severity.ERROR
                                    && i.message().contains("at least one delta")),
                    "the no-deltas error message must surface from the single-item shape");
        }

        @Test
        void tagsIssueWithTypeAndId() {
            // The parser labels each extracted issue "<type>/<id>" — here "change/broken-change".
            ValidationResult result = CliOutputParser.parseJsonOutput(
                    fixture16("validate-single-change-invalid.json"));

            assertTrue(result.issues().stream()
                            .anyMatch(i -> "change/broken-change".equals(i.filePath())),
                    "single-item issue must carry its type/id path from the envelope");
        }
    }

    /**
     * Floor-version parity guard. The plugin's supported CLI floor is 1.3.0, and the CLI-authoritative
     * merge depends on the {@code validate --json} shape being stable across the supported range. It
     * is: the item's {@code valid} field, {@code issues[].level/message}, {@code type} and {@code id}
     * are byte-identical across 1.3.0 → 1.6.0; only a top-level {@code root} key was added in 1.5,
     * which the parser ignores. This test parses a real captured 1.3.0 {@code validate --all --json}
     * (root sanitized to {@code /fixture}) and asserts the parser derives the same per-item verdict
     * from {@code valid} as it does on 1.6.0 — so a future CLI version, or a parser change, that breaks
     * the cross-version shape assumption fails here rather than silently in the field. Re-capture from
     * a real floor CLI if it ever changes.
     */
    @Nested
    class FloorVersionValidateContractV13 {

        @Test
        void derivesVerdictFromValidFieldOnTheFloorShape() {
            // The 1.3.0 capture has one invalid item ('bad', missing SHALL) and one valid-but-warning
            // item ('good'). The whole-project verdict fails on the invalid item, and the valid item's
            // WARNING is not extracted — proving the verdict comes from `valid`, not issue severities,
            // on the older shape that has no top-level `root` key.
            ValidationResult result = CliOutputParser.parseJsonOutput(loadFixture("1.3.0/validate.json"));

            assertNotNull(result);
            assertFalse(result.passed(), "the 1.3.0 capture has an invalid item, so the result fails");
            assertEquals(1, result.errorCount(),
                    "only the invalid item's ERROR surfaces; the valid item's warning rides valid:true and is skipped");
        }
    }

    /**
     * 1.7-generation status contract. 1.7 adds a per-artifact {@code requires: string[]} and reorders
     * {@code artifacts[]}/{@code missingDeps[]} to schema order [proposal, specs, design, tasks].
     * Assertions are keyed by artifact <b>id</b> (never array index) so the reorder is inert;
     * {@code ArtifactInfo} has no {@code requires} field so Gson drops it (additive-tolerant). One
     * JSON-level assertion proves the {@code requires} edges are present in the captured output — the
     * data a future {@code requires}-driven DAG derivation would consume.
     */
    @Nested
    class StatusContractV17 {

        @Test
        void parsesRealStatusOutputWithAdditive17Keys() {
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(fixture17("status.json"));
            assertNotNull(dag);
            assertEquals("demo-change", dag.getChangeName());
            assertEquals("spec-driven", dag.getSchemaName());
            assertFalse(dag.isComplete());
            assertEquals(List.of("tasks"), dag.getApplyRequires());
        }

        @Test
        void artifactStatusesDeserializeById() {
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(fixture17("status.json"));
            assertEquals(4, dag.getArtifacts().size());
            assertEquals(ArtifactStatus.DONE, byId(dag, "proposal").status());
            assertEquals(ArtifactStatus.DONE, byId(dag, "design").status());
            assertEquals(ArtifactStatus.READY, byId(dag, "specs").status());
            assertEquals(ArtifactStatus.DONE, byId(dag, "tasks").status());
        }

        @Test
        void getReadyArtifactsWorksOnRealData() {
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(fixture17("status.json"));
            assertEquals(1, dag.getReadyArtifacts().size());
            assertEquals("specs", dag.getReadyArtifacts().get(0).id());
        }

        @Test
        void parsesMixedStatusesAndMissingDepsById() {
            ChangeArtifactDag dag =
                    CliOutputParser.parseChangeStatus(fixture17("status-with-context.json"));
            assertEquals(ArtifactStatus.DONE, byId(dag, "proposal").status());
            assertEquals(ArtifactStatus.READY, byId(dag, "design").status());
            assertEquals(ArtifactStatus.READY, byId(dag, "specs").status());
            assertEquals(ArtifactStatus.BLOCKED, byId(dag, "tasks").status());
            // 1.7 reorders missingDeps to schema order — compare as a Set so ordering is irrelevant.
            assertEquals(java.util.Set.of("design", "specs"),
                    java.util.Set.copyOf(byId(dag, "tasks").missingDeps()));
        }

        @Test
        void parsesActionContext() {
            ChangeArtifactDag dag =
                    CliOutputParser.parseChangeStatus(fixture17("status-with-context.json"));
            ChangeArtifactDag.ActionContext ac = dag.getActionContext();
            assertNotNull(ac, "captured 1.7 status must surface actionContext");
            assertEquals("repo-local", ac.getMode());
            assertEquals("repo", ac.getSourceOfTruth());
            assertEquals(List.of("/fixture/demo-project"), ac.getAllowedEditRoots());
            assertFalse(ac.isRequiresAffectedAreaSelection());
        }

        @Test
        void parsesCompleteStatusWithIsCompleteTrue() {
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(fixture17("status-complete.json"));
            assertNotNull(dag);
            assertEquals("demo-change", dag.getChangeName());
            assertTrue(dag.isComplete());
            assertEquals(4, dag.getArtifacts().size());
            dag.getArtifacts().forEach(a ->
                    assertEquals(ArtifactStatus.DONE, a.status(), "every artifact is done: " + a.id()));
            assertTrue(dag.getReadyArtifacts().isEmpty());
        }

        /**
         * The additive 1.7 edge data. {@code ArtifactInfo} intentionally drops {@code requires}, so
         * this asserts at the JSON level that the captured output carries the dependency edges (the
         * source a future {@code requires}-driven DAG derivation would consume). Proves the capture is
         * 1.7-generation, not a 1.6 twin.
         */
        @Test
        void statusJsonCarriesAdditiveRequiresEdges() {
            com.google.gson.JsonObject root = com.google.gson.JsonParser
                    .parseString(fixture17("status.json")).getAsJsonObject();
            java.util.Map<String, java.util.List<String>> requires = new java.util.LinkedHashMap<>();
            for (com.google.gson.JsonElement el : root.getAsJsonArray("artifacts")) {
                com.google.gson.JsonObject a = el.getAsJsonObject();
                java.util.List<String> reqs = new java.util.ArrayList<>();
                if (a.has("requires")) {
                    a.getAsJsonArray("requires").forEach(r -> reqs.add(r.getAsString()));
                }
                requires.put(a.get("id").getAsString(), reqs);
            }
            assertTrue(requires.get("proposal").isEmpty(),
                    "the root proposal artifact has no requires edges");
            assertTrue(requires.get("tasks").containsAll(List.of("specs", "design")),
                    "1.7 tasks artifact must carry requires edges on specs+design; got " + requires.get("tasks"));
        }

        @Test
        void artifactInfoModelBindsRequiresEdges() {
            // Proves Gson deserializes the 1.7 `requires` array into ArtifactInfo's 5-arg canonical
            // constructor — the data downstream dependency reasoning consumes.
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(fixture17("status.json"));
            assertEquals(List.of("specs", "design"), byId(dag, "tasks").requires());
            assertEquals(List.of("proposal"), byId(dag, "design").requires());
            assertTrue(byId(dag, "proposal").requires().isEmpty(),
                    "the root proposal artifact carries no requires edges");
        }

        /**
         * A 1.7 {@code skip_specs: true} change reports its specs artifact as {@code skipped} and the
         * change {@code isComplete: true}. The plugin's {@link ArtifactStatus} enum has no SKIPPED
         * value, so {@code skipped} degrades to {@link ArtifactStatus#UNKNOWN} — this is graceful, not
         * a breach of the never-stricter-than-the-CLI invariant, because completeness is read from the
         * CLI's own {@code isComplete} flag, never re-derived from artifact statuses. This locks that
         * contract: a future change that makes {@code skipped} throw, or that re-derives completeness
         * and thereby blocks a genuinely-complete skip-specs change, fails here.
         */
        @Test
        void skipSpecsChangeStatusDegradesGracefully() {
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(fixture17("status-skipped.json"));
            assertNotNull(dag);
            assertEquals(ArtifactStatus.UNKNOWN, byId(dag, "specs").status(),
                    "1.7 'skipped' has no plugin enum value and degrades to UNKNOWN (graceful)");
            assertTrue(dag.isComplete(),
                    "a complete skip_specs change is complete regardless of the skipped specs artifact");
        }
    }

    /** 1.7-generation instructions contract. 1.7 reorders unlocks[] to schema order. */
    @Nested
    class InstructionContractV17 {

        @Test
        void parsesProposalWithReorderedUnlocks() {
            ArtifactInstruction inst =
                    CliOutputParser.parseArtifactInstruction(fixture17("instructions-proposal.json"));
            assertNotNull(inst);
            assertEquals("proposal", inst.artifactId());
            assertEquals("proposal.md", inst.outputPath());
            assertTrue(inst.dependencies().isEmpty());
            // 1.7 schema-order reorder: was [design, specs] on 1.6.
            assertEquals(List.of("specs", "design"), inst.unlocks());
        }

        @Test
        void parsesSpecsWithOneDependency() {
            ArtifactInstruction inst =
                    CliOutputParser.parseArtifactInstruction(fixture17("instructions-specs.json"));
            assertEquals("specs", inst.artifactId());
            assertEquals("demo-change", inst.changeName());
            assertEquals(1, inst.dependencies().size());
            ArtifactInstruction.Dependency dep = inst.dependencies().get(0);
            assertEquals("proposal", dep.id());
            assertTrue(dep.done());
            assertEquals("proposal.md", dep.path());
            assertNotNull(dep.description());
        }

        @Test
        void parsesTasksWithMultipleDependencies() {
            ArtifactInstruction inst =
                    CliOutputParser.parseArtifactInstruction(fixture17("instructions-tasks.json"));
            assertEquals("tasks", inst.artifactId());
            assertEquals(2, inst.dependencies().size());
            ArtifactInstruction.Dependency specsDep = inst.dependencies().stream()
                    .filter(d -> "specs".equals(d.id())).findFirst().orElseThrow();
            assertFalse(specsDep.done());
            assertEquals("specs/**/*.md", specsDep.path());
            ArtifactInstruction.Dependency designDep = inst.dependencies().stream()
                    .filter(d -> "design".equals(d.id())).findFirst().orElseThrow();
            assertTrue(designDep.done());
            assertEquals("design.md", designDep.path());
        }

        @Test
        void parsesEmptyUnlocksAsEmptyList() {
            ArtifactInstruction inst =
                    CliOutputParser.parseArtifactInstruction(fixture17("instructions-tasks.json"));
            assertNotNull(inst.unlocks());
            assertTrue(inst.unlocks().isEmpty());
        }

        @Test
        void buildPromptIncludesDependencies() {
            ArtifactInstruction inst =
                    CliOutputParser.parseArtifactInstruction(fixture17("instructions-specs.json"));
            String prompt = inst.buildPrompt();
            assertTrue(prompt.contains("Dependencies:"));
            assertTrue(prompt.contains("### proposal"));
        }
    }

    /** 1.7-generation validate contract — verbatim twin of {@link ValidateContractV16}. */
    @Nested
    class ValidateContractV17 {

        @Test
        void parsesRealValidateOutput() {
            ValidationResult result = CliOutputParser.parseJsonOutput(fixture17("validate.json"));
            assertNotNull(result);
            assertFalse(result.passed(), "capture contains two invalid items");
            assertEquals(2, result.errorCount());
        }

        @Test
        void extractsRepathedMissingShallError() {
            ValidationResult result = CliOutputParser.parseJsonOutput(fixture17("validate.json"));
            assertTrue(result.issues().stream().anyMatch(i ->
                    i.severity() == ValidationIssue.Severity.ERROR
                            && i.message().contains("must contain SHALL or MUST")));
        }

        @Test
        void extractsDeltaLessChangeError() {
            ValidationResult result = CliOutputParser.parseJsonOutput(fixture17("validate.json"));
            assertTrue(result.issues().stream().anyMatch(i ->
                    i.severity() == ValidationIssue.Severity.ERROR
                            && i.message().contains("Change must have at least one delta")));
        }

        @Test
        void skipsWarningAndInfoIssuesOnValidItems() {
            ValidationResult result = CliOutputParser.parseJsonOutput(fixture17("validate.json"));
            assertEquals(0, result.warningCount());
            assertTrue(result.issues().stream()
                    .noneMatch(i -> i.severity() == ValidationIssue.Severity.INFO));
        }

        @Test
        void warningOnlyInvalidItemReadsAsFailingFromValidField() {
            ValidationResult result =
                    CliOutputParser.parseJsonOutput(fixture17("validate-strict-warning-only.json"));
            assertNotNull(result);
            assertFalse(result.passed());
        }
    }

    /** 1.7-generation single-item validate contract — verbatim twin of {@link SingleItemValidateContractV16}. */
    @Nested
    class SingleItemValidateContractV17 {

        @Test
        void parsesValidSpecEnvelope() {
            ValidationResult result = CliOutputParser.parseJsonOutput(fixture17("validate-single-spec.json"));
            assertNotNull(result);
            assertTrue(result.passed());
            assertEquals(0, result.warningCount());
            assertTrue(result.issues().isEmpty());
        }

        @Test
        void parsesValidChangeEnvelope() {
            ValidationResult result = CliOutputParser.parseJsonOutput(fixture17("validate-single-change.json"));
            assertNotNull(result);
            assertTrue(result.passed());
            assertTrue(result.issues().isEmpty());
        }

        @Test
        void extractsErrorFromInvalidChangeEnvelope() {
            ValidationResult result = CliOutputParser.parseJsonOutput(
                    fixture17("validate-single-change-invalid.json"));
            assertNotNull(result);
            assertFalse(result.passed());
            assertEquals(1, result.errorCount());
            assertTrue(result.issues().stream().anyMatch(i ->
                    i.severity() == ValidationIssue.Severity.ERROR
                            && i.message().contains("at least one delta")));
        }

        @Test
        void tagsIssueWithTypeAndId() {
            ValidationResult result = CliOutputParser.parseJsonOutput(
                    fixture17("validate-single-change-invalid.json"));
            assertTrue(result.issues().stream()
                    .anyMatch(i -> "change/broken-change".equals(i.filePath())));
        }

        @Test
        void proposalLessChangeReadsAsValid() {
            // A change with a valid delta but no proposal.md is valid per the CLI — it resolves
            // changes by directory existence, not by requiring proposal.md (upstream #1182). Locks
            // that fact behind the proposal-required ERROR->WARNING demotion: a future CLI that starts
            // requiring proposal.md breaks here loudly.
            ValidationResult result = CliOutputParser.parseJsonOutput(
                    fixture17("validate-single-change-no-proposal.json"));
            assertNotNull(result);
            assertTrue(result.passed());
            assertTrue(result.issues().isEmpty());
        }
    }

    /**
     * OpenSpec 1.8 demoted the missing-SHALL/MUST rule: a body-carrying requirement without the keyword
     * is now a WARNING with a reworded "should contain…" message, valid in default and valid:false only
     * under {@code --strict}; a body-less requirement still ERRORs with "must contain…". 1.8 also added a
     * main-spec duplicate-requirement ERROR (upstream #1484). These lock those shapes against the real
     * 1.8.0 captures. (Warnings on valid items are dropped by {@code parseJsonOutput}, so the demotion is
     * asserted from the raw JSON; the body-less and duplicate ERRORs ride valid:false items and are
     * asserted through {@code parseJsonOutput} as well as the raw shape.)
     */
    @Nested
    class ValidateContractV18 {

        private com.google.gson.JsonObject itemById(String fixtureName, String id) {
            com.google.gson.JsonObject root = com.google.gson.JsonParser
                    .parseString(fixture18(fixtureName)).getAsJsonObject();
            for (com.google.gson.JsonElement el : root.getAsJsonArray("items")) {
                com.google.gson.JsonObject item = el.getAsJsonObject();
                if (id.equals(item.get("id").getAsString())) return item;
            }
            throw new IllegalStateException("no item '" + id + "' in " + fixtureName);
        }

        @Test
        void bodyCarryingMissingKeywordIsDemotedToWarningAndValidInDefault() {
            com.google.gson.JsonObject item = itemById("validate-parity-corpus.json", "should-only");
            assertTrue(item.get("valid").getAsBoolean(),
                    "1.8: a body-carrying requirement without SHALL/MUST is valid in default mode");
            com.google.gson.JsonObject issue = item.getAsJsonArray("issues").get(0).getAsJsonObject();
            assertEquals("WARNING", issue.get("level").getAsString(),
                    "1.8: the missing-keyword issue is a WARNING, not an ERROR");
            assertTrue(issue.get("message").getAsString().contains("should contain SHALL or MUST"),
                    "1.8 reworded the missing-keyword message to the RFC-2119 best-practice phrasing");
        }

        @Test
        void missingKeywordWarningRePromotesUnderStrict() {
            com.google.gson.JsonObject item = itemById("validate-parity-corpus-strict.json", "should-only");
            assertFalse(item.get("valid").getAsBoolean(),
                    "--strict counts the missing-keyword warning as a failure");
            assertEquals("WARNING",
                    item.getAsJsonArray("issues").get(0).getAsJsonObject().get("level").getAsString(),
                    "the level stays WARNING under strict; only the verdict flips");
        }

        @Test
        void bodyLessRequirementStaysError() {
            ValidationResult result = CliOutputParser.parseJsonOutput(
                    fixture18("validate-single-spec-no-body.json"));
            assertFalse(result.passed(), "a body-less requirement is invalid in default mode");
            assertTrue(result.issues().stream().anyMatch(i ->
                            i.severity() == ValidationIssue.Severity.ERROR
                                    && i.message().contains("must contain SHALL or MUST")),
                    "1.8 still ERRORs on a requirement with no body prose");
        }

        @Test
        void duplicateRequirementNameIsAnErrorWithCliShape() {
            // 1.8 (upstream #1484): a main spec that declares the same `### Requirement:` name twice is
            // an ERROR. Lock the exact captured shape — path is the literal string "file" (not the spec
            // path), the issue is anchored on the SECOND occurrence's line (15), and the message names
            // the FIRST occurrence's line (8). The CLI carries no machine rule-id for it (freeform
            // message only), so the plugin's parser and fallback key off level + path + message content.
            com.google.gson.JsonObject item =
                    itemById("validate-single-spec-duplicate-requirement.json", "dup-req");
            assertFalse(item.get("valid").getAsBoolean(),
                    "1.8: a spec with a duplicate requirement name is invalid");
            com.google.gson.JsonObject issue = item.getAsJsonArray("issues").get(0).getAsJsonObject();
            assertEquals("ERROR", issue.get("level").getAsString());
            assertEquals("file", issue.get("path").getAsString(),
                    "the duplicate issue path is the literal string \"file\", not the spec path");
            assertEquals(15, issue.get("line").getAsInt(),
                    "the issue is anchored on the second (duplicate) occurrence's line");
            assertTrue(issue.get("message").getAsString().contains("duplicates the requirement declared on line 8"),
                    "the message names the first occurrence's line");
            assertFalse(issue.has("id") || issue.has("code") || issue.has("ruleId"),
                    "the CLI carries no machine rule-id for the duplicate-requirement rule");

            // Through the parser, the verdict fails on the ERROR.
            ValidationResult result = CliOutputParser.parseJsonOutput(
                    fixture18("validate-single-spec-duplicate-requirement.json"));
            assertFalse(result.passed(), "the duplicate-requirement ERROR fails the item's verdict");
            assertTrue(result.issues().stream().anyMatch(i ->
                            i.severity() == ValidationIssue.Severity.ERROR
                                    && i.message().contains("duplicates the requirement declared on line")),
                    "the parser surfaces the duplicate-requirement ERROR");
        }

        @Test
        void requirementOutsideTheRequirementsSectionIsNotFlaggedAsADuplicate() {
            // Section-scoping guard, captured from the REAL CLI (not inferred from source): a name that
            // appears once inside `## Requirements` and once outside it is NOT a duplicate — the CLI
            // routes the out-of-section occurrence to a separate `requirement-outside-requirements`
            // diagnostic and never dedups it. This locks the premise the fallback's `## Requirements`
            // section-scoping relies on, guarding the one direction the fallback could become MORE
            // restrictive than the CLI (emitting a duplicate the CLI does not).
            com.google.gson.JsonObject item =
                    itemById("validate-single-spec-requirement-outside-section.json", "boundary");
            assertFalse(item.get("valid").getAsBoolean());
            for (com.google.gson.JsonElement el : item.getAsJsonArray("issues")) {
                assertFalse(el.getAsJsonObject().get("message").getAsString().contains("duplicates the requirement"),
                        "an out-of-section occurrence must NOT be reported as a duplicate by the CLI");
            }
            assertTrue(item.getAsJsonArray("issues").get(0).getAsJsonObject().get("message").getAsString()
                            .contains("appears outside the main ## Requirements section"),
                    "the CLI's signal here is the outside-section rule, a different (non-duplicate) diagnostic");
        }
    }
}
