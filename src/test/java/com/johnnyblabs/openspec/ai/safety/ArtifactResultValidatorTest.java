package com.johnnyblabs.openspec.ai.safety;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic adversarial examples exercise the plugin-owned artifact envelope, not CLI output. */
class ArtifactResultValidatorTest {
    @TempDir Path root;
    static String envelope(String artifact, String... paths) {
        JsonObject result = new JsonObject();
        result.addProperty("schemaVersion", 1);
        result.addProperty("artifactId", artifact);
        JsonArray files = new JsonArray();
        for (String path : paths) {
            JsonObject file = new JsonObject();
            file.addProperty("relativePath", path);
            file.addProperty("operation", "create");
            file.addProperty("content", "# Reviewed spec\n");
            files.add(file);
        }
        result.add("files", files);
        return result.toString();
    }
    @Test void concreteLegacyWrapsOneDestinationWithoutWriting() throws Exception {
        var batch = ArtifactResultValidator.validate(root, "proposal", "proposal.md", "# Proposal\n");
        assertEquals("proposal.md", batch.files().getFirst().relativePath());
        assertNull(batch.files().getFirst().baseHash());
        assertFalse(Files.exists(root.resolve("proposal.md")));
    }
    @Test void recursiveSpecsEnvelopePreservesSeparateFiles() throws Exception {
        var batch = ArtifactResultValidator.validate(root, "specs", "specs/**/*.md", envelope("specs", "specs/one/spec.md", "specs/two/spec.md"));
        assertEquals(2, batch.files().size());
        assertEquals("specs/two/spec.md", batch.files().getLast().relativePath());
    }
    @Test void allUntrustedDestinationsFailBeforeAnyWrite() throws Exception {
        for (String path : List.of("../outside.md", "/tmp/evil.md", "C:/evil.md", "specs/../../evil.md", "specs\\evil.md",
                "specs/evil:stream.md", "specs/**/*.md", "specs/.env.md", "specs/NUL.md", "specs/a./spec.md", "specs//spec.md", "tasks.md")) {
            assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "specs", "specs/**/*.md", envelope("specs", path)), path);
        }
        try (var files = Files.list(root)) { assertEquals(0, files.count()); }
    }
    @Test void globDoesNotGuessFilesFromPlainTextOrMarkdownFences() {
        assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "specs", "specs/**/*.md", "# specs/one/spec.md\nbody"));
        assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "specs", "specs/**/*.md", "```json\n" + envelope("specs", "specs/one/spec.md") + "\n```"));
    }
    @Test void malformedAndWrongVersionAndIdAreRejected() {
        assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "specs", "specs/**/*.md", "{broken"));
        assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "specs", "specs/**/*.md", envelope("proposal", "specs/one/spec.md")));
        assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "specs", "specs/**/*.md", envelope("specs", "specs/one/spec.md").replace("\"schemaVersion\":1", "\"schemaVersion\":2")));
    }
    @Test void unknownEnvelopeAndOperationFieldsAreRejected() {
        String valid = envelope("specs", "specs/one/spec.md");
        assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "specs", "specs/**/*.md", valid.replace("\"schemaVersion\":", "\"runCommand\":\"touch bad\",\"schemaVersion\":")));
        assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "specs", "specs/**/*.md", valid.replace("\"relativePath\":", "\"delete\":true,\"relativePath\":")));
    }
    @Test void duplicateAndParentCaseCollisionsAreRejected() {
        for (String[] paths : List.of(new String[]{"specs/a.md", "specs/a.md"}, new String[]{"specs/a.md", "specs/A.md"},
                new String[]{"specs/Foo/a.md", "specs/foo/b.md"})) {
            assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "specs", "specs/**/*.md", envelope("specs", paths)));
        }
    }
    @Test void existingCaseCollisionRejectedOnCaseSensitiveDisk() throws Exception {
        Files.createDirectories(root.resolve("specs/Foo"));
        assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "specs", "specs/**/*.md", envelope("specs", "specs/foo/spec.md")));
    }
    @Test void symlinkDestinationAndSymlinkParentAreRejected() throws Exception {
        Path outside = Files.createTempDirectory("openspec-validator-outside-");
        try {
            Files.createDirectory(root.resolve("specs"));
            try { Files.createSymbolicLink(root.resolve("specs/link"), outside); }
            catch (UnsupportedOperationException | IOException ex) { org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symlinks unavailable"); }
            assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "specs", "specs/**/*.md", envelope("specs", "specs/link/spec.md")));
            try (var files = Files.list(outside)) { assertEquals(0, files.count()); }
        } finally { Files.deleteIfExists(root.resolve("specs/link")); Files.deleteIfExists(outside); }
    }
    @Test void replaceRequiresExistingRegularFileAndMatchingBase() throws Exception {
        Files.writeString(root.resolve("proposal.md"), "original");
        String response = envelope("proposal", "proposal.md").replace("\"create\"", "\"replace\"");
        var batch = ArtifactResultValidator.validate(root, "proposal", "proposal.md", response);
        assertEquals(ArtifactResultValidator.hash("original"), batch.files().getFirst().baseHash());
        assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "proposal", "proposal.md", response.replace("\"content\":", "\"baseHash\":\"invalid\",\"content\":")));
        assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "proposal", "proposal.md", envelope("proposal", "proposal.md")));
    }
    @Test void boundedOutputsAndInvalidUnicodeAreRejected() {
        assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "proposal", "proposal.md", "a".repeat(ArtifactResultValidator.MAX_FILE_BYTES + 1)));
        assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "proposal", "proposal.md", "broken\ud800"));
        String[] paths = new String[65];
        for (int i = 0; i < paths.length; i++) paths[i] = "specs/" + i + ".md";
        assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "specs", "specs/**/*.md", envelope("specs", paths)));
    }
}
