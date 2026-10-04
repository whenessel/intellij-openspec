package com.johnnyblabs.openspec.ai.backend;

import com.google.gson.*;
import com.johnnyblabs.openspec.ai.AiApiException;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class CodexProtocolTest {
    private static JsonObject response(JsonArray capture,int id) {
        for(JsonElement entry:capture) {JsonObject value=entry.getAsJsonObject();if(value.has("id")&&value.get("id").getAsInt()==id)return value;}
        throw new AssertionError("Missing captured response");
    }
    @Test void actualCapturedInitializationAndThreadResponseDecodeIntoTypedResults() throws Exception {
        try(InputStream stream=getClass().getResourceAsStream("/fixtures/codex/0.160.0/handshake.json")){
            JsonArray capture=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonArray();
            CodexProtocol.SuccessFrame initializeFrame=assertInstanceOf(CodexProtocol.SuccessFrame.class,CodexProtocol.frame(response(capture,1)));
            assertEquals(1,initializeFrame.id());
            JsonObject initialize=initializeFrame.result();
            var handshake=CodexProtocol.initialized(initialize);
            assertTrue(handshake.userAgent().contains("/0.160.0 "));assertEquals("linux",handshake.platformOs());
            CodexProtocol.SuccessFrame threadFrame=assertInstanceOf(CodexProtocol.SuccessFrame.class,CodexProtocol.frame(response(capture,4)));
            assertEquals(4,threadFrame.id());
            JsonObject response=threadFrame.result();
            var thread=CodexProtocol.threadStarted(response);
            assertEquals(response.getAsJsonObject("thread").get("id").getAsString(),thread.threadId());assertEquals(response.get("model").getAsString(),thread.model());
        }
    }
    @Test void unknownOptionalNotificationsTolerateMissingNullAndScalarParams() throws Exception {
        for(String json:new String[]{"{\"method\":\"future/event\"}","{\"method\":\"future/event\",\"params\":null}","{\"method\":\"future/event\",\"params\":17}"})
            assertInstanceOf(CodexProtocol.Unknown.class,CodexProtocol.notification(JsonParser.parseString(json).getAsJsonObject()));
    }
    @Test void knownRequiredFieldsDoNotCoerceTypesOrInventDefaults() {
        for(String json:new String[]{"{\"method\":\"item/agentMessage/delta\"}","{\"method\":\"turn/completed\",\"params\":null}","{\"method\":\"item/agentMessage/delta\",\"params\":{\"threadId\":\"t\",\"turnId\":\"u\",\"itemId\":\"i\",\"delta\":false}}"})
            assertThrows(AiApiException.class,()->CodexProtocol.notification(JsonParser.parseString(json).getAsJsonObject()));
    }
    @Test void syntheticResponseFramesDiscriminateErrorsNotificationsRequestsAndSuccess() throws Exception {
        assertInstanceOf(CodexProtocol.SuccessFrame.class,CodexProtocol.frame(JsonParser.parseString("{\"id\":1,\"result\":{}}").getAsJsonObject()));
        assertInstanceOf(CodexProtocol.FailureFrame.class,CodexProtocol.frame(JsonParser.parseString("{\"id\":1,\"error\":{\"code\":-1}}").getAsJsonObject()));
        assertInstanceOf(CodexProtocol.NotificationFrame.class,CodexProtocol.frame(JsonParser.parseString("{\"method\":\"future/event\"}").getAsJsonObject()));
        assertInstanceOf(CodexProtocol.RequestFrame.class,CodexProtocol.frame(JsonParser.parseString("{\"id\":\"server-id\",\"method\":\"item/tool/request\"}").getAsJsonObject()));
        for(String json:new String[]{"{\"id\":\"1\",\"result\":{}}","{\"id\":1,\"result\":{},\"error\":{}}","{\"id\":1,\"result\":null}","{\"id\":1.5,\"result\":{}}"})
            assertThrows(AiApiException.class,()->CodexProtocol.frame(JsonParser.parseString(json).getAsJsonObject()));
    }
}
