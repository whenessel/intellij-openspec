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
