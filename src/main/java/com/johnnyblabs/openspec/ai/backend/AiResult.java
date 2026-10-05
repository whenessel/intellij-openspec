package com.johnnyblabs.openspec.ai.backend;

import com.google.gson.*;
import com.johnnyblabs.openspec.ai.AiApiException;
import com.johnnyblabs.openspec.ai.safety.ArtifactEnvelopeJson;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;

/** Successful outputs are discriminated; failed/partial/cancelled turns never construct a result. */
public record AiResult(Payload payload, String backendId, String model, String provider) {
    public sealed interface Payload permits TextOutput, ArtifactOutput { String text(); }
    public record TextOutput(String text) implements Payload {
        public TextOutput { Objects.requireNonNull(text); }
    }
    public record ArtifactOutput(String text, JsonObject envelope) implements Payload {
        public ArtifactOutput { Objects.requireNonNull(text); envelope=Objects.requireNonNull(envelope).deepCopy(); }
        @Override public JsonObject envelope() { return envelope.deepCopy(); }
    }
    public AiResult { Objects.requireNonNull(payload); Objects.requireNonNull(backendId); provider = provider == null ? "" : provider; }
    public AiResult(Payload payload, String backendId, String model) { this(payload, backendId, model, ""); }
    public AiResult(String text,String backendId,String model) { this(new TextOutput(text),backendId,model); }
    public AiResult withProvider(String actualProvider) { return new AiResult(payload, backendId, model, actualProvider); }
    public String text() { return payload.text(); }
    public static AiResult fromResponse(String text,String backendId,String model,boolean structured) throws AiApiException {
        if (text==null || text.isBlank()) throw new AiApiException("AI backend completed without an output; no result was applied.");
        if (!structured) return new AiResult(text,backendId,model);
        try {
            JsonObject envelope=ArtifactEnvelopeJson.parse(text);
            if (!Set.of("schemaVersion","artifactId","files").containsAll(envelope.keySet())
                    || !envelope.has("schemaVersion") || !envelope.get("schemaVersion").isJsonPrimitive()
                    || !envelope.get("schemaVersion").getAsJsonPrimitive().isNumber()
                    || !"1".equals(envelope.get("schemaVersion").getAsString())
                    || !isString(envelope,"artifactId") || envelope.get("artifactId").getAsString().isBlank()
                    || !envelope.has("files") || !envelope.get("files").isJsonArray()
                    || envelope.getAsJsonArray("files").isEmpty() || envelope.getAsJsonArray("files").size()>64) throw new IOException("Invalid artifact envelope");
            for (JsonElement entry:envelope.getAsJsonArray("files")) {
                if(!entry.isJsonObject())throw new IOException("Invalid artifact file");
                JsonObject file=entry.getAsJsonObject();
                if(!isString(file,"relativePath") || !isString(file,"operation"))throw new IOException("Missing artifact file fields");
                String operation=file.get("operation").getAsString();
                Set<String> allowed=operation.equals("patch") ? Set.of("relativePath","operation","baseHash","patch") : Set.of("relativePath","operation","baseHash","content");
                if(!Set.of("create","replace","patch").contains(operation) || !allowed.containsAll(file.keySet()))throw new IOException("Unsupported artifact operation");
                if(file.has("baseHash") && (!isString(file,"baseHash") || !file.get("baseHash").getAsString().matches("[a-f0-9]{64}")))throw new IOException("Invalid artifact base hash");
                if(operation.equals("patch")) {
                    if(!isString(file,"baseHash") || !file.has("patch") || !file.get("patch").isJsonObject())throw new IOException("Invalid artifact patch");
                } else if(!isString(file,"content") || operation.equals("create") && file.has("baseHash"))throw new IOException("Invalid artifact content");
            }
            return new AiResult(new ArtifactOutput(text,envelope),backendId,model);
        } catch(IOException | RuntimeException e) { throw new AiApiException("AI backend returned an invalid structured artifact envelope; no result was applied."); }
    }
    private static boolean isString(JsonObject value,String key) {
        JsonElement field=value.get(key);return field!=null && field.isJsonPrimitive() && field.getAsJsonPrimitive().isString();
    }
}
