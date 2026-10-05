package com.johnnyblabs.openspec.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Positive protocol contracts use recorded real API responses; mutations test robustness. */
class OpenRouterStreamingContractTest {
    private static List<String> events() throws Exception {
        return OpenRouterProtocolTest.fixture("completion-stream.sse").lines()
                .filter(line -> line.startsWith("data: ")).map(line -> line.substring(6)).toList();
    }
    private static OpenRouterProtocol.Completion parse(List<String> events, List<String> deltas) throws Exception {
        var parser = new OpenRouterProtocol.StreamParser();
        for (String event : events) parser.accept(event, deltas::add);
        return parser.finish();
    }
    @Test void capturedFreeSseProducesOnlyAssistantTextAndActualRoute() throws Exception {
        var deltas = new ArrayList<String>();
        var receipt = parse(events(), deltas);
        assertEquals("OK", receipt.text().trim());
        assertEquals(String.join("", deltas), receipt.text());
        assertEquals("liquid/lfm-2.5-2.6b:free", receipt.model());
        assertEquals("Liquid", receipt.provider());
        assertFalse(deltas.isEmpty());
    }
    @Test void disconnectWithoutDoneIsNeverAcceptedEvenAfterCompleteText() throws Exception {
        var capture = new ArrayList<>(events());
        assertEquals("[DONE]", capture.removeLast());
        var deltas = new ArrayList<String>();
        assertThrows(AiApiException.class, () -> parse(capture, deltas));
        assertFalse(deltas.isEmpty(), "Test must exercise disconnect after accepted output");
    }
    @Test void doneWithoutStopIsRejected() throws Exception {
        var capture = new ArrayList<>(events());
        capture.removeIf(event -> !event.equals("[DONE]") && event.contains("\"finish_reason\":\"stop\""));
        assertThrows(AiApiException.class, () -> parse(capture, new ArrayList<>()));
    }
    @Test void lengthAndRefusalMutationsNeverProduceCompletedReceipt() throws Exception {
        for (String mutation : List.of("length", "content_filter", "refusal", "choice-error")) {
            var capture = new ArrayList<String>();
            for (String event : events()) {
                if (event.equals("[DONE]")) { capture.add(event); continue; }
                JsonObject root = JsonParser.parseString(event).getAsJsonObject();
                var choices = root.getAsJsonArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    var choice = choices.get(0).getAsJsonObject();
                    if (mutation.equals("length") || mutation.equals("content_filter")) {
                        if (!choice.get("finish_reason").isJsonNull()) choice.addProperty("finish_reason", mutation);
                    } else if (mutation.equals("refusal")) {
                        choice.getAsJsonObject("delta").addProperty("refusal", "synthetic-secret");
                    } else {
                        var error = new JsonObject(); error.addProperty("code", 502); error.addProperty("message", "synthetic-secret");
                        choice.add("error", error);
                    }
                }
                capture.add(root.toString());
            }
            var error = assertThrows(AiApiException.class, () -> parse(capture, new ArrayList<>()));
            assertFalse(error.toString().contains("synthetic-secret"));
        }
    }
    @Test void invalidOrEmbeddedErrorAfterTextDoesNotComplete() throws Exception {
        var capture = events();
        var parser = new OpenRouterProtocol.StreamParser();
        var deltas = new ArrayList<String>();
        for (String event : capture) {
            if (event.equals("[DONE]")) break;
            parser.accept(event, deltas::add);
            if (!deltas.isEmpty()) break;
        }
        assertFalse(deltas.isEmpty());
        var error = assertThrows(AiApiException.class, () -> parser.accept("{", deltas::add));
        assertFalse(error.getMessage().contains("{"));
        var bodyError = JsonParser.parseString(capture.getFirst()).getAsJsonObject();
        var embedded = new JsonObject(); embedded.addProperty("code", 429); embedded.addProperty("message", "synthetic-secret");
        bodyError.add("error", embedded);
        var fresh = new OpenRouterProtocol.StreamParser();
        var bodyFailure = assertThrows(AiApiException.class, () -> fresh.accept(bodyError.toString(), ignored -> {}));
        assertFalse(bodyFailure.toString().contains("synthetic-secret"));
    }
    @Test void modelOrProviderChangeWithinResponseIsRejectedAndFailedParserCannotRecover() throws Exception {
        for (String field : List.of("model", "provider")) {
            var parser = new OpenRouterProtocol.StreamParser();
            var first = events().getFirst();
            parser.accept(first, ignored -> {});
            var changed = JsonParser.parseString(first).getAsJsonObject();
            changed.addProperty(field, "different-route");
            assertThrows(AiApiException.class, () -> parser.accept(changed.toString(), ignored -> {}));
            assertThrows(AiApiException.class, () -> parser.accept("[DONE]", ignored -> {}));
            assertThrows(AiApiException.class, parser::finish);
        }
    }
    @Test void capturedKeyStatusDoesNotDiscloseAccountIdentity() throws Exception {
        String status = OpenRouterProtocol.parseKeyStatus(OpenRouterProtocolTest.fixture("key-status.json"));
        assertTrue(status.contains("paid tier"));
        assertTrue(status.contains("remaining USD limit: unknown"));
        assertFalse(status.contains("sanitized"));
        assertFalse(status.contains("creator_user_id"));
        assertFalse(status.contains("workspace_id"));
        var root = JsonParser.parseString(OpenRouterProtocolTest.fixture("key-status.json")).getAsJsonObject();
        root.getAsJsonObject("data").addProperty("is_free_tier", true);
        root.getAsJsonObject("data").addProperty("limit_remaining", 0);
        String free = OpenRouterProtocol.parseKeyStatus(root.toString());
        assertTrue(free.contains("free tier"));
        assertTrue(free.contains("remaining USD limit: 0"));
        root.remove("data");
        assertThrows(AiApiException.class, () -> OpenRouterProtocol.parseKeyStatus(root.toString()));
    }
    @Test void unknownCatalogContextStillEnforcesConservativeInputBudget() throws Exception {
        for (boolean nullValue : List.of(false, true)) {
            var root = JsonParser.parseString(OpenRouterProtocolTest.fixture("models.json")).getAsJsonObject();
            var entry = root.getAsJsonArray("data").get(0).getAsJsonObject();
            if (nullValue) entry.add("context_length", com.google.gson.JsonNull.INSTANCE); else entry.remove("context_length");
            entry.getAsJsonObject("top_provider").remove("context_length");
            assertThrows(AiApiException.class, () -> OpenRouterProtocol.limits(root.toString(), entry.get("id").getAsString(), "x".repeat(50 * 1024), 256));
            assertEquals(256, OpenRouterProtocol.limits(root.toString(), entry.get("id").getAsString(), "short", 256));
        }
    }
    @Test void nativeStructuredRequirementsUseCapturedParametersAndFailClosed() throws Exception {
        var capture = OpenRouterProtocolTest.fixture("models.json");
        assertDoesNotThrow(() -> OpenRouterProtocol.requireStructured(capture, "liquid/lfm-2.5-2.6b:free"));
        assertThrows(AiApiException.class, () -> OpenRouterProtocol.requireStructured(capture, "manual/unknown"));
        assertThrows(AiApiException.class, () -> OpenRouterProtocol.requireStructured(null, "manual/unknown"));
        for (String missing : List.of("response_format", "structured_outputs")) {
            var root = JsonParser.parseString(capture).getAsJsonObject();
            var entry = root.getAsJsonArray("data").get(0).getAsJsonObject();
            var reduced = new com.google.gson.JsonArray();
            for (var parameter : entry.getAsJsonArray("supported_parameters")) if (!missing.equals(parameter.getAsString())) reduced.add(parameter);
            entry.add("supported_parameters", reduced);
            assertThrows(AiApiException.class, () -> OpenRouterProtocol.requireStructured(root.toString(), entry.get("id").getAsString()));
        }
    }
    @Test void outputLimitUsesCapturedCatalogAndHonorsUserBudget() throws Exception {
        String catalog = OpenRouterProtocolTest.fixture("models.json");
        assertEquals(256, OpenRouterProtocol.limits(catalog, "liquid/lfm-2.5-2.6b:free", "short prompt", 256));
        assertEquals(8192, OpenRouterProtocol.limits(catalog, "liquid/lfm-2.5-2.6b:free", "short prompt", 16000));
        assertEquals(256, OpenRouterProtocol.limits(catalog, "manual/not-in-catalog", "short prompt", 256));
        assertEquals(256, OpenRouterProtocol.limits(catalog, "openrouter/free", "short prompt", 256));
    }
    @Test void oversizedPromptRejectedBeforeInferenceAndSmallContextCapsOutput() throws Exception {
        var root = JsonParser.parseString(OpenRouterProtocolTest.fixture("models.json")).getAsJsonObject();
        var model = root.getAsJsonArray("data").get(0).getAsJsonObject();
        model.addProperty("context_length", 2048);
        model.getAsJsonObject("top_provider").addProperty("context_length", 2048);
        assertThrows(AiApiException.class, () -> OpenRouterProtocol.limits(root.toString(), model.get("id").getAsString(), "x".repeat(3000), 256));
        int bounded = OpenRouterProtocol.limits(root.toString(), model.get("id").getAsString(), "short", 4096);
        assertTrue(bounded > 0 && bounded < 2048, "Reserve prompt/context budget instead of sending requested 4096");
    }
}
