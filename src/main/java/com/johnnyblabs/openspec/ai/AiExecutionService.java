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
import com.johnnyblabs.openspec.ai.safety.ContextReviewService;
import com.johnnyblabs.openspec.ai.safety.SafeArtifactService;
import com.johnnyblabs.openspec.model.ArtifactInstruction;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
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
    private final java.util.concurrent.atomic.AtomicLong exploreEpoch = new java.util.concurrent.atomic.AtomicLong();
    private volatile String exploreExecutable = "";
    private volatile BackendStatus codexStatus = new BackendStatus(false, "unknown", "Check Codex status in Settings", "");
    private volatile String statusExecutable = "";
    private volatile boolean disposed;

    public AiExecutionService(Project project) { this.project = project; }

    /** Cached only: safe for AnAction.update and UI rendering. */
    public boolean isConfigured() {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        if ("REST".equals(settings.getAiBackend())) {
            DirectApiService rest = project.getService(DirectApiService.class);
            return rest != null && rest.isConfigured();
        }
        if (!"LOCAL_CODEX".equals(settings.getAiBackend())) return false;
        if (!settings.getCodexExecutable().equals(statusExecutable)) {
            refreshStatus();
            return false;
        }
        return codexStatus.available();
    }

    public String getBackendLabel() {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        if ("LOCAL_CODEX".equals(settings.getAiBackend())) {
            String model = settings.getCodexModel().isBlank() ? "account default" : settings.getCodexModel();
            return "Local Codex · " + model + " · " + billingLabel(codexStatus.authMode());
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
        String executable = OpenSpecSettings.getInstance(project).getCodexExecutable();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                BackendStatus status = new CodexAppServerBackend(executable).probe();
                if (!disposed) { codexStatus = status; statusExecutable = executable; }
            } catch (AiApiException ex) {
                if (!disposed) {
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
        AtomicBoolean canceled = reserveRun();
        try {
            SafeArtifactService writer = project.getService(SafeArtifactService.class);
            var snapshot = writer.begin(instruction);
            String result = execute(artifactPrompt(instruction) + "\nExisting artifact destinations (use replace; other destinations use create): " + snapshot.baseHashes().keySet(), artifactSchema(instruction.artifactId()), ignored -> {}, canceled);
            ProgressManager.checkCanceled();
            return writer.apply(writer.prepare(snapshot, result), () -> canceled.get() || disposed);
        } finally { active.compareAndSet(canceled, null); }
    }

    public String generateRaw(String prompt) throws AiApiException { return generateRaw(prompt, ignored -> {}); }
    public String generateRaw(String prompt, Consumer<String> onDelta) throws AiApiException {
        return execute(prompt, null, onDelta);
    }

    /** Explore history is held by one CLI-owned thread, isolated from generation and Verify. */
    public String generateExplore(String prompt, String contextScope, Consumer<String> onDelta) throws AiApiException {
        long epoch = exploreEpoch.get();
        AtomicBoolean canceled = reserveRun();
        activeExplore.set(canceled);
        if (epoch != exploreEpoch.get()) canceled.set(true);
        try { return execute(prompt, null, onDelta, canceled, contextScope, epoch); }
        finally { activeExplore.compareAndSet(canceled, null); active.compareAndSet(canceled, null); }
    }

    public void resetExploreConversation() {
        exploreEpoch.incrementAndGet();
        cancelExplore();
        CodexAppServerBackend previous = exploreBackend.getAndSet(null);
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
        return execute(prompt, outputSchema, onDelta, canceled, null, -1);
    }

    private String execute(String prompt, JsonObject outputSchema, Consumer<String> onDelta, AtomicBoolean canceled,
                           String contextScope, long conversationEpoch) throws AiApiException {
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        try {
            OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
            String backend = settings.getAiBackend();
            String executable = settings.getCodexExecutable();
            String codexModel = settings.getCodexModel();
            String restProvider = settings.getAiProvider();
            String restModel = settings.getAiModel();
            int timeout = settings.getCodexTimeoutSeconds();
            int budget = settings.getAiContextMaxBytes();
            if (!"REST".equals(backend) && !"LOCAL_CODEX".equals(backend)) throw new AiApiException("Unsupported AI backend; choose a backend in Settings.");
            CodexAppServerBackend local = null;
            BackendStatus executionStatus = null;
            if ("LOCAL_CODEX".equals(backend)) {
                local = new CodexAppServerBackend(executable);
                executionStatus = local.probe();
                codexStatus = executionStatus;
                statusExecutable = executable;
                if (!executionStatus.available()) throw new AiApiException(executionStatus.detail());
                if ("apikey".equals(executionStatus.authMode()) && !settings.isCodexApiBillingAcknowledged()) {
                    throw new AiApiException("Codex uses API billing. Acknowledge API billing in Settings before inference.");
                }
                if (!"chatgpt".equals(executionStatus.authMode()) && !"apikey".equals(executionStatus.authMode())) {
                    throw new AiApiException("Codex authentication mode is unknown or unsupported; check CLI login.");
                }
            }
            String expectedAuth = executionStatus == null ? "apikey" : executionStatus.authMode();
            String expectedAccount = executionStatus == null ? null : executionStatus.accountFingerprint();
            String reviewedDestination = local == null
                    ? AiProvider.fromString(restProvider).getDisplayName() + " · "
                      + (restModel.isBlank() ? AiProvider.fromString(restProvider).getDefaultModel() : restModel) + " · API billing"
                    : "Local Codex · " + (codexModel.isBlank() ? "account default" : codexModel) + " · " + billingLabel(expectedAuth);
            String historyNotice = contextScope == null || local == null ? "" : "\nExplore conversation: prior reviewed turns are retained by Codex. Clear only hides the display; New conversation discards reuse.\nCLI stores this conversation for resume. Native tools remain disabled.";
            try (var context = project.getService(ContextReviewService.class).review(prompt, reviewedDestination + historyNotice, budget)) {
                if (canceled.get() || disposed || indicator != null && indicator.isCanceled()) throw new ProcessCanceledException();
                if (!backend.equals(settings.getAiBackend()) || budget != settings.getAiContextMaxBytes()
                        || local != null && (!executable.equals(settings.getCodexExecutable())
                        || !codexModel.equals(settings.getCodexModel()) || timeout != settings.getCodexTimeoutSeconds()
                        || "apikey".equals(expectedAuth) && !settings.isCodexApiBillingAcknowledged())
                        || local == null && (!restProvider.equals(settings.getAiProvider()) || !restModel.equals(settings.getAiModel()))) {
                    throw new AiApiException("AI destination or review settings changed. Review the request again before sending.");
                }
                String result;
                if (local != null) {
                    AiRequest request = new AiRequest(context.prompt(), codexModel, context.root(),
                            Duration.ofSeconds(timeout), outputSchema, expectedAuth, expectedAccount);
                    CancellationToken token = () -> canceled.get() || disposed || indicator != null && indicator.isCanceled()
                            || contextScope != null && conversationEpoch != exploreEpoch.get();
                    AiResult response;
                    if (contextScope != null) {
                        if (conversationEpoch != exploreEpoch.get()) throw new ProcessCanceledException();
                        CodexAppServerBackend conversation = exploreBackend.get();
                        if (conversation != null && !executable.equals(exploreExecutable)) {
                            resetExploreConversation();
                            throw new AiApiException("Codex executable changed. Start and review a new Explore conversation.");
                        }
                        if (conversation == null) {
                            conversation = local;
                            exploreExecutable = executable;
                            exploreBackend.set(conversation);
                        }
                        String reviewedScope = com.johnnyblabs.openspec.ai.safety.ExploreConversationScope.key(
                                project.getBasePath(), executable, codexModel, contextScope, context.prompt(), budget);
                        response = conversation.generateConversation(request, token, onDelta, reviewedScope, false);
                    } else response = local.generate(request, token, onDelta);
                    result = response.text();
                } else {
                    AiBackend rest = new RestAiBackend(project.getService(DirectApiService.class),
                            AiProvider.fromString(restProvider), restModel);
                    result = rest.generate(new AiRequest(context.prompt(), restModel, context.root(), Duration.ofMinutes(5)),
                            () -> canceled.get() || disposed || indicator != null && indicator.isCanceled(), onDelta).text();
                }
                if (canceled.get() || disposed || indicator != null && indicator.isCanceled()) throw new ProcessCanceledException();
                return result;
            } catch (IOException ex) { throw new AiApiException("Context review failed: " + ex.getMessage(), ex); }
        } catch (AiApiException ex) {
            if (canceled.get() || disposed || Thread.currentThread().isInterrupted()
                    || indicator != null && indicator.isCanceled()) throw new ProcessCanceledException();
            throw ex;
        }
    }

    public void cancelActive() { AtomicBoolean current = active.get(); if (current != null) current.set(true); }
    public void cancelExplore() { AtomicBoolean current = activeExplore.get(); if (current != null) current.set(true); }
    @Override public void dispose() { disposed = true; cancelActive(); resetExploreConversation(); }

    private static String artifactPrompt(ArtifactInstruction instruction) throws AiApiException {
        String prompt;
        try { prompt = com.johnnyblabs.openspec.ai.safety.ArtifactPromptBuilder.build(instruction); }
        catch (IOException ex) { throw new AiApiException("Unsafe or oversized artifact context: " + ex.getMessage(), ex); }
        return prompt + "\n\nReturn ONLY a JSON artifact envelope with schemaVersion 1, artifactId "
                + instruction.artifactId() + ", and files [{relativePath, operation, content}]. "
                + "Use concrete paths matching " + instruction.outputPath() + ". operation is create or replace. "
                + "Never use a glob as a filename, write files yourself, or include paths outside this artifact.";
    }

    static JsonObject artifactSchema(String artifactId) {
        JsonObject schema = JsonParser.parseString("""
            {"type":"object","additionalProperties":false,"required":["schemaVersion","artifactId","files"],
             "properties":{"schemaVersion":{"type":"integer","enum":[1]},"artifactId":{"type":"string"},
             "files":{"type":"array","minItems":1,"items":{"type":"object","additionalProperties":false,
             "required":["relativePath","operation","content"],"properties":{"relativePath":{"type":"string"},
             "operation":{"type":"string","enum":["create","replace"]},"content":{"type":"string"}}}}}}
            """).getAsJsonObject();
        schema.getAsJsonObject("properties").getAsJsonObject("artifactId").add("enum", new com.google.gson.Gson().toJsonTree(List.of(artifactId)));
        return schema;
    }
}
