package com.johnnyblabs.openspec.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.backend.*;
import com.johnnyblabs.openspec.ai.routing.AiRoutingPolicy.*;
import com.johnnyblabs.openspec.services.DeliveryMethodResolver;
import com.johnnyblabs.openspec.ai.safety.ContextReviewService;
import com.johnnyblabs.openspec.ai.safety.SafeArtifactService;
import com.johnnyblabs.openspec.model.ArtifactInstruction;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Shared execution boundary. Manual delivery is resolved before reaching this service. */
public final class AiExecutionService implements Disposable {
    private final Project project;
    private final AtomicReference<AtomicBoolean> active = new AtomicReference<>();
    private final AtomicReference<AtomicBoolean> activeExplore = new AtomicReference<>();
    private final AtomicBoolean probing = new AtomicBoolean();
    private final AtomicReference<CodexAppServerBackend> exploreBackend = new AtomicReference<>();
    private final Object exploreLifecycle = new Object();
    private final java.util.concurrent.atomic.AtomicLong exploreEpoch = new java.util.concurrent.atomic.AtomicLong();
    private volatile String exploreExecutable = "";
    private volatile BackendStatus codexStatus = new BackendStatus(false, "unknown", "Check Codex status in Settings", "");
    private volatile String statusExecutable = "";
    private volatile boolean disposed;

    public AiExecutionService(Project project) { this.project = project; }
    public static AiExecutionService getInstance(Project project) { return project.getService(AiExecutionService.class); }

    /** Cached only: safe for AnAction.update and UI rendering. */
    public boolean isConfigured() { return cachedBackendReadiness().available(); }

    /** Readiness queries never start I/O; refresh is an explicit settings action. */
    public BackendReadiness cachedBackendReadiness() {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        if ("REST".equals(settings.getAiBackend())) {
            DirectApiService rest = project.getService(DirectApiService.class);
            return new BackendReadiness(rest != null && rest.isConfigured(), "REST API configuration");
        }
        if (!"LOCAL_CODEX".equals(settings.getAiBackend())) return new BackendReadiness(false, "Unsupported AI backend");
        if (!settings.getCodexExecutable().equals(statusExecutable)) return new BackendReadiness(false, "Refresh Codex status in Settings");
        return new BackendReadiness(codexStatus.available(), codexStatus.detail());
    }

    private RoutingSnapshot currentRoute() {
        DeliveryMethodResolver resolver = project.getService(DeliveryMethodResolver.class);
        if (resolver != null) return resolver.resolveSnapshot(null);
        // Keeps isolated service tests viable; production always registers the shared resolver.
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        DeliveryMode mode = DeliveryMode.DIRECT_API;
        try { if (!settings.getPreferredDeliveryMethod().isBlank()) mode = DeliveryMode.valueOf(settings.getPreferredDeliveryMethod()); }
        catch (IllegalArgumentException ignored) { }
        BackendSelection backend = configuredSelection(settings);
        return new RoutingSnapshot(mode, backend, cachedBackendReadiness(), Source.SAVED_PREFERENCE, "Selected AI backend");
    }

    public String getBackendLabel() {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        if ("LOCAL_CODEX".equals(settings.getAiBackend())) {
            String model = settings.getCodexModel().isBlank() ? "account default" : settings.getCodexModel();
            String effort = settings.getCodexReasoningEffort().isBlank() ? "" : " · effort: " + settings.getCodexReasoningEffort();
            return "Local Codex · " + model + effort + " · " + billingLabel(codexStatus.authMode());
        }
        if ("REST".equals(settings.getAiBackend())) {
            AiProvider provider = AiProvider.fromString(settings.getAiProvider());
            String model = settings.getAiModel().isBlank() ? provider.getDefaultModel() : settings.getAiModel();
            return provider.getDisplayName() + " · " + model + " · API billing";
        }
        return "Unsupported backend: " + settings.getAiBackend();
    }

    public static String billingLabel(String mode) {
        if ("chatgpt".equals(mode)) return "ChatGPT plan usage (online; limits apply)";
        if ("apikey".equals(mode)) return "API billing";
        return "Authentication/billing unknown";
    }

    public void refreshStatus() {
        if (disposed || ApplicationManager.getApplication() == null || !probing.compareAndSet(false, true)) return;
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        String executable = settings.getCodexExecutable();
        CancellationToken token = () -> disposed || !executable.equals(settings.getCodexExecutable());
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                BackendStatus status = new CodexAppServerBackend(executable).probe(token);
                if (!token.isCancelled()) { codexStatus = status; statusExecutable = executable; }
            } catch (AiApiException ex) {
                if (!(ex instanceof AiOperationCancelledException) && !token.isCancelled()) {
                    codexStatus = new BackendStatus(false, "unknown", ex.getMessage(), "");
                    statusExecutable = executable;
                }
            } finally { probing.set(false); }
        });
    }

    public String generate(ArtifactInstruction instruction) throws AiApiException {
        return execute(artifactPrompt(instruction), null, ignored -> {});
    }

    /** Snapshots targets before inference so concurrent edits cannot be overwritten after preview. */
    public List<Path> generateAndApply(ArtifactInstruction instruction) throws AiApiException, IOException {
        return generateAndApply(instruction, currentRoute());
    }
    public List<Path> generateAndApply(ArtifactInstruction instruction, RoutingSnapshot route) throws AiApiException, IOException {
        requireIntegrated(route);
        AtomicBoolean canceled = reserveRun();
        try {
            SafeArtifactService writer = project.getService(SafeArtifactService.class);
            var snapshot = writer.begin(instruction);
            String result = execute(artifactPrompt(instruction, snapshot, contextBudget(OpenSpecSettings.getInstance(project))), artifactSchema(instruction.artifactId()), ignored -> {}, canceled, null, -1, route);
            ProgressManager.checkCanceled();
            return writer.apply(writer.prepare(snapshot, result), () -> canceled.get() || disposed);
        } finally { active.compareAndSet(canceled, null); }
    }

    public String generateRaw(String prompt) throws AiApiException { return generateRaw(prompt, ignored -> {}); }
    public String generateRaw(String prompt, Consumer<String> onDelta) throws AiApiException {
        return generateRaw(prompt, onDelta, currentRoute());
    }
    public String generateRaw(String prompt, Consumer<String> onDelta, RoutingSnapshot route) throws AiApiException {
        requireIntegrated(route);
        AtomicBoolean canceled = reserveRun();
        try { return execute(prompt, null, onDelta, canceled, null, -1, route); }
        finally { active.compareAndSet(canceled, null); }
    }
    private static void requireIntegrated(RoutingSnapshot route) throws AiApiException {
        if (route == null || !route.executesBackend() || route.backend() == null)
            throw new AiApiException("Manual delivery selected; no AI request was sent.");
    }

    /** Explore history is held by one CLI-owned thread, isolated from generation and Verify. */
    public String generateExplore(String prompt, String contextScope, Consumer<String> onDelta) throws AiApiException {
        return generateExplore(prompt, contextScope, onDelta, currentRoute());
    }
    public String generateExplore(String prompt, String contextScope, Consumer<String> onDelta, RoutingSnapshot route) throws AiApiException {
        requireIntegrated(route);
        long epoch = exploreEpoch.get();
        AtomicBoolean canceled = reserveRun();
        activeExplore.set(canceled);
        if (epoch != exploreEpoch.get()) canceled.set(true);
        try { return execute(prompt, null, onDelta, canceled, contextScope, epoch, route); }
        finally { activeExplore.compareAndSet(canceled, null); active.compareAndSet(canceled, null); }
    }

    public void resetExploreConversation() {
        CodexAppServerBackend previous;
        synchronized (exploreLifecycle) {
            exploreEpoch.incrementAndGet();
            previous = exploreBackend.getAndSet(null);
        }
        cancelExplore();
        if (previous != null) {
            var app = ApplicationManager.getApplication();
            if (app != null && app.isDispatchThread()) app.executeOnPooledThread(previous::closeConversation);
            else previous.closeConversation();
        }
    }

    private AtomicBoolean reserveRun() throws AiApiException {
        AtomicBoolean canceled = new AtomicBoolean();
        if (disposed) throw new ProcessCanceledException();
        if (!active.compareAndSet(null, canceled)) throw new AiApiException("An AI request is already active. Stop it before starting another.");
        return canceled;
    }

    private String execute(String prompt, JsonObject outputSchema, Consumer<String> onDelta) throws AiApiException {
        AtomicBoolean canceled = reserveRun();
        try { return execute(prompt, outputSchema, onDelta, canceled); }
        finally { active.compareAndSet(canceled, null); }
    }

    private String execute(String prompt, JsonObject outputSchema, Consumer<String> onDelta, AtomicBoolean canceled) throws AiApiException {
        return execute(prompt, outputSchema, onDelta, canceled, null, -1, currentRoute());
    }

    private String execute(String prompt, JsonObject outputSchema, Consumer<String> onDelta, AtomicBoolean canceled,
                           String contextScope, long conversationEpoch, RoutingSnapshot route) throws AiApiException {
        requireIntegrated(route);
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        CancellationToken token = () -> canceled.get() || disposed || indicator != null && indicator.isCanceled()
                || contextScope != null && conversationEpoch != exploreEpoch.get();
        try {
            OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
            BackendSelection configuredAtStart = configuredSelection(settings);
            String deliveryAtStart = settings.getPreferredDeliveryMethod();
            BackendSelection destination = route.backend();
            String backend = destination.backendId();
            String executable = destination.executable();
            String codexModel = destination.model();
            String codexEffort = destination.reasoningEffort();
            String restProvider = destination.provider();
            String restModel = destination.model();
            int timeout = settings.getCodexTimeoutSeconds();
            int budget = settings.getAiContextMaxBytes();
            int tokenBudget = settings.getAiContextMaxInputTokens();
            if (!"REST".equals(backend) && !"LOCAL_CODEX".equals(backend)) throw new AiApiException("Unsupported AI backend; choose a backend in Settings.");
            if ("REST".equals(backend) && !codexEffort.isBlank()) throw new AiApiException("Reasoning effort override is unsupported by the existing REST adapter.");
            CodexAppServerBackend local = null;
            BackendStatus executionStatus = null;
            ModelSelection.Selection selection = null;
            if ("LOCAL_CODEX".equals(backend)) {
                local = new CodexAppServerBackend(executable);
                CancellationToken discoveryToken = () -> token.isCancelled()
                        || !configuredAtStart.equals(configuredSelection(settings))
                        || !java.util.Objects.equals(deliveryAtStart, settings.getPreferredDeliveryMethod());
                executionStatus = local.probe(discoveryToken);
                if (discoveryToken.isCancelled()) throw new ProcessCanceledException();
                codexStatus = executionStatus;
                statusExecutable = executable;
                if (!executionStatus.available()) throw new AiApiException(executionStatus.detail());
                if ("apikey".equals(executionStatus.authMode()) && !settings.isCodexApiBillingAcknowledged()) {
                    throw new AiApiException("Codex uses API billing. Acknowledge API billing in Settings before inference.");
                }
                if (!"chatgpt".equals(executionStatus.authMode()) && !"apikey".equals(executionStatus.authMode())) {
                    throw new AiApiException("Codex authentication mode is unknown or unsupported; check CLI login.");
                }
                selection = ModelSelection.negotiate(local.catalog(executionStatus, false, discoveryToken), codexModel, codexEffort, false);
                if (discoveryToken.isCancelled()) throw new ProcessCanceledException();
            }
            String expectedAuth = executionStatus == null ? "apikey" : executionStatus.authMode();
            String expectedAccount = executionStatus == null ? null : executionStatus.accountFingerprint();
            String reviewedDestination = local == null
                    ? AiProvider.fromString(restProvider).getDisplayName() + " · "
                      + (restModel.isBlank() ? AiProvider.fromString(restProvider).getDefaultModel() : restModel) + " · API billing"
                      + (AiProvider.fromString(restProvider) == AiProvider.OPENROUTER
                         ? " · Context is sent to OpenRouter and its downstream model provider; their data policies apply" : "")
                    : "Local Codex · " + selection.wireModel() + (selection.defaultSelection() ? " (account default)" : "")
                      + " · effort: " + (codexEffort.isBlank() ? "CLI default" : codexEffort) + " · " + billingLabel(expectedAuth);
            CodexAppServerBackend previousConversation = exploreBackend.get();
            String historyNotice = contextScope == null || local == null ? "" : "\nExplore conversation: prior reviewed turns are retained by Codex. Clear only hides the display; New conversation discards reuse.\nCLI stores this conversation for resume. Native tools remain disabled."
                    + "\n" + (previousConversation == null ? "No prior thread. Input-token cap: " + tokenBudget
                    : previousConversation.conversationBudgetSummary());
            try (var context = project.getService(ContextReviewService.class).review(prompt, reviewedDestination + historyNotice, budget)) {
                if (canceled.get() || disposed || indicator != null && indicator.isCanceled()) throw new ProcessCanceledException();
                if (!configuredAtStart.equals(configuredSelection(settings))
                        || !java.util.Objects.equals(deliveryAtStart, settings.getPreferredDeliveryMethod())
                        || budget != settings.getAiContextMaxBytes() || tokenBudget != settings.getAiContextMaxInputTokens() || timeout != settings.getCodexTimeoutSeconds()
                        || local != null && "apikey".equals(expectedAuth) && !settings.isCodexApiBillingAcknowledged()) {
                    throw new AiApiException("AI destination or review settings changed. Review the request again before sending.");
                }
                String result;
                if (local != null) {
                    Set<BackendCapability> required = outputSchema == null
                            ? Set.of(BackendCapability.CANCELLATION)
                            : Set.of(BackendCapability.CANCELLATION, BackendCapability.STRUCTURED_OUTPUT);
                    AiRequest request = new AiRequest(context.prompt(), selection.wireModel(), context.root(),
                            Duration.ofSeconds(timeout), outputSchema, expectedAuth, expectedAccount, selection.effort(), required);
                    AiResult response;
                    if (contextScope != null) {
                        CodexAppServerBackend conversation;
                        synchronized (exploreLifecycle) {
                            if (disposed || canceled.get() || conversationEpoch != exploreEpoch.get()) throw new ProcessCanceledException();
                            conversation = exploreBackend.get();
                            if (conversation != null && !executable.equals(exploreExecutable)) {
                                throw new AiApiException("Codex executable changed. Start and review a new Explore conversation.");
                            }
                            if (conversation == null) {
                                conversation = local;
                                exploreExecutable = executable;
                                exploreBackend.set(conversation);
                            }
                        }
                        String reviewedScope = com.johnnyblabs.openspec.ai.safety.ExploreConversationScope.key(
                                project.getBasePath(), executable, selection.wireModel(), codexEffort, contextScope, context.prompt(), budget);
                        CodexAppServerBackend selectedConversation = conversation;
                        response = AiExecutionEvents.run(delta -> selectedConversation.generateConversation(request, token, delta, reviewedScope, false, tokenBudget),
                                token, event -> { if (event instanceof AiEvent.Delta delta) onDelta.accept(delta.text()); });
                    } else {
                        CodexAppServerBackend selectedLocal = local;
                        response = AiExecutionEvents.run(delta -> selectedLocal.generate(request, token, delta),
                                token, event -> { if (event instanceof AiEvent.Delta delta) onDelta.accept(delta.text()); });
                    }
                    result = response.text();
                } else {
                    AiBackend rest = new RestAiBackend(project.getService(DirectApiService.class),
                            AiProvider.fromString(restProvider), restModel);
                    result = AiExecutionEvents.run(delta -> rest.generate(
                            new AiRequest(context.prompt(), restModel, context.root(), Duration.ofMinutes(5), outputSchema), token, delta),
                            token, event -> { if (event instanceof AiEvent.Delta delta) onDelta.accept(delta.text()); }).text();
                }
                if (canceled.get() || disposed || indicator != null && indicator.isCanceled()) throw new ProcessCanceledException();
                return result;
            } catch (IOException ex) { throw new AiApiException("Context review failed: " + ex.getMessage(), ex); }
        } catch (AiApiException ex) {
            if (ex instanceof AiOperationCancelledException || canceled.get() || disposed || Thread.currentThread().isInterrupted()
                    || indicator != null && indicator.isCanceled()) throw new ProcessCanceledException();
            throw ex;
        }
    }

    private static BackendSelection configuredSelection(OpenSpecSettings settings) {
        String backend = settings.getAiBackend();
        return new BackendSelection(backend, settings.getAiProvider(),
                "LOCAL_CODEX".equals(backend) ? settings.getCodexModel() : settings.getAiModel(),
                "LOCAL_CODEX".equals(backend) ? settings.getCodexExecutable() : "",
                "LOCAL_CODEX".equals(backend) ? settings.getCodexReasoningEffort() : "");
    }

    public boolean hasActiveRequest() { return active.get() != null; }

    public void cancelActive() { AtomicBoolean current = active.get(); if (current != null) current.set(true); }
    public void cancelExplore() { AtomicBoolean current = activeExplore.get(); if (current != null) current.set(true); }
    @Override public void dispose() { disposed = true; cancelActive(); resetExploreConversation(); }

    private static com.johnnyblabs.openspec.ai.safety.ContextManifest.Budget contextBudget(OpenSpecSettings settings) {
        return new com.johnnyblabs.openspec.ai.safety.ContextManifest.Budget(64, 32768,
                Math.min(settings.getAiContextMaxBytes(), com.johnnyblabs.openspec.ai.safety.ContextPayloadPolicy.MAX_PROMPT_BYTES),
                settings.getAiContextMaxInputTokens(), null, 4096, 1024);
    }
    private static String artifactPrompt(ArtifactInstruction instruction) throws AiApiException {
        return artifactPrompt(instruction, null, com.johnnyblabs.openspec.ai.safety.ContextManifest.Budget.defaults());
    }
    private static String artifactPrompt(ArtifactInstruction instruction,
            com.johnnyblabs.openspec.ai.safety.ArtifactRequestSnapshot snapshot,
            com.johnnyblabs.openspec.ai.safety.ContextManifest.Budget budget) throws AiApiException {
        try {
            String prompt = com.johnnyblabs.openspec.ai.safety.ArtifactPromptBuilder.buildManifest(instruction, List.of(), budget, snapshot).prompt()
                    + com.johnnyblabs.openspec.ai.safety.ArtifactEnvelopeSchema.instruction(instruction.artifactId(), instruction.outputPath());
            return com.johnnyblabs.openspec.ai.safety.ContextPayloadPolicy.redactAndBound(prompt, budget);
        } catch (IOException ex) { throw new AiApiException("Unsafe or oversized artifact context: " + ex.getMessage(), ex); }
    }
    static JsonObject artifactSchema(String artifactId) {
        return com.johnnyblabs.openspec.ai.safety.ArtifactEnvelopeSchema.forArtifact(artifactId);
    }
}
