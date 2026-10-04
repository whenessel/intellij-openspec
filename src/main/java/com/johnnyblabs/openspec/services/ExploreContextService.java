package com.johnnyblabs.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.model.OpenSpecConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import com.johnnyblabs.openspec.ai.safety.ContextManifest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Assembles an immutable reviewed manifest from config, the explicit selected change,
 * and explicit additional files. Shared by both {@code ExplorePanel} and
 * {@code ExploreContextAction}.
 */
@Service(Service.Level.PROJECT)
public final class ExploreContextService {
    private static final String[] CHANGE_ARTIFACTS = {"proposal.md", "design.md", "tasks.md"};

    private final Project project;
    private volatile String selectedChange;

    public void setSelectedChange(String changeName) { selectedChange = changeName; }

    public ExploreContextService(Project project) {
        this.project = project;
    }

    /**
     * Assembles selected context with visible exclusions and conservative budget disclosure.
     */
    public String assembleContext() {
        com.johnnyblabs.openspec.settings.OpenSpecSettings settings = com.johnnyblabs.openspec.settings.OpenSpecSettings.getInstance(project);
        ContextManifest.Budget budget = settings == null ? ContextManifest.Budget.defaults()
                : new ContextManifest.Budget(64, 32768,
                        Math.min(settings.getAiContextMaxBytes(), com.johnnyblabs.openspec.ai.safety.ContextPayloadPolicy.MAX_PROMPT_BYTES),
                        settings.getAiContextMaxInputTokens(), null, 4096, 1024);
        try { return assembleManifest(List.of(), budget).prompt(); }
        catch (IOException ex) { throw new UncheckedIOException("Explore context cannot fit safely; reduce selected scope", ex); }
    }

    public ContextManifest assembleManifest(List<ContextManifest.Selection> explicitSelections,
                                            ContextManifest.Budget budget) throws IOException {
        var builder = new ContextManifest.Builder(budget);
        appendContext(builder);
        for (ContextManifest.Selection selection : explicitSelections) builder.file(selection);
        return builder.build();
    }

    /** Adds to the same request builder so skill/topic/context share one admission budget. */
    public void appendContext(ContextManifest.Builder builder) throws IOException {
        StringBuilder context = new StringBuilder("# OpenSpec Explore Context\n\n");
        appendConfigSummary(context);
        appendDetectedTools(context);
        context.append("## Active Changes\n");
        ChangeService changeService = project.getService(ChangeService.class);
        List<Change> changes = changeService == null ? List.of() : changeService.getActiveChanges();
        String chosen = selectedChange;
        Change selected = changes.stream().filter(change -> change.getName().equals(chosen)).findFirst().orElse(null);
        if (changes.isEmpty()) context.append("No active changes.\n");
        else if (selected == null) context.append("No change selected; active change contents are excluded. Select a change explicitly.\n");
        else context.append("\n### ").append(selected.getName())
                .append(selected.getMetadata() != null && selected.getMetadata().getSchema() != null
                        ? " (" + selected.getMetadata().getSchema() + ")" : "").append("\n");
        context.append("\n## Specs\nProject-wide spec contents require explicit file inclusion.\n");
        builder.inline("Explore project instructions", ContextManifest.Origin.INSTRUCTION, context.toString(), true);
        if (!changes.isEmpty()) builder.omission("Other active changes", ContextManifest.Origin.ADDITIONAL_CHANGE,
                "Unselected changes excluded unless concrete files are explicitly included");
        if (selected != null) appendChangeArtifacts(selected, builder);
    }

    private void appendConfigSummary(StringBuilder context) {
        ConfigService configService = project.getService(ConfigService.class);
        if (configService == null) return;

        OpenSpecConfig config = configService.getConfig();
        if (config != null) {
            context.append("## Project Config\n");
            // `version:` is intentionally NOT surfaced here: it's a plugin-internal field upstream
            // strips and never reads, so emitting it into the AI-prompt context presents an off-model
            // field as real project config. The getVersion() reader stays for the config-format-axis
            // fallback (OpenSpecSettings.getEffectiveVersion), just not surfaced.
            if (config.getSchema() != null) {
                context.append("- Schema: ").append(config.getSchema()).append("\n");
            }
            if (!config.getContext().isEmpty()) {
                context.append("\n> ").append(config.getContext().replace("\n", "\n> ")).append("\n");
            }
            if (!config.getRules().isEmpty()) {
                context.append("\n**Rules:**\n");
                config.getRules().forEach((name, rule) ->
                        context.append("- **").append(name).append("**: ").append(rule).append("\n"));
            }
            context.append("\n");
        }
    }

    private void appendDetectedTools(StringBuilder context) {
        AiToolDetectionService detection = project.getService(AiToolDetectionService.class);
        if (detection == null) return;

        detection.detect();
        List<String> tools = detection.getDetectedTools();
        context.append("## Detected AI Tools\n");
        if (tools.isEmpty()) {
            context.append("None detected.\n");
        } else {
            for (String tool : tools) {
                AiToolDetectionService.ToolType type = AiToolDetectionService.getToolType(tool);
                context.append("- ").append(tool).append(" [").append(type).append("]\n");
            }
        }
        context.append("\n");
    }

    private void appendChangeArtifacts(Change change, ContextManifest.Builder builder) throws IOException {
        if (change.getPath() == null) {
            builder.omission("Selected change", ContextManifest.Origin.SELECTED_CHANGE, "Planning root unavailable");
            return;
        }
        Path root = Path.of(change.getPath()).toAbsolutePath().normalize();
        for (String artifact : CHANGE_ARTIFACTS) {
            builder.file(new ContextManifest.Selection(root, artifact, ContextManifest.Origin.SELECTED_CHANGE, false));
        }
        Path specs = root.resolve("specs");
        if (Files.isSymbolicLink(specs)) {
            builder.omission("specs", ContextManifest.Origin.SELECTED_CHANGE, "Symlink context omitted");
            return;
        }
        if (!Files.isDirectory(specs, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        // Immediate domains only; never traverse arbitrary workspace directories or symlinks.
        try (var domains = Files.list(specs)) {
            List<Path> paths = domains.limit(65).sorted().toList();
            for (Path domain : paths.stream().limit(64).toList()) {
                String relative = "specs/" + domain.getFileName() + "/spec.md";
                builder.file(new ContextManifest.Selection(root, relative, ContextManifest.Origin.SELECTED_CHANGE, false));
            }
            if (paths.size() > 64) builder.omission("Additional delta spec domains", ContextManifest.Origin.SELECTED_CHANGE,
                    "Traversal count bound; select concrete files explicitly");
        } catch (IOException | SecurityException ex) {
            builder.omission("Delta specs", ContextManifest.Origin.SELECTED_CHANGE, "Unsafe or unreadable delta specs omitted");
        }
    }
}
