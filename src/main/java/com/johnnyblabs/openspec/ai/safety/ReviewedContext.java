package com.johnnyblabs.openspec.ai.safety;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Temporary context has only the exact reviewed, redacted prompt; no project files or CLI credentials. */
public final class ReviewedContext implements AutoCloseable {
    private final String prompt;
    private final Path root;
    ReviewedContext(String prompt, Path root) { this.prompt = prompt; this.root = root; }
    public String prompt() { return prompt; }
    public Path root() { return root; }
    @Override public void close() throws IOException {
        if (!Files.exists(root)) return;
        // Do not follow links: an untrusted process cannot make cleanup delete outside this root.
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
