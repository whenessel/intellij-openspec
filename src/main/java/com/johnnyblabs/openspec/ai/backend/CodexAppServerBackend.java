package com.johnnyblabs.openspec.ai.backend;

import com.google.gson.*;
import com.johnnyblabs.openspec.ai.AiApiException;
import com.johnnyblabs.openspec.ai.safety.ArtifactEnvelopeJson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.security.MessageDigest;

/** Version-pinned stdio adapter. Auth and refresh remain entirely owned by the installed CLI. */
public final class CodexAppServerBackend implements AiBackend {
    public static final String SUPPORTED_VERSION = "0.160.0";
    private static final String PROFILE = "openspec_reviewed";
    private static final int MAX_TEXT = 4 * 1024 * 1024;
    private static final List<String> DISABLED_FEATURES = List.of("shell_tool", "view_image", "js_repl", "multi_agent", "multi_agent_v2", "apps", "plugins", "plugin_hooks", "browser_use", "computer_use", "memories", "remote_plugin", "tool_suggest", "request_permissions_tool", "agent_message_board");
    private final String executable;
    private final ProcessLauncher launcher;
    private final LongSupplier nanoTime;
    private static final ModelCatalogCache SHARED_CATALOG = new ModelCatalogCache(Clock.systemUTC(), Duration.ofMinutes(5), 16);
    private final ModelCatalogCache catalogCache;
    private final Object conversationLock = new Object();
    private final AtomicBoolean conversationBusy = new AtomicBoolean();
    private Conversation conversation;
    private static final class Conversation {
        final String scopeKey;
        final Path root;
        String threadId, model, accountFingerprint, authMode, effort;
        volatile CodexProtocol.TokenUsage usage;
        volatile int historyBudget;
        volatile boolean closed;
        boolean failed;
        volatile Session active;
        Conversation(String scopeKey, Path root) { this.scopeKey = scopeKey; this.root = root; }
    }
    private record AccountState(String mode, String fingerprint, boolean workspaceIdentityKnown) {}

    @FunctionalInterface public interface ProcessLauncher {
        Process start(List<String> arguments, Path workingDirectory) throws IOException;
    }
    public CodexAppServerBackend(String executable) {
        this(executable, (arguments, cwd) -> {
            try { return CodexProcessPolicy.builder(arguments,cwd,System.getenv()).start(); }
            catch (AiApiException e) { throw new IOException(e.getMessage()); }
        });
    }
    public CodexAppServerBackend(String executable, ProcessLauncher launcher) {
        this(executable, launcher, SHARED_CATALOG);
    }
    public CodexAppServerBackend(String executable, ProcessLauncher launcher, ModelCatalogCache catalogCache) {
        this(executable, launcher, catalogCache, System::nanoTime);
    }
    public CodexAppServerBackend(String executable, ProcessLauncher launcher, ModelCatalogCache catalogCache, LongSupplier nanoTime) {
        this.nanoTime = Objects.requireNonNull(nanoTime);
        this.catalogCache = Objects.requireNonNull(catalogCache);
        this.executable = executable == null || executable.isBlank() ? "codex" : executable.trim();
        this.launcher = Objects.requireNonNull(launcher);
    }
    @Override public String id() { return "local-codex"; }
    @Override public BackendCapabilities capabilities() { return new BackendCapabilities(true, true, true, false); }

    /** Cancellation is an operation outcome, never an unavailable-backend status. */
    private static void checkCancellation(CancellationToken token) throws AiOperationCancelledException {
        Objects.requireNonNull(token);
        if (token.isCancelled() || Thread.currentThread().isInterrupted()) throw new AiOperationCancelledException();
    }
    @Override public BackendStatus probe() throws AiApiException { return probe(CancellationToken.NONE); }
    public BackendStatus probe(CancellationToken token) throws AiApiException {
        checkCancellation(token);
        Path root = temporaryRoot();
        try (Session session = open(root, Duration.ofSeconds(20), token)) {
            AccountState account = session.account();
            String auth = account.mode();
            String limits = session.limitStatus(auth);
            session.check();
            return new BackendStatus(Set.of("chatgpt", "apiKey").contains(auth), ("apiKey".equals(auth) ? "apikey" : auth),
                    "Prompt-only reviewed context; environment access disabled; " + ("signedOut".equals(auth) ? "run codex login externally." : Set.of("chatgpt","apiKey").contains(auth) ? "CLI owns login and refresh; usage limits apply." : "Unsupported CLI authentication mode; execution is blocked.") + limits, SUPPORTED_VERSION, limits.strip(), account.fingerprint());
        } catch (AiApiException | RuntimeException e) {
            checkCancellation(token);
            if (e instanceof AiOperationCancelledException cancelled) throw cancelled;
            return new BackendStatus(false, "unknown", e instanceof AiApiException ? e.getMessage() : "Codex returned incompatible account/status metadata.", "unknown");
        } finally { deleteRoot(root); checkCancellation(token); }
    }
    @Override public List<ModelDescriptor> models() throws AiApiException { return catalog(null, false).models(); }

    /** Refreshes account status before using metadata; offline fallbacks remain explicitly stale. */
    public ModelCatalogSnapshot catalog(BackendStatus reviewedStatus, boolean forceRefresh) throws AiApiException {
        return catalog(reviewedStatus, forceRefresh, CancellationToken.NONE);
    }
    public ModelCatalogSnapshot catalog(BackendStatus reviewedStatus, boolean forceRefresh, CancellationToken token) throws AiApiException {
        checkCancellation(token);
        if (reviewedStatus != null && !SUPPORTED_VERSION.equals(reviewedStatus.version())) throw failure("Catalog status uses an unsupported or unknown Codex protocol version.");
        Path root = temporaryRoot();
        ModelCatalogCache.Key key = reviewedStatus == null ? null : catalogKey(reviewedStatus.authMode(), reviewedStatus.accountFingerprint());
        boolean accountVerified = false;
        boolean published = false;
        try (Session session = open(root, Duration.ofSeconds(20), token)) {
            AccountState account = session.account();
            String mode = canonicalMode(account.mode());
            if (reviewedStatus != null && (!mode.equals(reviewedStatus.authMode()) || !reviewedStatus.accountFingerprint().isBlank() && !reviewedStatus.accountFingerprint().equals(account.fingerprint()))) {
                catalogCache.invalidateExecutable(executable);
                throw failure("Codex catalog account changed after status review; refresh status and catalog.");
            }
            key = catalogKey(mode, account.workspaceIdentityKnown() ? account.fingerprint() : "");
            accountVerified = Set.of("chatgpt", "apikey").contains(mode);
            if (!forceRefresh && accountVerified) {
                var cached = catalogCache.fresh(key);
                session.check();
                if (cached.isPresent()) return new ModelCatalogSnapshot(cached.get().models(), cached.get().fetchedAt(), false, true, "Cached model metadata within TTL; current account verified.");
            }
            ModelCatalogSnapshot fresh = new ModelCatalogSnapshot(readModels(session), catalogCache.clock().instant(), false, accountVerified,
                    key.cacheable() ? "Fresh CLI model catalog." : "Fresh CLI model catalog; account identity unavailable, metadata is not cached.");
            session.check();
            published = true;
            catalogCache.put(key, fresh);
            session.check();
            return fresh;
        } catch (AiApiException | RuntimeException failure) {
            if (published && (token.isCancelled() || Thread.currentThread().isInterrupted() || failure instanceof AiOperationCancelledException)) catalogCache.invalidateExecutable(executable);
            checkCancellation(token);
            if (failure instanceof AiOperationCancelledException cancelled) throw cancelled;
            if (key != null) {
                var cached = catalogCache.stale(key);
                checkCancellation(token);
                if (cached.isPresent()) return new ModelCatalogSnapshot(cached.get().models(), cached.get().fetchedAt(), true, accountVerified,
                        accountVerified ? "Catalog refresh failed; cached suggestions only. Current account verified." : "Backend offline/unverified; cached suggestions only, execution selection is blocked.");
            }
            if (failure instanceof AiApiException api) throw api;
            throw failure("Codex returned an incompatible model catalog.");
        } finally {
            deleteRoot(root);
            if (published && (token.isCancelled() || Thread.currentThread().isInterrupted())) catalogCache.invalidateExecutable(executable);
            checkCancellation(token);
        }
    }
    private ModelCatalogCache.Key catalogKey(String mode, String fingerprint) { return new ModelCatalogCache.Key(executable, mode, fingerprint, SUPPORTED_VERSION); }
    private static String canonicalMode(String mode) { return "apiKey".equals(mode) ? "apikey" : mode; }
    private static List<ModelDescriptor> readModels(Session session) throws AiApiException {
        List<ModelDescriptor> result = new ArrayList<>(); Set<String> seenCursors = new HashSet<>(); Set<String> ids = new HashSet<>();
        String cursor = null;
        for (int page = 0; page < 10; page++) {
            JsonObject params = new JsonObject(); params.addProperty("limit", 100); if(cursor != null) params.addProperty("cursor", cursor);
            JsonObject response = session.rpc("model/list", params);
            session.check();
            for (JsonElement entry : array(response, "data")) {
                session.check();
                JsonObject model = entry.getAsJsonObject(); String id = string(model, "id"), wire = string(model, "model");
                if (id.isBlank() || wire.isBlank() || !ids.add(id)) throw failure("Codex returned duplicate or empty catalog identifiers.");
                List<ReasoningEffortDescriptor> efforts = new ArrayList<>(); Set<String> effortIds = new HashSet<>();
                for(JsonElement value : array(model, "supportedReasoningEfforts")) {
                    JsonObject effort=value.getAsJsonObject(); String name=string(effort,"reasoningEffort");
                    if(name.isBlank() || !effortIds.add(name)) throw failure("Codex returned duplicate or empty reasoning efforts.");
                    efforts.add(new ReasoningEffortDescriptor(name,string(effort,"description")));
                }
                String defaultEffort = string(model, "defaultReasoningEffort");
                if (!effortIds.contains(defaultEffort)) throw failure("Codex returned a default effort not supported by the selected model.");
                CapabilitySupport text = CapabilitySupport.UNKNOWN;
                if(model.has("inputModalities") && !model.get("inputModalities").isJsonNull()) {
                    boolean supportsText=false; for(JsonElement modality:array(model,"inputModalities")) if("text".equals(modality.getAsString())) supportsText=true;
                    text=supportsText ? CapabilitySupport.SUPPORTED : CapabilitySupport.UNSUPPORTED;
                }
                result.add(new ModelDescriptor(id,string(model,"displayName"),string(model,"description"),model.get("isDefault").getAsBoolean(),wire,efforts,defaultEffort,text));
                if(result.size()>1000)throw failure("Codex model catalog exceeded its bounded size.");
            }
            cursor=optional(response,"nextCursor"); session.check(); if(cursor==null)return List.copyOf(result);
            if (!seenCursors.add(cursor)) throw failure("Codex returned a repeated catalog cursor.");
        }
        throw failure("Codex model catalog exceeded its bounded pagination limit.");
    }
    @Override public AiResult generate(AiRequest request, CancellationToken cancellation, Consumer<String> onDelta) throws AiApiException {
        Objects.requireNonNull(cancellation); Objects.requireNonNull(onDelta);
        capabilities().require(request.requiredCapabilities());
        Path root = reviewedRoot(request);
        try (Session session = open(root, request.timeout(), cancellation)) {
            AccountState account = session.account();
            validateAccount(account, request); session.accountBaseline = account;
            JsonObject started = prepareThread(session, request, root, true, null, null);
            return runTurn(session, request, onDelta, string(started, "model"));
        } catch (RuntimeException e) { throw failure("Codex returned an incompatible event stream; no result was applied."); }
        finally { checkCancellation(cancellation); }
    }

    /** Reuses CLI-owned history through resume, with a fresh authenticated process per reviewed turn. */
    public AiResult generateConversation(AiRequest request, CancellationToken cancellation, Consumer<String> onDelta,
                                         String scopeKey, boolean newConversation) throws AiApiException {
        return generateConversation(request,cancellation,onDelta,scopeKey,newConversation,12000);
    }
    public AiResult generateConversation(AiRequest request, CancellationToken cancellation, Consumer<String> onDelta,
                                         String scopeKey, boolean newConversation,int historyBudgetTokens) throws AiApiException {
        Objects.requireNonNull(scopeKey); Objects.requireNonNull(cancellation); Objects.requireNonNull(onDelta);
        if (cancellation.isCancelled() || Thread.currentThread().isInterrupted()) throw failure("Codex conversation was cancelled before startup.");
        if(historyBudgetTokens<1)throw failure("Conversation history budget must be positive.");
        reviewedRoot(request);
        capabilities().require(request.requiredCapabilities());
        if (!conversationBusy.compareAndSet(false, true)) throw failure("A Codex conversation turn is already running.");
        Conversation state = null;
        boolean created = false;
        try {
            if (newConversation) closeConversation();
            synchronized (conversationLock) {
                if (cancellation.isCancelled() || Thread.currentThread().isInterrupted()) throw failure("Codex conversation was cancelled before startup.");
                if (conversation == null) { conversation = new Conversation(scopeKey, temporaryRoot()); created = true; }
                if (!conversation.scopeKey.equals(scopeKey)) throw failure("Conversation context/project/model/security scope changed; start a New conversation.");
                if (conversation.failed) throw failure("Previous conversation turn failed or was cancelled; start a New conversation before continuing.");
                state = conversation;
            }
            Conversation current = state;
            CancellationToken guarded = () -> cancellation.isCancelled() || current.closed;
            try (Session session = open(current.root, request.timeout(), guarded)) {
                current.active = session;
                if(current.threadId!=null)admitConversationHistory(current,request,historyBudgetTokens);
                current.historyBudget=historyBudgetTokens;
                session.previousUsage=current.usage;
                session.check();
                AccountState account = session.account();
                validateAccount(account, request); session.accountBaseline = account;
                if (!"chatgpt".equals(account.mode()) || !account.workspaceIdentityKnown() || account.fingerprint().isBlank()) throw failure("Persistent Codex conversations require a CLI-reported ChatGPT account identity; this auth mode has no verifiable identity.");
                if (current.threadId != null && (!account.mode().equals(current.authMode) || !account.fingerprint().equals(current.accountFingerprint))) throw failure("Codex conversation account changed; start a New conversation.");
                if (current.threadId != null && !Objects.equals(current.effort, request.reasoningEffort())) throw failure("Conversation reasoning effort changed; start a New conversation.");
                if (current.threadId != null && (request.model() == null || request.model().isBlank())) verifyDefaultModel(session, current.model);
                JsonObject started = prepareThread(session, request, current.root, false, current.threadId, current.model);
                current.threadId = session.threadId;
                current.model = string(started, "model");
                current.authMode = account.mode(); current.accountFingerprint = account.fingerprint(); current.effort = request.reasoningEffort();
                AiResult result=runTurn(session, request, onDelta, current.model);
                current.usage=session.usage;
                return result;
            } finally { current.active = null; checkCancellation(guarded); }
        } catch (AiApiException e) { if (state != null) { state.failed = true; if (created) deleteRoot(state.root); } throw e; }
        catch (RuntimeException e) { if (state != null) { state.failed = true; if (created) deleteRoot(state.root); } throw failure("Codex returned an incompatible conversation protocol; no result was applied."); }
        finally { conversationBusy.set(false); }
    }
    private static void admitConversationHistory(Conversation state,AiRequest request,int budget) throws AiApiException {
        if(state.historyBudget!=budget || state.usage==null)throw failure("Conversation history usage is unknown or its reviewed budget changed; start a New conversation.");
        long effective=historyInputLimit(budget,state.usage.contextWindow());
        long prompt=request.prompt().getBytes(StandardCharsets.UTF_8).length;
        long used=state.usage.totalTokens(),reasoning=state.usage.reasoningTokens();
        // Counting reasoning again is deliberately conservative: it may already be included in total.
        if(used>effective || reasoning>effective-used || prompt>effective-used-reasoning)throw failure("Conversation history exceeds the reviewed context budget; start a New conversation.");
    }
    private static long historyInputLimit(int budget,Long window) {
        // The configured budget is an input cap; reserve output/safety from a known model window.
        return window==null ? budget : Math.min(budget,Math.max(0,window-4096-1024));
    }
    public String conversationBudgetSummary() {
        synchronized(conversationLock) {
            Conversation state=conversation;
            if(state==null)return "New conversation; CLI history token usage is not yet available.";
            if(state.failed)return "Conversation failed or was cancelled; start a New conversation.";
            if(state.usage==null)return "CLI history token usage is unavailable; start a New conversation before another turn.";
            long limit=historyInputLimit(state.historyBudget,state.usage.contextWindow());
            return "CLI cumulative history usage: "+state.usage.totalTokens()+" tokens; reported reasoning: "+state.usage.reasoningTokens()
                    +" tokens; reviewed input cap: "+limit+" tokens. "+(state.usage.contextWindow()==null?"Model context window unknown.":"Model window: "+state.usage.contextWindow()+"; output/safety reserve: 5120 tokens.")
                    +" Next prompt is admitted conservatively by UTF-8 bytes; exceeding the cap requires New.";
        }
    }
    /** Explicit New conversation/disposal clears remembered history and stops any pending process. */
    public void closeConversation() {
        Conversation previous;
        synchronized (conversationLock) { previous = conversation; conversation = null; if (previous != null) previous.closed = true; }
        if (previous != null) { if (previous.active != null) previous.active.close(); deleteRoot(previous.root); }
    }
    private static Path reviewedRoot(AiRequest request) throws AiApiException {
        Path root;
        try { root = request.contextRoot().toRealPath(); } catch (IOException e) { throw failure("Reviewed Codex context is unavailable."); }
        if (!Files.isDirectory(root) || Files.isSymbolicLink(request.contextRoot())) throw failure("Codex requires a reviewed context directory without symbolic links.");
        return root;
    }
    private static void validateAccount(AccountState account, AiRequest request) throws AiApiException {
        if (!Set.of("chatgpt", "apiKey").contains(account.mode())) throw failure("Codex is signed out or uses an unsupported auth mode; run codex login externally.");
        String canonical = "apiKey".equals(account.mode()) ? "apikey" : account.mode();
        if (request.expectedAuthMode() != null && !request.expectedAuthMode().equals(canonical)) throw failure("Codex authentication mode changed after billing review; review settings again.");
        if (request.expectedAccountFingerprint() != null && !request.expectedAccountFingerprint().isBlank() && !request.expectedAccountFingerprint().equals(account.fingerprint())) throw failure("Codex account identity changed after context review; review the account again.");
    }
    private static void verifyDefaultModel(Session session, String expectedModel) throws AiApiException {
        String cursor = null;
        for (int page = 0; page < 10; page++) {
            JsonObject params = new JsonObject(); params.addProperty("limit", 100); if (cursor != null) params.addProperty("cursor", cursor);
            JsonObject result = session.rpc("model/list", params);
            for (JsonElement value : array(result, "data")) {
                JsonObject model = value.getAsJsonObject();
                if (model.has("isDefault") && model.get("isDefault").getAsBoolean()) {
                    if (!expectedModel.equals(optional(model, "model")) && !expectedModel.equals(string(model, "id"))) throw failure("Codex account default model changed; start a New conversation.");
                    return;
                }
            }
            cursor = optional(result, "nextCursor"); if (cursor == null) break;
        }
        throw failure("Codex account default model cannot be verified; start a New conversation or choose an explicit model.");
    }
    private JsonObject prepareThread(Session session, AiRequest request, Path root, boolean ephemeral, String resumeId, String expectedModel) throws AiApiException {
        JsonObject params = new JsonObject();
        if (resumeId == null) { params.add("environments", new JsonArray()); params.addProperty("ephemeral", ephemeral); }
        else params.addProperty("threadId", resumeId);
        params.addProperty("cwd", root.toString()); params.addProperty("permissions", PROFILE); params.addProperty("approvalPolicy", "never");
        params.addProperty("baseInstructions", "Use only the reviewed prompt context. Return the requested answer. Do not use tools or modify files.");
        params.addProperty("developerInstructions", "");
        String model = request.model() == null || request.model().isBlank() ? expectedModel : request.model();
        if (model != null && !model.isBlank()) params.addProperty("model", model);
        JsonObject started = session.rpc(resumeId == null ? "thread/start" : "thread/resume", params);
        JsonObject active = object(started, "activePermissionProfile"), sandbox = object(started, "sandbox");
        if (!PROFILE.equals(string(active, "id")) || optional(active, "extends") != null || !"never".equals(string(started, "approvalPolicy")) || !"openai".equals(string(started, "modelProvider"))) throw failure("Codex did not accept the reviewed permission/provider profile.");
        if (!"readOnly".equals(string(sandbox, "type")) || sandbox.get("networkAccess").getAsBoolean()) throw failure("Codex returned broader permissions than requested.");
        if (!array(started, "instructionSources").isEmpty()) throw failure("Codex included instruction sources outside reviewed context.");
        if (model != null && !model.isBlank() && !model.equals(string(started, "model"))) throw failure("Codex changed the selected model; review model settings again.");
        if (!array(object(started, "thread"), "environments").isEmpty()) throw failure("Codex enabled an execution environment outside the reviewed prompt-only profile.");
        CodexProtocol.ThreadStarted thread = CodexProtocol.threadStarted(started);
        session.threadId = thread.threadId();
        if (resumeId != null && !resumeId.equals(session.threadId)) throw failure("Codex resumed a different thread; no result was applied.");
        return started;
    }
    private AiResult runTurn(Session session, AiRequest request, Consumer<String> onDelta, String model) throws AiApiException {
            if (!request.reasoningEffort().isBlank()) {
                ModelCatalogSnapshot fresh = new ModelCatalogSnapshot(readModels(session), catalogCache.clock().instant(), false, true, "Fresh turn capability negotiation.");
                ModelSelection.Selection selected = ModelSelection.negotiate(fresh, model, request.reasoningEffort(), false);
                if (!model.equals(selected.wireModel())) throw failure("Codex catalog model identifier changed before turn start.");
            }
            JsonObject turnParams = new JsonObject(); turnParams.addProperty("threadId", session.threadId);
            JsonArray input = new JsonArray(); JsonObject text = new JsonObject(); text.addProperty("type", "text"); text.addProperty("text", request.prompt()); input.add(text); turnParams.add("input", input);
            if (request.outputSchema() != null) turnParams.add("outputSchema", request.outputSchema());
            if (!request.reasoningEffort().isBlank()) turnParams.addProperty("effort", request.reasoningEffort());
            JsonObject turn = session.rpc("turn/start", turnParams);
            CodexProtocol.TurnStarted startedTurn = CodexProtocol.turnStarted(turn);
            if (startedTurn.status() != CodexProtocol.TurnStatus.IN_PROGRESS) throw failure("Codex did not start an active turn.");
            session.turnId = startedTurn.turnId();
            int streamedCharacters = 0;
            String finalText = null;
            while (true) {
                JsonObject event = session.next();
                session.rejectRequest(event);
                CodexProtocol.Frame frame = CodexProtocol.frame(event);
                if (!(frame instanceof CodexProtocol.NotificationFrame)) throw failure("Unexpected Codex response outside an active RPC.");
                CodexProtocol.Notification notification = CodexProtocol.notification(event);
                if (notification instanceof CodexProtocol.Unknown) continue;
                if (notification instanceof CodexProtocol.AccountUpdated) {
                    AccountState current = session.account();
                    if (session.accountBaseline == null || !current.equals(session.accountBaseline)) { session.interrupt(); throw failure("Codex account changed during the turn; no result was applied. Review the account and start a New conversation."); }
                    session.notifications.removeIf(pending -> "account/updated".equals(optional(pending, "method")));
                    continue;
                }
                if(notification instanceof CodexProtocol.TokenUsage measured) {
                    if(!session.threadId.equals(measured.threadId()) || !session.turnId.equals(measured.turnId()))continue;
                    if((session.usage!=null && measured.totalTokens()<session.usage.totalTokens()) || (session.previousUsage!=null && measured.totalTokens()<session.previousUsage.totalTokens()))throw failure("Codex conversation usage decreased unexpectedly; start a New conversation.");
                    if(session.previousUsage!=null && !Objects.equals(session.previousUsage.contextWindow(),measured.contextWindow()))throw failure("Codex model context window changed; start a New conversation.");
                    session.usage=measured;continue;
                }
                if (notification instanceof CodexProtocol.TurnCompleted ended) {
                    if (!session.threadId.equals(ended.threadId()) || !session.turnId.equals(ended.turnId())) continue;
                    if (ended.status() != CodexProtocol.TurnStatus.COMPLETED) {
                        session.terminal.finish(ended.status() == CodexProtocol.TurnStatus.INTERRUPTED ? TurnTerminalState.Status.CANCELLED : TurnTerminalState.Status.FAILED, "");
                        throw failure("Codex turn failed or was interrupted; no result was applied.");
                    }
                    session.check();
                    if (finalText == null || finalText.isBlank()) throw failure("Codex completed without a final assistant message.");
                    AiResult result = AiResult.fromResponse(finalText, id(), model, request.outputSchema() != null);
                    session.check(); session.terminal.finish(TurnTerminalState.Status.COMPLETED, result.text());
                    session.terminal.requireCompleted(); return result;
                }
                if (notification instanceof CodexProtocol.Delta delta) {
                    if (!session.threadId.equals(delta.threadId()) || !session.turnId.equals(delta.turnId())) continue;
                    if ((long) streamedCharacters + delta.text().length() > MAX_TEXT) throw failure("Codex output exceeded the size limit.");
                    streamedCharacters += delta.text().length(); session.check(); onDelta.accept(delta.text());
                } else if (notification instanceof CodexProtocol.ItemCompleted item) {
                    if (!session.threadId.equals(item.threadId()) || !session.turnId.equals(item.turnId())) continue;
                    if (item.assistant() && (item.phase() == null || "final_answer".equals(item.phase()))) {
                        finalText = item.text();
                        if (finalText.length() > MAX_TEXT) throw failure("Codex output exceeded the size limit.");
                    }
                } else if (notification instanceof CodexProtocol.TurnError error
                        && session.threadId.equals(error.threadId()) && session.turnId.equals(error.turnId())) throw failure("Codex reported a turn error; no result was applied.");
            }
    }

    private Session open(Path root, Duration timeout, CancellationToken token) throws AiApiException {
        checkCancellation(token);
        CodexProcessPolicy.validate(executable);
        try {
            Session session = new Session(launcher.start(arguments(root), root), timeout, token, nanoTime);
            try { session.check(); session.initialize(root); return session; }
            catch (AiApiException | RuntimeException e) { session.close(); if (e instanceof AiApiException api) throw api; throw failure("Codex returned an incompatible handshake/configuration."); }
        } catch (IOException e) { checkCancellation(token); throw failure("Cannot start Codex. Configure the installed CLI executable path."); }
    }
    List<String> arguments(Path root) {
        List<String> args = new ArrayList<>(List.of(executable, "app-server", "--listen", "stdio://"));
        override(args, "permissions." + PROFILE + "={filesystem={" + toml(root.toString()) + "=\"read\"},network={enabled=false}}");
        override(args, "default_permissions=" + toml(PROFILE));
        override(args, "approval_policy=\"never\""); override(args, "project_doc_max_bytes=0");
        override(args, "skills.include_instructions=false"); override(args, "cloud.skills.enabled=false");
        override(args, "web_search=\"disabled\""); override(args, "plugins.unified-computer-use@openai-bundled.enabled=false");
        for (String feature : DISABLED_FEATURES) override(args, "features." + feature + "=false");
        return List.copyOf(args);
    }
    private static void override(List<String> args, String value) { args.add("-c"); args.add(value); }
    private static String toml(String text) { return new Gson().toJson(text); }
    private static Path temporaryRoot() throws AiApiException { try { return Files.createTempDirectory("openspec-codex-probe-"); } catch(IOException e) { throw failure("Cannot create isolated Codex probe directory."); } }
    private static void deleteRoot(Path root) { try { Files.deleteIfExists(root); } catch(IOException ignored) { /* No user files are deleted. */ } }
    private static AiApiException failure(String text) { return new AiApiException(text, 0, "Local Codex", "Use Codex 0.160.0 on Linux/macOS with the reviewed profile; no REST fallback occurs."); }
    private static String optional(JsonObject object, String key) { JsonElement value = object.get(key); return value == null || value.isJsonNull() ? null : value.getAsString(); }
    private static String string(JsonObject object, String key) { String value = optional(object,key); if(value==null)throw new IllegalStateException("Missing protocol field"); return value; }
    private static JsonObject object(JsonObject object, String key) { JsonElement value = object.get(key); return value == null || value.isJsonNull() ? null : value.getAsJsonObject(); }
    private static JsonArray array(JsonObject object, String key) { return object.getAsJsonArray(key); }

    private static final class Session implements AutoCloseable {
        private final Process process;
        private final Writer input;
        private final ExecutorService writes = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "openspec-codex-stdin"); thread.setDaemon(true); return thread;
        });
        private final BlockingQueue<Object> incoming = new ArrayBlockingQueue<>(128);
        private final Deque<JsonObject> notifications = new ArrayDeque<>();
        private final CancellationToken token;
        private final long deadline;
        private final LongSupplier nanoTime;
        private int sequence;
        private String threadId, turnId;
        private AccountState accountBaseline;
        private CodexProtocol.TokenUsage usage,previousUsage;
        private final TurnTerminalState terminal = new TurnTerminalState();
        private volatile boolean closed, invalidStream;
        Session(Process process, Duration timeout, CancellationToken token, LongSupplier nanoTime) {
            this.nanoTime=nanoTime;
            this.process=process; this.token=token; this.deadline=nanoTime.getAsLong()+(timeout.compareTo(Duration.ofHours(1)) > 0 ? Duration.ofHours(1).toNanos() : timeout.toNanos());
            input=new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
            Thread stdout = new Thread(() -> read(process.getInputStream()), "openspec-codex-stdout"); stdout.setDaemon(true); stdout.start();
            Thread stderr = new Thread(() -> { try(InputStream stream=process.getErrorStream()) { byte[] buffer=new byte[2048]; while(!closed && stream.read(buffer)!=-1) { /* Discard bounded chunks: stderr may contain private context. */ } } catch(IOException ignored) {} }, "openspec-codex-stderr"); stderr.setDaemon(true); stderr.start();
        }
        void initialize(Path root) throws AiApiException {
            JsonObject params=new JsonObject(), info=new JsonObject(), capabilities=new JsonObject();
            info.addProperty("name","openspec_intellij"); info.addProperty("version","1.0"); params.add("clientInfo",info); capabilities.addProperty("experimentalApi",true); params.add("capabilities",capabilities);
            JsonObject initialized=rpc("initialize",params);
            CodexProtocol.Initialized handshake=CodexProtocol.initialized(initialized);
            String agent=handshake.userAgent(); String os=handshake.platformOs();
            if (!agent.contains("/"+SUPPORTED_VERSION+" ")) throw failure("Unsupported Codex version; the reviewed protocol baseline is 0.160.0.");
            if (!Set.of("linux","macos").contains(os)) throw failure("Unsupported Codex OS for restricted read access; Linux/macOS are required.");
            send(null,"initialized",null);
            JsonObject config=object(rpc("config/read",new JsonObject()),"config");
            validateConfig(config,root);
        }
        AccountState account() throws AiApiException {
            JsonObject params=new JsonObject(); params.addProperty("refreshToken",false);
            CodexProtocol.AccountRead account=CodexProtocol.account(rpc("account/read",params));
            String mode=account.reportedMode(),email=account.email();
            CodexProtocol.WorkspaceRouting routing=account.routing();
            String id=routing==null?null:routing.accountId();
            boolean known=id!=null && !id.isBlank();
            String key=known ? "id:"+id+":origin:"+routing.backendOrigin()+":routing:"+routing.routingOverride()+":plan:"+account.planType() : email!=null && !email.isBlank()?"email:"+email:null;
            if (key == null) return new AccountState(mode, "", false);
            try { return new AccountState(mode, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((mode + ":" + key).getBytes(StandardCharsets.UTF_8))), known); }
            catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        }
        String limitStatus(String auth) throws AiApiException {
            if (!"chatgpt".equals(auth)) return " Limits unavailable.";
            try {
                JsonObject limits = rpc("account/rateLimits/read", new JsonObject());
                return switch(CodexProtocol.usage(limits)) {
                    case ALLOWED -> " Included usage currently allowed.";
                    case UNAVAILABLE -> " Included usage currently unavailable; check Codex account limits.";
                    case UNKNOWN -> " Included-usage availability unknown.";
                };
            } catch (AiApiException | RuntimeException ignored) { if (ignored instanceof AiOperationCancelledException cancelled) throw cancelled; check(); return " Limits unavailable; no recovery is inferred."; }
        }
        void validateConfig(JsonObject config,Path root) throws AiApiException {
            if (!PROFILE.equals(optional(config,"default_permissions")) || !"never".equals(optional(config,"approval_policy")) || !"disabled".equals(optional(config,"web_search"))) throw failure("Codex configuration conflicts with the reviewed profile.");
            String chatgptBase = optional(config, "chatgpt_base_url");
            if (chatgptBase != null && !Set.of("https://chatgpt.com/backend-api", "https://chatgpt.com/backend-api/").contains(chatgptBase)) throw failure("Codex uses an unsupported ChatGPT endpoint in reviewed context mode.");
            String provider = optional(config, "model_provider");
            if (provider != null && !"openai".equals(provider)) throw failure("Codex uses an unsupported model provider in reviewed context mode.");
            JsonObject customProviders = object(config, "model_providers");
            if (customProviders != null && !customProviders.isEmpty()) throw failure("Custom Codex model providers are unsupported in reviewed context mode.");
            JsonObject profile=object(object(config,"permissions"),PROFILE);
            if(optional(profile,"extends")!=null) throw failure("Codex reviewed profile must not inherit broader permissions.");
            JsonObject filesystem=object(profile,"filesystem");
            for (Map.Entry<String,JsonElement> entry: filesystem.entrySet()) {
                if("glob_scan_max_depth".equals(entry.getKey()) && entry.getValue().isJsonNull()) continue;
                if(!root.toString().equals(entry.getKey()) || !"read".equals(entry.getValue().getAsString())) throw failure("Codex profile includes filesystem permissions outside reviewed context.");
            }
            JsonObject network = object(profile,"network");
            for (Map.Entry<String, JsonElement> entry : network.entrySet()) if (!"enabled".equals(entry.getKey()) && !entry.getValue().isJsonNull()) throw failure("Codex reviewed profile includes unsupported network policy additions.");
            if(!filesystem.has(root.toString()) || network.get("enabled").getAsBoolean()) throw failure("Codex profile does not enforce restricted reads/network.");
            JsonElement roots=profile.get("workspace_roots"); if(roots!=null&&!roots.isJsonNull()) throw failure("Codex profile includes additional workspace roots.");
            for(String key:List.of("mcp_servers","hooks","model_instructions_file","notify","model_catalog_json","experimental_compact_prompt_file","openai_base_url")) {
                JsonElement value=config.get(key);
                if(value!=null&&!value.isJsonNull() && !(value.isJsonObject()&&value.getAsJsonObject().isEmpty()) && !(value.isJsonArray()&&value.getAsJsonArray().isEmpty())) throw failure("Codex config contains unsupported "+key+"; reviewed context cannot be guaranteed.");
            }
            JsonObject plugins=object(config,"plugins");
            if(plugins!=null)for(JsonElement value:plugins.asMap().values())if(!value.isJsonObject() || !value.getAsJsonObject().has("enabled") || value.getAsJsonObject().get("enabled").getAsBoolean())throw failure("Enabled Codex plugins are unsupported in reviewed context mode.");
            JsonObject features=object(config,"features");
            for(String feature:DISABLED_FEATURES) if(!features.has(feature)||features.get(feature).getAsBoolean())throw failure("Codex tool restriction was not accepted.");
            if(config.get("project_doc_max_bytes").getAsInt()!=0 || object(config,"skills").get("include_instructions").getAsBoolean() || object(object(config,"cloud"),"skills").get("enabled").getAsBoolean()) throw failure("Codex context expansion was not disabled.");
        }
        JsonObject rpc(String method,JsonObject params) throws AiApiException {
            int id=++sequence; send(id,method,params);
            while(true) {
                JsonObject event=receive(); rejectRequest(event);
                CodexProtocol.Frame frame = CodexProtocol.frame(event);
                if (frame instanceof CodexProtocol.NotificationFrame) {
                    if(notifications.size()>=128)throw failure("Codex sent too many pending events.");
                    notifications.add(event); continue;
                }
                if (frame instanceof CodexProtocol.FailureFrame error) {
                    if (error.id() != id) throw failure("Codex response correlation failed.");
                    throw failure("Codex rejected "+method+"; check CLI configuration/authentication.");
                }
                if (!(frame instanceof CodexProtocol.SuccessFrame success) || success.id() != id) throw failure("Codex response correlation failed.");
                check(); return success.result();
            }
        }
        void rejectRequest(JsonObject event) throws AiApiException {
            if(event.has("method")&&event.has("id")) {
                JsonObject response=new JsonObject();response.add("id",event.get("id"));JsonObject error=new JsonObject();error.addProperty("code",-32601);error.addProperty("message","Approvals and tools are disabled in OpenSpec reviewed context mode");response.add("error",error);write(response);
                throw failure("Codex requested an unsupported approval/tool action; the turn was stopped.");
            }
        }
        JsonObject next() throws AiApiException { check(); return notifications.isEmpty()?receive():notifications.removeFirst(); }
        JsonObject receive() throws AiApiException {
            while(true) {
                check();
                try { Object event=incoming.poll(50,TimeUnit.MILLISECONDS);if(event instanceof JsonObject json){check();return json;}if(event!=null)throw failure("Codex stream closed or violated the bounded JSON protocol."); }
                catch(InterruptedException e) {terminal.finish(TurnTerminalState.Status.CANCELLED, "");Thread.currentThread().interrupt();throw new AiOperationCancelledException();}
            }
        }
        void check() throws AiApiException {
            if(token.isCancelled()||Thread.currentThread().isInterrupted()) {terminal.finish(TurnTerminalState.Status.CANCELLED, "");interrupt();throw new AiOperationCancelledException();}
            if (invalidStream) throw failure("Codex stream violated the bounded JSON protocol; no result was applied.");
            if(nanoTime.getAsLong()-deadline>=0) {terminal.finish(TurnTerminalState.Status.TIMED_OUT, "");interrupt();throw failure("Codex operation timed out; no result was applied.");}
        }
        void interrupt() { if(threadId!=null&&turnId!=null)try {JsonObject params=new JsonObject();params.addProperty("threadId",threadId);params.addProperty("turnId",turnId);send(++sequence,"turn/interrupt",params);}catch(AiApiException ignored) {} }
        void send(Integer id,String method,JsonObject params) throws AiApiException {JsonObject value=new JsonObject();if(id!=null)value.addProperty("id",id);value.addProperty("method",method);if(params!=null)value.add("params",params);write(value);}
        void write(JsonObject value) throws AiApiException {
            boolean interruption = "turn/interrupt".equals(optional(value, "method"));
            long writeDeadline = interruption ? System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100) : deadline;
            Future<?> write = writes.submit(() -> {
                try { input.write(value.toString()); input.write('\n'); input.flush(); }
                catch (IOException e) { throw new UncheckedIOException(e); }
            });
            while (true) {
                if ((interruption ? System.nanoTime() - writeDeadline >= 0 : nanoTime.getAsLong() - deadline >= 0) || (!interruption && (token.isCancelled() || Thread.currentThread().isInterrupted()))) {
                    terminal.finish((interruption ? System.nanoTime() - writeDeadline >= 0 : nanoTime.getAsLong() - deadline >= 0) ? TurnTerminalState.Status.TIMED_OUT : TurnTerminalState.Status.CANCELLED, "");
                    write.cancel(true); close();
                    checkCancellation(token);
                    throw failure("Codex input write timed out; no result was applied.");
                }
                try { write.get(25, TimeUnit.MILLISECONDS); return; }
                catch (TimeoutException ignored) { /* Poll cancellation while stdin is backpressured. */ }
                catch (InterruptedException e) {
                    terminal.finish(TurnTerminalState.Status.CANCELLED, "");
                    write.cancel(true); Thread.currentThread().interrupt(); close();
                    throw new AiOperationCancelledException();
                }
                catch (ExecutionException e) { throw failure("Codex input stream closed."); }
            }
        }
        void read(InputStream stream) {
            try(Reader reader=new InputStreamReader(stream,StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT))) {
                StringBuilder line=new StringBuilder();int ch;
                while(!closed&&(ch=reader.read())!=-1) {
                    if(ch=='\n') {if(line.isEmpty())continue;JsonObject value=ArtifactEnvelopeJson.parse(line.toString());if(!incoming.offer(value))throw new IOException("event overflow");line.setLength(0);}
                    else {if(line.length()>=256*1024)throw new IOException("oversized line");line.append((char)ch);}
                }
            } catch(IOException|RuntimeException ignored) { if (!closed) invalidStream = true; } finally {incoming.offer(Boolean.FALSE);}
        }
        @Override public synchronized void close() {
            if (closed) return;
            closed=true;
            terminal.finish(TurnTerminalState.Status.FAILED, "");
            writes.shutdownNow();
            List<ProcessHandle> descendants = process.descendants().toList();
            descendants.forEach(ProcessHandle::destroy);
            process.destroy();
            try {if(!process.waitFor(200,TimeUnit.MILLISECONDS)) process.destroyForcibly();}
            catch(InterruptedException e){Thread.currentThread().interrupt();process.destroyForcibly();}
            descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
            try {input.close();process.getInputStream().close();process.getErrorStream().close();}catch(IOException ignored){}
        }
    }
}
