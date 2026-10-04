package com.johnnyblabs.openspec.ai.safety;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArtifactPatchCodecTest {
    private static ArtifactPatch patch(ArtifactPatch.TextEdit... edits) { return new ArtifactPatch(1, List.of(edits)); }
    @Test void exactOrderedRangesKeepUntouchedContentAndCrLf() throws Exception {
        String base = "# Heading\r\nold one\r\nold two\r\n";
        var value = patch(new ArtifactPatch.TextEdit(11, 18, "old one", "new one"), new ArtifactPatch.TextEdit(20, 27, "old two", "new two"));
        assertEquals("# Heading\r\nnew one\r\nnew two\r\n", ArtifactPatchCodec.apply(base, value));
        assertEquals(value, ArtifactPatchCodec.parse(ArtifactPatchCodec.encode(value)));
    }
    @Test void insertionAndTextDeletionNeverDeleteTheArtifactFile() throws Exception {
        assertEquals("start body", ArtifactPatchCodec.apply("body", patch(new ArtifactPatch.TextEdit(0, 0, "", "start "))));
        assertEquals("", ArtifactPatchCodec.apply("body", patch(new ArtifactPatch.TextEdit(0, 4, "body", ""))));
    }
    @Test void wrongOldTextAndNoOpNeverFuzzilyMatch() {
        assertThrows(IOException.class, () -> ArtifactPatchCodec.apply("a repeated repeated", patch(new ArtifactPatch.TextEdit(0, 8, "repeated", "new"))));
        assertThrows(IOException.class, () -> ArtifactPatchCodec.apply("base", patch(new ArtifactPatch.TextEdit(0, 4, "base", "base"))));
    }
    @Test void overlappingOutOfOrderDuplicateAndOutOfBoundsRangesRejected() {
        var invalid = List.of(
                patch(new ArtifactPatch.TextEdit(0, 3, "abc", "x"), new ArtifactPatch.TextEdit(2, 4, "cd", "y")),
                patch(new ArtifactPatch.TextEdit(3, 4, "d", "x"), new ArtifactPatch.TextEdit(0, 1, "a", "y")),
                patch(new ArtifactPatch.TextEdit(0, 0, "", "x"), new ArtifactPatch.TextEdit(0, 0, "", "y")),
                patch(new ArtifactPatch.TextEdit(-1, 0, "", "x")), patch(new ArtifactPatch.TextEdit(0, 9, "abcd", "x")),
                patch(new ArtifactPatch.TextEdit(3, 2, "", "x")));
        for (var value : invalid) assertThrows(IOException.class, () -> ArtifactPatchCodec.apply("abcd", value));
    }
    @Test void offsetsCannotSplitSurrogatePairs() throws Exception {
        String base = "A😀B";
        assertEquals("AXB", ArtifactPatchCodec.apply(base, patch(new ArtifactPatch.TextEdit(1, 3, "😀", "X"))));
        assertThrows(IOException.class, () -> ArtifactPatchCodec.apply(base, patch(new ArtifactPatch.TextEdit(2, 3, "\ude00", "X"))));
        assertThrows(IOException.class, () -> ArtifactPatchCodec.apply(base, patch(new ArtifactPatch.TextEdit(2, 2, "", "X"))));
    }
    @Test void unknownMissingVersionAndInvalidIntegerTypesRejected() {
        String valid = "{\"schemaVersion\":1,\"edits\":[{\"start\":0,\"end\":1,\"oldText\":\"a\",\"newText\":\"b\"}]}";
        for (String value : List.of(valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
                valid.replace("\"start\":0", "\"start\":0.5"), valid.replace("\"start\":0", "\"start\":\"0\""),
                valid.replace("\"start\":0", "\"start\":-1"), valid.replace("\"start\":0", "\"fuzzy\":true,\"start\":0"),
                valid.replace("\"oldText\":\"a\",", ""), "{\"schemaVersion\":1,\"edits\":[]}")) {
            assertThrows(IOException.class, () -> ArtifactPatchCodec.parse(JsonParser.parseString(value).getAsJsonObject()));
        }
    }
    @Test void invalidUnicodeAndOversizedPatchedResultRejected() {
        assertThrows(IOException.class, () -> ArtifactPatchCodec.apply("base", patch(new ArtifactPatch.TextEdit(0, 4, "base", "\ud800"))));
        assertThrows(IOException.class, () -> ArtifactPatchCodec.apply("base", patch(new ArtifactPatch.TextEdit(0, 0, "", "x".repeat(ArtifactResultValidator.MAX_FILE_BYTES)))));
        assertThrows(IOException.class, () -> ArtifactPatchCodec.apply("x".repeat(ArtifactResultValidator.MAX_FILE_BYTES + 1), patch(new ArtifactPatch.TextEdit(0, 1, "x", "y"))));
    }
}
