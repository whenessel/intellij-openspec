package com.johnnyblabs.openspec.ai;

import com.google.gson.*;
import com.johnnyblabs.openspec.ai.backend.CapabilitySupport;
import com.johnnyblabs.openspec.ai.backend.ModelDescriptor;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;

/** OpenRouter's text contracts. Never reflects raw errors or credentials. */
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

    public static HttpRequest keyStatus(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Provide an OpenRouter API key");
        return HttpRequest.newBuilder(URI.create(API + "key")).header("Authorization", "Bearer " + key)
                .timeout(Duration.ofSeconds(30)).GET().build();
    }

    public static String parseKeyStatus(String body) throws AiApiException {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            rejectError(root);
            JsonObject data = root.getAsJsonObject("data");
            if (data == null) throw new IllegalArgumentException();
            String tier = data.has("is_free_tier") && data.get("is_free_tier").isJsonPrimitive()
                    && data.getAsJsonPrimitive("is_free_tier").isBoolean()
                    ? (data.get("is_free_tier").getAsBoolean() ? "free tier" : "paid tier") : "tier unknown";
            return "Key valid; " + tier + "; remaining USD limit: " + price(data, "limit_remaining")
                    + ". No inference performed.";
        } catch (AiApiException e) { throw e; }
        catch (RuntimeException e) { throw new AiApiException("Invalid OpenRouter key status response"); }
    }

    public static HttpRequest streaming(String model, String key, String prompt, int maxTokens,
                                       OpenRouterPolicy policy, JsonObject schema) {
        HttpRequest base = completion(model, key, prompt, maxTokens);
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", maxTokens);
        body.addProperty("stream", true);
        JsonObject message = new JsonObject(); message.addProperty("role", "user"); message.addProperty("content", prompt);
        JsonArray messages = new JsonArray(); messages.add(message); body.add("messages", messages);
        JsonObject routing = new JsonObject();
        JsonArray only = new JsonArray(); policy.only().forEach(only::add);
        JsonArray order = new JsonArray(); policy.order().forEach(order::add);
        if (!only.isEmpty()) routing.add("only", only);
        if (!order.isEmpty()) routing.add("order", order);
        routing.addProperty("allow_fallbacks", policy.allowFallbacks());
        routing.addProperty("data_collection", policy.dataCollection());
        routing.addProperty("zdr", policy.zdr());
        routing.addProperty("require_parameters", true);
        body.add("provider", routing);
        if (schema != null) {
            JsonObject format = new JsonObject(); format.addProperty("type", "json_schema");
            JsonObject jsonSchema = new JsonObject(); jsonSchema.addProperty("name", "openspec_result");
            jsonSchema.addProperty("strict", true); jsonSchema.add("schema", schema.deepCopy());
            format.add("json_schema", jsonSchema); body.add("response_format", format);
        }
        return HttpRequest.newBuilder(base.uri()).header("Content-Type", "application/json")
                .header("Accept", "text/event-stream").header("Authorization", "Bearer " + key)
                .timeout(Duration.ofMinutes(5)).POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
    }

    /** UTF-8 byte count is deliberately a conservative upper bound, not a model tokenizer. */
    public static int limits(String catalog, String model, String prompt, int output) throws AiApiException {
        if (output < 1 || output > 16000) throw new AiApiException("Invalid OpenRouter output limit");
        JsonObject entry = catalogEntry(catalog, model);
        if (entry == null) {
            if (prompt.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 48 * 1024)
                throw new AiApiException("Unknown model bounds; reduce context or refresh the OpenRouter catalog");
            return output;
        }
        JsonObject architecture = entry.has("architecture") && entry.get("architecture").isJsonObject() ? entry.getAsJsonObject("architecture") : null;
        if (architecture != null && (architecture.has("input_modalities") && !hasText(architecture.getAsJsonArray("input_modalities"))
                || architecture.has("output_modalities") && !hasText(architecture.getAsJsonArray("output_modalities"))))
            throw new AiApiException("Selected OpenRouter model does not support text input/output");
        int max = positive(entry, "context_length");
        JsonObject top = entry.has("top_provider") && entry.get("top_provider").isJsonObject() ? entry.getAsJsonObject("top_provider") : null;
        int routeContext = positive(top, "context_length");
        if (routeContext > 0) max = max > 0 ? Math.min(max, routeContext) : routeContext;
        int completionMax = positive(top, "max_completion_tokens");
        if (completionMax > 0) output = Math.min(output, completionMax);
        if (max > 0) {
            long remaining = (long) max - prompt.getBytes(java.nio.charset.StandardCharsets.UTF_8).length - Math.min(1024, Math.max(16, max / 8));
            if (remaining < 1) throw new AiApiException("OpenRouter model context limit exceeded; reduce reviewed context");
            output = (int) Math.min(output, remaining);
        } else if (prompt.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 48 * 1024) {
            throw new AiApiException("Unknown model context bounds; reduce context or refresh the OpenRouter catalog");
        }
        return output;
    }

    public static void requireStructured(String catalog, String model) throws AiApiException {
        JsonObject entry = catalogEntry(catalog, model);
        JsonArray parameters = entry == null ? null : entry.getAsJsonArray("supported_parameters");
        boolean format = false, structured = false;
        if (parameters != null) for (JsonElement p : parameters) {
            format |= "response_format".equals(p.getAsString()); structured |= "structured_outputs".equals(p.getAsString());
        }
        if (!format || !structured) throw new AiApiException("Selected OpenRouter model has unknown or unsupported native structured output; select a compatible model and refresh catalog");
    }
    private static JsonObject catalogEntry(String catalog, String model) throws AiApiException {
        if (catalog == null) return null;
        try {
            JsonObject root = JsonParser.parseString(catalog).getAsJsonObject(); rejectError(root);
            for (JsonElement value : root.getAsJsonArray("data")) {
                JsonObject entry = value.getAsJsonObject(); if (model.equals(string(entry, "id"))) return entry;
            }
            return null;
        } catch (AiApiException e) { throw e; }
        catch (RuntimeException e) { throw new AiApiException("Invalid OpenRouter catalog bounds"); }
    }
    private static int positive(JsonObject object, String key) {
        try { int result = object.get(key).getAsInt(); return Math.max(0, result); }
        catch (RuntimeException e) { return 0; }
    }

    public record Completion(String text, String model, String provider) { }
    public static final class StreamParser {
        private final StringBuilder text = new StringBuilder();
        private String model = "", provider = "";
        private boolean stopped, done, failed;
        public void accept(String data, java.util.function.Consumer<String> delta) throws AiApiException {
            try {
                if (failed || done) throw new AiApiException("Unexpected OpenRouter data after terminal event");
                if ("[DONE]".equals(data)) { done = true; return; }
                JsonObject root = JsonParser.parseString(data).getAsJsonObject(); rejectError(root);
                String nextModel = string(root, "model"), nextProvider = string(root, "provider");
                if (!nextModel.isBlank()) {
                    if (!model.isBlank() && !model.equals(nextModel)) throw new AiApiException("OpenRouter model changed within response");
                    model = nextModel;
                }
                if (!nextProvider.isBlank()) {
                    if (!provider.isBlank() && !provider.equals(nextProvider)) throw new AiApiException("OpenRouter provider changed within response");
                    provider = nextProvider;
                }
                JsonArray choices = root.getAsJsonArray("choices");
                if (choices == null || choices.isEmpty()) {
                    if (!root.has("usage")) throw new AiApiException("Invalid OpenRouter stream choice");
                    return;
                }
                if (choices.size() != 1) throw new AiApiException("Unexpected OpenRouter stream choices");
                JsonObject choice = choices.get(0).getAsJsonObject(); rejectError(choice);
                if (choice.has("index") && choice.get("index").getAsInt() != 0) throw new AiApiException("Invalid OpenRouter choice index");
                JsonObject content = choice.getAsJsonObject("delta");
                String piece = content == null ? "" : string(content, "content");
                if (content != null && (!string(content, "refusal").isBlank() || content.has("tool_calls")
                        || !string(content, "role").isBlank() && !"assistant".equals(string(content, "role"))))
                    throw new AiApiException("OpenRouter returned unsupported or refused output");
                if (stopped && !piece.isEmpty()) throw new AiApiException("OpenRouter content after completion");
                if (text.length() + piece.length() > 1024 * 1024) throw new AiApiException("OpenRouter output exceeds safety limit");
                if (!piece.isEmpty()) { text.append(piece); delta.accept(piece); }
                String finish = string(choice, "finish_reason");
                if (!finish.isBlank()) {
                    if (!"stop".equals(finish)) throw new AiApiException("OpenRouter stream incomplete or blocked; no result was applied");
                    stopped = true;
                }
            } catch (AiApiException e) { failed = true; throw e; }
            catch (com.intellij.openapi.progress.ProcessCanceledException e) { failed = true; throw e; }
            catch (RuntimeException e) { failed = true; throw new AiApiException("Invalid OpenRouter stream response"); }
        }
        public Completion finish() throws AiApiException {
            if (failed || !done || !stopped || text.toString().isBlank() || model.isBlank())
                throw new AiApiException("OpenRouter stream disconnected or incomplete; no result was applied");
            return new Completion(text.toString(), safeMetadata(model), safeMetadata(provider));
        }
    }
    private static String safeMetadata(String value) {
        return value.replaceAll("[\\p{Cntrl}<>]", "").substring(0, Math.min(value.replaceAll("[\\p{Cntrl}<>]", "").length(), 200));
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
                        + "; context tokens: " + (positive(model, "context_length") > 0 ? positive(model, "context_length") : "unknown")
                        + "; output tokens: " + (positive(model.getAsJsonObject("top_provider"), "max_completion_tokens") > 0 ? positive(model.getAsJsonObject("top_provider"), "max_completion_tokens") : "unknown")
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
            case 404 -> "Check model availability and allowed provider/privacy requirements; refresh the catalog";
            case 408, 504 -> "OpenRouter timed out; retry when the provider is available";
            case 429 -> "OpenRouter rate limit reached; wait before retrying";
            case 400, 422 -> "Check the selected model, its context limit and supported request parameters";
            default -> status >= 500 ? "OpenRouter has no available route; check provider/privacy and required-parameter constraints or try again later" : "Check OpenRouter request and account settings";
        };
        return new AiApiException("OpenRouter API error" + (status > 0 ? " (HTTP " + status + ")" : "") + ". " + suggestion,
                status, "OpenRouter", suggestion);
    }
}
