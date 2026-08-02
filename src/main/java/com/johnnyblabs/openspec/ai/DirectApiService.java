package com.johnnyblabs.openspec.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.ArtifactInstruction;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service(Service.Level.PROJECT)
public final class DirectApiService {
    private static final Logger LOG = Logger.getInstance(DirectApiService.class);
    private static final Duration TIMEOUT = Duration.ofMinutes(5);
    private static final int MAX_TOKENS = 8192;

    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final Project project;

    public DirectApiService(Project project) {
        this.project = project;
    }

    private HttpClient createHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Checks if a direct API provider is configured with credentials.
     */
    public boolean isConfigured() {
        AiProvider provider = getProvider();
        return provider != AiProvider.NONE && AiCredentialStore.hasApiKey(provider);
    }

    /**
     * Generates artifact content using the configured AI provider.
     */
    public String generate(ArtifactInstruction instruction) throws AiApiException {
        AiProvider provider = getProvider();
        if (provider == AiProvider.NONE) {
            throw new AiApiException("No AI provider configured");
        }

        String apiKey = AiCredentialStore.getApiKey(provider);
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiApiException("No API key configured for " + provider.getDisplayName());
        }

        String model = getModel(provider);
        String prompt = instruction.buildPrompt();

        return switch (provider) {
            case CLAUDE -> callClaude(apiKey, model, prompt);
            case OPENAI -> callOpenAi(apiKey, model, prompt);
            case GEMINI -> callGemini(apiKey, model, prompt);
            case NONE -> throw new AiApiException("No AI provider configured");
        };
    }

    /**
     * Sends a raw prompt to the configured AI provider and returns the response.
     * Used for explore and other non-artifact interactions.
     */
    public String generateRaw(String prompt) throws AiApiException {
        AiProvider provider = getProvider();
        if (provider == AiProvider.NONE) {
            throw new AiApiException("No AI provider configured");
        }

        String apiKey = AiCredentialStore.getApiKey(provider);
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiApiException("No API key configured for " + provider.getDisplayName());
        }

        String model = getModel(provider);

        return switch (provider) {
            case CLAUDE -> callClaude(apiKey, model, prompt);
            case OPENAI -> callOpenAi(apiKey, model, prompt);
            case GEMINI -> callGemini(apiKey, model, prompt);
            case NONE -> throw new AiApiException("No AI provider configured");
        };
    }

    /**
     * Tests the API connection with a simple request.
     */
    public String testConnection() throws AiApiException {
        AiProvider provider = getProvider();
        String apiKey = AiCredentialStore.getApiKey(provider);
        String model = getModel(provider);
        return testConnection(provider, apiKey, model);
    }

    /**
     * Tests the API connection using explicit provider, key, and model values.
     * Used by the settings panel to test before Apply is clicked.
     */
    public String testConnection(AiProvider provider, String apiKey, String model) throws AiApiException {
        if (provider == AiProvider.NONE) {
            throw new AiApiException("No AI provider selected");
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new AiApiException("No API key provided for " + provider.getDisplayName());
        }

        if (model == null || model.isBlank()) {
            model = provider.getDefaultModel();
        }

        String testPrompt = "Respond with exactly: OK";

        try {
            String response = switch (provider) {
                case CLAUDE -> callClaude(apiKey, model, testPrompt);
                case OPENAI -> callOpenAi(apiKey, model, testPrompt);
                case GEMINI -> callGemini(apiKey, model, testPrompt);
                case NONE -> throw new AiApiException("No provider");
            };
            return "Connected to " + provider.getDisplayName() + " (" + model + ")";
        } catch (AiApiException e) {
            throw new AiApiException("Connection test failed: " + e.getMessage(), e);
        }
    }

    /**
     * The required Anthropic API version. This is a fixed, enumerated header value — feature
     * opt-ins ship via {@code anthropic-beta}, not new version dates — so it is stable.
     */
    static final String ANTHROPIC_VERSION = "2023-06-01";

    /**
     * Builds a Claude Messages API request. Extracted (mirroring {@link #buildGeminiRequest})
     * so the required {@code anthropic-version} header value is assertable in a unit test.
     */
    static HttpRequest buildClaudeRequest(String model, String apiKey, String prompt) {
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

    /**
     * Extracts the generated text from a Claude Messages API response body. Extracted for
     * contract-testing against captured provider example output.
     */
    static String parseClaudeResponse(String body) throws AiApiException {
        JsonObject responseJson = JsonParser.parseString(body).getAsJsonObject();
        JsonArray content = responseJson.getAsJsonArray("content");
        if (content != null && content.size() > 0) {
            return content.get(0).getAsJsonObject().get("text").getAsString();
        }
        throw new AiApiException("Empty response from Claude API");
    }

    private String callClaude(String apiKey, String model, String prompt) throws AiApiException {
        try {
            HttpRequest request = buildClaudeRequest(model, apiKey, prompt);
            HttpResponse<String> response = createHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw buildApiError("Claude", response.statusCode(), response.body());
            }
            return parseClaudeResponse(response.body());
        } catch (AiApiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiApiException("Claude API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the token-limit parameter name for the given OpenAI model. The reasoning family
     * ({@code o1}/{@code o3}/{@code o4}/{@code gpt-5}) rejects {@code max_tokens} and requires
     * {@code max_completion_tokens}; non-reasoning chat models use {@code max_tokens}.
     */
    static String openAiTokenParam(String model) {
        if (model == null) return "max_tokens";
        for (String prefix : REASONING_MODEL_PREFIXES) {
            if (model.startsWith(prefix)) return "max_completion_tokens";
        }
        return "max_tokens";
    }

    private static final java.util.List<String> REASONING_MODEL_PREFIXES =
            java.util.List.of("o1", "o3", "o4", "gpt-5");

    /**
     * Builds an OpenAI Chat Completions request. Extracted (mirroring {@link #buildGeminiRequest})
     * so the model-dependent token-limit parameter placement is assertable.
     */
    static HttpRequest buildOpenAiRequest(String model, String apiKey, String prompt) {
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

    /**
     * Extracts the generated text from an OpenAI Chat Completions response body. Extracted for
     * contract-testing against captured provider example output.
     */
    static String parseOpenAiResponse(String body) throws AiApiException {
        JsonObject responseJson = JsonParser.parseString(body).getAsJsonObject();
        JsonArray choices = responseJson.getAsJsonArray("choices");
        if (choices != null && choices.size() > 0) {
            return choices.get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();
        }
        throw new AiApiException("Empty response from OpenAI API");
    }

    private String callOpenAi(String apiKey, String model, String prompt) throws AiApiException {
        try {
            HttpRequest request = buildOpenAiRequest(model, apiKey, prompt);
            HttpResponse<String> response = createHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw buildApiError("OpenAI", response.statusCode(), response.body());
            }
            return parseOpenAiResponse(response.body());
        } catch (AiApiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiApiException("OpenAI API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Builds a Gemini generateContent request. Extracted for testability — the API key
     * is sent via the x-goog-api-key header (Google's recommended pattern) rather than
     * embedded in the URL query string, so that keys do not leak into request logs.
     */
    static HttpRequest buildGeminiRequest(String model, String apiKey, String prompt) {
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

    /**
     * Extracts the generated text from a Gemini generateContent response body. Extracted for
     * contract-testing against captured provider example output.
     */
    static String parseGeminiResponse(String body) throws AiApiException {
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

    private String callGemini(String apiKey, String model, String prompt) throws AiApiException {
        try {
            HttpRequest request = buildGeminiRequest(model, apiKey, prompt);
            HttpResponse<String> response = createHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw buildApiError("Gemini", response.statusCode(), response.body());
            }
            return parseGeminiResponse(response.body());
        } catch (AiApiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiApiException("Gemini API call failed: " + e.getMessage(), e);
        }
    }

    private AiApiException buildApiError(String providerName, int statusCode, String responseBody) {
        LOG.warn(providerName + " API error (HTTP " + statusCode + "): " + responseBody);
        String errorMessage = extractErrorMessage(responseBody);
        String suggestion = suggestionForStatus(statusCode);
        String userMessage = providerName + " API error: " + errorMessage;
        return new AiApiException(userMessage, statusCode, providerName, suggestion);
    }

    private static String extractErrorMessage(String responseBody) {
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

    private static String suggestionForStatus(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return "Check your API key in Settings \u2192 Tools \u2192 OpenSpec";
        } else if (statusCode == 429) {
            return "Rate limited \u2014 wait a moment and retry";
        } else if (statusCode >= 500) {
            return "The provider may be experiencing issues \u2014 try again shortly";
        }
        return null;
    }

    private AiProvider getProvider() {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        return AiProvider.fromString(settings.getAiProvider());
    }

    private String getModel(AiProvider provider) {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        String model = settings.getAiModel();
        if (model == null || model.isBlank()) {
            return provider.getDefaultModel();
        }
        return model;
    }
}
