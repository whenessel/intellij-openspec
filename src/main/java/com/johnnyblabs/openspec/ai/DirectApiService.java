package com.johnnyblabs.openspec.ai;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.ArtifactInstruction;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service(Service.Level.PROJECT)
public final class DirectApiService {
    private static final Logger LOG = Logger.getInstance(DirectApiService.class);
    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    private final Project project;
    private final HttpClient injectedClient;
    private final ThreadLocal<java.util.function.BooleanSupplier> cancellation = new ThreadLocal<>();
    private volatile String openRouterCatalog;
    private volatile long catalogFetchedAt;

    public String generateRaw(String prompt, java.util.function.BooleanSupplier canceled) throws AiApiException {
        AiProvider provider = getProvider();
        if (provider == AiProvider.NONE) throw new AiApiException("No AI provider configured");
        return generateRaw(prompt, provider, getModel(provider), canceled);
    }

    /** Uses the exact provider/model reviewed by the caller; never resolves mutable settings again. */
    public String generateRaw(String prompt, AiProvider provider, String model,
                              java.util.function.BooleanSupplier canceled) throws AiApiException {
        cancellation.set(canceled);
        try { return generateCapturedRaw(prompt, provider, model); } finally { cancellation.remove(); }
    }

    private HttpResponse<String> sendRequest(HttpRequest request) throws Exception {
        checkRequestCanceled();
        var future = createHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString());
        long deadline = System.nanoTime() + request.timeout().orElse(TIMEOUT).toNanos();
        try {
            while (true) {
                checkRequestCanceled();
                if (System.nanoTime() > deadline) throw new java.util.concurrent.TimeoutException("AI request timed out");
                try { return future.get(100, java.util.concurrent.TimeUnit.MILLISECONDS); }
                catch (java.util.concurrent.TimeoutException ignored) { /* poll cancellation */ }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new com.intellij.openapi.progress.ProcessCanceledException();
        } finally { if (!future.isDone()) future.cancel(true); }
    }

    private void checkRequestCanceled() {
        com.intellij.openapi.progress.ProgressManager.checkCanceled();
        var token = cancellation.get();
        if (Thread.currentThread().isInterrupted() || token != null && token.getAsBoolean())
            throw new com.intellij.openapi.progress.ProcessCanceledException();
    }

    public DirectApiService(Project project) { this(project, null); }

    DirectApiService(Project project, HttpClient client) {
        this.project = project;
        this.injectedClient = client;
        // Warm the has-key cache off the EDT so isConfigured() (reached from ~8 UI paths) never does a
        // synchronous PasswordSafe read on the EDT. Guard on a live Application so plain unit tests
        // (no IntelliJ Application) can construct the service.
        var app = ApplicationManager.getApplication();
        if (app != null) {
            app.executeOnPooledThread(() -> {
                AiProvider p = getProvider();
                if (p != AiProvider.NONE) {
                    AiCredentialStore.hasApiKeyCached(p);
                }
            });
        }
    }

    private HttpClient createHttpClient() {
        if (injectedClient != null) return injectedClient;
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30)).followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * Checks if a direct API provider is configured with credentials.
     */
    public boolean isConfigured() {
        return isConfigured(getProvider());
    }

    /** Readiness for a captured provider, independent of a later settings selection. */
    public boolean isConfigured(AiProvider provider) {
        // hasApiKeyCached is EDT-safe (a cached map read; warmed off-EDT in the constructor).
        return provider != null && provider != AiProvider.NONE && AiCredentialStore.hasApiKeyCached(provider);
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
            case OPENROUTER -> callOpenRouter(apiKey, model, prompt, 4096);
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
        return generateCapturedRaw(prompt, provider, getModel(provider));
    }

    private String generateCapturedRaw(String prompt, AiProvider provider, String model) throws AiApiException {
        if (provider == null || provider == AiProvider.NONE) throw new AiApiException("No AI provider configured");

        String apiKey = AiCredentialStore.getApiKey(provider);
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiApiException("No API key configured for " + provider.getDisplayName());
        }

        if (model == null || model.isBlank()) model = provider.getDefaultModel();

        return switch (provider) {
            case CLAUDE -> callClaude(apiKey, model, prompt);
            case OPENAI -> callOpenAi(apiKey, model, prompt);
            case GEMINI -> callGemini(apiKey, model, prompt);
            case OPENROUTER -> callOpenRouter(apiKey, model, prompt, 4096);
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
                case OPENROUTER -> callOpenRouter(apiKey, model, testPrompt, 256);
                case NONE -> throw new AiApiException("No provider");
            };
            return "Connected to " + provider.getDisplayName() + " (" + model + ")";
        } catch (AiApiException e) {
            throw new AiApiException("Connection test failed: " + e.getMessage(), e);
        }
    }

    /** Settings test uses the same immutable route/privacy requirements and cancelable stream as execution. */
    public String testConnection(AiProvider provider, String key, String model, OpenRouterPolicy policy,
                                 java.util.function.BooleanSupplier canceled) throws AiApiException {
        if (provider != AiProvider.OPENROUTER) {
            cancellation.set(canceled);
            try { return testConnection(provider, key, model); } finally { cancellation.remove(); }
        }
        String selected = model == null || model.isBlank() ? provider.getDefaultModel() : model;
        var capped = new OpenRouterPolicy(policy.only(), policy.order(), policy.allowFallbacks(), policy.dataCollection(),
                policy.zdr(), Math.min(256, policy.maxOutputTokens()));
        var result = streamOpenRouter(new com.johnnyblabs.openspec.ai.backend.AiRequest("Respond with exactly: OK", selected,
                java.nio.file.Path.of("."), Duration.ofMinutes(5)), selected, capped, canceled, ignored -> {}, key);
        return "Connected to OpenRouter (" + result.model() + "; provider: " + (result.provider().isBlank() ? "not reported" : result.provider()) + ")";
    }

    /**
     * The required Anthropic API version. This is a fixed, enumerated header value — feature
     * opt-ins ship via {@code anthropic-beta}, not new version dates — so it is stable.
     */
    static final String ANTHROPIC_VERSION = DirectApiProtocol.ANTHROPIC_VERSION;

    /**
     * Builds a Claude Messages API request. Extracted (mirroring {@link #buildGeminiRequest})
     * so the required {@code anthropic-version} header value is assertable in a unit test.
     */
    static HttpRequest buildClaudeRequest(String model, String apiKey, String prompt) {
        return DirectApiProtocol.buildClaudeRequest(model, apiKey, prompt);
    }

    /**
     * Extracts the generated text from a Claude Messages API response body. Extracted for
     * contract-testing against captured provider example output.
     */
    static String parseClaudeResponse(String body) throws AiApiException {
        return DirectApiProtocol.parseClaudeResponse(body);
    }

    private String callClaude(String apiKey, String model, String prompt) throws AiApiException {
        try {
            HttpRequest request = buildClaudeRequest(model, apiKey, prompt);
            HttpResponse<String> response = sendRequest(request);

            if (response.statusCode() != 200) {
                throw buildApiError("Claude", response.statusCode(), response.body());
            }
            return parseClaudeResponse(response.body());
        } catch (AiApiException e) {
            throw e;
        } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
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
        return DirectApiProtocol.openAiTokenParam(model);
    }

    /**
     * Builds an OpenAI Chat Completions request. Extracted (mirroring {@link #buildGeminiRequest})
     * so the model-dependent token-limit parameter placement is assertable.
     */
    static HttpRequest buildOpenAiRequest(String model, String apiKey, String prompt) {
        return DirectApiProtocol.buildOpenAiRequest(model, apiKey, prompt);
    }

    /**
     * Extracts the generated text from an OpenAI Chat Completions response body. Extracted for
     * contract-testing against captured provider example output.
     */
    static String parseOpenAiResponse(String body) throws AiApiException {
        return DirectApiProtocol.parseOpenAiResponse(body);
    }

    private String callOpenAi(String apiKey, String model, String prompt) throws AiApiException {
        try {
            HttpRequest request = buildOpenAiRequest(model, apiKey, prompt);
            HttpResponse<String> response = sendRequest(request);

            if (response.statusCode() != 200) {
                throw buildApiError("OpenAI", response.statusCode(), response.body());
            }
            return parseOpenAiResponse(response.body());
        } catch (AiApiException e) {
            throw e;
        } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
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
        return DirectApiProtocol.buildGeminiRequest(model, apiKey, prompt);
    }

    /**
     * Extracts the generated text from a Gemini generateContent response body. Extracted for
     * contract-testing against captured provider example output.
     */
    static String parseGeminiResponse(String body) throws AiApiException {
        return DirectApiProtocol.parseGeminiResponse(body);
    }

    private String callGemini(String apiKey, String model, String prompt) throws AiApiException {
        try {
            HttpRequest request = buildGeminiRequest(model, apiKey, prompt);
            HttpResponse<String> response = sendRequest(request);

            if (response.statusCode() != 200) {
                throw buildApiError("Gemini", response.statusCode(), response.body());
            }
            return parseGeminiResponse(response.body());
        } catch (AiApiException e) {
            throw e;
        } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            throw new AiApiException("Gemini API call failed: " + e.getMessage(), e);
        }
    }

    private String callOpenRouter(String apiKey, String model, String prompt, int maxTokens) throws AiApiException {
        try {
            var response = sendRequest(OpenRouterProtocol.completion(model, apiKey, prompt, maxTokens));
            checkRequestCanceled();
            if (response.statusCode() != 200) throw OpenRouterProtocol.error(response.statusCode());
            return OpenRouterProtocol.parseCompletion(response.body());
        } catch (AiApiException | com.intellij.openapi.progress.ProcessCanceledException e) { throw e; }
        catch (Exception e) { throw new AiApiException("OpenRouter request failed. Check network/proxy access to openrouter.ai and retry."); }
    }

    public OpenRouterProtocol.Completion generateOpenRouter(
            com.johnnyblabs.openspec.ai.backend.AiRequest request, String model, OpenRouterPolicy policy,
            java.util.function.BooleanSupplier canceled, java.util.function.Consumer<String> delta) throws AiApiException {
        String key = AiCredentialStore.getApiKey(AiProvider.OPENROUTER);
        return streamOpenRouter(request, model, policy, canceled, delta, key);
    }
    private OpenRouterProtocol.Completion streamOpenRouter(
            com.johnnyblabs.openspec.ai.backend.AiRequest request, String model, OpenRouterPolicy policy,
            java.util.function.BooleanSupplier canceled, java.util.function.Consumer<String> delta, String key) throws AiApiException {
        cancellation.set(canceled);
        Duration timeout = request.timeout().compareTo(TIMEOUT) < 0 ? request.timeout() : TIMEOUT;
        long started = System.nanoTime();
        try {
            if (key == null || key.isBlank()) throw new AiApiException("No OpenRouter API key configured");
            checkRequestCanceled();
            if (openRouterCatalog == null || System.nanoTime() - catalogFetchedAt > Duration.ofMinutes(5).toNanos()) {
                try { refreshOpenRouterModels(canceled, timeout.compareTo(Duration.ofSeconds(30)) < 0 ? timeout : Duration.ofSeconds(30)); }
                catch (AiApiException ignored) { /* Manual IDs remain usable with conservative unknown bounds. */ }
                finally { cancellation.set(canceled); }
            }
            boolean structured = request.requiredCapabilities().contains(com.johnnyblabs.openspec.ai.backend.BackendCapability.STRUCTURED_OUTPUT);
            if (structured) {
                if (request.outputSchema() == null) throw new AiApiException("Native structured output requires a schema");
                OpenRouterProtocol.requireStructured(openRouterCatalog, model);
            }
            int tokens = OpenRouterProtocol.limits(openRouterCatalog, model, request.prompt(), policy.maxOutputTokens());
            Duration remaining = timeout.minusNanos(System.nanoTime() - started);
            if (remaining.isNegative() || remaining.isZero()) throw new AiApiException("OpenRouter request timed out before inference");
            return new OpenRouterTransport(createHttpClient()).stream(
                    OpenRouterProtocol.streaming(model, key, request.prompt(), tokens, policy, structured ? request.outputSchema() : null),
                    remaining, canceled, delta);
        } finally { cancellation.remove(); }
    }

    public String checkOpenRouterKeyStatus(String key, java.util.function.BooleanSupplier canceled) throws AiApiException {
        cancellation.set(canceled);
        try {
            var response = sendRequest(OpenRouterProtocol.keyStatus(key));
            checkRequestCanceled();
            if (response.statusCode() != 200) throw OpenRouterProtocol.error(response.statusCode());
            return OpenRouterProtocol.parseKeyStatus(response.body());
        } catch (AiApiException | com.intellij.openapi.progress.ProcessCanceledException e) { throw e; }
        catch (Exception e) { throw new AiApiException("OpenRouter key status failed. Check network/proxy access to openrouter.ai."); }
        finally { cancellation.remove(); }
    }

    /** Explicit public catalog refresh. Must run off EDT. No key or inference request. */
    public java.util.List<com.johnnyblabs.openspec.ai.backend.ModelDescriptor> refreshOpenRouterModels(
            java.util.function.BooleanSupplier canceled) throws AiApiException {
        return refreshOpenRouterModels(canceled, Duration.ofSeconds(30));
    }
    private java.util.List<com.johnnyblabs.openspec.ai.backend.ModelDescriptor> refreshOpenRouterModels(
            java.util.function.BooleanSupplier canceled, Duration timeout) throws AiApiException {
        cancellation.set(canceled);
        try {
            var response = sendRequest(HttpRequest.newBuilder(OpenRouterProtocol.models().uri()).timeout(timeout).GET().build());
            checkRequestCanceled();
            if (response.statusCode() != 200) throw OpenRouterProtocol.error(response.statusCode());
            var models = OpenRouterProtocol.parseModels(response.body());
            openRouterCatalog = response.body(); catalogFetchedAt = System.nanoTime();
            return models;
        } catch (AiApiException | com.intellij.openapi.progress.ProcessCanceledException e) { throw e; }
        catch (Exception e) { throw new AiApiException("OpenRouter catalog refresh failed. Check network/proxy access to openrouter.ai; manual model IDs remain available."); }
        finally { cancellation.remove(); }
    }

    private AiApiException buildApiError(String providerName, int statusCode, String responseBody) {
        LOG.warn(providerName + " API error (HTTP " + statusCode + ")");
        String errorMessage = extractErrorMessage(responseBody);
        String suggestion = suggestionForStatus(statusCode);
        String userMessage = providerName + " API error: " + errorMessage;
        return new AiApiException(userMessage, statusCode, providerName, suggestion);
    }

    private static String extractErrorMessage(String responseBody) {
        return DirectApiProtocol.extractErrorMessage(responseBody);
    }

    private static String suggestionForStatus(int statusCode) {
        return DirectApiProtocol.suggestionForStatus(statusCode);
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
