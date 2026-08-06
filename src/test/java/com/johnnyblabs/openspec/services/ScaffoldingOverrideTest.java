package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.model.ArtifactStatus;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.util.CliOutputParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Exercises the <b>real</b> {@link ArtifactOrchestrationService#applyScaffoldingOverrides} (via a
 * mocked {@link Project} + a real {@link ScaffoldingDetectionService} reading {@code @TempDir}
 * files) rather than a hand-copy of the logic — so the test cannot pass while the production method
 * drifts. The {@code reorderInvariant_*} case pins that the 1.7 schema-order reorder of
 * {@code artifacts[]} does not change the override verdict: the only reordered artifact ({@code
 * specs}) is a glob that the override skips, so the participating non-glob artifacts keep their
 * relative order and the derived statuses are identical across generations.
 */
@ExtendWith(MockitoExtension.class)
class ScaffoldingOverrideTest {

    @Mock Project project;

    @TempDir
    Path tempDir;

    private static final String SCAFFOLDED_DESIGN = """
            # Design: my-change

            ## Approach

            <!-- Describe the technical approach -->

            ## Components Affected

            <!-- List affected components -->
            """;

    private static final String SCAFFOLDED_TASKS = """
            # Tasks: my-change

            ## Implementation Tasks

            - [ ] Task 1
            - [ ] Task 2
            - [ ] Task 3

            ## Testing Tasks

            - [ ] Write unit tests
            - [ ] Integration testing
            """;

    private static final String REAL_CONTENT = """
            ## Approach

            Use a ScaffoldingDetectionService to check file content.
            Override status in ArtifactOrchestrationService after CLI parsing.
            """;

    @Test
    void allScaffolded_overridesToReadyAndBlocked() throws IOException {
        // proposal is real, design and tasks are scaffolded
        setupChangeDir("test-change",
                "proposal.md", REAL_CONTENT,
                "design.md", SCAFFOLDED_DESIGN,
                "tasks.md", SCAFFOLDED_TASKS);

        ChangeArtifactDag dag = createDag(true,
                artifact("proposal", "proposal.md", ArtifactStatus.DONE),
                artifact("design", "design.md", ArtifactStatus.DONE),
                artifact("specs", "specs/**/*.md", ArtifactStatus.DONE),
                artifact("tasks", "tasks.md", ArtifactStatus.DONE));

        runRealOverrides(dag, "test-change");

        assertEquals(ArtifactStatus.DONE, findArtifact(dag, "proposal").status());
        assertEquals(ArtifactStatus.READY, findArtifact(dag, "design").status());
        assertEquals(ArtifactStatus.DONE, findArtifact(dag, "specs").status()); // glob skipped
        assertEquals(ArtifactStatus.BLOCKED, findArtifact(dag, "tasks").status());
        assertEquals(List.of("design"), findArtifact(dag, "tasks").missingDeps());
        assertFalse(dag.isComplete());
    }

    @Test
    void noneScaffolded_noChanges() throws IOException {
        setupChangeDir("test-change",
                "proposal.md", REAL_CONTENT,
                "design.md", REAL_CONTENT,
                "tasks.md", REAL_CONTENT);

        ChangeArtifactDag dag = createDag(true,
                artifact("proposal", "proposal.md", ArtifactStatus.DONE),
                artifact("design", "design.md", ArtifactStatus.DONE),
                artifact("tasks", "tasks.md", ArtifactStatus.DONE));

        runRealOverrides(dag, "test-change");

        assertEquals(ArtifactStatus.DONE, findArtifact(dag, "proposal").status());
        assertEquals(ArtifactStatus.DONE, findArtifact(dag, "design").status());
        assertEquals(ArtifactStatus.DONE, findArtifact(dag, "tasks").status());
        assertTrue(dag.isComplete());
    }

    @Test
    void someScaffolded_partialOverride() throws IOException {
        // proposal and design are real, tasks is scaffolded
        setupChangeDir("test-change",
                "proposal.md", REAL_CONTENT,
                "design.md", REAL_CONTENT,
                "tasks.md", SCAFFOLDED_TASKS);

        ChangeArtifactDag dag = createDag(true,
                artifact("proposal", "proposal.md", ArtifactStatus.DONE),
                artifact("design", "design.md", ArtifactStatus.DONE),
                artifact("tasks", "tasks.md", ArtifactStatus.DONE));

        runRealOverrides(dag, "test-change");

        assertEquals(ArtifactStatus.DONE, findArtifact(dag, "proposal").status());
        assertEquals(ArtifactStatus.DONE, findArtifact(dag, "design").status());
        assertEquals(ArtifactStatus.READY, findArtifact(dag, "tasks").status());
        assertFalse(dag.isComplete());
    }

    @Test
    void globArtifact_skipped() throws IOException {
        setupChangeDir("test-change",
                "proposal.md", REAL_CONTENT);

        ChangeArtifactDag dag = createDag(true,
                artifact("proposal", "proposal.md", ArtifactStatus.DONE),
                artifact("specs", "specs/**/*.md", ArtifactStatus.DONE));

        runRealOverrides(dag, "test-change");

        // specs has glob pattern, should not be checked
        assertEquals(ArtifactStatus.DONE, findArtifact(dag, "specs").status());
        assertTrue(dag.isComplete());
    }

    @Test
    void alreadyNotDone_notAffected() throws IOException {
        setupChangeDir("test-change",
                "proposal.md", REAL_CONTENT);

        ChangeArtifactDag dag = createDag(false,
                artifact("proposal", "proposal.md", ArtifactStatus.DONE),
                artifact("design", "design.md", ArtifactStatus.READY),
                artifact("tasks", "tasks.md", ArtifactStatus.BLOCKED));

        runRealOverrides(dag, "test-change");

        assertEquals(ArtifactStatus.DONE, findArtifact(dag, "proposal").status());
        assertEquals(ArtifactStatus.READY, findArtifact(dag, "design").status());
        assertEquals(ArtifactStatus.BLOCKED, findArtifact(dag, "tasks").status());
    }

    /**
     * Pins reorder-invariance: run the real override over the SAME scaffolded files using the two
     * real captured artifact orderings (1.6 {@code [proposal, design, specs, tasks]} vs 1.7
     * {@code [proposal, specs, design, tasks]}) and assert identical derived status + missingDeps
     * per id. A guard first proves the two orderings actually differ, so this can never degrade to
     * a tautology if the fixtures ever converge.
     */
    @Test
    void reorderInvariant_scaffoldedDag_identicalUnder16And17Order() throws IOException {
        setupChangeDir("demo-change",
                "proposal.md", REAL_CONTENT,
                "design.md", SCAFFOLDED_DESIGN,
                "tasks.md", SCAFFOLDED_TASKS);

        ChangeArtifactDag dag16 = CliOutputParser.parseChangeStatus(fixture("1.6.0/status.json"));
        ChangeArtifactDag dag17 = CliOutputParser.parseChangeStatus(fixture("1.7.0/status.json"));

        List<String> order16 = dag16.getArtifacts().stream().map(ArtifactInfo::id).toList();
        List<String> order17 = dag17.getArtifacts().stream().map(ArtifactInfo::id).toList();
        assertNotEquals(order16, order17, "guard: the 1.6 and 1.7 fixtures must actually differ in order");

        runRealOverrides(dag16, "demo-change");
        runRealOverrides(dag17, "demo-change");

        assertEquals(statusById(dag16), statusById(dag17),
                "the schema-order reorder must not change any artifact's overridden status");
        assertEquals(missingDepsById(dag16), missingDepsById(dag17),
                "the schema-order reorder must not change any artifact's blocked-by list");
    }

    // --- Helpers ---

    private void runRealOverrides(ChangeArtifactDag dag, String changeName) {
        when(project.getBasePath()).thenReturn(tempDir.toString());
        when(project.getService(ScaffoldingDetectionService.class))
                .thenReturn(new ScaffoldingDetectionService(null));
        new ArtifactOrchestrationService(project).applyScaffoldingOverrides(dag, changeName);
    }

    private void setupChangeDir(String changeName, String... fileAndContent) throws IOException {
        Path changeDir = tempDir.resolve("openspec/changes/" + changeName);
        Files.createDirectories(changeDir);
        for (int i = 0; i < fileAndContent.length; i += 2) {
            Files.writeString(changeDir.resolve(fileAndContent[i]), fileAndContent[i + 1]);
        }
    }

    private static Map<String, ArtifactStatus> statusById(ChangeArtifactDag dag) {
        Map<String, ArtifactStatus> m = new LinkedHashMap<>();
        dag.getArtifacts().forEach(a -> m.put(a.id(), a.status()));
        return m;
    }

    private static Map<String, List<String>> missingDepsById(ChangeArtifactDag dag) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        dag.getArtifacts().forEach(a -> m.put(a.id(), a.missingDeps()));
        return m;
    }

    private static ArtifactInfo artifact(String id, String outputPath, ArtifactStatus status) {
        return new ArtifactInfo(id, outputPath, status, List.of());
    }

    private static ChangeArtifactDag createDag(boolean isComplete, ArtifactInfo... artifacts) {
        ChangeArtifactDag dag = new ChangeArtifactDag();
        dag.setChangeName("test-change");
        dag.setSchemaName("spec-driven");
        dag.setComplete(isComplete);
        dag.setArtifacts(List.of(artifacts));
        return dag;
    }

    private static ArtifactInfo findArtifact(ChangeArtifactDag dag, String id) {
        return dag.getArtifacts().stream()
                .filter(a -> a.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Artifact not found: " + id));
    }

    private static String fixture(String name) {
        String path = "/fixtures/cli/" + name;
        try (InputStream is = ScaffoldingOverrideTest.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
