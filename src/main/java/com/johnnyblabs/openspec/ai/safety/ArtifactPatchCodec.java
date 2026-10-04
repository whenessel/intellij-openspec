package com.johnnyblabs.openspec.ai.safety;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

/** Exact patches have no fuzzy matching, rebasing, implicit files, or file deletion. */
public final class ArtifactPatchCodec {
    public static final int MAX_EDITS = 64;
    private ArtifactPatchCodec() { }

    public static ArtifactPatch parse(JsonObject object) throws IOException {
        requireKeys(object, Set.of("schemaVersion", "edits"));
        if (integer(object, "schemaVersion") != 1) throw new IOException("Unsupported artifact patch schema");
        JsonElement value = object.get("edits");
        if (value == null || !value.isJsonArray()) throw new IOException("Invalid artifact patch edits");
        JsonArray edits = value.getAsJsonArray();
        if (edits.isEmpty() || edits.size() > MAX_EDITS) throw new IOException("Invalid artifact patch edit count");
        var result = new ArrayList<ArtifactPatch.TextEdit>();
        long bytes = 0;
        for (JsonElement element : edits) {
            if (!element.isJsonObject()) throw new IOException("Invalid artifact patch edit");
            JsonObject edit = element.getAsJsonObject();
            requireKeys(edit, Set.of("start", "end", "oldText", "newText"));
            String oldText = string(edit, "oldText");
            String newText = string(edit, "newText");
            bytes += ArtifactResultValidator.utf8(oldText).length + ArtifactResultValidator.utf8(newText).length;
            if (bytes > 2L * ArtifactResultValidator.MAX_FILE_BYTES) throw new IOException("Artifact patch exceeds the review budget");
            result.add(new ArtifactPatch.TextEdit(integer(edit, "start"), integer(edit, "end"), oldText, newText));
        }
        return new ArtifactPatch(1, result);
    }

    public static JsonObject encode(ArtifactPatch patch) {
        JsonObject result = new JsonObject();
        result.addProperty("schemaVersion", patch.schemaVersion());
        JsonArray edits = new JsonArray();
        for (var edit : patch.edits()) {
            JsonObject value = new JsonObject();
            value.addProperty("start", edit.start());
            value.addProperty("end", edit.end());
            value.addProperty("oldText", edit.oldText());
            value.addProperty("newText", edit.newText());
            edits.add(value);
        }
        result.add("edits", edits);
        return result;
    }

    public static String apply(String base, ArtifactPatch patch) throws IOException {
        if (base == null || patch == null || patch.schemaVersion() != 1 || patch.edits().isEmpty()
                || patch.edits().size() > MAX_EDITS) throw new IOException("Invalid artifact patch");
        if (ArtifactResultValidator.utf8(base).length > ArtifactResultValidator.MAX_FILE_BYTES) throw new IOException("Artifact patch base exceeds the review budget");
        StringBuilder result = new StringBuilder();
        int cursor = 0;
        int previousStart = -1;
        long editBytes = 0;
        for (var edit : patch.edits()) {
            if (edit.oldText() == null || edit.newText() == null || edit.start() < cursor || edit.start() <= previousStart
                    || edit.end() < edit.start() || edit.end() > base.length()) throw new IOException("Artifact patch ranges must be ordered, unique and nonoverlapping");
            if (!isBoundary(base, edit.start()) || !isBoundary(base, edit.end())) throw new IOException("Artifact patch splits a Unicode character");
            if (!base.substring(edit.start(), edit.end()).equals(edit.oldText())) throw new IOException("Artifact patch oldText conflicts with its exact base");
            if (edit.oldText().equals(edit.newText())) throw new IOException("Artifact patch contains an empty/no-op edit");
            editBytes += ArtifactResultValidator.utf8(edit.oldText()).length + ArtifactResultValidator.utf8(edit.newText()).length;
            if (editBytes > 2L * ArtifactResultValidator.MAX_FILE_BYTES) throw new IOException("Artifact patch exceeds the review budget");
            result.append(base, cursor, edit.start()).append(edit.newText());
            cursor = edit.end();
            previousStart = edit.start();
            // Bound allocation even before the final UTF-8 encoding.
            if (result.length() > ArtifactResultValidator.MAX_FILE_BYTES) throw new IOException("Patched artifact exceeds the review budget");
        }
        result.append(base, cursor, base.length());
        String text = result.toString();
        if (ArtifactResultValidator.utf8(text).length > ArtifactResultValidator.MAX_FILE_BYTES) throw new IOException("Patched artifact exceeds the review budget");
        return text;
    }

    private static boolean isBoundary(String value, int offset) {
        return offset >= 0 && offset <= value.length() && (offset == 0 || offset == value.length()
                || !Character.isHighSurrogate(value.charAt(offset - 1)) || !Character.isLowSurrogate(value.charAt(offset)));
    }
    private static void requireKeys(JsonObject object, Set<String> keys) throws IOException {
        if (!object.keySet().equals(keys)) throw new IOException("Missing or unknown artifact patch fields");
    }
    private static int integer(JsonObject object, String key) throws IOException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("0|[1-9][0-9]{0,5}")) throw new IOException("Invalid artifact patch integer: " + key);
        int result = value.getAsInt();
        if (result > ArtifactResultValidator.MAX_FILE_BYTES) throw new IOException("Artifact patch offset exceeds the file budget");
        return result;
    }
    private static String string(JsonObject object, String key) throws IOException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IOException("Invalid artifact patch text: " + key);
        return value.getAsString();
    }
}
