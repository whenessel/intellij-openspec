package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.AiExecutionService;
import com.johnnyblabs.openspec.ai.DeliveryMode;
import com.johnnyblabs.openspec.services.DeliveryMethodResolver;
import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.model.ArtifactInstruction;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.services.ArtifactOrchestrationService;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class OpenSpecContinueAction extends OpenSpecBaseAction {

    @Override
    protected String getWorkflowId() { return "continue"; }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        ChangeService changeService = project.getService(ChangeService.class);
        List<Change> activeChanges = changeService.getActiveChanges();
        if (activeChanges.isEmpty()) {
            OpenSpecNotifier.warn(project, "No active changes",
                    "Create a change first with Propose or Fast-Forward.");
            return;
        }

        String changeName = activeChanges.getFirst().getName();

        DeliveryMethodResolver resolver = project.getService(DeliveryMethodResolver.class);
        if (resolver == null) return;
        var routing = resolver.resolveSnapshot(null);
        if (routing.mode() != DeliveryMode.DIRECT_API) {
            var toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("OpenSpec");
            if (toolWindow != null) toolWindow.activate(() -> {
                var content = toolWindow.getContentManager().findContent("Browse");
                var panel = content == null ? null : OpenSpecFfAction.findWorkflowPanel(content.getComponent());
                if (panel != null) panel.selectChangeAndGenerate(changeName);
            });
            return;
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Continue: " + changeName, true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                ArtifactOrchestrationService orchestration =
                        project.getService(ArtifactOrchestrationService.class);

                ArtifactInfo nextArtifact = orchestration.getNextReadyArtifact(changeName);
                if (nextArtifact == null) {
                    // Check if all complete
                    ChangeArtifactDag dag = orchestration.getCachedArtifactStatus(changeName);
                    if (dag != null && dag.isComplete()) {
                        ApplicationManager.getApplication().invokeLater(() ->
                                OpenSpecNotifier.info(project, "All artifacts complete",
                                        "Change '" + changeName + "' is ready for implementation. Run Apply or Archive."));
                    } else {
                        ApplicationManager.getApplication().invokeLater(() ->
                                OpenSpecNotifier.warn(project, "No ready artifacts",
                                        "No artifacts are ready for generation in '" + changeName + "'."));
                    }
                    return;
                }

                indicator.setText("Generating " + nextArtifact.id() + "...");
                AiExecutionService apiService = project.getService(AiExecutionService.class);
                if (apiService == null || !routing.available()) {
                    ApplicationManager.getApplication().invokeLater(() ->
                            OpenSpecNotifier.error(project, "AI backend not ready",
                                    "Selected backend is unavailable: " + routing.readiness().detail() + ". Check OpenSpec settings."));
                    return;
                }

                try {
                    ArtifactInstruction instruction = orchestration.getInstruction(changeName, nextArtifact.id());
                    apiService.generateAndApply(instruction, routing);
                    indicator.checkCanceled();
                    orchestration.invalidateCache(changeName);

                    ApplicationManager.getApplication().invokeLater(() -> {
                        OpenSpecNotifier.info(project, "Artifact generated",
                                "Created " + nextArtifact.id() + " for '" + changeName + "'.");
                        refreshToolWindow(project);
                    });
                } catch (com.intellij.openapi.progress.ProcessCanceledException ex) {
                    apiService.cancelActive();
                    throw ex;
                } catch (Exception ex) {
                    ApplicationManager.getApplication().invokeLater(() ->
                            OpenSpecNotifier.error(project, "Generation failed",
                                    "Failed to generate " + nextArtifact.id() + ": " + ex.getMessage()));
                }
            }
        });
    }
}
