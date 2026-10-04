package com.johnnyblabs.openspec.ai.safety;

import java.nio.file.Path;
import java.util.Map;

/** Captured before inference so a generated replacement cannot erase an intervening edit. */
public record ArtifactRequestSnapshot(Path root, String artifactId, String outputPattern,
                                      Map<String, String> baseHashes) {
    public ArtifactRequestSnapshot { baseHashes = Map.copyOf(baseHashes); }
}
