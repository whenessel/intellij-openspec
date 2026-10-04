package com.johnnyblabs.openspec.ai.safety;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Fixtures represent the plugin-owned v1 result contract; no external/paid model call is needed. */
class ArtifactPatchResultTest {
    @TempDir Path root;
    private static JsonObject patchFile(String relative, String base, int start, int end, String oldText, String newText) {
        JsonObject file = new JsonObject();
        file.addProperty("relativePath", relative);
        file.addProperty("operation", "patch");
        file.addProperty("baseHash", ArtifactResultValidator.hash(base));
        file.add("patch", ArtifactPatchCodec.encode(new ArtifactPatch(1, List.of(new ArtifactPatch.TextEdit(start, end, oldText, newText)))));
        return file;
    }
    private static String envelope(String id, JsonObject... operations) {
        JsonObject result = new JsonObject();
        result.addProperty("schemaVersion", 1);
        result.addProperty("artifactId", id);
        JsonArray files = new JsonArray();
        for (JsonObject operation : operations) files.add(operation);
        result.add("files", files);
        return result.toString();
    }
    private void existing(String relative, String content) throws IOException {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
    @Test void twoDomainPatchResultsResolveConcreteFilesWithoutWriting() throws Exception {
        existing("specs/one/spec.md", "# Old one\n");
        existing("specs/two/spec.md", "# Old two\n");
        var batch = ArtifactResultValidator.validate(root, "specs", "specs/**/*.md", envelope("specs",
                patchFile("specs/one/spec.md", "# Old one\n", 2, 5, "Old", "New"),
                patchFile("specs/two/spec.md", "# Old two\n", 2, 5, "Old", "New")));
        assertEquals(List.of("specs/one/spec.md", "specs/two/spec.md"), batch.files().stream().map(ReviewedArtifactBatch.FileEdit::relativePath).toList());
        assertEquals(List.of("# New one\n", "# New two\n"), batch.files().stream().map(ReviewedArtifactBatch.FileEdit::content).toList());
        assertTrue(batch.files().stream().allMatch(file -> file.operation().equals("patch") && file.patch() != null));
        assertEquals("# Old one\n", Files.readString(root.resolve("specs/one/spec.md")));
        assertEquals("# Old two\n", Files.readString(root.resolve("specs/two/spec.md")));
        try (var paths = Files.walk(root)) {
            assertTrue(paths.noneMatch(path -> path.getFileName().toString().contains("*")));
        }
    }
    @Test void crLfPatchBindsRawDiskBaseAndCanonicalLogicalProjection() throws Exception {
        String base = "# Old\r\n";
        existing("proposal.md", base);
        String response = envelope("proposal", patchFile("proposal.md", base, 2, 5, "Old", "New"));
        var batch = ArtifactResultValidator.validate(root, "proposal", "proposal.md", response);
        assertEquals("# New\n", batch.files().getFirst().content());
        assertEquals(base, batch.files().getFirst().original());
        assertEquals(ArtifactResultValidator.hash(base), batch.files().getFirst().baseHash());
        Files.writeString(root.resolve("proposal.md"), "# Old\n");
        assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "proposal", "proposal.md", response));
    }
    @Test void patchWithMixedProjectionOrUnsupportedDiskSeparatorsIsRejected() throws Exception {
        existing("proposal.md", "first\r\nsecond\r\n");
        assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "proposal", "proposal.md", envelope("proposal",
                patchFile("proposal.md", "first\r\nsecond\r\n", 0, 0, "", "added\n"))));
        existing("proposal.md", "first\rsecond");
        assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "proposal", "proposal.md", envelope("proposal",
                patchFile("proposal.md", "first\rsecond", 0, 5, "first", "new"))));
    }
    @Test void patchesMixWithCreatesAndReplacementsInOneReviewedBatch() throws Exception {
        existing("specs/patch/spec.md", "old");
        existing("specs/replace/spec.md", "original");
        var create = new JsonObject();
        create.addProperty("relativePath", "specs/create/spec.md"); create.addProperty("operation", "create"); create.addProperty("content", "created");
        var replace = create.deepCopy(); replace.addProperty("relativePath", "specs/replace/spec.md"); replace.addProperty("operation", "replace"); replace.addProperty("content", "replacement");
        var batch = ArtifactResultValidator.validate(root, "specs", "specs/**/*.md", envelope("specs", patchFile("specs/patch/spec.md", "old", 0, 3, "old", "patched"), create, replace));
        assertEquals(List.of("patched", "created", "replacement"), batch.files().stream().map(ReviewedArtifactBatch.FileEdit::content).toList());
    }
    @Test void missingMalformedAndStaleBaseHashesRejectEntireBatch() throws Exception {
        existing("proposal.md", "base");
        for (String hash : List.of("missing", "not-a-hash", ArtifactResultValidator.hash("stale"))) {
            JsonObject operation = patchFile("proposal.md", "base", 0, 4, "base", "new");
            if (hash.equals("missing")) operation.remove("baseHash"); else operation.addProperty("baseHash", hash);
            assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "proposal", "proposal.md", envelope("proposal", operation)));
        }
        assertEquals("base", Files.readString(root.resolve("proposal.md")));
    }
    @Test void patchRequiresExistingConcreteArtifactAndCannotDeleteFile() throws Exception {
        assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "proposal", "proposal.md", envelope("proposal", patchFile("proposal.md", "base", 0, 4, "base", "new"))));
        existing("proposal.md", "base");
        JsonObject deletion = patchFile("proposal.md", "base", 0, 4, "base", "new"); deletion.addProperty("operation", "delete");
        assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "proposal", "proposal.md", envelope("proposal", deletion)));
        assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "specs", "specs/**/*.md", envelope("specs", patchFile("specs/**/*.md", "base", 0, 4, "base", "new"))));
        assertEquals("base", Files.readString(root.resolve("proposal.md")));
    }
    @Test void malformedPatchAndAmbiguousContentAreRejected() throws Exception {
        existing("proposal.md", "base");
        JsonObject valid = patchFile("proposal.md", "base", 0, 4, "base", "new");
        JsonObject content = valid.deepCopy(); content.addProperty("content", "other");
        JsonObject unknown = valid.deepCopy(); unknown.getAsJsonObject("patch").addProperty("fuzzy", true);
        JsonObject invalid = valid.deepCopy(); invalid.getAsJsonObject("patch").getAsJsonArray("edits").get(0).getAsJsonObject().addProperty("oldText", "wrong");
        JsonObject primitive = valid.deepCopy(); primitive.addProperty("patch", "diff");
        for (JsonObject operation : List.of(content, unknown, invalid, primitive)) {
            assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "proposal", "proposal.md", envelope("proposal", operation)));
        }
    }
    @Test void oneInvalidOperationRejectsValidSiblingBeforeAnyWrite() throws Exception {
        existing("specs/one/spec.md", "base one"); existing("specs/two/spec.md", "base two");
        assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "specs", "specs/**/*.md", envelope("specs",
                patchFile("specs/one/spec.md", "base one", 0, 4, "base", "new"), patchFile("specs/two/spec.md", "base two", 0, 4, "wrong", "new"))));
        assertEquals("base one", Files.readString(root.resolve("specs/one/spec.md")));
        assertEquals("base two", Files.readString(root.resolve("specs/two/spec.md")));
    }
    @Test void strictJsonRejectsDuplicatesCommentsTrailingDataAndDeepNesting() throws Exception {
        existing("proposal.md", "base");
        String valid = envelope("proposal", patchFile("proposal.md", "base", 0, 4, "base", "new"));
        for (String invalid : List.of(valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2,\"schemaVersion\":1"),
                valid.replace("\"operation\":\"patch\"", "\"operation\":\"create\",\"operation\":\"patch\""),
                valid.replace("\"oldText\":\"base\"", "\"oldText\":\"evil\",\"oldText\":\"base\""), "{/* comment */" + valid.substring(1), valid + "{}",
                "{\"files\":" + "[".repeat(20) + "0" + "]".repeat(20) + "}")) {
            assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "proposal", "proposal.md", invalid));
        }
    }
    @Test void redactedBaseCannotBePatchedEvenIfEditOnlyTouchesAnUnredactedPrefix() throws Exception {
        String base = "# Old heading\napi_key=\"fixture-key-for-redaction\"\n";
        existing("proposal.md", base);
        assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "proposal", "proposal.md", envelope("proposal",
                patchFile("proposal.md", base, 2, 5, "Old", "New"))));
        assertEquals(base, Files.readString(root.resolve("proposal.md")));
    }
    @Test void unadvertisedLegacyAndPatchFieldsOnCreateAreRejected() throws Exception {
        JsonObject create = new JsonObject(); create.addProperty("relativePath", "proposal.md");
        create.addProperty("operation", "legacy"); create.addProperty("content", "body");
        assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "proposal", "proposal.md", envelope("proposal", create)));
        create.addProperty("operation", "create"); create.add("patch", ArtifactPatchCodec.encode(new ArtifactPatch(1, List.of(new ArtifactPatch.TextEdit(0, 0, "", "insert")))));
        assertThrows(IOException.class, () -> ArtifactResultValidator.validate(root, "proposal", "proposal.md", envelope("proposal", create)));
    }
    @Test void snapshotsPreserveImmutableBaseContentAndRejectMismatchingHash() {
        var contents = new java.util.HashMap<String, String>(); contents.put("proposal.md", "base");
        var snapshot = new ArtifactRequestSnapshot(root, "proposal", "proposal.md", Map.of("proposal.md", ArtifactResultValidator.hash("base")), contents);
        contents.put("proposal.md", "changed");
        assertEquals("base", snapshot.baseContents().get("proposal.md"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.baseContents().put("other.md", "x"));
        assertThrows(IllegalArgumentException.class, () -> new ArtifactRequestSnapshot(root, "proposal", "proposal.md", Map.of("proposal.md", "wrong"), contents));
    }
}
