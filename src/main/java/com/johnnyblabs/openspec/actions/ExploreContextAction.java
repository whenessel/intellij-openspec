package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.DeliveryMode;
import com.johnnyblabs.openspec.ai.AiExecutionService;
import com.johnnyblabs.openspec.dialogs.ExploreTopicDialog;
import com.johnnyblabs.openspec.services.DeliveryMethodResolver;
import com.johnnyblabs.openspec.services.ExplorePromptService;
import com.johnnyblabs.openspec.toolwindow.ExplorePanel;
import com.johnnyblabs.openspec.toolwindow.ExplorePanelService;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Explore action aligned with the OpenSpec explore workflow.
 * Prompts for an optional topic, assembles the explore prompt (skill instructions +
 * project context + topic), and delivers via the configured delivery mode.
 */
public class ExploreContextAction extends OpenSpecBaseAction {

    @Override
    protected String getWorkflowId() { return "explore"; }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        // If Direct API, activate the Explore panel and focus its inline input
        DeliveryMethodResolver resolver = project.getService(DeliveryMethodResolver.class);
        DeliveryMethodResolver.ResolvedMethod resolved = resolver != null
                ? resolver.resolve()
                : new DeliveryMethodResolver.ResolvedMethod(DeliveryMode.CLIPBOARD, "Copy to Clipboard");

        if (resolved.mode() == DeliveryMode.DIRECT_API) {
            ExplorePanelService panelService = project.getService(ExplorePanelService.class);
            ExplorePanel panel = panelService != null ? panelService.getAndActivate() : null;
            if (panel != null) {
                panel.focusInput();
                return;
            }
            // Fall through to dialog if panel not available
        }

        runExploreWithDialog(project);
    }

    /**
     * Runs the explore workflow via the modal dialog for non-Direct-API delivery modes.
     */
    private static void runExploreWithDialog(@NotNull Project project) {
        ExploreTopicDialog dialog = new ExploreTopicDialog(project);
        if (!dialog.showAndGet()) {
            return; // User cancelled
        }
        String topic = dialog.getTopic();
        runExploreDeliver(project, topic);
    }

    /**
     * Runs the explore workflow: show topic dialog, build prompt, deliver.
     * Can be called from the action or from the ExplorePanel toolbar.
     */
    public static void runExplore(@NotNull Project project) {
        runExplore(project, null);
    }

    /**
     * Runs the explore workflow with an optional pre-filled topic.
     * If topic is null, shows the dialog. If non-null, skips the dialog and uses it directly.
     */
    public static void runExplore(@NotNull Project project, String prefillTopic) {
        String topic;
        if (prefillTopic != null) {
            topic = prefillTopic;
        } else {
            ExploreTopicDialog dialog = new ExploreTopicDialog(project);
            if (!dialog.showAndGet()) {
                return; // User cancelled
            }
            topic = dialog.getTopic();
        }
        runExploreDeliver(project, topic);
    }

    /**
     * Builds the explore prompt and delivers via the shared delivery mode resolver.
     * Used by the Explore panel's inline input, which only exists when Direct API is configured.
     */
    public static void runExploreDirect(@NotNull Project project, String topic) {
        runExploreDeliver(project, topic);
    }

    /**
     * Builds the explore prompt and delivers via the configured delivery mode.
     */
    private static final class UiRun {
        ExplorePanel panel;
        long epoch;
        boolean current() { return panel == null || panel.isCurrent(epoch); }
    }

    private static void runExploreDeliver(@NotNull Project project, String topic) {
        var resolver = project.getService(DeliveryMethodResolver.class);
        var routing = resolver == null ? null : resolver.resolveSnapshot(null);
        UiRun ui = new UiRun();
        ExplorePanelService panels = project.getService(ExplorePanelService.class);
        ui.panel = panels == null ? null : panels.getExplorePanel();
        if (ui.panel != null) ui.epoch = ui.panel.beginRequest();
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Exploring...", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    if (ui.panel != null) ui.panel.attachIndicator(ui.epoch, indicator);
                    indicator.setIndeterminate(true);
                    indicator.setText("Preparing Explore context...");
                    indicator.checkCanceled();
                    ExplorePromptService prompts = project.getService(ExplorePromptService.class);
                    if (prompts == null) { recoverInput(project, ui); return; }
                    ExplorePromptService.ExploreRequest request = prompts.buildRequest(topic);
                    String prompt = request.prompt();
                    indicator.checkCanceled();
                    DeliveryMode mode = routing == null ? DeliveryMode.CLIPBOARD : routing.mode();
                    switch (mode) {
                        case CLIPBOARD -> ApplicationManager.getApplication().invokeLater(() -> {
                            if (!project.isDisposed() && ui.current() && !indicator.isCanceled()) deliverClipboard(project, prompt);
                            recoverInput(project, ui);
                        });
                        case EDITOR_TAB -> { if (ui.current()) deliverEditorTab(project, prompt, ui); recoverInput(project, ui); }
                        case DIRECT_API -> deliverDirectApi(project, request, topic, indicator, ui, routing);
                    }
                } catch (com.intellij.openapi.progress.ProcessCanceledException ex) {
                    recoverInput(project, ui);
                    throw ex;
                } catch (Exception ex) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (project.isDisposed() || !ui.current()) return;
                        if (ui.panel != null) ui.panel.showError(ui.epoch, topic, ex.getMessage());
                        OpenSpecNotifier.warn(project, "Explore", "AI workflow error: " + ex.getMessage());
                    });
                    recoverInput(project, ui);
                }
            }
        });
    }

    private static void recoverInput(Project project, UiRun ui) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed() && ui.panel != null) ui.panel.restoreInput(ui.epoch);
        });
    }

    private static void deliverClipboard(@NotNull Project project, String prompt) {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(prompt), null);
        OpenSpecNotifier.info(project, "Explore",
                "Explore prompt copied \u2014 paste into your AI tool to start exploring.");
    }

    /** Called only by the background preparation task. */
    private static void deliverEditorTab(@NotNull Project project, String prompt, UiRun ui) throws IOException {
        Path tmpFile = Files.createTempFile("openspec-explore-", ".md");
        Files.writeString(tmpFile, prompt, StandardCharsets.UTF_8);
        var vf = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tmpFile);
        if (vf != null) ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed() && ui.current()) com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(vf, true);
        });
    }

    /** Called only by the background preparation task; all panel access is marshalled to EDT. */
    private static void deliverDirectApi(@NotNull Project project, ExplorePromptService.ExploreRequest request, String topic,
                                         ProgressIndicator indicator, UiRun ui,
                                         com.johnnyblabs.openspec.ai.routing.AiRoutingPolicy.RoutingSnapshot routing) throws Exception {
        AiExecutionService apiService = project.getService(AiExecutionService.class);
        if (apiService == null || !routing.available()) {
            throw new IllegalStateException("Selected AI backend is not ready: " + routing.readiness().detail() + ". Check OpenSpec settings.");
        }
        ApplicationManager.getApplication().invokeAndWait(() -> {
            if (project.isDisposed()) return;
            ExplorePanelService service = project.getService(ExplorePanelService.class);
            if (ui.panel == null) {
                ui.panel = service == null ? null : service.getAndActivate();
                if (ui.panel != null) ui.epoch = ui.panel.beginRequest();
            }
            if (ui.panel != null) ui.panel.showLoading(ui.epoch, topic);
        });
        if (ui.panel != null) ui.panel.attachIndicator(ui.epoch, indicator);
        if (!ui.current()) throw new com.intellij.openapi.progress.ProcessCanceledException();
        indicator.checkCanceled();
        indicator.setText("Sending Explore prompt to AI backend...");
        try {
            String response = apiService.generateExplore(request.prompt(), request.contextScope(), delta -> {
                indicator.checkCanceled();
                if (!ui.current()) throw new com.intellij.openapi.progress.ProcessCanceledException();
                if (ui.panel != null) ui.panel.appendDelta(ui.epoch, delta);
            }, routing);
            indicator.checkCanceled();
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!project.isDisposed() && ui.panel != null) ui.panel.showResult(ui.epoch, topic, response);
            });
        } catch (com.intellij.openapi.progress.ProcessCanceledException ex) {
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!project.isDisposed() && ui.panel != null) ui.panel.showError(ui.epoch, topic, "Exploration cancelled. Start a New conversation before continuing.");
            });
            throw ex;
        }
    }
}
