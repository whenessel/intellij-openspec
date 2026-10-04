package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.wm.ToolWindowManager;
import com.johnnyblabs.openspec.ai.AiExecutionService;
import com.johnnyblabs.openspec.ai.DeliveryMode;
import com.johnnyblabs.openspec.ai.routing.AiRoutingPolicy.*;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.services.DeliveryMethodResolver;
import com.johnnyblabs.openspec.services.ExplorePromptService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Exercises real action entry points: explicit manual routing must not start AI inference. */
class AiWorkflowRoutingTest {
    private static RoutingSnapshot snapshot(DeliveryMode mode) {
        return new RoutingSnapshot(mode, mode == DeliveryMode.DIRECT_API
                ? new BackendSelection("LOCAL_CODEX", "NONE", "", "codex", "") : null,
                new BackendReadiness(true, "Fixture readiness"), Source.SAVED_PREFERENCE, mode.getDisplayName());
    }

    @Test
    void inlineExploreHonorsEditorDeliveryWithoutCallingBackend() throws Exception {
        Project project = mock(Project.class);
        ExplorePromptService prompts = mock(ExplorePromptService.class);
        DeliveryMethodResolver resolver = mock(DeliveryMethodResolver.class);
        Application application = mock(Application.class);
        when(project.getService(ExplorePromptService.class)).thenReturn(prompts);
        when(project.getService(DeliveryMethodResolver.class)).thenReturn(resolver);
        when(prompts.buildRequest("design question")).thenReturn(new ExplorePromptService.ExploreRequest("assembled context", "context-scope"));
        when(resolver.resolveSnapshot(null)).thenReturn(snapshot(DeliveryMode.EDITOR_TAB));
        ProgressManager progress = mock(ProgressManager.class);
        LocalFileSystem files = mock(LocalFileSystem.class);
        try (MockedStatic<ApplicationManager> apps = mockStatic(ApplicationManager.class);
             MockedStatic<ProgressManager> managers = mockStatic(ProgressManager.class);
             MockedStatic<LocalFileSystem> fileSystems = mockStatic(LocalFileSystem.class)) {
            apps.when(ApplicationManager::getApplication).thenReturn(application);
            managers.when(ProgressManager::getInstance).thenReturn(progress);
            fileSystems.when(LocalFileSystem::getInstance).thenReturn(files);
            ExploreContextAction.runExploreDirect(project, "design question");
            verifyNoInteractions(prompts);
            org.mockito.ArgumentCaptor<Task.Backgroundable> task = org.mockito.ArgumentCaptor.forClass(Task.Backgroundable.class);
            verify(progress).run(task.capture());
            task.getValue().run(mock(ProgressIndicator.class));
            verify(prompts).buildRequest("design question");
            org.mockito.ArgumentCaptor<java.nio.file.Path> promptFile = org.mockito.ArgumentCaptor.forClass(java.nio.file.Path.class);
            verify(files).refreshAndFindFileByNioFile(promptFile.capture());
            java.nio.file.Files.deleteIfExists(promptFile.getValue());
            verify(project, never()).getService(AiExecutionService.class);
        }
    }

    @Test
    void cancelledExplorePreparationRestoresInputAndRethrowsCancellation() {
        Project project = mock(Project.class);
        ExplorePromptService prompts = mock(ExplorePromptService.class);
        Application application = mock(Application.class);
        ProgressManager progress = mock(ProgressManager.class);
        when(project.getService(ExplorePromptService.class)).thenReturn(prompts);
        when(prompts.buildRequest("topic")).thenThrow(new ProcessCanceledException());
        try (MockedStatic<ApplicationManager> apps = mockStatic(ApplicationManager.class);
             MockedStatic<ProgressManager> managers = mockStatic(ProgressManager.class)) {
            apps.when(ApplicationManager::getApplication).thenReturn(application);
            managers.when(ProgressManager::getInstance).thenReturn(progress);
            ExploreContextAction.runExploreDirect(project, "topic");
            org.mockito.ArgumentCaptor<Task.Backgroundable> task = org.mockito.ArgumentCaptor.forClass(Task.Backgroundable.class);
            verify(progress).run(task.capture());
            assertThrows(ProcessCanceledException.class, () -> task.getValue().run(mock(ProgressIndicator.class)));
            verify(application).invokeLater(any(Runnable.class));
            verify(project, never()).getService(AiExecutionService.class);
        }
    }

    @Test
    void integratedExplorePassesStableScopeToConversationApi() throws Exception {
        Project project = mock(Project.class);
        ExplorePromptService prompts = mock(ExplorePromptService.class);
        DeliveryMethodResolver resolver = mock(DeliveryMethodResolver.class);
        AiExecutionService backend = mock(AiExecutionService.class);
        Application application = mock(Application.class);
        ProgressManager progress = mock(ProgressManager.class);
        when(project.getService(ExplorePromptService.class)).thenReturn(prompts);
        when(project.getService(DeliveryMethodResolver.class)).thenReturn(resolver);
        when(project.getService(AiExecutionService.class)).thenReturn(backend);
        when(prompts.buildRequest("follow up")).thenReturn(new ExplorePromptService.ExploreRequest("reviewable prompt", "stable scope"));
        RoutingSnapshot routing = snapshot(DeliveryMode.DIRECT_API);
        when(resolver.resolveSnapshot(null)).thenReturn(routing);
        when(backend.generateExplore(eq("reviewable prompt"), eq("stable scope"), any(), eq(routing))).thenReturn("answer");
        doAnswer(invocation -> { invocation.getArgument(0, Runnable.class).run(); return null; })
                .when(application).invokeAndWait(any(Runnable.class));
        try (MockedStatic<ApplicationManager> apps = mockStatic(ApplicationManager.class);
             MockedStatic<ProgressManager> managers = mockStatic(ProgressManager.class)) {
            apps.when(ApplicationManager::getApplication).thenReturn(application);
            managers.when(ProgressManager::getInstance).thenReturn(progress);
            ExploreContextAction.runExploreDirect(project, "follow up");
            org.mockito.ArgumentCaptor<Task.Backgroundable> task = org.mockito.ArgumentCaptor.forClass(Task.Backgroundable.class);
            verify(progress).run(task.capture());
            task.getValue().run(mock(ProgressIndicator.class));
            verify(backend).generateExplore(eq("reviewable prompt"), eq("stable scope"), any(), eq(routing));
            verify(backend, never()).generateRaw(anyString(), any());
        }
    }

    @Test
    void continueHonorsClipboardDeliveryWithoutCallingBackend() {
        Project project = mock(Project.class);
        AnActionEvent event = mock(AnActionEvent.class);
        ChangeService changes = mock(ChangeService.class);
        Change change = mock(Change.class);
        DeliveryMethodResolver resolver = mock(DeliveryMethodResolver.class);
        ToolWindowManager windows = mock(ToolWindowManager.class);
        when(event.getProject()).thenReturn(project);
        when(project.getService(ChangeService.class)).thenReturn(changes);
        when(project.getService(DeliveryMethodResolver.class)).thenReturn(resolver);
        when(changes.getActiveChanges()).thenReturn(List.of(change));
        when(change.getName()).thenReturn("design-change");
        when(resolver.resolveSnapshot(null)).thenReturn(snapshot(DeliveryMode.CLIPBOARD));
        try (MockedStatic<ToolWindowManager> managers = mockStatic(ToolWindowManager.class)) {
            managers.when(() -> ToolWindowManager.getInstance(project)).thenReturn(windows);
            new OpenSpecContinueAction().actionPerformed(event);
            verify(windows).getToolWindow("OpenSpec");
            verify(project, never()).getService(AiExecutionService.class);
        }
    }
}
