package com.johnnyblabs.openspec.ai.safety;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArtifactEnvelopeSchemaTest {
    @Test void structuredGenerationCanRequestPatchWithoutWholeFileContent() {
        var schema = ArtifactEnvelopeSchema.forArtifact("specs");
        var properties = schema.getAsJsonObject("properties");
        assertEquals("specs", properties.getAsJsonObject("artifactId").getAsJsonArray("enum").get(0).getAsString());
        var file = properties.getAsJsonObject("files").getAsJsonObject("items");
        assertTrue(file.getAsJsonObject("properties").getAsJsonObject("operation").getAsJsonArray("enum")
                .asList().stream().anyMatch(value -> value.getAsString().equals("patch")));
        var patchBranch = file.getAsJsonArray("anyOf").get(1).getAsJsonObject();
        assertEquals(java.util.List.of("baseHash", "patch"), patchBranch.getAsJsonArray("required").asList().stream().map(value -> value.getAsString()).toList());
        assertFalse(patchBranch.getAsJsonArray("required").asList().stream().anyMatch(value -> value.getAsString().equals("content")));
        var edit = file.getAsJsonObject("properties").getAsJsonObject("patch").getAsJsonObject("properties")
                .getAsJsonObject("edits").getAsJsonObject("items");
        assertEquals(4, edit.getAsJsonArray("required").size());
        assertFalse(edit.get("additionalProperties").getAsBoolean());
    }
    @Test void schemaIsFreshAndGuidanceMatchesExactArtifactOnlyPatchContract() {
        var schema = ArtifactEnvelopeSchema.forArtifact("proposal");
        schema.remove("properties");
        assertTrue(ArtifactEnvelopeSchema.forArtifact("proposal").has("properties"));
        String prompt = ArtifactEnvelopeSchema.instruction("specs", "specs/**/*.md");
        assertTrue(prompt.contains("UTF-16"));
        assertTrue(prompt.contains("baseHash"));
        assertTrue(prompt.contains("exact oldText"));
        assertTrue(prompt.contains("never") || prompt.contains("Never"));
        assertTrue(prompt.contains("LF-normalized"));
    }
}
