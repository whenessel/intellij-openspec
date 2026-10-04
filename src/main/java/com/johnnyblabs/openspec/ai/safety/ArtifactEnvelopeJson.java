package com.johnnyblabs.openspec.ai.safety;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.HashSet;

/** Strict bounded JSON decoding rejects duplicate keys, comments and trailing payloads. */
public final class ArtifactEnvelopeJson {
    private ArtifactEnvelopeJson() { }
    public static JsonObject parse(String text) throws IOException {
        if (text == null || text.length() > ArtifactResultValidator.MAX_TOTAL_BYTES * 2
                || ArtifactResultValidator.utf8(text).length > ArtifactResultValidator.MAX_TOTAL_BYTES * 2) {
            throw new IOException("Artifact JSON exceeds the response budget");
        }
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement result = read(reader, 0, new int[]{0});
            if (reader.peek() != JsonToken.END_DOCUMENT || !result.isJsonObject()) throw new IOException("Expected one artifact JSON object");
            return result.getAsJsonObject();
        } catch (RuntimeException ex) {
            throw new IOException("Malformed structured artifact JSON", ex);
        }
    }
    private static JsonElement read(JsonReader reader, int depth, int[] nodes) throws IOException {
        if (depth > 8 || ++nodes[0] > 40_000) throw new IOException("Artifact JSON exceeds structural limits");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                JsonObject object = new JsonObject();
                var keys = new HashSet<String>();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (!keys.add(key)) throw new IOException("Duplicate artifact JSON field");
                    object.add(key, read(reader, depth + 1, nodes));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                JsonArray array = new JsonArray();
                while (reader.hasNext()) array.add(read(reader, depth + 1, nodes));
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new IOException("Invalid artifact JSON token");
        };
    }
}
