package com.johnnyblabs.openspec.settings;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.AiExecutionService;
import org.junit.jupiter.api.Test;

import javax.swing.JComboBox;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpenSpecConfigurableCodexTest {
    @Test
    void resetLoadsDistinctBackendPreferencesWithoutInferringOrProbing() throws Exception {
        Project project = mock(Project.class);
        OpenSpecSettings settings = new OpenSpecSettings();
        settings.setAiBackend("LOCAL_CODEX");
        settings.setAiProvider("OPENAI");
        settings.setAiModel("legacy-rest-model");
        settings.setCodexExecutable("/tools/codex");
        settings.setCodexModel("codex-override");
        settings.setCodexReasoningEffort("high");
        settings.setCodexTimeoutSeconds(60);
        settings.setAiContextMaxBytes(8192);
        settings.setCodexApiBillingAcknowledged(true);
        when(project.getService(OpenSpecSettings.class)).thenReturn(settings);
        OpenSpecSettingsPanel panel = mock(OpenSpecSettingsPanel.class);
        OpenSpecConfigurable configurable = new OpenSpecConfigurable(project);
        inject(configurable, "panel", panel);
        configurable.reset();
        verify(panel).setAiBackend("LOCAL_CODEX");
        verify(panel).setCodexExecutable("/tools/codex");
        verify(panel).setCodexModel("codex-override");
        verify(panel).setCodexReasoningEffort("high");
        verify(panel).setCodexTimeoutSeconds(60);
        verify(panel).setCodexApiBillingAcknowledged(true);
        verify(panel).setAiContextMaxBytes(8192);
        verify(panel).setAiProvider("OPENAI");
        verify(panel).setAiModel("legacy-rest-model");
        configurable.disposeUIResources();
        verify(panel).dispose();
    }

    @Test
    void applyPersistsCodexDraftThenRefreshesRoutingStatusWithoutAKeyOrInference() throws Exception {
        Project project = mock(Project.class);
        OpenSpecSettings settings = new OpenSpecSettings();
        when(project.getService(OpenSpecSettings.class)).thenReturn(settings);
        AiExecutionService execution = mock(AiExecutionService.class);
        when(project.getService(AiExecutionService.class)).thenReturn(execution);
        OpenSpecSettingsPanel panel = mock(OpenSpecSettingsPanel.class);
        when(panel.getProfile()).thenReturn("");
        when(panel.getAiBackend()).thenReturn("LOCAL_CODEX");
        when(panel.getCodexExecutable()).thenReturn("/tools/codex");
        when(panel.getCodexModel()).thenReturn("manual-override");
        when(panel.getCodexReasoningEffort()).thenReturn("medium");
        when(panel.getCodexTimeoutSeconds()).thenReturn(75);
        when(panel.getAiContextMaxBytes()).thenReturn(4096);
        when(panel.isCodexApiBillingAcknowledged()).thenReturn(true);
        when(panel.getAiProvider()).thenReturn("CLAUDE");
        when(panel.getAiModel()).thenReturn("saved-rest-model");
        doAnswer(ignored -> {
            assertEquals("LOCAL_CODEX", settings.getAiBackend());
            assertEquals("/tools/codex", settings.getCodexExecutable());
            assertEquals("manual-override", settings.getCodexModel());
            assertEquals("medium", settings.getCodexReasoningEffort());
            assertEquals(75, settings.getCodexTimeoutSeconds());
            assertEquals(4096, settings.getAiContextMaxBytes());
            assertTrue(settings.isCodexApiBillingAcknowledged());
            return null;
        }).when(execution).refreshStatus();
        OpenSpecConfigurable configurable = new OpenSpecConfigurable(project);
        inject(configurable, "panel", panel);
        configurable.apply();
        verify(execution).refreshStatus();
        assertEquals("CLAUDE", settings.getAiProvider());
        assertEquals("saved-rest-model", settings.getAiModel());
    }

    @Test
    void editableCodexModelUsesDraftEditorTextIncludingManualUnknownOverride() throws Exception {
        OpenSpecSettingsPanel panel = mock(OpenSpecSettingsPanel.class, CALLS_REAL_METHODS);
        JComboBox<String> model = new JComboBox<>(new String[]{"", "catalog-model"});
        model.setEditable(true);
        inject(panel, "codexModelCombo", model);
        panel.setCodexModel("manual-model");
        assertEquals("manual-model", panel.getCodexModel());
        model.getEditor().setItem("  newly-typed-model  ");
        assertEquals("newly-typed-model", panel.getCodexModel());
        panel.setCodexModel(null);
        assertEquals("", panel.getCodexModel());
    }

    @Test
    void reasoningEffortEditorPreservesManualDraftWithoutInventingSupportedValues() throws Exception {
        OpenSpecSettingsPanel panel = mock(OpenSpecSettingsPanel.class, CALLS_REAL_METHODS);
        JComboBox<String> effort = new JComboBox<>(new String[]{""});
        effort.setEditable(true);
        inject(panel, "codexReasoningEffortCombo", effort);
        panel.setCodexReasoningEffort("future-effort");
        assertEquals("future-effort", panel.getCodexReasoningEffort());
        effort.getEditor().setItem("  medium  ");
        assertEquals("medium", panel.getCodexReasoningEffort());
        panel.setCodexReasoningEffort(null);
        assertEquals("", panel.getCodexReasoningEffort());
        assertEquals(1, effort.getItemCount(), "Manual input does not fabricate catalog-supported values");
    }

    private static void inject(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
