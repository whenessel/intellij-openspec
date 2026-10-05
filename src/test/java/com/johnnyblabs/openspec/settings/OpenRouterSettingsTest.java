package com.johnnyblabs.openspec.settings;

import com.johnnyblabs.openspec.ai.AiProvider;
import com.johnnyblabs.openspec.ai.backend.ModelDescriptor;
import org.junit.jupiter.api.Test;
import javax.swing.JComboBox;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OpenRouterSettingsTest {
    @Test void providerAndModelsPersistIndependentlyFromCodexAndOtherRestProviders() {
        var settings = new OpenSpecSettings();
        settings.setAiProvider("OPENAI");
        settings.setAiModel("old-openai");
        settings.setAiProvider("OPENROUTER");
        settings.setAiModel("vendor/model:free");
        settings.setCodexModel("codex-model");
        var reload = new OpenSpecSettings();
        reload.loadState(settings.getState());
        assertEquals(AiProvider.OPENROUTER, AiProvider.fromString(reload.getAiProvider()));
        assertEquals("vendor/model:free", reload.getAiModel());
        assertEquals("old-openai", reload.getAiModel("OPENAI"));
        assertEquals("codex-model", reload.getCodexModel());
        reload.setAiProvider("OPENAI");
        assertEquals("old-openai", reload.getAiModel());
        reload.setAiProvider("OpenRouter");
        assertEquals("vendor/model:free", reload.getAiModel());
        assertEquals("OPENROUTER", AiSettingsMigration.canonicalProvider("OpenRouter"));
    }
    @Test void policySurvivesReloadAndProviderRoundTripWithoutAffectingModels() {
        var settings = new OpenSpecSettings();
        var policy = new com.johnnyblabs.openspec.ai.OpenRouterPolicy(List.of(" ProviderA ", "ProviderA"),
                List.of("ProviderB", "ProviderA"), false, "deny", true, 2048);
        settings.setOpenRouterPolicy(policy);
        settings.setAiProvider("OPENROUTER");
        settings.setAiModel("vendor/model");
        settings.setAiProvider("OPENAI");
        var reload = new OpenSpecSettings();
        reload.loadState(settings.getState());
        assertEquals(policy, reload.getOpenRouterPolicy());
        assertEquals(List.of("ProviderA"), reload.getOpenRouterPolicy().only());
        assertEquals("vendor/model", reload.getAiModel("OPENROUTER"));
        assertEquals("deny", reload.getOpenRouterPolicy().dataCollection());
    }
    @Test void legacyStateGetsSafeDefaultPolicyAndMalformedBoundsAreNormalized() {
        var settings = new OpenSpecSettings();
        assertEquals(com.johnnyblabs.openspec.ai.OpenRouterPolicy.defaults(), settings.getOpenRouterPolicy());
        settings.getState().openRouterMaxOutputTokens = Integer.MAX_VALUE;
        settings.getState().openRouterDataCollection = "invalid";
        settings.getState().openRouterOnly = null;
        var policy = settings.getOpenRouterPolicy();
        assertEquals(16000, policy.maxOutputTokens());
        assertEquals("deny", policy.dataCollection());
        assertThrows(IllegalArgumentException.class, () -> new com.johnnyblabs.openspec.ai.OpenRouterPolicy(
                List.of(), List.of(), true, "invalid", false, 4096));
        assertEquals(List.of(), policy.only());
        assertThrows(UnsupportedOperationException.class, () -> policy.only().add("mutation"));
        assertEquals(List.of("A", "B"), OpenSpecSettingsPanel.splitProviderIds(" A, B, A, ,"));
    }
    @Test void catalogPreservesCurrentEditableModelIncludingManualIds() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            JComboBox<String> combo = new JComboBox<>(new String[]{"old"});
            combo.setEditable(true);
            combo.getEditor().setItem("manual/not-in-catalog");
            OpenSpecSettingsPanel.applyRestCatalog(combo, List.of(new ModelDescriptor("new/model", "New", "", false)));
            assertEquals(1, combo.getItemCount());
            assertEquals("new/model", combo.getItemAt(0));
            assertEquals("manual/not-in-catalog", combo.getEditor().getItem());
        });
    }
}
