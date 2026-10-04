package com.johnnyblabs.openspec.ai.safety;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Provider-neutral result schema; path, base and patch semantics are validated before application. */
public final class ArtifactEnvelopeSchema {
    private ArtifactEnvelopeSchema() { }
    public static JsonObject forArtifact(String artifactId) {
        JsonObject schema = JsonParser.parseString("""
            {"type":"object","additionalProperties":false,"required":["schemaVersion","artifactId","files"],
             "properties":{"schemaVersion":{"type":"integer","enum":[1]},"artifactId":{"type":"string"},
             "files":{"type":"array","minItems":1,"maxItems":64,"items":{"type":"object","additionalProperties":false,
             "required":["relativePath","operation"],"properties":{"relativePath":{"type":"string"},
             "operation":{"type":"string","enum":["create","replace","patch"]},"content":{"type":"string"},
             "baseHash":{"type":"string","pattern":"^[a-f0-9]{64}$"},
             "patch":{"type":"object","additionalProperties":false,"required":["schemaVersion","edits"],
             "properties":{"schemaVersion":{"type":"integer","enum":[1]},"edits":{"type":"array","minItems":1,"maxItems":64,
             "items":{"type":"object","additionalProperties":false,"required":["start","end","oldText","newText"],
             "properties":{"start":{"type":"integer","minimum":0},"end":{"type":"integer","minimum":0},
             "oldText":{"type":"string"},"newText":{"type":"string"}}}}}}},
             "anyOf":[{"properties":{"operation":{"enum":["create","replace"]}},"required":["content"]},
             {"properties":{"operation":{"enum":["patch"]}},"required":["baseHash","patch"]}]}}}}
            """).getAsJsonObject();
        var ids = new com.google.gson.JsonArray(); ids.add(artifactId);
        schema.getAsJsonObject("properties").getAsJsonObject("artifactId").add("enum", ids);
        return schema;
    }
    public static String instruction(String artifactId, String outputPattern) {
        return "\n\nReturn ONLY a JSON artifact envelope: schemaVersion=1, artifactId=" + artifactId
                + ", files=[{relativePath,operation,content}]. Use concrete paths matching " + outputPattern
                + ". create/replace require full content. patch requires baseHash and patch={schemaVersion:1,edits:[{start,end,oldText,newText}]},"
                + " no content field. Patch offsets are UTF-16 code units, end-exclusive, sorted and nonoverlapping, with exact oldText and supplied base SHA-256."
                + " Never patch redacted bases, use fuzzy matching, delete files, use globs as filenames, or write files yourself."
                + " Results are reviewed as LF-normalized document text; consistent existing line separators are preserved by the IDE.";
    }
}
