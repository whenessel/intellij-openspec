package com.johnnyblabs.openspec.ai;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-structure regression guards for the Direct-API-flow hardening. These lock the placement of
 * calls that plain behavioral tests cannot reach (they live on the EDT or inside Swing workers/tasks):
 * blocking credential I/O must sit off the EDT, per-chip Cancel must cancel the single generation
 * rather than the whole Generate-All flow, generation must honor cancellation, and tool-window
 * contents must own their disposable panels. Mirrors {@code WorkflowActionPanelTest.EdtThreadingSafety}.
 */
class DirectApiFlowStructuralGuardTest {

    private static String read(String relPath) throws IOException {
        return Files.readString(Path.of(relPath));
    }

    /** Extract a method body by its signature prefix, brace-matched from the first '{'. */
    private static String methodBody(String content, String signaturePrefix) {
        int sig = content.indexOf(signaturePrefix);
        assertTrue(sig >= 0, "expected to find method: " + signaturePrefix);
        int bodyStart = content.indexOf('{', sig);
        int depth = 0;
        for (int i = bodyStart; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) return content.substring(bodyStart, i + 1);
            }
        }
        throw new AssertionError("unbalanced braces for: " + signaturePrefix);
    }

    private static final String SETTINGS =
            "src/main/java/com/johnnyblabs/openspec/settings/OpenSpecSettingsPanel.java";
    private static final String WORKFLOW =
            "src/main/java/com/johnnyblabs/openspec/toolwindow/WorkflowActionPanel.java";
    private static final String FACTORY =
            "src/main/java/com/johnnyblabs/openspec/toolwindow/OpenSpecToolWindowFactory.java";
    private static final String TW_PANEL =
            "src/main/java/com/johnnyblabs/openspec/toolwindow/OpenSpecToolWindowPanel.java";
    private static final String CONFIGURABLE =
            "src/main/java/com/johnnyblabs/openspec/settings/OpenSpecConfigurable.java";
    private static final String WIZARD_MODEL =
            "src/main/java/com/johnnyblabs/openspec/dialogs/SetupWizardModel.java";
    private static final String WIZARD_DIALOG =
            "src/main/java/com/johnnyblabs/openspec/dialogs/SetupWizardDialog.java";

    @Test
    void onProviderChanged_readsHasApiKeyOffTheEdt() throws IOException {
        String body = methodBody(read(SETTINGS), "private void onProviderChanged()");
        int pooled = body.indexOf("executeOnPooledThread(");
        assertTrue(pooled >= 0, "onProviderChanged must hop to a pooled thread for the keystore read");
        // The blocking hasApiKey (PasswordSafe.get, @RequiresBackgroundThread) must live INSIDE the
        // pooled lambda — never before it on the EDT.
        assertFalse(body.substring(0, pooled).contains("AiCredentialStore.hasApiKey("),
                "hasApiKey must not be read on the EDT before the pooled hop");
        assertTrue(body.substring(pooled).contains("AiCredentialStore.hasApiKey("),
                "hasApiKey must be read inside the pooled lambda");
    }

    @Test
    void testApiConnection_doesCredentialIoInsideTheWorker_notOnTheEdt() throws IOException {
        String body = methodBody(read(SETTINGS), "private void testApiConnection()");
        int worker = body.indexOf("new SwingWorker");
        assertTrue(worker >= 0, "testApiConnection must run its blocking work in a SwingWorker");
        // Nothing touches AiCredentialStore on the EDT before the worker starts (the pre-worker
        // getApiKey() is the panel's own JPasswordField accessor, not the credential store).
        assertFalse(body.substring(0, worker).contains("AiCredentialStore."),
                "credential store I/O must not run on the EDT before the worker");
        String workerBody = body.substring(worker);
        assertTrue(workerBody.contains("AiCredentialStore.getApiKey("),
                "stored-key read must happen inside doInBackground (off the EDT)");
        assertFalse(body.contains("AiCredentialStore.storeApiKey("),
                "connection tests must not persist unsaved credentials; Apply owns writes");
    }

    @Test
    void perChipCancel_cancelsSingleGeneration_notGenerateAll() throws IOException {
        String content = read(WORKFLOW);
        assertTrue(content.contains("-> onCancelGeneration(artifact.id())"),
                "the per-chip context-menu Cancel must route to onCancelGeneration(<that chip's id>)");
        String body = methodBody(content, "private void onCancelGeneration(String artifactId)");
        assertTrue(body.contains(".cancel()"),
                "onCancelGeneration must cancel that artifact's own indicator");
        assertTrue(body.contains("activeGenerations.get(artifactId)"),
                "cancel must look up the indicator by artifact id — not a single shared field that mis-targets");
        assertFalse(body.contains("onCancelGenerateAll"),
                "per-chip cancel must NOT fall through to the Generate-All cancel");
    }

    @Test
    void generation_honorsCancellation() throws IOException {
        String content = read(WORKFLOW);
        assertTrue(content.contains("activeGenerations.put(artifactId, indicator)"),
                "the running task must register its indicator keyed by artifact id so its own cancel reaches it");
        assertTrue(content.contains("activeGenerations.remove(artifactId, indicator)"),
                "the run must deregister its indicator when it ends (conditional remove, so a newer run survives)");
        int generate = content.indexOf("apiService.generateAndApply(instruction, routing)");
        int cancelCheck = content.indexOf("indicator.checkCanceled();", generate);
        int invalidate = content.indexOf("orchestration.invalidateCache(changeName);", generate);
        assertTrue(generate >= 0 && cancelCheck > generate && cancelCheck < invalidate,
                "generation must propagate cancellation after inference and before reporting completion");
        assertTrue(content.contains("catch (com.intellij.openapi.progress.ProcessCanceledException"),
                "a cancellation must propagate as ProcessCanceledException, not be reported as an error");
    }

    @Test
    void configurableApply_persistsKeyOffTheEdt() throws IOException {
        String body = methodBody(read(CONFIGURABLE), "public void apply()");
        int pooled = body.indexOf("executeOnPooledThread(");
        assertTrue(pooled >= 0, "apply() must persist the key off the EDT");
        // The blocking storeApiKey must live inside the pooled hop, never before it on the EDT.
        assertFalse(body.substring(0, pooled).contains("AiCredentialStore.storeApiKey("),
                "storeApiKey must not run on the EDT before the pooled hop");
        assertTrue(body.substring(pooled).contains("AiCredentialStore.storeApiKey("),
                "storeApiKey must run inside the pooled lambda");
    }

    @Test
    void wizardPersist_writesKeyOffTheEdt() throws IOException {
        String body = methodBody(read(WIZARD_MODEL), "public void persist(");
        int pooled = body.indexOf("executeOnPooledThread(");
        assertTrue(pooled >= 0, "persist() (called on the EDT via wizard OK) must write the key off the EDT");
        assertFalse(body.substring(0, pooled).contains("AiCredentialStore.storeApiKey("),
                "the blocking storeApiKey must not run on the EDT before the pooled hop");
        assertTrue(body.substring(pooled).contains("AiCredentialStore.storeApiKey("),
                "storeApiKey must run inside the pooled lambda");
    }

    @Test
    void wizardTestConnection_usesCapturedValuesWithoutPersistingOrMutatingSettings() throws IOException {
        String body = methodBody(read(WIZARD_DIALOG), "private void testApiConnection()");
        int worker = body.indexOf("new SwingWorker");
        assertTrue(worker >= 0, "testApiConnection must run its blocking work in a SwingWorker");
        assertFalse(body.substring(0, worker).contains("AiCredentialStore.storeApiKey("),
                "the key must not be stored on the EDT before the worker");
        assertFalse(body.contains("AiCredentialStore.storeApiKey("), "unsaved wizard keys must not be persisted by Test");
        assertFalse(body.contains("settings.set"), "wizard Test must not change project settings");
        assertTrue(body.substring(worker).contains("testConnection(provider, key, selectedModel, routePolicy,"),
                "test must use the explicitly captured provider/key/model inside worker");
        assertTrue(body.indexOf("getOpenRouterPolicy()") < worker, "route policy must be captured before background work");
        assertTrue(body.substring(worker).contains("isCancelled() || isDisposed() || project.isDisposed()"),
                "test transport must observe worker/dialog/project cancellation");
    }

    @Test
    void toolWindowContents_ownTheirDisposablePanels() throws IOException {
        String body = methodBody(read(FACTORY), "static void createNormalContent(");
        assertTrue(body.contains("browseContent.setDisposer(browsePanel)"),
                "the browse content must dispose its panel (Alarms/subscriptions are parented to it)");
        assertTrue(body.contains("consoleContent.setDisposer(consolePanel)"),
                "the console content must dispose its panel (it owns a ConsoleView)");
    }

    @Test
    void toolWindowPanel_parentsAlarmsToItself_notTheProject() throws IOException {
        String content = read(TW_PANEL);
        assertTrue(content.contains("implements DataProvider, Disposable"),
                "the panel must be Disposable so its content can own it");
        // EVERY Alarm must be parented to the panel (this), not just one — a partial regression that
        // reverted a single alarm's parent to `project` would leak it for the project's lifetime.
        int alarms = countOccurrences(content, "new Alarm(");
        int parentedToThis = countOccurrences(content, "new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)");
        assertTrue(alarms >= 2, "the panel constructs its refresh and preview alarms");
        assertEquals(alarms, parentedToThis,
                "every Alarm must be parented to the panel (this), so they die with the tab — not leak on the project");
        assertTrue(content.contains("project.getMessageBus().connect(this)"),
                "the VFS subscription must be parented to the panel (this), not the bare project connection");
    }

    @Test
    void toolWindowFactory_detectsStateStatically_withoutConstructingAThrowawayPanel() throws IOException {
        // GettingStartedPanel is a Disposable that parents an Alarm to itself; constructing one only to
        // read detectState() and then discarding it self-registers in the Disposer and leaks. The factory
        // must use the STATIC detectState(project) on its detection paths and construct a panel only when
        // it actually shows one.
        String content = read(FACTORY);
        assertTrue(content.contains("GettingStartedPanel.detectState(project)"),
                "the factory must detect state via the static GettingStartedPanel.detectState(project)");
        assertFalse(content.contains(".detectState()"),
                "the factory must NOT call the no-arg instance detectState() — that means it built a throwaway panel");
    }

    private static int countOccurrences(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }
}
