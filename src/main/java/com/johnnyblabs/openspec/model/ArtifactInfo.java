package com.johnnyblabs.openspec.model;

import java.util.List;

/**
 * One artifact of a change's status DAG, as reported by {@code openspec status --json}.
 *
 * <p>{@code requires} carries the CLI's own dependency edges (the artifacts this one directly
 * depends on) — added by CLI 1.7. It is optional and additive: pre-1.7 output omits it, so it
 * normalizes to an empty list, and consumers that want authoritative dependency reasoning fall
 * back to their prior heuristics when it is empty. Gson deserializes 1.7+ status JSON through the
 * 5-arg canonical constructor; the 4-arg convenience constructor keeps the many pre-1.7 call sites
 * (and synthetic display artifacts, which carry no edges) compiling unchanged.
 */
public record ArtifactInfo(String id, String outputPath, ArtifactStatus status,
                           List<String> missingDeps, List<String> requires) {

    public ArtifactInfo {
        if (status == null) status = ArtifactStatus.UNKNOWN;
        if (missingDeps == null) missingDeps = List.of();
        if (requires == null) requires = List.of();
    }

    /** Convenience constructor for callers with no {@code requires} edges (pre-1.7 / synthetic). */
    public ArtifactInfo(String id, String outputPath, ArtifactStatus status, List<String> missingDeps) {
        this(id, outputPath, status, missingDeps, List.of());
    }
}
