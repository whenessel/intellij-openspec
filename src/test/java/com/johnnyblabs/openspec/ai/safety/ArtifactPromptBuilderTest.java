package com.johnnyblabs.openspec.ai.safety;

import com.johnnyblabs.openspec.model.ArtifactInstruction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArtifactPromptBuilderTest {
    @TempDir Path root;
    private ArtifactInstruction instruction(String dependency) {
        return new ArtifactInstruction("example", "design", root.toString(), "design.md", "Write a design", "# Design",
                List.of(new ArtifactInstruction.Dependency("proposal", true, dependency, "Needed context")), List.of());
    }
    @Test void planningDependenciesAreCapturedAndRedacted() throws Exception {
        Files.writeString(root.resolve("proposal.md"), "# Proposal\napi_key=sk-abcdefghijklmnop\n");
        String prompt = ArtifactPromptBuilder.build(instruction("proposal.md"));
        assertTrue(prompt.contains("# Proposal"));
        assertTrue(prompt.contains("[REDACTED]"));
        assertFalse(prompt.contains("abcdefghijklmnop"));
    }
    @Test void secretAndEscapingDependenciesNeverBecomeContext() {
        for (String path : List.of("../auth.json", "../../outside.md", ".codex/auth.json", "secrets.md", "credentials.md", "config.yaml")) {
            assertThrows(IOException.class, () -> ArtifactPromptBuilder.build(instruction(path)), path);
        }
    }
    @Test void recursiveSpecsRemainsVisiblePathOnlyReference() throws Exception {
        Files.createDirectories(root.resolve("specs/a"));
        Files.createDirectories(root.resolve("specs/b"));
        Files.writeString(root.resolve("specs/a/spec.md"), "first capability");
        Files.writeString(root.resolve("specs/b/spec.md"), "second capability");
        String prompt = ArtifactPromptBuilder.build(instruction("specs/**/*.md"));
        assertTrue(prompt.contains("specs/**/*.md"));
        assertTrue(prompt.contains("path-only reference"));
        assertFalse(prompt.contains("first capability"));
        assertFalse(prompt.contains("second capability"));
    }
    @Test void requiredContentOverflowFailsRatherThanTruncates() throws Exception {
        Files.writeString(root.resolve("proposal.md"), "a".repeat(32 * 1024 + 1));
        assertThrows(IOException.class, () -> ArtifactPromptBuilder.build(instruction("proposal.md")));
    }
}
