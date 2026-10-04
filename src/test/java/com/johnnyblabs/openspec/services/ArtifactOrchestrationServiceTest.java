package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.AiApiException;
import com.johnnyblabs.openspec.ai.AiExecutionService;
import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.model.ArtifactInstruction;
import com.johnnyblabs.openspec.model.ArtifactStatus;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.util.CliOutputParser;
import com.johnnyblabs.openspec.util.CliRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtifactOrchestrationServiceTest {

    @Mock Project project;
    @Mock ScaffoldingDetectionService scaffoldingService;

    private ArtifactOrchestrationService service;

    @BeforeEach
    void setUp() {
        service = new ArtifactOrchestrationService(project);
    }

    @Nested
    class DagParsing {

        @Test
        void parsesCompleteDagFromCliJson() {
            String json = """
                    {
                      "changeName": "test-change",
                      "schemaName": "spec-driven",
                      "isComplete": false,
                      "artifacts": [
                        {"id": "proposal", "outputPath": "proposal.md", "status": "done", "missingDeps": []},
                        {"id": "design", "outputPath": "design.md", "status": "done", "missingDeps": []},
                        {"id": "specs", "outputPath": "specs/**/*.md", "status": "ready", "missingDeps": []},
                        {"id": "tasks", "outputPath": "tasks.md", "status": "blocked", "missingDeps": ["specs"]}
                      ]
                    }
                    """;

            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(json);

            assertNotNull(dag);
            assertEquals(4, dag.getArtifacts().size());
            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().get(0).status());
            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().get(1).status());
            assertEquals(ArtifactStatus.READY, dag.getArtifacts().get(2).status());
            assertEquals(ArtifactStatus.BLOCKED, dag.getArtifacts().get(3).status());
            assertEquals("proposal", dag.getArtifacts().get(0).id());
            assertEquals("tasks", dag.getArtifacts().get(3).id());
        }

        @Test
        void getArtifactStatus_callsCliAndParsesResult() {
            CliRunner.CliResult cliResult = new CliRunner.CliResult(0,
                    "{\"changeName\":\"c\",\"isComplete\":true,\"artifacts\":[" +
                            "{\"id\":\"p\",\"outputPath\":\"p.md\",\"status\":\"done\",\"missingDeps\":[]}]}",
                    "");

            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                cli.when(() -> CliRunner.run(eq(project), eq("status"), eq("--change"), eq("c"), eq("--json")))
                        .thenReturn(cliResult);
                // No scaffolding service needed since no DONE artifact with non-glob path will trigger it
                // Actually it will try — let's mock it
                when(project.getBasePath()).thenReturn("/tmp/test");
                when(project.getService(ScaffoldingDetectionService.class)).thenReturn(scaffoldingService);
                when(scaffoldingService.isScaffolding(anyString())).thenReturn(false);

                ChangeArtifactDag result = service.getArtifactStatus("c");

                assertNotNull(result);
                assertEquals(1, result.getArtifacts().size());
                assertEquals("p", result.getArtifacts().getFirst().id());
            }
        }

        @Test
        void cacheInvalidation_reInvokesCli() {
            CliRunner.CliResult cliResult = new CliRunner.CliResult(0,
                    "{\"changeName\":\"c\",\"isComplete\":true,\"artifacts\":[" +
                            "{\"id\":\"p\",\"outputPath\":\"p.md\",\"status\":\"done\",\"missingDeps\":[]}]}",
                    "");

            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                cli.when(() -> CliRunner.run(eq(project), eq("status"), eq("--change"), eq("c"), eq("--json")))
                        .thenReturn(cliResult);
                when(project.getBasePath()).thenReturn("/tmp/test");
                when(project.getService(ScaffoldingDetectionService.class)).thenReturn(scaffoldingService);
                when(scaffoldingService.isScaffolding(anyString())).thenReturn(false);

                // First call
                service.getArtifactStatus("c");
                // Invalidate
                service.invalidateCache("c");
                // Second call should re-invoke CLI
                service.getArtifactStatus("c");

                cli.verify(() -> CliRunner.run(eq(project), eq("status"), eq("--change"), eq("c"), eq("--json")),
                        times(2));
            }
        }
    }

    @Nested
    class ScaffoldingOverride {

        @Test
        void scaffoldedDoneArtifact_overriddenToReady() {
            when(project.getBasePath()).thenReturn("/tmp/test");
            when(project.getService(ScaffoldingDetectionService.class)).thenReturn(scaffoldingService);

            // proposal is DONE but is scaffolding
            when(scaffoldingService.isScaffolding("/tmp/test/openspec/changes/c/proposal.md")).thenReturn(true);

            ChangeArtifactDag dag = new ChangeArtifactDag();
            dag.setChangeName("c");
            dag.setComplete(true);
            dag.setArtifacts(List.of(
                    new ArtifactInfo("proposal", "proposal.md", ArtifactStatus.DONE, List.of())
            ));

            service.applyScaffoldingOverrides(dag, "c");

            assertEquals(ArtifactStatus.READY, dag.getArtifacts().getFirst().status());
            assertFalse(dag.isComplete());
        }

        @Test
        void nonScaffoldedDoneArtifact_remainsDone() {
            when(project.getBasePath()).thenReturn("/tmp/test");
            when(project.getService(ScaffoldingDetectionService.class)).thenReturn(scaffoldingService);
            when(scaffoldingService.isScaffolding(anyString())).thenReturn(false);

            ChangeArtifactDag dag = new ChangeArtifactDag();
            dag.setChangeName("c");
            dag.setComplete(true);
            dag.setArtifacts(List.of(
                    new ArtifactInfo("proposal", "proposal.md", ArtifactStatus.DONE, List.of())
            ));

            service.applyScaffoldingOverrides(dag, "c");

            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().getFirst().status());
            assertTrue(dag.isComplete());
        }

        @Test
        void globOutputPath_skippedByScaffoldingDetection() {
            when(project.getBasePath()).thenReturn("/tmp/test");
            when(project.getService(ScaffoldingDetectionService.class)).thenReturn(scaffoldingService);

            ChangeArtifactDag dag = new ChangeArtifactDag();
            dag.setChangeName("c");
            dag.setArtifacts(List.of(
                    new ArtifactInfo("specs", "specs/**/*.md", ArtifactStatus.DONE, List.of())
            ));

            service.applyScaffoldingOverrides(dag, "c");

            // Glob paths should not be checked for scaffolding
            verify(scaffoldingService, never()).isScaffolding(anyString());
            assertEquals(ArtifactStatus.DONE, dag.getArtifacts().getFirst().status());
        }
    }

    @Nested
    class NextReadyArtifact {

        @Test
        void returnsFirstReadyArtifact() {
            ChangeArtifactDag dag = new ChangeArtifactDag();
            dag.setChangeName("c");
            dag.setComplete(false);
            dag.setArtifacts(List.of(
                    new ArtifactInfo("proposal", "proposal.md", ArtifactStatus.DONE, List.of()),
                    new ArtifactInfo("design", "design.md", ArtifactStatus.READY, List.of()),
                    new ArtifactInfo("tasks", "tasks.md", ArtifactStatus.BLOCKED, List.of("design"))
            ));

            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                cli.when(() -> CliRunner.run(eq(project), eq("status"), eq("--change"), eq("c"), eq("--json")))
                        .thenReturn(new CliRunner.CliResult(0,
                                "{\"changeName\":\"c\",\"isComplete\":false,\"artifacts\":[" +
                                "{\"id\":\"proposal\",\"outputPath\":\"proposal.md\",\"status\":\"done\",\"missingDeps\":[]}," +
                                "{\"id\":\"design\",\"outputPath\":\"design.md\",\"status\":\"ready\",\"missingDeps\":[]}," +
                                "{\"id\":\"tasks\",\"outputPath\":\"tasks.md\",\"status\":\"blocked\",\"missingDeps\":[\"design\"]}" +
                                "]}", ""));
                when(project.getBasePath()).thenReturn("/tmp/test");
                when(project.getService(ScaffoldingDetectionService.class)).thenReturn(scaffoldingService);

                ArtifactInfo next = service.getNextReadyArtifact("c");
                assertNotNull(next);
                assertEquals("design", next.id());
            }
        }

        @Test
        void returnsNull_whenAllComplete() {
            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                cli.when(() -> CliRunner.run(eq(project), eq("status"), eq("--change"), eq("c"), eq("--json")))
                        .thenReturn(new CliRunner.CliResult(0,
                                "{\"changeName\":\"c\",\"isComplete\":true,\"artifacts\":[" +
                                "{\"id\":\"proposal\",\"outputPath\":\"proposal.md\",\"status\":\"done\",\"missingDeps\":[]}," +
                                "{\"id\":\"tasks\",\"outputPath\":\"tasks.md\",\"status\":\"done\",\"missingDeps\":[]}" +
                                "]}", ""));
                when(project.getBasePath()).thenReturn("/tmp/test");
                when(project.getService(ScaffoldingDetectionService.class)).thenReturn(scaffoldingService);
                when(scaffoldingService.isScaffolding(anyString())).thenReturn(false);

                ArtifactInfo next = service.getNextReadyArtifact("c");
                assertNull(next);
            }
        }

        @Test
        void returnsNull_whenAllBlocked() {
            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                cli.when(() -> CliRunner.run(eq(project), eq("status"), eq("--change"), eq("c"), eq("--json")))
                        .thenReturn(new CliRunner.CliResult(0,
                                "{\"changeName\":\"c\",\"isComplete\":false,\"artifacts\":[" +
                                "{\"id\":\"design\",\"outputPath\":\"design.md\",\"status\":\"blocked\",\"missingDeps\":[\"proposal\"]}," +
                                "{\"id\":\"tasks\",\"outputPath\":\"tasks.md\",\"status\":\"blocked\",\"missingDeps\":[\"design\"]}" +
                                "]}", ""));
                when(project.getBasePath()).thenReturn("/tmp/test");
                when(project.getService(ScaffoldingDetectionService.class)).thenReturn(scaffoldingService);

                ArtifactInfo next = service.getNextReadyArtifact("c");
                assertNull(next);
            }
        }
    }

    @Nested
    class GenerateAllLoop {

        @Mock AiExecutionService apiService;

        @BeforeEach
        void configureOptionalRoutingServices() {
            doReturn(null).when(project).getService(DeliveryMethodResolver.class);
            doReturn(null).when(project).getService(WorkflowSchemaContextService.class);
        }

        @TempDir
        Path tempDir;

        private ChangeArtifactDag makeDag(boolean complete, ArtifactInfo... artifacts) {
            ChangeArtifactDag dag = new ChangeArtifactDag();
            dag.setChangeName("c");
            dag.setComplete(complete);
            dag.setArtifacts(List.of(artifacts));
            return dag;
        }

        @Test
        void generatesInDependencyOrder_firesCallbacksCorrectly() throws Exception {
            // Track listener calls
            List<String> calls = new ArrayList<>();

            GenerateAllListener listener = new GenerateAllListener() {
                @Override public void onArtifactStarted(String id, int index, int total) {
                    calls.add("started:" + id + ":" + index + "/" + total);
                }
                @Override public void onArtifactCompleted(String id) {
                    calls.add("completed:" + id);
                }
                @Override public void onAllComplete() {
                    calls.add("allComplete");
                }
                @Override public void onError(String id, Exception e) {
                    calls.add("error:" + id);
                }
                @Override public void onCancelled(String id) {
                    calls.add("cancelled:" + id);
                }
            };

            // Set up temp change dir for file writes
            Path changeDir = tempDir.resolve("openspec/changes/c");
            Files.createDirectories(changeDir);

            AtomicInteger callCount = new AtomicInteger(0);

            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                when(project.getBasePath()).thenReturn(tempDir.toString());
                when(project.getService(ScaffoldingDetectionService.class)).thenReturn(scaffoldingService);

                // Mock status calls — evolving DAG state
                cli.when(() -> CliRunner.run(eq(project), eq("status"), eq("--change"), eq("c"), eq("--json")))
                        .thenAnswer(inv -> {
                            int n = callCount.getAndIncrement();
                            String json = switch (n) {
                                case 0 -> // Initial count call: 3 remaining
                                        "{\"changeName\":\"c\",\"isComplete\":false,\"artifacts\":[" +
                                        "{\"id\":\"proposal\",\"outputPath\":\"proposal.md\",\"status\":\"ready\",\"missingDeps\":[]}," +
                                        "{\"id\":\"design\",\"outputPath\":\"design.md\",\"status\":\"blocked\",\"missingDeps\":[\"proposal\"]}," +
                                        "{\"id\":\"tasks\",\"outputPath\":\"tasks.md\",\"status\":\"blocked\",\"missingDeps\":[\"design\"]}" +
                                        "]}";
                                case 1 -> // After invalidate, loop iteration 1: proposal still ready
                                        "{\"changeName\":\"c\",\"isComplete\":false,\"artifacts\":[" +
                                        "{\"id\":\"proposal\",\"outputPath\":\"proposal.md\",\"status\":\"ready\",\"missingDeps\":[]}," +
                                        "{\"id\":\"design\",\"outputPath\":\"design.md\",\"status\":\"blocked\",\"missingDeps\":[\"proposal\"]}," +
                                        "{\"id\":\"tasks\",\"outputPath\":\"tasks.md\",\"status\":\"blocked\",\"missingDeps\":[\"design\"]}" +
                                        "]}";
                                case 2 -> // After proposal done, iteration 2: design now ready
                                        "{\"changeName\":\"c\",\"isComplete\":false,\"artifacts\":[" +
                                        "{\"id\":\"proposal\",\"outputPath\":\"proposal.md\",\"status\":\"done\",\"missingDeps\":[]}," +
                                        "{\"id\":\"design\",\"outputPath\":\"design.md\",\"status\":\"ready\",\"missingDeps\":[]}," +
                                        "{\"id\":\"tasks\",\"outputPath\":\"tasks.md\",\"status\":\"blocked\",\"missingDeps\":[\"design\"]}" +
                                        "]}";
                                case 3 -> // After design done, iteration 3: tasks now ready
                                        "{\"changeName\":\"c\",\"isComplete\":false,\"artifacts\":[" +
                                        "{\"id\":\"proposal\",\"outputPath\":\"proposal.md\",\"status\":\"done\",\"missingDeps\":[]}," +
                                        "{\"id\":\"design\",\"outputPath\":\"design.md\",\"status\":\"done\",\"missingDeps\":[]}," +
                                        "{\"id\":\"tasks\",\"outputPath\":\"tasks.md\",\"status\":\"ready\",\"missingDeps\":[]}" +
                                        "]}";
                                default -> // After tasks done: all complete
                                        "{\"changeName\":\"c\",\"isComplete\":true,\"artifacts\":[" +
                                        "{\"id\":\"proposal\",\"outputPath\":\"proposal.md\",\"status\":\"done\",\"missingDeps\":[]}," +
                                        "{\"id\":\"design\",\"outputPath\":\"design.md\",\"status\":\"done\",\"missingDeps\":[]}," +
                                        "{\"id\":\"tasks\",\"outputPath\":\"tasks.md\",\"status\":\"done\",\"missingDeps\":[]}" +
                                        "]}";
                            };
                            return new CliRunner.CliResult(0, json, "");
                        });

                // Mock instruction calls
                ArtifactInstruction proposalInstr = new ArtifactInstruction("c", "proposal", changeDir.toString(), "proposal.md", "write proposal", null, List.of(), List.of());
                ArtifactInstruction designInstr = new ArtifactInstruction("c", "design", changeDir.toString(), "design.md", "write design", null, List.of(), List.of());
                ArtifactInstruction tasksInstr = new ArtifactInstruction("c", "tasks", changeDir.toString(), "tasks.md", "write tasks", null, List.of(), List.of());

                cli.when(() -> CliRunner.run(eq(project), eq("instructions"), eq("proposal"), eq("--change"), eq("c"), eq("--json")))
                        .thenReturn(new CliRunner.CliResult(0, "{\"changeName\":\"c\",\"artifactId\":\"proposal\",\"changeDir\":\"" + jsonEscapedPath(changeDir) + "\",\"outputPath\":\"proposal.md\",\"instruction\":\"write proposal\"}", ""));
                cli.when(() -> CliRunner.run(eq(project), eq("instructions"), eq("design"), eq("--change"), eq("c"), eq("--json")))
                        .thenReturn(new CliRunner.CliResult(0, "{\"changeName\":\"c\",\"artifactId\":\"design\",\"changeDir\":\"" + jsonEscapedPath(changeDir) + "\",\"outputPath\":\"design.md\",\"instruction\":\"write design\"}", ""));
                cli.when(() -> CliRunner.run(eq(project), eq("instructions"), eq("tasks"), eq("--change"), eq("c"), eq("--json")))
                        .thenReturn(new CliRunner.CliResult(0, "{\"changeName\":\"c\",\"artifactId\":\"tasks\",\"changeDir\":\"" + jsonEscapedPath(changeDir) + "\",\"outputPath\":\"tasks.md\",\"instruction\":\"write tasks\"}", ""));

                // Mock API generate
                when(apiService.generateAndApply(any())).thenReturn(List.of());

                service.generateAllRemaining("c", apiService, listener);
            }

            // Verify callback order
            assertEquals(7, calls.size());
            assertEquals("started:proposal:1/3", calls.get(0));
            assertEquals("completed:proposal", calls.get(1));
            assertEquals("started:design:2/3", calls.get(2));
            assertEquals("completed:design", calls.get(3));
            assertEquals("started:tasks:3/3", calls.get(4));
            assertEquals("completed:tasks", calls.get(5));
            assertEquals("allComplete", calls.get(6));

            // The execution service owns validated writes; orchestration submits instructions in order.
            org.mockito.ArgumentCaptor<ArtifactInstruction> instructions =
                    org.mockito.ArgumentCaptor.forClass(ArtifactInstruction.class);
            verify(apiService, times(3)).generateAndApply(instructions.capture());
            assertEquals(List.of("proposal", "design", "tasks"), instructions.getAllValues().stream()
                    .map(ArtifactInstruction::artifactId).toList());
            verifyNoMoreInteractions(apiService);
        }

        @Test
        void stopsOnApiError_firesOnError() throws Exception {
            List<String> calls = new ArrayList<>();
            GenerateAllListener listener = new GenerateAllListener() {
                @Override public void onArtifactStarted(String id, int index, int total) {
                    calls.add("started:" + id);
                }
                @Override public void onArtifactCompleted(String id) {
                    calls.add("completed:" + id);
                }
                @Override public void onAllComplete() {
                    calls.add("allComplete");
                }
                @Override public void onError(String id, Exception e) {
                    calls.add("error:" + id + ":" + e.getMessage());
                }
                @Override public void onCancelled(String id) {
                    calls.add("cancelled:" + id);
                }
            };

            Path changeDir = tempDir.resolve("openspec/changes/c");
            Files.createDirectories(changeDir);
            AtomicInteger callCount = new AtomicInteger(0);

            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                when(project.getBasePath()).thenReturn(tempDir.toString());
                when(project.getService(ScaffoldingDetectionService.class)).thenReturn(scaffoldingService);

                // Two artifacts: proposal (ready), design (ready after proposal)
                cli.when(() -> CliRunner.run(eq(project), eq("status"), eq("--change"), eq("c"), eq("--json")))
                        .thenAnswer(inv -> {
                            int n = callCount.getAndIncrement();
                            String json = switch (n) {
                                case 0, 1 ->
                                        "{\"changeName\":\"c\",\"isComplete\":false,\"artifacts\":[" +
                                        "{\"id\":\"proposal\",\"outputPath\":\"proposal.md\",\"status\":\"ready\",\"missingDeps\":[]}," +
                                        "{\"id\":\"design\",\"outputPath\":\"design.md\",\"status\":\"blocked\",\"missingDeps\":[\"proposal\"]}" +
                                        "]}";
                                default ->
                                        "{\"changeName\":\"c\",\"isComplete\":false,\"artifacts\":[" +
                                        "{\"id\":\"proposal\",\"outputPath\":\"proposal.md\",\"status\":\"done\",\"missingDeps\":[]}," +
                                        "{\"id\":\"design\",\"outputPath\":\"design.md\",\"status\":\"ready\",\"missingDeps\":[]}" +
                                        "]}";
                            };
                            return new CliRunner.CliResult(0, json, "");
                        });

                cli.when(() -> CliRunner.run(eq(project), eq("instructions"), eq("proposal"), eq("--change"), eq("c"), eq("--json")))
                        .thenReturn(new CliRunner.CliResult(0, "{\"changeName\":\"c\",\"artifactId\":\"proposal\",\"changeDir\":\"" + jsonEscapedPath(changeDir) + "\",\"outputPath\":\"proposal.md\",\"instruction\":\"x\"}", ""));
                cli.when(() -> CliRunner.run(eq(project), eq("instructions"), eq("design"), eq("--change"), eq("c"), eq("--json")))
                        .thenReturn(new CliRunner.CliResult(0, "{\"changeName\":\"c\",\"artifactId\":\"design\",\"changeDir\":\"" + jsonEscapedPath(changeDir) + "\",\"outputPath\":\"design.md\",\"instruction\":\"x\"}", ""));

                // First generate succeeds, second throws
                when(apiService.generateAndApply(any()))
                        .thenReturn(List.of())
                        .thenThrow(new AiApiException("API rate limit (HTTP 429)"));

                service.generateAllRemaining("c", apiService, listener);
            }

            assertTrue(calls.contains("started:proposal"));
            assertTrue(calls.contains("completed:proposal"));
            assertTrue(calls.contains("started:design"));
            assertTrue(calls.contains("error:design:API rate limit (HTTP 429)"));
            assertFalse(calls.contains("allComplete"));

            verify(apiService, times(2)).generateAndApply(any());
            verifyNoMoreInteractions(apiService);
        }

        @Test
        void respectsCancellation_firesOnCancelled() throws Exception {
            when(project.getService(AiExecutionService.class)).thenReturn(apiService);
            List<String> calls = new ArrayList<>();
            GenerateAllListener listener = new GenerateAllListener() {
                @Override public void onArtifactStarted(String id, int index, int total) {
                    calls.add("started:" + id);
                }
                @Override public void onArtifactCompleted(String id) {
                    calls.add("completed:" + id);
                    // Cancel after first artifact completes
                    service.cancelGenerateAll();
                }
                @Override public void onAllComplete() {
                    calls.add("allComplete");
                }
                @Override public void onError(String id, Exception e) {
                    calls.add("error:" + id);
                }
                @Override public void onCancelled(String id) {
                    calls.add("cancelled:" + id);
                }
            };

            Path changeDir = tempDir.resolve("openspec/changes/c");
            Files.createDirectories(changeDir);
            AtomicInteger callCount = new AtomicInteger(0);

            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                when(project.getBasePath()).thenReturn(tempDir.toString());
                when(project.getService(ScaffoldingDetectionService.class)).thenReturn(scaffoldingService);

                cli.when(() -> CliRunner.run(eq(project), eq("status"), eq("--change"), eq("c"), eq("--json")))
                        .thenAnswer(inv -> {
                            int n = callCount.getAndIncrement();
                            String json = n <= 1
                                    ? "{\"changeName\":\"c\",\"isComplete\":false,\"artifacts\":[" +
                                      "{\"id\":\"proposal\",\"outputPath\":\"proposal.md\",\"status\":\"ready\",\"missingDeps\":[]}," +
                                      "{\"id\":\"design\",\"outputPath\":\"design.md\",\"status\":\"blocked\",\"missingDeps\":[\"proposal\"]}" +
                                      "]}"
                                    : "{\"changeName\":\"c\",\"isComplete\":false,\"artifacts\":[" +
                                      "{\"id\":\"proposal\",\"outputPath\":\"proposal.md\",\"status\":\"done\",\"missingDeps\":[]}," +
                                      "{\"id\":\"design\",\"outputPath\":\"design.md\",\"status\":\"ready\",\"missingDeps\":[]}" +
                                      "]}";
                            return new CliRunner.CliResult(0, json, "");
                        });

                cli.when(() -> CliRunner.run(eq(project), eq("instructions"), eq("proposal"), eq("--change"), eq("c"), eq("--json")))
                        .thenReturn(new CliRunner.CliResult(0, "{\"changeName\":\"c\",\"artifactId\":\"proposal\",\"changeDir\":\"" + jsonEscapedPath(changeDir) + "\",\"outputPath\":\"proposal.md\",\"instruction\":\"x\"}", ""));

                when(apiService.generateAndApply(any())).thenReturn(List.of());

                service.generateAllRemaining("c", apiService, listener);
            }

            assertTrue(calls.contains("started:proposal"));
            assertTrue(calls.contains("completed:proposal"));
            assertTrue(calls.stream().anyMatch(c -> c.startsWith("cancelled:")));
            assertFalse(calls.contains("allComplete"));
            // Second artifact was never started
            assertFalse(calls.contains("started:design"));

            verify(apiService).generateAndApply(argThat(instruction -> instruction.artifactId().equals("proposal")));
            verify(apiService).cancelActive();
            verifyNoMoreInteractions(apiService);
        }
    }

    /**
     * JSON-escapes a filesystem path for embedding in hand-built mock CLI JSON.
     * Windows paths contain backslashes, which are invalid JSON escape openers —
     * embedding them raw corrupts the parsed changeDir (the Windows CI leg caught this).
     */
    private static String jsonEscapedPath(java.nio.file.Path p) {
        return p.toString().replace("\\", "\\\\");
    }

    private static String fixture(String name) {
        String path = "/fixtures/cli/" + name;
        try (java.io.InputStream is = ArtifactOrchestrationServiceTest.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    class RequiredClosure {
        @Test
        void capturedCliDoneRootStillIncludesIncompleteDependencies() {
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(fixture("1.7.0/status.json"));
            List<ArtifactInfo> required = ArtifactOrchestrationService.requiredArtifacts(dag);
            assertEquals(List.of("proposal", "specs", "design", "tasks"),
                    required.stream().map(ArtifactInfo::id).toList());
            assertEquals(ArtifactStatus.DONE, required.getLast().status());
            assertTrue(required.stream().anyMatch(a -> a.status() == ArtifactStatus.READY));
        }

        @Test
        void nonDefaultDagFollowsEdgesIndependentOfOrderAndExcludesOptionalBranches() {
            // Domain objects deliberately exercise a custom schema, not an inferred CLI JSON shape.
            ChangeArtifactDag dag = new ChangeArtifactDag();
            dag.setApplyRequires(List.of("release-note"));
            dag.setArtifacts(List.of(
                    new ArtifactInfo("release-note", "release.md", ArtifactStatus.DONE, List.of(), List.of("analysis")),
                    new ArtifactInfo("optional-diagram", "diagram.md", ArtifactStatus.READY, List.of(), List.of("brief")),
                    new ArtifactInfo("analysis", "analysis.md", ArtifactStatus.BLOCKED, List.of("brief"), List.of("brief")),
                    new ArtifactInfo("brief", "brief.md", ArtifactStatus.READY, List.of(), List.of())));
            assertEquals(List.of("release-note", "analysis", "brief"),
                    ArtifactOrchestrationService.requiredArtifacts(dag).stream().map(ArtifactInfo::id).toList());
        }

        @Test
        void unknownDependencyDoesNotSilentlyComplete() {
            ChangeArtifactDag dag = new ChangeArtifactDag();
            dag.setApplyRequires(List.of("final"));
            dag.setArtifacts(List.of(new ArtifactInfo("final", "final.md", ArtifactStatus.DONE,
                    List.of(), List.of("missing"))));
            assertThrows(IllegalStateException.class, () -> ArtifactOrchestrationService.requiredArtifacts(dag));
        }

        @Test
        void realSkippedFixtureCompletesWithoutExecution() {
            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                cli.when(() -> CliRunner.run(project, "status", "--change", "demo-change", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, fixture("1.7.0/status-skipped.json"), ""));
                AiExecutionService execution = mock(AiExecutionService.class);
                GenerateAllListener listener = mock(GenerateAllListener.class);
                service.generateAllRemaining("demo-change", execution, listener);
                verify(listener).onAllComplete();
                verifyNoMoreInteractions(listener);
                verifyNoInteractions(execution);
            }
        }

        @Test
        void generatesMissingDependencyEvenWhenCliApplyGateIsComplete() throws Exception {
            var optimistic = com.google.gson.JsonParser.parseString(fixture("1.7.0/status.json")).getAsJsonObject();
            optimistic.addProperty("isComplete", true);
            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                cli.when(() -> CliRunner.run(project, "status", "--change", "demo-change", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, optimistic.toString(), ""),
                                new CliRunner.CliResult(0, optimistic.toString(), ""),
                                new CliRunner.CliResult(0, fixture("1.7.0/status-skipped.json"), ""));
                cli.when(() -> CliRunner.run(project, "instructions", "specs", "--change", "demo-change", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, fixture("1.7.0/instructions-specs.json"), ""));
                AiExecutionService execution = mock(AiExecutionService.class);
                GenerateAllListener listener = mock(GenerateAllListener.class);
                service.generateAllRemaining("demo-change", execution, listener);
                var order = inOrder(listener, execution);
                order.verify(listener).onArtifactStarted("specs", 1, 1);
                order.verify(execution).generateAndApply(argThat(i -> i.artifactId().equals("specs")));
                order.verify(listener).onArtifactCompleted("specs");
                order.verify(listener).onAllComplete();
                verifyNoMoreInteractions(listener, execution);
            }
        }

        @Test
        void blockedRequiredClosureNeverReportsCompletion() {
            // Mutate the parsed real fixture to model an incompatible/blocked dependency state.
            var json = com.google.gson.JsonParser.parseString(fixture("1.7.0/status.json")).getAsJsonObject();
            json.addProperty("isComplete", true); // optimistic CLI apply gate cannot decide full completion
            for (var artifact : json.getAsJsonArray("artifacts")) {
                if (artifact.getAsJsonObject().get("id").getAsString().equals("specs"))
                    artifact.getAsJsonObject().addProperty("status", "blocked");
            }
            try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
                cli.when(() -> CliRunner.run(project, "status", "--change", "demo-change", "--json"))
                        .thenReturn(new CliRunner.CliResult(0, json.toString(), ""));
                AiExecutionService execution = mock(AiExecutionService.class);
                GenerateAllListener listener = mock(GenerateAllListener.class);
                service.generateAllRemaining("demo-change", execution, listener);
                verify(listener).onError(isNull(), isA(IllegalStateException.class));
                verify(listener, never()).onAllComplete();
                verifyNoInteractions(execution);
            }
        }
    }

    /**
     * {@link ArtifactOrchestrationService#completedDownstream} — the DONE artifacts a regeneration
     * could invalidate, which feeds the Regenerate confirmation dialog. Pinned against the real
     * captured 1.6 and 1.7 status DAGs. 1.7 adds the CLI's {@code requires} edges and reorders
     * {@code artifacts[]} to schema order; the derivation must consume the real edges (precise,
     * order-independent) on 1.7 and fall back to the positional heuristic on pre-1.7 output.
     */
    @Nested
    class CompletedDownstream {

        @Test
        void specs_downstreamIsTasksOnly_andInvariantAcrossGenerations() {
            List<ArtifactInfo> a16 = CliOutputParser.parseChangeStatus(fixture("1.6.0/status.json")).getArtifacts();
            List<ArtifactInfo> a17 = CliOutputParser.parseChangeStatus(fixture("1.7.0/status.json")).getArtifacts();

            // Guard: the two generations exercise the two code paths (edges present vs absent) and
            // genuinely differ in array order — so this can't silently become a tautology.
            assertTrue(a17.stream().anyMatch(x -> !x.requires().isEmpty()), "1.7 must carry requires edges");
            assertTrue(a16.stream().allMatch(x -> x.requires().isEmpty()), "1.6 must have no requires edges");
            assertNotEquals(a16.stream().map(ArtifactInfo::id).toList(),
                    a17.stream().map(ArtifactInfo::id).toList(), "orders must differ");

            assertEquals(List.of("tasks"),
                    ArtifactOrchestrationService.completedDownstream(a17, "specs"),
                    "1.7: only tasks requires specs");
            assertEquals(List.of("tasks"),
                    ArtifactOrchestrationService.completedDownstream(a16, "specs"),
                    "1.6 fallback: tasks is the only DONE artifact after specs");
        }

        @Test
        void specs_downstreamOn1_7_excludesUnrelatedSiblingDesign() {
            // The precise fix: design requires proposal, NOT specs — the old positional heuristic
            // over-listed it on the 1.7 order. The requires-driven derivation must not.
            List<ArtifactInfo> a17 = CliOutputParser.parseChangeStatus(fixture("1.7.0/status.json")).getArtifacts();
            assertFalse(ArtifactOrchestrationService.completedDownstream(a17, "specs").contains("design"),
                    "design does not depend on specs and must not be listed as its downstream");
        }

        @Test
        void proposal_downstreamOn1_7_isTransitiveDoneOnly() {
            // proposal is required (transitively) by specs, design, tasks — but specs is READY, so
            // only the DONE dependents surface, in array order.
            List<ArtifactInfo> a17 = CliOutputParser.parseChangeStatus(fixture("1.7.0/status.json")).getArtifacts();
            assertEquals(List.of("design", "tasks"),
                    ArtifactOrchestrationService.completedDownstream(a17, "proposal"));
        }

        @Test
        void noRequiresEdges_usesPositionalFallback() {
            // A pre-1.7-shaped DAG (no requires) must use the original list-order heuristic.
            List<ArtifactInfo> artifacts = List.of(
                    new ArtifactInfo("proposal", "proposal.md", ArtifactStatus.DONE, List.of()),
                    new ArtifactInfo("design", "design.md", ArtifactStatus.DONE, List.of()),
                    new ArtifactInfo("tasks", "tasks.md", ArtifactStatus.DONE, List.of()));
            assertEquals(List.of("tasks"),
                    ArtifactOrchestrationService.completedDownstream(artifacts, "design"),
                    "fallback: DONE artifacts appearing after 'design' in the list");
            assertEquals(List.of("design", "tasks"),
                    ArtifactOrchestrationService.completedDownstream(artifacts, "proposal"));
        }
    }
}
