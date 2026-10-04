package com.johnnyblabs.openspec.ai.safety;

import com.johnnyblabs.openspec.model.ArtifactInstruction;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Generation context is confined to reviewed planning Markdown, with bounded dependencies. */
public final class ArtifactPromptBuilder {
    private ArtifactPromptBuilder() { }
    public static String build(ArtifactInstruction instruction) throws IOException {
        return buildManifest(instruction, List.of(), ContextManifest.Budget.defaults()).prompt();
    }
    public static ContextManifest buildManifest(ArtifactInstruction instruction,
            List<ContextManifest.Selection> explicitSelections, ContextManifest.Budget budget) throws IOException {
        return buildManifest(instruction, explicitSelections, budget, null);
    }
    public static ContextManifest buildManifest(ArtifactInstruction instruction,
            List<ContextManifest.Selection> explicitSelections, ContextManifest.Budget budget,
            ArtifactRequestSnapshot snapshot) throws IOException {
        if (instruction.changeDir() == null) throw new IOException("Missing planning context root");
        Path root = Path.of(instruction.changeDir()).toAbsolutePath().normalize();
        ArtifactResultValidator.checkRoot(root);
        var builder = new ContextManifest.Builder(budget);
        String header = instruction.changeName() == null ? "" : "# Change: " + instruction.changeName() + "\n\n";
        builder.inline("Artifact instructions", ContextManifest.Origin.INSTRUCTION,
                header + (instruction.instruction() == null ? "" : instruction.instruction()), true);
        if (instruction.template() != null) builder.inline("Template", ContextManifest.Origin.TEMPLATE, instruction.template(), true);
        for (var dependency : instruction.dependencies()) {
            String description = "Dependency: " + dependency.id()
                    + (dependency.description() == null ? "" : " — " + dependency.description());
            if (!dependency.done() || dependency.path() == null) {
                builder.inline("Dependency state", ContextManifest.Origin.INSTRUCTION, description + " (not yet completed)", true);
                continue;
            }
            if (ArtifactResultValidator.isGlob(dependency.path())) {
                if (!dependency.path().equals("specs/**/*.md")) throw new IOException("Unsupported dependency output glob");
                builder.inline("Dependency reference", ContextManifest.Origin.INSTRUCTION,
                        description + "\nPath: " + dependency.path() + " (path-only reference; no files automatically included)", true);
                builder.omission(dependency.path(), ContextManifest.Origin.SELECTED_CHANGE,
                        "Glob dependency contents require explicit concrete file inclusion");
                continue;
            }
            requirePlanningMarkdown(dependency.path());
            builder.inline("Dependency description", ContextManifest.Origin.INSTRUCTION, description, true);
            builder.file(new ContextManifest.Selection(root, dependency.path(), ContextManifest.Origin.SELECTED_CHANGE, true));
        }
        if (snapshot != null) {
            if (!snapshot.root().toAbsolutePath().normalize().equals(root) || !snapshot.artifactId().equals(instruction.artifactId())
                    || !java.util.Objects.equals(snapshot.outputPattern(), instruction.outputPath()))
                throw new IOException("Artifact base snapshot does not match context scope");
            for (var base : snapshot.baseContents().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
                requirePlanningMarkdown(base.getKey());
                if (!ArtifactResultValidator.matches(instruction.outputPath(), base.getKey()))
                    throw new IOException("Artifact base context is outside output scope");
                builder.inline("Existing output base " + base.getKey(), ContextManifest.Origin.SELECTED_CHANGE,
                        "Path: " + base.getKey() + "\nBase SHA-256: " + snapshot.baseHashes().get(base.getKey())
                                + "\nUTF-16 offsets apply to the following exact original text; redacted bases cannot be patched.", true);
                builder.capturedFile(base.getKey(), ContextManifest.Origin.SELECTED_CHANGE, base.getValue(), true, ContextManifest.hash(root.toString()));
            }
        }
        for (ContextManifest.Selection selection : explicitSelections) builder.file(selection);
        return builder.build();
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
