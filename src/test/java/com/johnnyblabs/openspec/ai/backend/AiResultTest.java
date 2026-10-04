package com.johnnyblabs.openspec.ai.backend;

import com.google.gson.JsonObject;
import com.johnnyblabs.openspec.ai.AiApiException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiResultTest {
    private static final String ENVELOPE="{\"schemaVersion\":1,\"artifactId\":\"specs\",\"files\":[{\"relativePath\":\"specs/a/spec.md\",\"operation\":\"create\",\"content\":\"# Spec\"}]}";
    @Test void successfulTextAndArtifactOutputsAreDiscriminatedWithoutChangingTextApi() throws Exception {
        var text=new AiResult("answer","local-codex","model");assertInstanceOf(AiResult.TextOutput.class,text.payload());assertEquals("answer",text.text());
        var result=AiResult.fromResponse(ENVELOPE,"local-codex","model",true);
        var artifact=assertInstanceOf(AiResult.ArtifactOutput.class,result.payload());assertEquals(ENVELOPE,result.text());
        JsonObject exposed=artifact.envelope();exposed.addProperty("schemaVersion",99);
        assertEquals(1,artifact.envelope().get("schemaVersion").getAsInt());
        JsonObject input=artifact.envelope();var payload=new AiResult.ArtifactOutput(ENVELOPE,input);input.remove("files");assertTrue(payload.envelope().has("files"));
    }
    @Test void structuredOutputsRejectTextDuplicatesMalformedOrUnsupportedEnvelopes() {
        for(String invalid:new String[]{"answer",ENVELOPE+" trailing",ENVELOPE.replace("\"schemaVersion\":1","\"schemaVersion\":1,\"schemaVersion\":1"),ENVELOPE.replace("\"schemaVersion\":1","\"schemaVersion\":2"),ENVELOPE.replace("\"create\"","\"delete\""),ENVELOPE.replace("\"# Spec\"","false"),"{\"schemaVersion\":1,\"artifactId\":\"specs\",\"files\":[]}"})
            assertThrows(AiApiException.class,()->AiResult.fromResponse(invalid,"local-codex","model",true),invalid);
    }
    @Test void artifactOutputClassificationDoesNotGrantFilesystemAcceptance() throws Exception {
        String escaping=ENVELOPE.replace("specs/a/spec.md","../outside.md");
        assertInstanceOf(AiResult.ArtifactOutput.class,AiResult.fromResponse(escaping,"local-codex","model",true).payload());
        // The writer's scope/base/path validation is mandatory after syntactic classification.
    }
}
