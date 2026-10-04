package com.johnnyblabs.openspec.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

/**
 * SDK-independent codecs for the existing REST providers. Transport, routing and PasswordSafe
 * remain in their existing services. These preserve the legacy wire contract without live calls.
 */
public final class DirectApiProtocol {
    private DirectApiProtocol() { }
    private static final Duration TIMEOUT = Duration.ofMinutes(5);
    private static final int MAX_TOKENS = 16000;
    public static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final java.util.List<String> REASONING_MODEL_PREFIXES =
            java.util.List.of("o1", "o3", "o4", "gpt-5");

    public static HttpRequest buildClaudeRequest(String model, String apiKey, String prompt) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", MAX_TOKENS);

        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        messages.add(message);
        body.add("messages", messages);

        return HttpRequest.newBuilder()
                .uri(URI.create(CLAUDE_API_URL))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(body)))
                .build();
    }

    public static String parseClaudeResponse(String body) throws AiApiException {
        JsonObject responseJson = JsonParser.parseString(body).getAsJsonObject();
        JsonArray content = responseJson.getAsJsonArray("content");
        if (content != null && content.size() > 0) {
            return content.get(0).getAsJsonObject().get("text").getAsString();
        }
        throw new AiApiException("Empty response from Claude API");
    }

    public static String openAiTokenParam(String model) {
        if (model == null) return "max_tokens";
        for (String prefix : REASONING_MODEL_PREFIXES) {
            if (model.startsWith(prefix)) return "max_completion_tokens";
        }
        return "max_tokens";
    }

    public static HttpRequest buildOpenAiRequest(String model, String apiKey, String prompt) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty(openAiTokenParam(model), MAX_TOKENS);

        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        messages.add(message);
        body.add("messages", messages);

        return HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(body)))
                .build();
    }

    public static String parseOpenAiResponse(String body) throws AiApiException {
        JsonObject responseJson = JsonParser.parseString(body).getAsJsonObject();
        JsonArray choices = responseJson.getAsJsonArray("choices");
        if (choices != null && choices.size() > 0) {
            return choices.get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();
        }
        throw new AiApiException("Empty response from OpenAI API");
    }

    public static HttpRequest buildGeminiRequest(String model, String apiKey, String prompt) {
        JsonObject body = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject part = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", prompt);
        parts.add(textPart);
        part.add("parts", parts);
        contents.add(part);
        body.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("maxOutputTokens", MAX_TOKENS);
        body.add("generationConfig", generationConfig);

        String url = GEMINI_API_URL + model + ":generateContent";
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(body)))
                .build();
    }

    public static String parseGeminiResponse(String body) throws AiApiException {
        JsonObject responseJson = JsonParser.parseString(body).getAsJsonObject();
        JsonArray candidates = responseJson.getAsJsonArray("candidates");
        if (candidates != null && candidates.size() > 0) {
            JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
            JsonArray responseParts = content.getAsJsonArray("parts");
            if (responseParts != null && responseParts.size() > 0) {
                return responseParts.get(0).getAsJsonObject().get("text").getAsString();
            }
        }
        throw new AiApiException("Empty response from Gemini API");
    }

    public static String extractErrorMessage(String responseBody) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            if (json.has("error")) {
                JsonObject error = json.getAsJsonObject("error");
                if (error != null && error.has("message")) {
                    return error.get("message").getAsString();
                }
            }
        } catch (Exception ignored) {
            // Not valid JSON or unexpected structure — fall through to truncation
        }
        if (responseBody.length() > 200) {
            return responseBody.substring(0, 200) + "...";
        }
        return responseBody;
    }

    public static String suggestionForStatus(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return "Check your API key in Settings \u2192 Tools \u2192 OpenSpec";
        } else if (statusCode == 429) {
            return "Rate limited \u2014 wait a moment and retry";
        } else if (statusCode >= 500) {
            return "The provider may be experiencing issues \u2014 try again shortly";
        }
        return null;
    }
}
