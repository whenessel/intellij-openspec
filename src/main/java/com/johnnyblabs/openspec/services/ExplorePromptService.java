package com.johnnyblabs.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Builds the full explore prompt by combining skill instructions, project context, and topic.
 * Reads the explore skill file from the project when available, falling back to a built-in default.
 */
@Service(Service.Level.PROJECT)
public final class ExplorePromptService {

    /**
     * Skill file paths to search, in priority order.
     */
    private static final String[] SKILL_FILE_PATHS = {
            // Skills-era location first — the CLI's tracked skill surface since its 1.5.0
            // skills-only migration (1.6 stamps allowed-tools/generatedBy frontmatter,
            // which the frontmatter stripping below removes).
            ".claude/skills/openspec-explore/SKILL.md",
            // Legacy pre-1.5 command paths, kept for still-supported 1.3/1.4 projects.
            ".claude/commands/opsx/explore.md",
            ".augment/commands/opsx-explore.md",
            ".github/prompts/opsx-explore.prompt.md"
    };

    static final String DEFAULT_EXPLORE_PROMPT = """
            Enter explore mode. Think deeply. Visualize freely. Follow the conversation wherever it goes.

            **IMPORTANT: Explore mode is for thinking, not implementing.** You may read files, search code, \
            and investigate the codebase, but you must NEVER write code or implement features.

            **This is a stance, not a workflow.** There are no fixed steps, no required sequence, no mandatory outputs. \
            You're a thinking partner helping the user explore.

            ## The Stance

            - **Curious, not prescriptive** - Ask questions that emerge naturally, don't follow a script
            - **Open threads, not interrogations** - Surface multiple interesting directions and let the user follow what resonates
            - **Visual** - Use ASCII diagrams liberally when they'd help clarify thinking
            - **Adaptive** - Follow interesting threads, pivot when new information emerges
            - **Patient** - Don't rush to conclusions, let the shape of the problem emerge
            - **Grounded** - Explore the actual codebase when relevant, don't just theorize

            ## What You Might Do

            - Explore the problem space: ask clarifying questions, challenge assumptions, reframe the problem
            - Investigate the codebase: map architecture, find integration points, surface hidden complexity
            - Compare options: brainstorm approaches, build comparison tables, sketch tradeoffs
            - Visualize: use ASCII diagrams for system diagrams, state machines, data flows
            - Surface risks and unknowns: identify what could go wrong, find gaps in understanding

            ## Guardrails

            - Don't implement - never write code or implement features
            - Don't fake understanding - if something is unclear, dig deeper
            - Don't rush - discovery is thinking time, not task time
            - Don't force structure - let patterns emerge naturally
            - Do visualize - a good diagram is worth many paragraphs
            - Do explore the codebase - ground discussions in reality
            - Do question assumptions - including the user's and your own
            """;

    private final Project project;

    public ExplorePromptService(Project project) {
        this.project = project;
    }

    /**
     * Builds the full explore prompt: skill instructions + project context + topic.
     *
     * @param topic the user's explore topic, or empty/null for open exploration
     * @return the assembled prompt string
     */
    public String buildPrompt(String topic) { return buildRequest(topic).prompt(); }

    /** One context read per turn; topic changes preserve the conversation scope. */
    public ExploreRequest buildRequest(String topic) {
        String prefix = loadSkillInstructions() + "\n\n---\n\n" + assembleContext();
        String scope;
        try {
            scope = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(prefix.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        return new ExploreRequest(prefix + "\n\n---\n\n" + buildTopicSection(topic), scope);
    }

    public record ExploreRequest(String prompt, String contextScope) {}

    static final int MAX_SKILL_BYTES = 32768;
    static final String SKILL_OMISSION = "\n\n**Context omission:** An unsafe, unreadable or oversized project Explore skill was omitted; safe instructions are used.";

    /** Rejects symlink ancestors and bounds the read even if the file grows after its size check. */
    String loadSkillInstructions() {
        String basePath = project.getBasePath();
        boolean omitted = false;
        if (basePath != null) {
            Path root = Path.of(basePath).toAbsolutePath().normalize();
            for (String skillPath : SKILL_FILE_PATHS) {
                Path path = root.resolve(skillPath).normalize();
                if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) continue;
                try {
                    requireSafeSkill(root, path);
                    if (Files.size(path) > MAX_SKILL_BYTES) throw new IOException("Skill exceeds limit");
                    byte[] bytes;
                    try (var input = Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                        bytes = input.readNBytes(MAX_SKILL_BYTES + 1);
                    }
                    if (bytes.length > MAX_SKILL_BYTES) throw new IOException("Skill exceeds limit");
                    requireSafeSkill(root, path);
                    String content = new String(bytes, StandardCharsets.UTF_8);
                    if (content.startsWith("---")) {
                        int end = content.indexOf("---", 3);
                        if (end != -1) content = content.substring(end + 3).strip();
                    }
                    return content + (omitted ? SKILL_OMISSION : "");
                } catch (IOException | SecurityException ex) { omitted = true; }
            }
        }
        return DEFAULT_EXPLORE_PROMPT + (omitted ? SKILL_OMISSION : "");
    }

    private static void requireSafeSkill(Path root, Path path) throws IOException {
        if (!path.startsWith(root) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Unsafe skill file");
        for (Path ancestor = path; ancestor != null; ancestor = ancestor.getParent()) {
            if (Files.isSymbolicLink(ancestor)) throw new IOException("Symlinked skill path");
        }
    }

    private String assembleContext() {
        ExploreContextService contextService = project.getService(ExploreContextService.class);
        if (contextService != null) {
            return contextService.assembleContext();
        }
        return "# Project Context\n\nNo project context available.";
    }

    private static String buildTopicSection(String topic) {
        if (topic == null || topic.isBlank()) {
            return "**Topic:** Open exploration — what would you like to think about?";
        }
        return "**Topic:** " + topic.strip();
    }
}
