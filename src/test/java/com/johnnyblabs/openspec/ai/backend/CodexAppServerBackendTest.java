package com.johnnyblabs.openspec.ai.backend;

import com.google.gson.*;
import com.johnnyblabs.openspec.ai.AiApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class CodexAppServerBackendTest {
    @TempDir Path context;
    private static JsonArray capture() throws IOException {
        try (InputStream stream = CodexAppServerBackendTest.class.getResourceAsStream("/fixtures/codex/0.160.0/handshake.json")) {
            assertNotNull(stream); return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray();
        }
    }
    private static JsonObject captured(int id) throws IOException {
        for (JsonElement value : capture()) if (value.getAsJsonObject().has("id") && value.getAsJsonObject().get("id").getAsInt()==id) return value.getAsJsonObject().getAsJsonObject("result").deepCopy();
        throw new AssertionError("Missing captured response");
    }
    private AiRequest request() {return new AiRequest("Reviewed prompt", "selected-model", context, Duration.ofSeconds(3));}

    @Test void capturedSignedOutHandshakeAndRealCatalogAreParsedWithoutInference() throws Exception {
        MockProcess process=new MockProcess(context, Mode.SIGNED_OUT);
        var backend=backend(process);
        BackendStatus status=backend.probe();
        assertFalse(status.available());assertEquals("signedOut",status.authMode());assertEquals("0.160.0",status.version());
        assertTrue(process.destroyed.get()); assertFalse(process.methods.contains("turn/start"));
        MockProcess catalog=new MockProcess(context, Mode.SIGNED_OUT);
        List<ModelDescriptor> models=backend(catalog).models();
        JsonArray expected=captured(5).getAsJsonArray("data");
        assertEquals(expected.size(),models.size()); assertEquals(expected.get(0).getAsJsonObject().get("id").getAsString(),models.get(0).id());
    }
    @Test void explicitApiBillingAndSubscriptionModeAreDistinguished() throws Exception {
        assertEquals("apikey",backend(new MockProcess(context,Mode.API_KEY)).probe().authMode());
        assertEquals("chatgpt",backend(new MockProcess(context,Mode.SUCCESS)).probe().authMode());
    }
    @Test void streamsCorrelatedDeltaAndReturnsCompletedFinalAnswerOnly() throws Exception {
        MockProcess process=new MockProcess(context,Mode.SUCCESS);
        List<String> deltas=new ArrayList<>();
        AiResult result=backend(process).generate(request(),CancellationToken.NONE,deltas::add);
        assertEquals("final answer",result.text());assertEquals("local-codex",result.backendId());
        assertEquals(List.of("final ","answer"),deltas);
        assertEquals(List.of("initialize","initialized","config/read","account/read","thread/start","turn/start"),process.methods);
        assertTrue(process.destroyed.get());
        assertEquals("never",process.threadParams.get("approvalPolicy").getAsString());
        assertEquals("openspec_reviewed",process.threadParams.get("permissions").getAsString());
        assertFalse(process.threadParams.has("sandbox"));assertTrue(process.threadParams.getAsJsonArray("environments").isEmpty());
    }
    @Test void outputSchemaAndPromptUseStdinWhileExecutableAndOverridesUseArgumentArray() throws Exception {
        MockProcess process=new MockProcess(context,Mode.SUCCESS); List<String> args=new ArrayList<>();
        var backend=new CodexAppServerBackend("/opt/codex with spaces",(arguments,cwd)->{args.addAll(arguments);return process;});
        JsonObject schema=new JsonObject();schema.addProperty("type","object");
        backend.generate(new AiRequest("$(touch forbidden) ; secret",null,context,Duration.ofSeconds(3),schema),CancellationToken.NONE,ignored->{});
        assertEquals("/opt/codex with spaces",args.get(0));assertFalse(args.contains("sh"));assertTrue(args.stream().noneMatch(x->x.contains("touch forbidden")));
        assertEquals(schema,process.turnParams.getAsJsonObject("outputSchema"));assertTrue(process.turnParams.getAsJsonArray("input").get(0).getAsJsonObject().get("text").getAsString().contains("$(touch"));
    }
    @Test void failedOrInterruptedTerminalCannotReturnPartialOutput() throws Exception {
        for(Mode mode:List.of(Mode.FAILED,Mode.INTERRUPTED,Mode.NO_FINAL)) {
            MockProcess process=new MockProcess(context,mode);
            assertThrows(AiApiException.class,()->backend(process).generate(request(),CancellationToken.NONE,ignored->{}));
            assertTrue(process.destroyed.get());assertTrue(process.methods.contains("turn/start"));
        }
    }
    @Test void serverApprovalRequestFailsClosedAndStopsProcess() throws Exception {
        MockProcess process=new MockProcess(context,Mode.APPROVAL);
        AiApiException error=assertThrows(AiApiException.class,()->backend(process).generate(request(),CancellationToken.NONE,ignored->{}));
        assertTrue(error.getMessage().contains("approval/tool"));assertTrue(process.deniedRequest.get());assertTrue(process.destroyed.get());
    }
    @Test void cancellationAndDeadlineStopProcessAndSendInterrupt() throws Exception {
        MockProcess process=new MockProcess(context,Mode.HANG);AtomicBoolean cancelled=new AtomicBoolean();
        Thread cancel=new Thread(()->{try {while(!process.methods.contains("turn/start"))Thread.sleep(5);Thread.sleep(30);cancelled.set(true);}catch(InterruptedException ignored){}});cancel.start();
        assertThrows(AiApiException.class,()->backend(process).generate(request(),cancelled::get,ignored->{}));cancel.join(1000);
        assertTrue(process.methods.contains("turn/interrupt"));assertTrue(process.destroyed.get());
        MockProcess timeout=new MockProcess(context,Mode.HANG);
        assertThrows(AiApiException.class,()->backend(timeout).generate(new AiRequest("prompt",null,context,Duration.ofMillis(250)),CancellationToken.NONE,ignored->{}));
        assertTrue(timeout.destroyed.get());
    }
    @Test void incompatibleVersionOsOrConfigIsVisibleAndNeverStartsTurn() throws Exception {
        for(Mode mode:List.of(Mode.WRONG_VERSION,Mode.WINDOWS,Mode.BROAD_PROFILE,Mode.MCP_CONFIG,Mode.PLUGIN_CONFIG,Mode.CUSTOM_PROVIDER,Mode.CUSTOM_ENDPOINT)) {
            MockProcess process=new MockProcess(context,mode);BackendStatus status=backend(process).probe();
            assertFalse(status.available());assertFalse(status.detail().isBlank());assertFalse(process.methods.contains("thread/start"));assertFalse(process.methods.contains("turn/start"));assertTrue(process.destroyed.get());
        }
    }
    @Test void malformedOversizedStreamAndWrongRpcCorrelationFailClosed() throws Exception {
        for(Mode mode:List.of(Mode.MALFORMED,Mode.OVERSIZED,Mode.WRONG_ID)) {
            MockProcess process=new MockProcess(context,mode);
            assertThrows(AiApiException.class,()->backend(process).generate(request(),CancellationToken.NONE,ignored->{}));assertTrue(process.destroyed.get());assertTrue(process.methods.contains("turn/start"));
        }
    }
    @Test void changedAuthenticationCannotStartInferenceAfterBillingReview() throws Exception {
        MockProcess process = new MockProcess(context, Mode.API_KEY);
        AiApiException failure = assertThrows(AiApiException.class, () -> backend(process).generate(
                new AiRequest("prompt", null, context, Duration.ofSeconds(3), null, "chatgpt"), CancellationToken.NONE, ignored -> {}));
        assertTrue(failure.getMessage().contains("authentication mode changed"));
        assertFalse(process.methods.contains("thread/start")); assertFalse(process.methods.contains("turn/start"));
        assertTrue(process.destroyed.get());
    }
    @Test void hungStdinCannotBypassCancellationOrDeadline() throws Exception {
        MockProcess process = new MockProcess(context, Mode.BLOCK_STDIN);
        long started = System.nanoTime();
        assertThrows(AiApiException.class, () -> backend(process).generate(
                new AiRequest("prompt", null, context, Duration.ofMillis(150)), CancellationToken.NONE, ignored -> {}));
        assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(2)) < 0);
        assertTrue(process.destroyed.get());
    }
    @Test void earlyInterleavedNotificationsArePreservedUntilTurnResponse() throws Exception {
        MockProcess process = new MockProcess(context, Mode.EARLY_DELTA);
        List<String> deltas = new ArrayList<>();
        assertEquals("final answer", backend(process).generate(request(), CancellationToken.NONE, deltas::add).text());
        assertEquals(List.of("early ", "final ", "answer"), deltas);
    }
    @Test void broadenedThreadEnvironmentIsRejectedBeforeTurn() throws Exception {
        MockProcess process = new MockProcess(context, Mode.BROAD_ENVIRONMENT);
        AiApiException error = assertThrows(AiApiException.class, () -> backend(process).generate(request(), CancellationToken.NONE, ignored -> {}));
        assertTrue(error.getMessage().contains("execution environment"));
        assertTrue(process.methods.contains("thread/start")); assertFalse(process.methods.contains("turn/start")); assertTrue(process.destroyed.get());
    }
    @Test void stubbornDescendantIsForcedEvenWhenParentAlreadyExited() throws Exception {
        MockProcess process = new MockProcess(context, Mode.HANG_CHILD);
        backend(process).generate(request(), CancellationToken.NONE, ignored -> {});
        assertTrue(process.destroyed.get()); assertTrue(process.childForced.get());
    }
    @Test void conversationUsesPersistedThreadResumeWithFreshTransportAndStableScope() throws Exception {
        List<MockProcess> processes = new ArrayList<>();
        var backend = conversationBackend(processes, Mode.SUCCESS, Mode.SUCCESS, Mode.SUCCESS);
        assertEquals("final answer", backend.generateConversation(request(), CancellationToken.NONE, ignored -> {}, "project/model/review-scope", false).text());
        assertFalse(processes.get(0).threadParams.get("ephemeral").getAsBoolean());
        assertEquals("final answer", backend.generateConversation(request(), CancellationToken.NONE, ignored -> {}, "project/model/review-scope", false).text());
        assertTrue(processes.get(1).methods.contains("thread/resume")); assertFalse(processes.get(1).methods.contains("thread/start"));
        assertEquals("thread-test", processes.get(1).threadParams.get("threadId").getAsString());
        assertFalse(processes.get(1).threadParams.has("environments"));
        assertEquals(processes.get(0).root, processes.get(1).root); assertTrue(processes.stream().allMatch(p -> p.destroyed.get()));
        backend.generateConversation(request(), CancellationToken.NONE, ignored -> {}, "different reviewed scope", true);
        assertTrue(processes.get(2).methods.contains("thread/start")); assertNotEquals(processes.get(0).root, processes.get(2).root);
        Path stableRoot = processes.get(2).root; backend.closeConversation(); assertFalse(java.nio.file.Files.exists(stableRoot));
    }
    @Test void changedScopeAccountOrReviewedIdentityCannotResumeOrStartTurn() throws Exception {
        List<MockProcess> processes = new ArrayList<>(); var backend = conversationBackend(processes, Mode.SUCCESS, Mode.OTHER_ACCOUNT);
        backend.generateConversation(request(), CancellationToken.NONE, ignored -> {}, "scope", false);
        assertThrows(AiApiException.class, () -> backend.generateConversation(request(), CancellationToken.NONE, ignored -> {}, "changed-scope", false));
        assertEquals(1, processes.size());
        AiApiException changed = assertThrows(AiApiException.class, () -> backend.generateConversation(request(), CancellationToken.NONE, ignored -> {}, "scope", false));
        assertTrue(changed.getMessage().contains("account changed")); assertFalse(processes.get(1).methods.contains("thread/resume")); assertFalse(processes.get(1).methods.contains("turn/start")); backend.closeConversation();
        MockProcess fresh = new MockProcess(context, Mode.SUCCESS);
        AiApiException identity = assertThrows(AiApiException.class, () -> backend(fresh).generate(new AiRequest("prompt", null, context, Duration.ofSeconds(3), null, "chatgpt", "wrong-fingerprint"), CancellationToken.NONE, ignored -> {}));
        assertTrue(identity.getMessage().contains("identity changed")); assertFalse(fresh.methods.contains("turn/start"));
    }
    @Test void unavailableAccountIdentityAndFailedConversationRequireVisibleFreshStart() throws Exception {
        List<MockProcess> processes = new ArrayList<>(); var api = conversationBackend(processes, Mode.API_KEY);
        assertThrows(AiApiException.class, () -> api.generateConversation(request(), CancellationToken.NONE, ignored -> {}, "scope", false));
        assertFalse(processes.get(0).methods.contains("thread/start")); api.closeConversation();
        List<MockProcess> failedProcesses = new ArrayList<>(); var failed = conversationBackend(failedProcesses, Mode.FAILED, Mode.SUCCESS);
        assertThrows(AiApiException.class, () -> failed.generateConversation(request(), CancellationToken.NONE, ignored -> {}, "scope", false));
        assertThrows(AiApiException.class, () -> failed.generateConversation(request(), CancellationToken.NONE, ignored -> {}, "scope", false));
        assertEquals(1, failedProcesses.size());
        assertEquals("final answer", failed.generateConversation(request(), CancellationToken.NONE, ignored -> {}, "scope", true).text()); failed.closeConversation();
    }
    private CodexAppServerBackend conversationBackend(List<MockProcess> processes, Mode... modes) {
        return new CodexAppServerBackend("codex", (arguments, root) -> { MockProcess process = new MockProcess(root, modes[processes.size()]); processes.add(process); return process; });
    }
    @Test void changedRoutingAndAccountDefaultRequireExplicitNewConversation() throws Exception {
        for (Mode changed : List.of(Mode.ROUTING_CHANGED, Mode.DEFAULT_CHANGED)) {
            List<MockProcess> processes = new ArrayList<>(); var backend = conversationBackend(processes, Mode.SUCCESS, changed);
            AiRequest defaultModel = new AiRequest("prompt", null, context, Duration.ofSeconds(3));
            backend.generateConversation(defaultModel, CancellationToken.NONE, ignored -> {}, "scope", false);
            AiApiException error = assertThrows(AiApiException.class, () -> backend.generateConversation(defaultModel, CancellationToken.NONE, ignored -> {}, "scope", false));
            assertTrue(error.getMessage().contains(changed == Mode.ROUTING_CHANGED ? "account changed" : "default model changed"));
            assertFalse(processes.get(1).methods.contains("thread/resume")); assertFalse(processes.get(1).methods.contains("turn/start")); backend.closeConversation();
        }
    }
    @Test void accountNotificationDuringTurnInterruptsAndRejectsOutput() throws Exception {
        MockProcess process = new MockProcess(context, Mode.ACCOUNT_UPDATED);
        AiApiException error = assertThrows(AiApiException.class, () -> backend(process).generate(request(), CancellationToken.NONE, ignored -> {}));
        assertTrue(error.getMessage().contains("account changed during the turn"));
        assertTrue(process.methods.contains("turn/interrupt")); assertTrue(process.destroyed.get());
    }
    @Test void delayedSameAccountNotificationIsVerifiedAndDoesNotAbortTurn() throws Exception {
        MockProcess process = new MockProcess(context, Mode.SAME_ACCOUNT_UPDATED);
        assertEquals("final answer", backend(process).generate(request(), CancellationToken.NONE, ignored -> {}).text());
        assertEquals(2, process.accountReads); assertFalse(process.methods.contains("turn/interrupt")); assertTrue(process.destroyed.get());
    }
    @Test void generatedSchemasContainRequiredProtocolAndPermissionFields() throws Exception {
        for(String name:List.of("ThreadStartParams","ThreadStartResponse","TurnStartParams","TurnCompletedNotification","AgentMessageDeltaNotification","ItemCompletedNotification","GetAccountResponse","ModelListResponse","ThreadResumeParams","ThreadResumeResponse")) {
            try(InputStream stream=getClass().getResourceAsStream("/fixtures/codex/0.160.0/schema/"+name+".json")) {
                assertNotNull(stream);JsonObject schema=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();assertEquals(name,schema.get("title").getAsString());
                if(name.equals("ThreadStartParams")){assertTrue(schema.getAsJsonObject("properties").has("permissions"));assertTrue(schema.getAsJsonObject("properties").has("environments"));}
                if(name.equals("ThreadStartResponse"))assertTrue(schema.getAsJsonObject("properties").has("activePermissionProfile"));
            }
        }
    }
    private CodexAppServerBackend backend(MockProcess process) { return new CodexAppServerBackend("codex",(arguments,cwd)->{process.root=cwd;return process;}); }
    private enum Mode {SIGNED_OUT, API_KEY, SUCCESS, FAILED, INTERRUPTED, NO_FINAL, APPROVAL, HANG, WRONG_VERSION, WINDOWS, BROAD_PROFILE, MCP_CONFIG, PLUGIN_CONFIG, MALFORMED, OVERSIZED, WRONG_ID, BLOCK_STDIN, EARLY_DELTA, BROAD_ENVIRONMENT, HANG_CHILD, OTHER_ACCOUNT, ROUTING_CHANGED, DEFAULT_CHANGED, ACCOUNT_UPDATED, SAME_ACCOUNT_UPDATED, CUSTOM_PROVIDER, CUSTOM_ENDPOINT}
    /** Reactive mock uses real captured handshake/config/model shapes; turn events are deliberate adversarial fixtures. */
    private static final class MockProcess extends Process {
        private final PipedInputStream stdout=new PipedInputStream(1024*1024);
        private final PipedOutputStream server;
        private final ByteArrayOutputStream stdin=new ByteArrayOutputStream();
        final List<String> methods=new CopyOnWriteArrayList<>();
        final AtomicBoolean destroyed=new AtomicBoolean(),deniedRequest=new AtomicBoolean(),childForced=new AtomicBoolean();
        private final Mode mode; private int accountReads; Path root; JsonObject threadParams,turnParams;
        MockProcess(Path root,Mode mode) throws IOException {this.root=root;this.mode=mode;server=new PipedOutputStream(stdout);}
        @Override public OutputStream getOutputStream(){return new OutputStream(){
            @Override public void write(int b)throws IOException {if(mode==Mode.BLOCK_STDIN){while(!destroyed.get())try{Thread.sleep(5);}catch(InterruptedException ignored){}throw new IOException("closed");}synchronized(stdin){if(b=='\n'){String line=stdin.toString(StandardCharsets.UTF_8);stdin.reset();respond(JsonParser.parseString(line).getAsJsonObject());}else stdin.write(b);}}
        };}
        private void respond(JsonObject req)throws IOException {
            if(!req.has("method")){deniedRequest.set(req.has("error"));return;}
            String method=req.get("method").getAsString();methods.add(method);
            if(!req.has("id"))return;
            JsonObject result=new JsonObject();
            switch(method){
                case "initialize" -> {result=captured(1);if(mode==Mode.WRONG_VERSION)result.addProperty("userAgent","openspec/9.0.0 bad");if(mode==Mode.WINDOWS)result.addProperty("platformOs","windows");}
                case "config/read" -> {result=captured(3);JsonObject config=result.getAsJsonObject("config");JsonObject fs=config.getAsJsonObject("permissions").getAsJsonObject("openspec_reviewed").getAsJsonObject("filesystem");fs.remove("/reviewed/context");fs.addProperty(root.toString(),"read");if(mode==Mode.BROAD_PROFILE)fs.addProperty(":root","read");if(mode==Mode.MCP_CONFIG){JsonObject mcp=new JsonObject();mcp.add("server",new JsonObject());config.add("mcp_servers",mcp);}if(mode==Mode.CUSTOM_PROVIDER){JsonObject provider=new JsonObject();provider.addProperty("base_url","https://unreviewed.example.test");config.getAsJsonObject("model_providers").add("openai",provider);}if(mode==Mode.CUSTOM_ENDPOINT)config.addProperty("chatgpt_base_url","https://unreviewed.example.test");if(mode==Mode.PLUGIN_CONFIG){JsonObject plugin=new JsonObject();plugin.addProperty("enabled",true);config.getAsJsonObject("plugins").add("extra",plugin);}}
                case "account/read" -> {accountReads++;result=captured(2);if(mode!=Mode.SIGNED_OUT){JsonObject account=new JsonObject();account.addProperty("type",mode==Mode.API_KEY?"apiKey":"chatgpt");account.addProperty("planType","plus");if(mode==Mode.API_KEY)account.add("email",JsonNull.INSTANCE);else account.addProperty("email",mode==Mode.OTHER_ACCOUNT?"other@example.test":"fixture@example.test");result.add("account",account);if(mode!=Mode.API_KEY){JsonObject routing=new JsonObject();routing.addProperty("chatgptAccountId",(mode==Mode.OTHER_ACCOUNT || mode==Mode.ACCOUNT_UPDATED && accountReads>1)?"other-account":"fixture-account");routing.addProperty("backendOrigin",mode==Mode.ROUTING_CHANGED?"https://another-route.example.test":"https://chatgpt.com");routing.addProperty("accountRoutingOverride","NO_CONSTRAINT");result.add("workspaceRouting",routing);}}}
                case "model/list" -> {result=captured(5);if(mode==Mode.DEFAULT_CHANGED){for(JsonElement entry:result.getAsJsonArray("data")){JsonObject model=entry.getAsJsonObject();if(model.get("isDefault").getAsBoolean()){model.addProperty("id","changed-default");model.addProperty("model","changed-default");}}}}
                case "thread/start", "thread/resume" -> {threadParams=req.getAsJsonObject("params").deepCopy();result=captured(4);result.getAsJsonObject("thread").addProperty("id","thread-test");result.addProperty("cwd",root.toString());if(threadParams.has("model"))result.add("model",threadParams.get("model"));if(method.equals("thread/resume"))result.getAsJsonObject("thread").add("id",threadParams.get("threadId"));if(mode==Mode.BROAD_ENVIRONMENT){JsonObject environment=new JsonObject();environment.addProperty("environmentId","local");result.getAsJsonObject("thread").getAsJsonArray("environments").add(environment);}}
                case "turn/start" -> {turnParams=req.getAsJsonObject("params").deepCopy();JsonObject turn=new JsonObject();turn.addProperty("id","turn-test");turn.addProperty("status","inProgress");turn.add("items",new JsonArray());turn.add("error",JsonNull.INSTANCE);result.add("turn",turn);}
            }
            JsonObject response=new JsonObject();response.add("id",req.get("id"));response.add("result",result);
            if(mode==Mode.WRONG_ID&&method.equals("turn/start"))response.addProperty("id",999);
            if (mode == Mode.EARLY_DELTA && method.equals("turn/start")) {JsonObject early=params();early.addProperty("itemId","message-1");early.addProperty("delta","early ");event("item/agentMessage/delta",early);}
            emit(response);
            if(method.equals("turn/start"))turnEvents();
        }
        private void turnEvents()throws IOException {
            if(mode==Mode.HANG)return;
            if(mode==Mode.MALFORMED){server.write("broken json\n".getBytes(StandardCharsets.UTF_8));return;}
            if(mode==Mode.OVERSIZED){server.write(("x".repeat(256*1024+1)+"\n").getBytes(StandardCharsets.UTF_8));return;}
            if(mode==Mode.APPROVAL){JsonObject approval=new JsonObject();approval.addProperty("id","approval-1");approval.addProperty("method","item/commandExecution/requestApproval");approval.add("params",new JsonObject());emit(approval);return;}
            if (mode == Mode.ACCOUNT_UPDATED || mode == Mode.SAME_ACCOUNT_UPDATED) {JsonObject account=new JsonObject();account.addProperty("authMode","apiKey");account.add("planType",JsonNull.INSTANCE);event("account/updated",account);if(mode==Mode.ACCOUNT_UPDATED)return;}
            JsonObject wrong=params();wrong.addProperty("turnId","other-turn");wrong.addProperty("itemId","ignored");wrong.addProperty("delta","WRONG");event("item/agentMessage/delta",wrong);
            for(String delta:List.of("final ","answer")){JsonObject p=params();p.addProperty("itemId","message-1");p.addProperty("delta",delta);event("item/agentMessage/delta",p);}
            if(mode!=Mode.NO_FINAL){JsonObject p=params(),item=new JsonObject();item.addProperty("type","agentMessage");item.addProperty("id","message-1");item.addProperty("text","final answer");item.addProperty("phase","final_answer");p.add("item",item);p.addProperty("completedAtMs",0);event("item/completed",p);}
            JsonObject p=new JsonObject();p.addProperty("threadId","thread-test");JsonObject turn=new JsonObject();turn.addProperty("id","turn-test");turn.addProperty("status",mode==Mode.FAILED?"failed":mode==Mode.INTERRUPTED?"interrupted":"completed");turn.add("items",new JsonArray());turn.add("error",JsonNull.INSTANCE);p.add("turn",turn);event("turn/completed",p);
        }
        private JsonObject params(){JsonObject p=new JsonObject();p.addProperty("threadId","thread-test");p.addProperty("turnId","turn-test");return p;}
        private void event(String method,JsonObject params)throws IOException {JsonObject e=new JsonObject();e.addProperty("method",method);e.add("params",params);emit(e);}
        private void emit(JsonObject e)throws IOException {server.write((e+"\n").getBytes(StandardCharsets.UTF_8));server.flush();}
        @Override public InputStream getInputStream(){return stdout;}
        @Override public InputStream getErrorStream(){return InputStream.nullInputStream();}
        @Override public int waitFor(){return 0;}
        @Override public boolean waitFor(long timeout,TimeUnit unit){return destroyed.get();}
        @Override public int exitValue(){if(!destroyed.get())throw new IllegalThreadStateException();return 0;}
        @Override public void destroy(){destroyed.set(true);try{server.close();}catch(IOException ignored){}}
        @Override public Process destroyForcibly(){destroy();return this;}
        @Override public boolean isAlive(){return !destroyed.get();}
        @Override public java.util.stream.Stream<ProcessHandle> descendants(){
            if(mode!=Mode.HANG_CHILD)return java.util.stream.Stream.empty();
            ProcessHandle child=(ProcessHandle)java.lang.reflect.Proxy.newProxyInstance(ProcessHandle.class.getClassLoader(),new Class<?>[]{ProcessHandle.class},(proxy,method,args)->switch(method.getName()) {
                case "destroy" -> false;
                case "destroyForcibly" -> {childForced.set(true);yield true;}
                case "isAlive" -> !childForced.get();
                case "pid" -> 123L;
                default -> throw new UnsupportedOperationException(method.getName());
            });
            return java.util.stream.Stream.of(child);
        }
    }
}
