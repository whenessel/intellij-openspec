package com.johnnyblabs.openspec.ai;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class OpenRouterProtocolTest {
    static String fixture(String name) throws Exception {
        try (var input = OpenRouterProtocolTest.class.getResourceAsStream("/fixtures/ai/openrouter/" + name)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    @Test void capturedCompletedTextIsParsedAndTruncationRejected() throws Exception {
        assertEquals("OK", OpenRouterProtocol.parseCompletion(fixture("completion.json")));
        assertThrows(AiApiException.class, () -> OpenRouterProtocol.parseCompletion(fixture("completion-length.json")));
    }
    @Test void capturedCatalogRetainsQualifiedIDsAndPrices() throws Exception {
        String capture = fixture("models.json");
        var parsed = OpenRouterProtocol.parseModels(capture);
        assertTrue(parsed.stream().anyMatch(m -> m.id().equals("liquid/lfm-2.5-2.6b:free")
                && m.description().contains("input: 0; output: 0")));
        var source = JsonParser.parseString(capture).getAsJsonObject().getAsJsonArray("data");
        for (var entry : source) {
            var json = entry.getAsJsonObject();
            boolean text = json.getAsJsonObject("architecture").getAsJsonArray("output_modalities").toString().contains("\"text\"")
                    && json.getAsJsonObject("architecture").getAsJsonArray("input_modalities").toString().contains("\"text\"");
            assertEquals(text, parsed.stream().anyMatch(m -> m.id().equals(json.get("id").getAsString())));
        }
        assertTrue(parsed.stream().allMatch(m -> m.id().equals(m.wireModel()) && m.textInput().name().equals("SUPPORTED")));
    }
    @Test void catalogRejectsUnknownOrNonTextModalities() throws Exception {
        var source = JsonParser.parseString(fixture("models.json")).getAsJsonObject();
        for (var entry : source.getAsJsonArray("data"))
            entry.getAsJsonObject().getAsJsonObject("architecture").remove("output_modalities");
        assertThrows(AiApiException.class, () -> OpenRouterProtocol.parseModels(source.toString()));
    }
    @Test void catalogInputRequirementPricesAndTimestampArePreserved() throws Exception {
        String capture = fixture("models.json");
        var fixed = java.time.Instant.parse("2026-10-05T12:00:00Z");
        var paid = OpenRouterProtocol.parseModels(capture, fixed).stream()
                .filter(m -> m.id().equals("openai/gpt-4o")).findFirst().orElseThrow();
        var source = JsonParser.parseString(capture).getAsJsonObject();
        var original = source.getAsJsonArray("data").asList().stream().map(e -> e.getAsJsonObject())
                .filter(e -> e.get("id").getAsString().equals("openai/gpt-4o")).findFirst().orElseThrow();
        assertTrue(paid.description().contains("USD/token input: " + original.getAsJsonObject("pricing").get("prompt").getAsString()));
        assertTrue(paid.description().contains("output: " + original.getAsJsonObject("pricing").get("completion").getAsString()));
        assertTrue(paid.description().contains("fetched: " + fixed));
        assertTrue(paid.description().contains("source: https://openrouter.ai/api/v1/models"));
        for (var entry : source.getAsJsonArray("data"))
            entry.getAsJsonObject().getAsJsonObject("architecture").remove("input_modalities");
        assertThrows(AiApiException.class, () -> OpenRouterProtocol.parseModels(source.toString()));
    }
    @Test void capturedResponseMutationsRejectRefusalChoiceErrorAndUnknownFinish() throws Exception {
        for (String mutation : java.util.List.of("refusal", "choice-error", "finish")) {
            var root = JsonParser.parseString(fixture("completion.json")).getAsJsonObject();
            var choice = root.getAsJsonArray("choices").get(0).getAsJsonObject();
            if (mutation.equals("refusal")) choice.getAsJsonObject("message").addProperty("refusal", "blocked");
            if (mutation.equals("choice-error")) choice.add("error", JsonParser.parseString("{\"code\":502,\"message\":\"synthetic-secret\"}"));
            if (mutation.equals("finish")) choice.addProperty("finish_reason", "unknown");
            assertThrows(AiApiException.class, () -> OpenRouterProtocol.parseCompletion(root.toString()));
        }
    }
    @Test void normalizedRequestUsesBearerAndExactModelWithoutOpenAiTokenHeuristics() throws Exception {
        var request = OpenRouterProtocol.completion("openai/gpt-5", "synthetic-key", "synthetic prompt", 256);
        assertEquals("https://openrouter.ai/api/v1/chat/completions", request.uri().toString());
        assertEquals("Bearer synthetic-key", request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("POST", request.method());
        String raw = requestBody(request);
        var body = JsonParser.parseString(raw).getAsJsonObject();
        assertEquals("openai/gpt-5", body.get("model").getAsString());
        assertEquals(256, body.get("max_tokens").getAsInt());
        assertFalse(body.has("max_completion_tokens"));
        assertFalse(body.get("stream").getAsBoolean());
        assertFalse(raw.contains("synthetic-key"));
        assertFalse(request.uri().toString().contains("synthetic-key"));
        assertTrue(OpenRouterProtocol.models().headers().firstValue("Authorization").isEmpty());
        assertEquals("https://openrouter.ai/api/v1/models", OpenRouterProtocol.models().uri().toString());
    }
    static String requestBody(java.net.http.HttpRequest request) {
        var bytes = new java.io.ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
            public void onSubscribe(java.util.concurrent.Flow.Subscription sub) { sub.request(Long.MAX_VALUE); }
            public void onNext(java.nio.ByteBuffer item) { byte[] part = new byte[item.remaining()]; item.get(part); bytes.writeBytes(part); }
            public void onError(Throwable error) { fail(error); }
            public void onComplete() { }
        });
        return bytes.toString(StandardCharsets.UTF_8);
    }
    @Test void malformedNullRefusedAndEmbeddedErrorsAreSafeFailures() {
        for (String body : java.util.List.of("null", "{", "{}", "{\"choices\":[]}",
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":null}}]}",
                "{\"error\":{\"code\":401,\"message\":\"synthetic-secret\"}}",
                "{\"choices\":[{\"finish_reason\":\"content_filter\",\"message\":{\"content\":\"partial\"}}]}")) {
            var error = assertThrows(AiApiException.class, () -> OpenRouterProtocol.parseCompletion(body));
            assertFalse(error.getMessage().contains("synthetic-secret"));
        }
        assertThrows(AiApiException.class, () -> OpenRouterProtocol.parseModels("{\"data\":[]}"));
        assertThrows(AiApiException.class, () -> OpenRouterProtocol.parseModels("null"));
    }
    @Test void unknownPricesStayUnknownAndDuplicateIdsAreDeduplicated() throws Exception {
        var source = JsonParser.parseString(fixture("models.json")).getAsJsonObject();
        var data = source.getAsJsonArray("data");
        var text = data.asList().stream().map(e -> e.getAsJsonObject())
                .filter(e -> e.get("id").getAsString().equals("liquid/lfm-2.5-2.6b:free")).findFirst().orElseThrow();
        text.remove("pricing");
        data.add(text.deepCopy());
        var result = OpenRouterProtocol.parseModels(source.toString());
        assertEquals(1, result.stream().filter(m -> m.id().equals("liquid/lfm-2.5-2.6b:free")).count());
        assertTrue(result.stream().filter(m -> m.id().equals("liquid/lfm-2.5-2.6b:free")).findFirst().orElseThrow().description().contains("unknown"));
    }
}
