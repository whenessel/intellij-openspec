package com.johnnyblabs.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.AiApiException;
import com.johnnyblabs.openspec.ai.AiExecutionService;
import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.model.ArtifactInstruction;
import com.johnnyblabs.openspec.model.ArtifactStatus;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.util.CliOutputParser;
import com.johnnyblabs.openspec.util.CliRunner;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service(Service.Level.PROJECT)
public final class ArtifactOrchestrationService {
    private static final Logger LOG = Logger.getInstance(ArtifactOrchestrationService.class);

    private final Project project;
    private final Map<String, ChangeArtifactDag> dagCache = new ConcurrentHashMap<>();
    private final AtomicBoolean generateAllCancelled = new AtomicBoolean(false);

    public ArtifactOrchestrationService(Project project) {
        this.project = project;
    }

    /**
     * Returns the cached DAG without spawning a CLI process.
     * Safe to call from EDT. Returns null if no cached data exists.
     */
    public ChangeArtifactDag getCachedArtifactStatus(String changeName) {
        return dagCache.get(changeName);
    }

    /**
     * Gets artifact DAG status for a change by calling the CLI.
     * <b>Must NOT be called on EDT</b> — spawns an external process.
     * Falls back to cache on failure.
     */
    public ChangeArtifactDag getArtifactStatus(String changeName) {
        try {
            CliRunner.CliResult result = CliRunner.run(project, "status", "--change", changeName, "--json");
            if (result.isSuccess() && !result.stdout().isBlank()) {
                ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(result.stdout());
                if (dag != null) {
                    applyScaffoldingOverrides(dag, changeName);
                    dagCache.put(changeName, dag);
                    return dag;
                }
            }
        } catch (CliRunner.CliException e) {
            LOG.warn("Failed to get artifact status for change: " + changeName, e);
        }
        return dagCache.get(changeName);
    }

    /**
     * Checks each "done" artifact for scaffolding content and overrides status accordingly.
     * Skips glob-pattern output paths (e.g., specs) since they can't be resolved to a single file.
     */
    void applyScaffoldingOverrides(ChangeArtifactDag dag, String changeName) {
        String basePath = project.getBasePath();
        if (basePath == null) return;

        ScaffoldingDetectionService detector = project.getService(ScaffoldingDetectionService.class);
        if (detector == null) return;

        String changeDir = basePath + "/openspec/changes/" + changeName;
        List<ArtifactInfo> artifacts = dag.getArtifacts();

        // First pass: identify which artifacts are scaffolding
        Set<String> scaffoldedIds = new HashSet<>();
        for (ArtifactInfo artifact : artifacts) {
            if (artifact.status() != ArtifactStatus.DONE) continue;

            String outputPath = artifact.outputPath();
            // Skip glob patterns — can't resolve to a single file
            if (outputPath == null || outputPath.contains("*")) continue;

            String filePath = changeDir + "/" + outputPath;
            if (detector.isScaffolding(filePath)) {
                scaffoldedIds.add(artifact.id());
            }
        }

        if (scaffoldedIds.isEmpty()) return;

        // Second pass: override status based on dependency analysis
        // Use earlier artifacts in the list as implicit dependencies
        // (DAG order: proposal → design/specs → tasks)
        List<ArtifactInfo> updatedArtifacts = new ArrayList<>();
        for (ArtifactInfo artifact : artifacts) {
            if (!scaffoldedIds.contains(artifact.id())) {
                updatedArtifacts.add(artifact);
                continue;
            }

            // Prefer actual schema edges; array position is only a legacy fallback.
            List<String> blockedBy = new ArrayList<>();
            if (artifacts.stream().anyMatch(a -> !a.requires().isEmpty())) {
                for (String dependency : artifact.requires()) {
                    if (scaffoldedIds.contains(dependency) || artifacts.stream().noneMatch(a ->
                            a.id().equals(dependency) && isSatisfied(a.status()))) {
                        blockedBy.add(dependency);
                    }
                }
            } else {
                for (ArtifactInfo earlier : artifacts) {
                    if (earlier.id().equals(artifact.id())) break;
                    if (scaffoldedIds.contains(earlier.id())) blockedBy.add(earlier.id());
                }
            }

            if (blockedBy.isEmpty()) {
                updatedArtifacts.add(new ArtifactInfo(artifact.id(), artifact.outputPath(), ArtifactStatus.READY,
                        List.of(), artifact.requires()));
            } else {
                updatedArtifacts.add(new ArtifactInfo(artifact.id(), artifact.outputPath(), ArtifactStatus.BLOCKED,
                        blockedBy, artifact.requires()));
            }
        }
        dag.setArtifacts(updatedArtifacts);

        // Recalculate isComplete
        boolean allDone = updatedArtifacts.stream()
                .allMatch(a -> isSatisfied(a.status()));
        dag.setComplete(allDone);
    }

    /**
     * Gets generation instructions for a specific artifact.
     */
    public ArtifactInstruction getInstruction(String changeName, String artifactId) throws CliRunner.CliException {
        CliRunner.CliResult result = CliRunner.run(project,
                "instructions", artifactId, "--change", changeName, "--json");
        if (result.isSuccess() && !result.stdout().isBlank()) {
            return CliOutputParser.parseArtifactInstruction(result.stdout());
        }
        throw new CliRunner.CliException(
                "Failed to get instructions: " + (result.stderr().isBlank() ? "empty response" : result.stderr()));
    }

    /**
     * Checks if regenerating the given artifact would affect downstream artifacts
     * that are already complete.
     */
    public List<String> getCompletedDownstream(String changeName, String artifactId) {
        ChangeArtifactDag dag = getArtifactStatus(changeName);
        if (dag == null) return List.of();
        return completedDownstream(dag.getArtifacts(), artifactId);
    }

    /**
     * The DONE artifacts that depend on {@code artifactId} — i.e. those a regeneration of it could
     * make inconsistent. Pure and static so it can be pinned directly against captured status DAGs.
     *
     * <p>When the DAG carries the CLI's own dependency edges ({@code requires}, 1.7+), this walks
     * those edges transitively — precise and independent of the array's order, so it never
     * over-lists a sibling that merely appears later (1.7 reordered {@code artifacts[]} to schema
     * order, which broke the old positional heuristic's assumption). Pre-1.7 output has no
     * {@code requires}, so it falls back to the original list-order heuristic (every DONE artifact
     * appearing after {@code artifactId}), preserving 1.3–1.6 behavior exactly.
     */
    static List<String> completedDownstream(List<ArtifactInfo> artifacts, String artifactId) {
        boolean hasRequiresEdges = artifacts.stream().anyMatch(a -> !a.requires().isEmpty());

        if (hasRequiresEdges) {
            // Transitive dependents over the reverse `requires` edges.
            Set<String> dependents = new HashSet<>();
            Deque<String> frontier = new ArrayDeque<>();
            frontier.add(artifactId);
            while (!frontier.isEmpty()) {
                String target = frontier.poll();
                for (ArtifactInfo a : artifacts) {
                    if (a.requires().contains(target) && dependents.add(a.id())) {
                        frontier.add(a.id());
                    }
                }
            }
            return artifacts.stream()
                    .filter(a -> dependents.contains(a.id()) && a.status() == ArtifactStatus.DONE)
                    .map(ArtifactInfo::id)
                    .toList();
        }

        // Fallback (pre-1.7): positional — DONE artifacts appearing after `artifactId`.
        boolean found = false;
        List<String> downstream = new ArrayList<>();
        for (ArtifactInfo a : artifacts) {
            if (a.id().equals(artifactId)) {
                found = true;
                continue;
            }
            if (found && a.status() == ArtifactStatus.DONE) {
                downstream.add(a.id());
            }
        }
        return downstream;
    }

    /**
     * Returns IDs of artifacts that are ready for generation.
     */
    public List<String> getGenerationOrder(String changeName) {
        ChangeArtifactDag dag = getArtifactStatus(changeName);
        if (dag == null) return List.of();
        return dag.getReadyArtifacts().stream()
                .map(ArtifactInfo::id)
                .collect(Collectors.toList());
    }

    /**
     * Returns the first ready artifact for a change, or null if none are ready.
     * Must NOT be called on EDT — spawns an external process.
     */
    public ArtifactInfo getNextReadyArtifact(String changeName) {
        ChangeArtifactDag dag = getArtifactStatus(changeName);
        if (dag == null) return null;
        List<ArtifactInfo> ready = dag.getReadyArtifacts();
        return ready.isEmpty() ? null : ready.getFirst();
    }

    /**
     * Clears the cached DAG for a change (e.g., after generation). Also invalidates the
     * derived {@link WorkflowSchemaContextService} cache so mode/version context stays in
     * lockstep with the underlying status.
     */
    public void invalidateCache(String changeName) {
        dagCache.remove(changeName);
        WorkflowSchemaContextService contextService = project.getService(WorkflowSchemaContextService.class);
        if (contextService != null) {
            contextService.invalidateCache(changeName);
        }
    }

    /**
     * Clears all cached DAGs and the derived schema-context cache.
     */
    public void invalidateAllCaches() {
        dagCache.clear();
        WorkflowSchemaContextService contextService = project.getService(WorkflowSchemaContextService.class);
        if (contextService != null) {
            contextService.invalidateAllCaches();
        }
    }

    /**
     * Generates all remaining artifacts for a change in dependency order.
     * Calls the AiExecutionService for each artifact, writes the result to disk,
     * and fires listener callbacks at each stage. Checks the cancellation flag
     * between artifacts.
     *
     * Must be called from a background thread.
     */
    public void generateAllRemaining(String changeName, AiExecutionService apiService,
                                     GenerateAllListener listener) {
        DeliveryMethodResolver resolver = project.getService(DeliveryMethodResolver.class);
        generateAllRemaining(changeName, apiService, listener, resolver == null ? null : resolver.resolveSnapshot(null));
    }

    public void generateAllRemaining(String changeName, AiExecutionService apiService,
                                     GenerateAllListener listener,
                                     com.johnnyblabs.openspec.ai.routing.AiRoutingPolicy.RoutingSnapshot route) {
        if (route != null && !route.executesBackend()) {
            listener.onError(null, new IllegalStateException("Manual delivery selected; Generate All starts no backend."));
            return;
        }
        generateAllCancelled.set(false);

        // Count total remaining artifacts
        ChangeArtifactDag dag = getArtifactStatus(changeName);
        if (dag == null) {
            listener.onError(null, new RuntimeException("Failed to load artifact status"));
            return;
        }
        int total;
        try {
            total = (int) requiredArtifacts(dag).stream().filter(a -> !isSatisfied(a.status())).count();
        } catch (IllegalStateException e) {
            listener.onError(null, e);
            return;
        }
        int index = 0;
        Set<String> attempted = new HashSet<>();

        while (true) {
            if (generateAllCancelled.get()) {
                String nextId = findNextReadyArtifactId(dag);
                listener.onCancelled(nextId);
                return;
            }

            // Re-read DAG to respect current dependency state
            invalidateCache(changeName);
            dag = getArtifactStatus(changeName);
            if (dag == null) {
                listener.onError(null, new RuntimeException("Failed to reload artifact status"));
                return;
            }

            List<ArtifactInfo> required;
            try {
                required = requiredArtifacts(dag);
            } catch (IllegalStateException e) {
                listener.onError(null, e);
                return;
            }
            if (required.stream().allMatch(a -> isSatisfied(a.status()))) {
                listener.onAllComplete();
                return;
            }

            String artifactId = required.stream().filter(a -> a.status() == ArtifactStatus.READY)
                    .map(ArtifactInfo::id).findFirst().orElse(null);
            if (artifactId == null) {
                listener.onError(null, new IllegalStateException("Required artifacts remain incomplete but none are ready: "
                        + required.stream().filter(a -> !isSatisfied(a.status())).map(ArtifactInfo::id).toList()
                        + ". Review blocked or conditional artifacts and refresh status."));
                return;
            }
            if (!attempted.add(artifactId)) {
                listener.onError(artifactId, new IllegalStateException("Artifact remains incomplete after generation: "
                        + artifactId + ". Review the accepted output and refresh status."));
                return;
            }

            index++;
            listener.onArtifactStarted(artifactId, index, total);

            try {
                ArtifactInstruction instruction = getInstruction(changeName, artifactId);
                if (route == null) apiService.generateAndApply(instruction);
                else apiService.generateAndApply(instruction, route);
                invalidateCache(changeName);
                listener.onArtifactCompleted(artifactId);
            } catch (AiApiException e) {
                listener.onError(artifactId, e);
                return;
            } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
                listener.onCancelled(artifactId);
                return;
            } catch (Exception e) {
                listener.onError(artifactId, e);
                return;
            }
        }
    }

    /**
     * Cancels a running generateAllRemaining operation.
     * The cancellation takes effect before the next artifact starts.
     */
    public void cancelGenerateAll() {
        generateAllCancelled.set(true);
        project.getService(AiExecutionService.class).cancelActive();
    }

    private String findNextReadyArtifactId(ChangeArtifactDag dag) {
        List<ArtifactInfo> ready = dag.getReadyArtifacts();
        return ready.isEmpty() ? null : ready.getFirst().id();
    }

    private static boolean isSatisfied(ArtifactStatus status) {
        return status == ArtifactStatus.DONE || status == ArtifactStatus.SKIPPED;
    }

    /** Full apply-required closure, including dependencies of artifacts already marked done.
     * Older CLI output has no edges; conservatively retain all artifacts in that case. */
    static List<ArtifactInfo> requiredArtifacts(ChangeArtifactDag dag) {
        List<ArtifactInfo> artifacts = dag.getArtifacts();
        for (String root : dag.getApplyRequires()) {
            if (artifacts.stream().noneMatch(a -> a.id().equals(root)))
                throw new IllegalStateException("Required artifact missing from CLI status: " + root);
        }
        if (dag.getApplyRequires().isEmpty() || artifacts.stream().allMatch(a -> a.requires().isEmpty())) {
            return artifacts;
        }
        Map<String, ArtifactInfo> byId = artifacts.stream().collect(Collectors.toMap(ArtifactInfo::id, a -> a));
        Set<String> required = new HashSet<>();
        Deque<String> frontier = new ArrayDeque<>(dag.getApplyRequires());
        while (!frontier.isEmpty()) {
            String id = frontier.removeFirst();
            if (!required.add(id)) continue;
            ArtifactInfo artifact = byId.get(id);
            if (artifact == null) throw new IllegalStateException("Required artifact missing from CLI status: " + id);
            frontier.addAll(artifact.requires());
        }
        return artifacts.stream().filter(a -> required.contains(a.id())).toList();
    }

    /**
     * Writes generated artifact content to the file specified by the instruction.
     */
    public void writeArtifactResult(ArtifactInstruction instruction, String content) throws IOException {
        var writer = project.getService(com.johnnyblabs.openspec.ai.safety.SafeArtifactService.class);
        writer.apply(writer.prepare(instruction, content));
    }
}
