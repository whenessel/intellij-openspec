package com.johnnyblabs.openspec.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Success responses are existing provider-published fixtures (see fixtures/ai/README.md),
 * not live paid captures. Request inputs and invalid/error cases are synthetic regressions.
 * No network or IntelliJ SDK is required.
 */
class DirectApiProtocolTest {
    private static final String KEY = "synthetic-test-key";
    private static final String PROMPT = "Reviewed \"text\"\nUnicode: Привет; $(literal command)";

    @Test void claudePreservesMessagesEndpointHeadersPromptAndOutputLimit() throws Exception {
        HttpRequest request = DirectApiProtocol.buildClaudeRequest("claude-sonnet-4-5", KEY, PROMPT);
        assertEquals("https://api.anthropic.com/v1/messages", request.uri().toString());
        assertEquals(KEY, request.headers().firstValue("x-api-key").orElseThrow());
        assertEquals("2023-06-01", request.headers().firstValue("anthropic-version").orElseThrow());
        assertTrue(request.headers().firstValue("Authorization").isEmpty());
        JsonObject body = body(request);
        assertEquals("claude-sonnet-4-5", body.get("model").getAsString());
        assertEquals(16000, body.get("max_tokens").getAsInt());
        assertUserMessage(body);
        assertCommonRequest(request);
    }

    @Test void openAiPreservesBearerHeaderAndReasoningOutputLimit() throws Exception {
        HttpRequest request = DirectApiProtocol.buildOpenAiRequest("gpt-5", KEY, PROMPT);
        assertEquals("https://api.openai.com/v1/chat/completions", request.uri().toString());
        assertEquals("Bearer " + KEY, request.headers().firstValue("Authorization").orElseThrow());
        assertTrue(request.headers().firstValue("x-api-key").isEmpty());
        JsonObject body = body(request);
        assertEquals("gpt-5", body.get("model").getAsString());
        assertEquals(16000, body.get("max_completion_tokens").getAsInt());
        assertFalse(body.has("max_tokens"));
        assertUserMessage(body);
        assertCommonRequest(request);
    }

    @Test void openAiChatRequestUsesLegacyChatOutputLimit() throws Exception {
        JsonObject body = body(DirectApiProtocol.buildOpenAiRequest("gpt-4o", KEY, PROMPT));
        assertEquals(16000, body.get("max_tokens").getAsInt());
        assertFalse(body.has("max_completion_tokens"));
        assertUserMessage(body);
    }

    @Test void geminiPreservesHeaderAuthContentsAndOutputLimit() throws Exception {
        HttpRequest request = DirectApiProtocol.buildGeminiRequest("gemini-2.5-pro", KEY, PROMPT);
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:generateContent", request.uri().toString());
        assertEquals(KEY, request.headers().firstValue("x-goog-api-key").orElseThrow());
        assertTrue(request.headers().firstValue("Authorization").isEmpty());
        JsonObject body = body(request);
        assertEquals(PROMPT, body.getAsJsonArray("contents").get(0).getAsJsonObject()
                .getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString());
        assertEquals(16000, body.getAsJsonObject("generationConfig").get("maxOutputTokens").getAsInt());
        assertCommonRequest(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"o1", "o1-mini", "o1-preview", "o3", "o3-mini", "o4-mini", "gpt-5", "gpt-5-mini"})
    void reasoningFamilyUsesCompletionTokenParameter(String model) {
        assertEquals("max_completion_tokens", DirectApiProtocol.openAiTokenParam(model));
    }

    @ParameterizedTest
    @ValueSource(strings = {"gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo"})
    void chatFamilyUsesLegacyTokenParameter(String model) {
        assertEquals("max_tokens", DirectApiProtocol.openAiTokenParam(model));
    }

    @Test void absentModelPreservesLegacyTokenParameter() {
        assertEquals("max_tokens", DirectApiProtocol.openAiTokenParam(null));
    }

    @Test void parsesProviderPublishedClaudeFixture() throws Exception {
        assertEquals("Hi! My name is Claude.", DirectApiProtocol.parseClaudeResponse(fixture("claude/messages-response.json")));
    }

    @Test void parsesProviderPublishedOpenAiFixture() throws Exception {
        assertEquals("\n\nHello there, how may I assist you today?", DirectApiProtocol.parseOpenAiResponse(fixture("openai/chat-completion-response.json")));
    }

    @Test void parsesProviderPublishedGeminiFixture() throws Exception {
        assertEquals("AI works by learning patterns from large amounts of data, then using those patterns to make predictions or generate new content.",
                DirectApiProtocol.parseGeminiResponse(fixture("gemini/generatecontent-response.json")));
    }

    @Test void syntheticEmptyProviderResponsesRemainFailures() {
        assertTrue(assertThrows(AiApiException.class, () -> DirectApiProtocol.parseClaudeResponse("{\"content\":[]}")).getMessage().contains("Claude"));
        assertTrue(assertThrows(AiApiException.class, () -> DirectApiProtocol.parseOpenAiResponse("{\"choices\":[]}")).getMessage().contains("OpenAI"));
        assertTrue(assertThrows(AiApiException.class, () -> DirectApiProtocol.parseGeminiResponse("{\"candidates\":[]}")).getMessage().contains("Gemini"));
    }

    @Test void syntheticErrorShapesPreserveMessageAndBoundedFallback() {
        assertEquals("synthetic denied", DirectApiProtocol.extractErrorMessage("{\"error\":{\"message\":\"synthetic denied\"}}"));
        assertEquals("provider unavailable", DirectApiProtocol.extractErrorMessage("provider unavailable"));
        assertEquals("x".repeat(200) + "...", DirectApiProtocol.extractErrorMessage("x".repeat(201)));
    }

    @Test void statusSuggestionsPreserveAuthenticationLimitAndOutageMeaning() {
        for (int status : new int[]{401, 403}) assertTrue(DirectApiProtocol.suggestionForStatus(status).contains("API key"));
        assertTrue(DirectApiProtocol.suggestionForStatus(429).contains("Rate limited"));
        for (int status : new int[]{500, 503}) assertTrue(DirectApiProtocol.suggestionForStatus(status).contains("provider"));
        assertNull(DirectApiProtocol.suggestionForStatus(400));
    }

    private static void assertCommonRequest(HttpRequest request) {
        assertEquals("POST", request.method());
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElseThrow());
        assertEquals(Duration.ofMinutes(5), request.timeout().orElseThrow());
        assertFalse(request.uri().toString().contains(KEY));
        assertNull(request.uri().getRawQuery());
    }

    private static void assertUserMessage(JsonObject body) {
        assertEquals(1, body.getAsJsonArray("messages").size());
        JsonObject message = body.getAsJsonArray("messages").get(0).getAsJsonObject();
        assertEquals("user", message.get("role").getAsString());
        assertEquals(PROMPT, message.get("content").getAsString());
    }

    private static String fixture(String relative) throws Exception {
        try (InputStream stream = DirectApiProtocolTest.class.getResourceAsStream("/fixtures/ai/" + relative)) {
            assertNotNull(stream, relative);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static JsonObject body(HttpRequest request) throws Exception {
        CompletableFuture<String> completed = new CompletableFuture<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer item) {
                byte[] chunk = new byte[item.remaining()]; item.get(chunk); bytes.writeBytes(chunk);
            }
            @Override public void onError(Throwable error) { completed.completeExceptionally(error); }
            @Override public void onComplete() { completed.complete(bytes.toString(StandardCharsets.UTF_8)); }
        });
        return JsonParser.parseString(completed.get(2, TimeUnit.SECONDS)).getAsJsonObject();
    }
}
