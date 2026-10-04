package com.johnnyblabs.openspec.settings;

import com.johnnyblabs.openspec.ai.backend.BackendStatus;
import com.johnnyblabs.openspec.ai.backend.ModelDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenSpecSettingsCodexTest {
    @Test
    void legacySettingsKeepRestRouteAndAllIndependentPreferences() {
        OpenSpecSettings.State legacy = new OpenSpecSettings.State();
        legacy.aiBackend = null;
        legacy.aiProvider = "GEMINI";
        legacy.aiModel = "custom-rest-model";
        legacy.cliPath = "/tools/openspec";
        legacy.profile = "core";
        legacy.preferredTool = "Codex";
        legacy.preferredDeliveryMethod = "CLIPBOARD";
        OpenSpecSettings settings = new OpenSpecSettings();
        settings.loadState(legacy);
        assertEquals("REST", settings.getAiBackend());
        settings.setAiBackend("LOCAL_CODEX");
        settings.setCodexModel("custom-codex-model");
        assertEquals("GEMINI", settings.getAiProvider());
        assertEquals("custom-rest-model", settings.getAiModel());
        assertEquals("/tools/openspec", settings.getCliPath());
        assertEquals("core", settings.getProfile());
        assertEquals("Codex", settings.getPreferredTool());
        assertEquals("CLIPBOARD", settings.getPreferredDeliveryMethod());
        settings.setAiBackend("REST");
        assertEquals("custom-codex-model", settings.getCodexModel());
    }

    @Test
    void defaultAndInvalidLegacyNumericValuesResolveSafely() {
        OpenSpecSettings settings = new OpenSpecSettings();
        assertEquals("REST", settings.getAiBackend());
        assertEquals("codex", settings.getCodexExecutable());
        assertEquals("", settings.getCodexModel());
        assertEquals(180, settings.getCodexTimeoutSeconds());
        assertEquals(262144, settings.getAiContextMaxBytes());
        assertFalse(settings.isCodexApiBillingAcknowledged());
        settings.setAiBackend("");
        settings.setCodexExecutable(null);
        settings.setCodexModel(null);
        settings.setCodexTimeoutSeconds(0);
        settings.setAiContextMaxBytes(-1);
        assertEquals("REST", settings.getAiBackend());
        assertEquals("codex", settings.getCodexExecutable());
        assertEquals("", settings.getCodexModel());
        assertEquals(180, settings.getCodexTimeoutSeconds());
        assertEquals(262144, settings.getAiContextMaxBytes());
    }

    @Test
    void codexPreferencesSurvivePersistentStateRoundTrip() {
        OpenSpecSettings original = new OpenSpecSettings();
        original.setAiBackend("LOCAL_CODEX");
        original.setCodexExecutable("/native binaries/codex");
        original.setCodexModel("future-model");
        original.setCodexTimeoutSeconds(45);
        original.setAiContextMaxBytes(4096);
        original.setCodexApiBillingAcknowledged(true);
        OpenSpecSettings reloaded = new OpenSpecSettings();
        reloaded.loadState(original.getState());
        assertEquals("LOCAL_CODEX", reloaded.getAiBackend());
        assertEquals("/native binaries/codex", reloaded.getCodexExecutable());
        assertEquals("future-model", reloaded.getCodexModel());
        assertEquals(45, reloaded.getCodexTimeoutSeconds());
        assertEquals(4096, reloaded.getAiContextMaxBytes());
        assertTrue(reloaded.isCodexApiBillingAcknowledged());
    }

    @Test
    void billingAndUnknownAuthAreVisibleAndUntrustedDetailIsEscaped() {
        assertTrue(OpenSpecSettingsPanel.formatCodexStatus(new BackendStatus(true, "chatgpt", "Ready", "0.160.0"))
                .contains("subscription; online inference and usage limits apply"));
        assertTrue(OpenSpecSettingsPanel.formatCodexStatus(new BackendStatus(true, "apikey", "Ready", "0.160.0"))
                .contains("API billed; explicit billing acknowledgement required"));
        String unknown = OpenSpecSettingsPanel.formatCodexStatus(new BackendStatus(false, null, "<untrusted>", null));
        assertTrue(unknown.contains("Auth: unknown"));
        assertTrue(unknown.contains("&lt;untrusted&gt;"));
        assertFalse(unknown.contains("<untrusted>"));
    }

    @Test
    void dynamicCatalogHasCliDefaultAndDoesNotInventModels() {
        assertEquals(List.of("", "available-model"), OpenSpecSettingsPanel.catalogModelIds(List.of(
                new ModelDescriptor("available-model", "Available", "", true),
                new ModelDescriptor("available-model", "Duplicate", "", false),
                new ModelDescriptor("", "Empty", "", false))));
        assertEquals(List.of(""), OpenSpecSettingsPanel.catalogModelIds(List.of()));
    }
}
