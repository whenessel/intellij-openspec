package com.johnnyblabs.openspec.ai.safety;

import com.johnnyblabs.openspec.model.ArtifactInstruction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Generation context is confined to reviewed planning Markdown, with bounded dependencies. */
public final class ArtifactPromptBuilder {
    private ArtifactPromptBuilder() { }
    public static String build(ArtifactInstruction instruction) throws IOException {
        if (instruction.changeDir() == null) throw new IOException("Missing planning context root");
        Path root = Path.of(instruction.changeDir()).toAbsolutePath().normalize();
        ArtifactResultValidator.checkRoot(root);
        StringBuilder prompt = new StringBuilder();
        if (instruction.changeName() != null) prompt.append("# Change: ").append(instruction.changeName()).append("\n\n");
        if (instruction.instruction() != null) prompt.append(instruction.instruction());
        if (instruction.template() != null) prompt.append("\n\nTemplate:\n").append(instruction.template());
        int count = 0;
        for (var dependency : instruction.dependencies()) {
            prompt.append("\n\nDependency: ").append(dependency.id());
            if (dependency.description() != null) prompt.append(" — ").append(dependency.description());
            if (!dependency.done() || dependency.path() == null) {
                prompt.append(" (not yet completed)\n");
                continue;
            }
            if (ArtifactResultValidator.isGlob(dependency.path())) {
                if (!dependency.path().equals("specs/**/*.md")) throw new IOException("Unsupported dependency output glob");
                prompt.append("\nPath: ").append(dependency.path()).append(" (path-only reference; no files automatically included)\n");
                continue;
            }
            List<Path> files = dependencyFiles(root, dependency.path());
            for (Path file : files) {
                if (++count > ArtifactResultValidator.MAX_FILES) throw new IOException("Too many dependency files; reduce context scope");
                String relative = root.relativize(file).toString().replace('\\', '/');
                requirePlanningMarkdown(relative);
                ArtifactResultValidator.checkedTarget(root, relative);
                if (Files.size(file) > ArtifactResultValidator.MAX_FILE_BYTES) throw new IOException("Dependency exceeds per-file context budget: " + relative);
                prompt.append("\n### ").append(relative).append("\n").append(Files.readString(file, StandardCharsets.UTF_8));
                ContextPayloadPolicy.redactAndBound(prompt.toString());
            }
        }
        return ContextPayloadPolicy.redactAndBound(prompt.toString());
    }
    private static List<Path> dependencyFiles(Path root, String path) throws IOException {
        requirePlanningMarkdown(path);
        Path file = ArtifactResultValidator.checkedTarget(root, path);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Completed dependency is missing: " + path);
        return List.of(file);
    }
    public static void requirePlanningMarkdown(String relative) throws IOException {
        ArtifactResultValidator.checkRelativePath(relative);
        String lower = relative.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".md") || lower.contains("credential") || lower.contains("secret")
                || lower.contains("auth.json") || lower.contains("private-key") || lower.contains("password")) {
            throw new IOException("Only non-secret planning Markdown dependencies may be included");
        }
    }
}
