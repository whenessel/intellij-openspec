package com.johnnyblabs.openspec.ai.safety;

import com.johnnyblabs.openspec.model.ArtifactInstruction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ArtifactInstructionSafetyTest {
    @TempDir Path temp;
    private ArtifactInstruction instruction(Path root, String dependency) {
        return new ArtifactInstruction("c", "design", root.toString(), "design.md", "Design the change", "template",
                List.of(new ArtifactInstruction.Dependency("proposal", true, dependency, "proposal")), List.of());
    }
    @Test void traversalDoesNotReadOutsidePlanningRoot() throws Exception {
        Path root = Files.createDirectory(temp.resolve("change"));
        Files.writeString(temp.resolve("outside.md"), "OUTSIDE_PRIVATE_CONTENT");
        String prompt = instruction(root, "../outside.md").buildPrompt();
        assertFalse(prompt.contains("OUTSIDE_PRIVATE_CONTENT"));
        assertTrue(prompt.contains("Path: ../outside.md"));
    }
    @Test void symlinkDoesNotReadExternalContent() throws Exception {
        Path root = Files.createDirectory(temp.resolve("change"));
        Path outside = Files.writeString(temp.resolve("outside.md"), "OUTSIDE_PRIVATE_CONTENT");
        Files.createSymbolicLink(root.resolve("proposal.md"), outside);
        assertFalse(instruction(root, "proposal.md").buildPrompt().contains("OUTSIDE_PRIVATE_CONTENT"));
    }
    @Test void oversizedDependencyRemainsPathOnly() throws Exception {
        Path root = Files.createDirectory(temp.resolve("change"));
        Files.writeString(root.resolve("proposal.md"), "OVERSIZED_MARKER" + "x".repeat(32768));
        String prompt = instruction(root, "proposal.md").buildPrompt();
        assertFalse(prompt.contains("OVERSIZED_MARKER"));
        assertTrue(prompt.contains("Path: proposal.md"));
    }
    @Test void normalMarkdownIsStillIncluded() throws Exception {
        Path root = Files.createDirectory(temp.resolve("change"));
        Files.writeString(root.resolve("proposal.md"), "REVIEWED_PROPOSAL");
        assertTrue(instruction(root, "proposal.md").buildPrompt().contains("REVIEWED_PROPOSAL"));
    }
}
