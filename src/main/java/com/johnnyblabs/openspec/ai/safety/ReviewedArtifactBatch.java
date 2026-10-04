package com.johnnyblabs.openspec.ai.safety;

import java.nio.file.Path;
import java.util.List;

/** Immutable proposed contents and disk bases; acceptance never authorizes a different batch. */
public record ReviewedArtifactBatch(Path root, String artifactId, String outputPattern, List<FileEdit> files) {
    public ReviewedArtifactBatch { files = List.copyOf(files); }
    public record FileEdit(String relativePath, String operation, String content, String original, String baseHash) { }
}
