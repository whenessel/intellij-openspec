package com.johnnyblabs.openspec.ai;

import com.google.gson.*;
import com.johnnyblabs.openspec.ai.backend.CapabilitySupport;
import com.johnnyblabs.openspec.ai.backend.ModelDescriptor;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;

/** OpenRouter's normalized, nonstreaming text contract. Never reflects raw errors or credentials. */
public final class OpenRouterProtocol {
    private OpenRouterProtocol() { }
    private static final String API = "https://openrouter.ai/api/v1/";

    public static HttpRequest completion(String model, String key, String prompt, int maxTokens) {
        if (model == null || model.isBlank()) throw new IllegalArgumentException("Select an OpenRouter model");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Provide an OpenRouter API key");
        if (maxTokens < 1 || maxTokens > 16000) throw new IllegalArgumentException("Invalid output token limit");
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", maxTokens);
        body.addProperty("stream", false);
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        JsonArray messages = new JsonArray();
        messages.add(message);
        body.add("messages", messages);
        return HttpRequest.newBuilder(URI.create(API + "chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key)
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
    }

    /** Public catalog: no credentials and no inference. */
    public static HttpRequest models() {
        return HttpRequest.newBuilder(URI.create(API + "models"))
                .timeout(Duration.ofSeconds(30)).GET().build();
    }

    public static String parseCompletion(String body) throws AiApiException {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            rejectError(root);
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) throw new AiApiException("OpenRouter returned no completion");
            JsonObject choice = choices.get(0).getAsJsonObject();
            rejectError(choice);
            String finish = string(choice, "finish_reason");
            if (!"stop".equals(finish)) throw new AiApiException(
                    "OpenRouter returned an incomplete or blocked completion. Adjust the model or output limit and retry.");
            JsonObject message = choice.getAsJsonObject("message");
            if (message == null || !"assistant".equals(string(message, "role")) || !string(message, "refusal").isBlank())
                throw new AiApiException("OpenRouter refused the request");
            String text = string(message, "content");
            if (text.isBlank()) throw new AiApiException("OpenRouter returned no assistant text");
            return text;
        } catch (AiApiException e) { throw e; }
        catch (RuntimeException e) { throw new AiApiException("Invalid OpenRouter completion response"); }
    }

    public static List<ModelDescriptor> parseModels(String body) throws AiApiException {
        return parseModels(body, java.time.Instant.now());
    }

    public static List<ModelDescriptor> parseModels(String body, java.time.Instant fetchedAt) throws AiApiException {
        try {
            if (body == null || body.length() > 4 * 1024 * 1024) throw new AiApiException("Invalid OpenRouter catalog size");
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            rejectError(root);
            JsonArray data = root.getAsJsonArray("data");
            if (data == null) throw new AiApiException("OpenRouter returned no model catalog");
            var models = new LinkedHashMap<String, ModelDescriptor>();
            for (JsonElement entry : data) {
                JsonObject model = entry.getAsJsonObject();
                JsonObject architecture = model.getAsJsonObject("architecture");
                if (architecture == null || !hasText(architecture.getAsJsonArray("input_modalities"))
                        || !hasText(architecture.getAsJsonArray("output_modalities"))) continue;
                String id = string(model, "id");
                if (id.isBlank()) continue;
                String name = string(model, "name");
                JsonObject pricing = model.getAsJsonObject("pricing");
                String price = "USD/token input: " + price(pricing, "prompt") + "; output: " + price(pricing, "completion")
                        + "; cached input USD/token: " + price(pricing, "input_cache_read")
                        + "; cache write USD/token: " + price(pricing, "input_cache_write")
                        + "; USD/request: " + price(pricing, "request") + "; USD/image: " + price(pricing, "image")
                        + "; source: " + API + "models; fetched: " + fetchedAt;
                models.putIfAbsent(id, new ModelDescriptor(id, name.isBlank() ? id : name, price,
                        id.equals(AiProvider.OPENROUTER.getDefaultModel()), id, List.of(), "", CapabilitySupport.SUPPORTED));
            }
            if (models.isEmpty()) throw new AiApiException("OpenRouter returned no text models; enter a model ID manually");
            return List.copyOf(models.values());
        } catch (AiApiException e) { throw e; }
        catch (RuntimeException e) { throw new AiApiException("Invalid OpenRouter model catalog"); }
    }

    private static String price(JsonObject json, String key) {
        if (json == null) return "unknown";
        String value = string(json, key);
        try { return new java.math.BigDecimal(value).signum() < 0 ? "unknown" : value; }
        catch (NumberFormatException e) { return "unknown"; }
    }
    private static boolean hasText(JsonArray values) {
        if (values == null) return false;
        for (JsonElement value : values) if (value.isJsonPrimitive() && "text".equals(value.getAsString())) return true;
        return false;
    }
    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
    private static void rejectError(JsonObject root) throws AiApiException {
        if (root.has("error") && !root.get("error").isJsonNull()) {
            int code = 0;
            try { code = root.getAsJsonObject("error").get("code").getAsInt(); }
            catch (RuntimeException ignored) { }
            throw error(code);
        }
    }
    public static AiApiException error(int status) {
        String suggestion = switch (status) {
            case 401 -> "Check your OpenRouter API key in Settings → Tools → OpenSpec";
            case 402 -> "Check OpenRouter credits and the key's spending limit";
            case 403 -> "Check OpenRouter permissions, privacy settings and model restrictions";
            case 404 -> "Refresh OpenRouter models or enter an available model ID";
            case 408, 504 -> "OpenRouter timed out; retry when the provider is available";
            case 429 -> "OpenRouter rate limit reached; wait before retrying";
            case 400, 422 -> "Check the selected model, its context limit and supported request parameters";
            default -> status >= 500 ? "OpenRouter is unavailable; try again later" : "Check OpenRouter request and account settings";
        };
        return new AiApiException("OpenRouter API error" + (status > 0 ? " (HTTP " + status + ")" : "") + ". " + suggestion,
                status, "OpenRouter", suggestion);
    }
}
