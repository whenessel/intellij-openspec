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
    @Test void requiredInstructionTemplateAndDependencyOrderAndExplicitSourcesAreManifested() throws Exception {
        Files.writeString(root.resolve("proposal.md"), "proposal content");
        Files.writeString(root.resolve("additional.java"), "explicit source content");
        var manifest = ArtifactPromptBuilder.buildManifest(instruction("proposal.md"),
                List.of(new ContextManifest.Selection(root, "additional.java", ContextManifest.Origin.SOURCE, false)),
                ContextManifest.Budget.defaults());
        String prompt = manifest.prompt();
        assertTrue(prompt.indexOf("Write a design") < prompt.indexOf("# Design"));
        assertTrue(prompt.indexOf("# Design") < prompt.indexOf("proposal content"));
        assertTrue(prompt.indexOf("proposal content") < prompt.indexOf("explicit source content"));
        assertTrue(manifest.entries().stream().anyMatch(entry -> entry.origin() == ContextManifest.Origin.SOURCE && entry.included()));
        assertFalse(ArtifactPromptBuilder.build(instruction("proposal.md")).contains("explicit source content"));
    }
    @Test void capturedOutputBasesAreIncludedBoundedAndScopeBoundBeforePatchInference() throws Exception {
        String original = "# Existing design\n";
        var snapshot = new ArtifactRequestSnapshot(root, "design", "design.md",
                java.util.Map.of("design.md", ArtifactResultValidator.hash(original)), java.util.Map.of("design.md", original));
        var noDependencies = new ArtifactInstruction("example", "design", root.toString(), "design.md", "Write a design", "# Template", List.of(), List.of());
        var manifest = ArtifactPromptBuilder.buildManifest(noDependencies, List.of(), ContextManifest.Budget.defaults(), snapshot);
        assertTrue(manifest.prompt().contains(original));
        assertTrue(manifest.prompt().contains(snapshot.baseHashes().get("design.md")));
        assertTrue(manifest.prompt().contains("UTF-16 offsets"));
        var tightCount = new ContextManifest.Budget(1, 32768, 49152, 12000, null, 4096, 1024);
        Files.writeString(root.resolve("proposal.md"), "required dependency");
        assertThrows(IOException.class, () -> ArtifactPromptBuilder.buildManifest(instruction("proposal.md"), List.of(), tightCount, snapshot));
        var wrong = new ArtifactRequestSnapshot(root, "proposal", "proposal.md", java.util.Map.of());
        assertThrows(IOException.class, () -> ArtifactPromptBuilder.buildManifest(noDependencies, List.of(), ContextManifest.Budget.defaults(), wrong));
    }
}
