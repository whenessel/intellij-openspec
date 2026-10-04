package com.johnnyblabs.openspec.ai.safety;

import java.nio.file.Path;
import java.util.Map;

/** Captured before inference so a generated replacement cannot erase an intervening edit. */
public record ArtifactRequestSnapshot(Path root, String artifactId, String outputPattern,
                                      Map<String, String> baseHashes, Map<String, String> baseContents) {
    public ArtifactRequestSnapshot {
        baseHashes = Map.copyOf(baseHashes);
        baseContents = Map.copyOf(baseContents);
        for (var base : baseContents.entrySet()) {
            if (!ArtifactResultValidator.hash(base.getValue()).equals(baseHashes.get(base.getKey()))) {
                throw new IllegalArgumentException("Artifact base content/hash mismatch");
            }
        }
    }
    public ArtifactRequestSnapshot(Path root, String artifactId, String outputPattern, Map<String, String> baseHashes) {
        this(root, artifactId, outputPattern, baseHashes, Map.of());
    }
}
