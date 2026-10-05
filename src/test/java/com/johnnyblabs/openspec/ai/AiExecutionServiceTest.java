package com.johnnyblabs.openspec.ai;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.backend.BackendStatus;
import com.johnnyblabs.openspec.ai.backend.AiResult;
import com.johnnyblabs.openspec.ai.backend.CodexAppServerBackend;
import com.johnnyblabs.openspec.ai.backend.ModelCatalogSnapshot;
import com.johnnyblabs.openspec.ai.backend.ModelDescriptor;
import com.johnnyblabs.openspec.ai.backend.CancellationToken;
import com.johnnyblabs.openspec.ai.backend.AiOperationCancelledException;
import com.johnnyblabs.openspec.ai.safety.ContextReviewService;
import com.johnnyblabs.openspec.ai.safety.ReviewedContext;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises orchestration gates with mocked platform services and no real subprocess or AI call. */
class AiExecutionServiceTest {
    private void configureOpenRouter() throws Exception {
        settings.setAiBackend("REST"); settings.setAiProvider("OPENROUTER"); settings.setAiModel("reviewed/model");
        when(rest.isConfigured()).thenReturn(true);
        when(rest.isConfigured(AiProvider.OPENROUTER)).thenReturn(true);
        when(review.review(anyString(), anyString(), anyInt())).thenAnswer(call -> {
            ReviewedContext context = mock(ReviewedContext.class);
            when(context.prompt()).thenReturn(call.getArgument(0));
            when(context.root()).thenReturn(java.nio.file.Path.of("/unused-reviewed-context"));
            return context;
        });
    }

    @Test void openRouterTwoExploreTurnsAndVerifyUseSameReviewedStreamBoundary() throws Exception {
        configureOpenRouter();
        when(rest.generateOpenRouter(any(), eq("reviewed/model"), eq(settings.getOpenRouterPolicy()), any(), any()))
                .thenAnswer(call -> {
                    com.johnnyblabs.openspec.ai.backend.AiRequest request = call.getArgument(0);
                    assertNull(request.outputSchema());
                    java.util.function.Consumer<String> delta = call.getArgument(4);
                    delta.accept("stream:"); delta.accept(request.prompt());
                    return new OpenRouterProtocol.Completion("stream:" + request.prompt(), "actual/model", "actual-provider");
                });
        java.util.List<String> deltas = new java.util.ArrayList<>();
        try (MockedStatic<ProgressManager> progress = platformProgress();
             MockedConstruction<CodexAppServerBackend> local = mockConstruction(CodexAppServerBackend.class)) {
            assertEquals("stream:explore one", execution.generateExplore("explore one", "scope", deltas::add));
            assertEquals("stream:explore two", execution.generateExplore("explore two", "scope", deltas::add));
            assertEquals("stream:verify synthetic requirement", execution.generateRaw("verify synthetic requirement", deltas::add));
            assertEquals(java.util.List.of("stream:", "explore one", "stream:", "explore two", "stream:", "verify synthetic requirement"), deltas);
            verify(rest, times(3)).generateOpenRouter(any(), eq("reviewed/model"), eq(settings.getOpenRouterPolicy()), any(), any());
            verify(review, times(3)).review(anyString(), contains(settings.getOpenRouterPolicy().reviewSummary()), anyInt());
            verify(rest, never()).generateRaw(anyString(), any(AiProvider.class), anyString(), any());
            assertTrue(local.constructed().isEmpty());
        }
    }

    @Test void openRouterGenerateHandsValidatedSyntheticEnvelopeToExistingSafeApplyBoundary(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
        configureOpenRouter();
        var instruction = new com.johnnyblabs.openspec.model.ArtifactInstruction("synthetic", "specs", root.toString(),
                "specs/a/spec.md", "Generate a synthetic spec", "", java.util.List.of(), java.util.List.of());
        var snapshot = new com.johnnyblabs.openspec.ai.safety.ArtifactRequestSnapshot(root, "specs", "specs/a/spec.md", java.util.Map.of());
        var writer = mock(com.johnnyblabs.openspec.ai.safety.SafeArtifactService.class);
        var batch = mock(com.johnnyblabs.openspec.ai.safety.ReviewedArtifactBatch.class);
        when(project.getService(com.johnnyblabs.openspec.ai.safety.SafeArtifactService.class)).thenReturn(writer);
        when(writer.begin(instruction)).thenReturn(snapshot);
        String envelope = "{\"schemaVersion\":1,\"artifactId\":\"specs\",\"files\":[{\"relativePath\":\"specs/a/spec.md\",\"operation\":\"create\",\"content\":\"# Synthetic spec\"}]}";
        when(rest.generateOpenRouter(any(), eq("reviewed/model"), eq(settings.getOpenRouterPolicy()), any(), any()))
                .thenAnswer(call -> {
                    com.johnnyblabs.openspec.ai.backend.AiRequest request = call.getArgument(0);
                    assertNotNull(request.outputSchema());
                    assertEquals(AiExecutionService.artifactSchema("specs"), request.outputSchema());
                    return new OpenRouterProtocol.Completion(envelope, "actual/model", "actual-provider");
                });
        when(writer.prepare(snapshot, envelope)).thenReturn(batch);
        when(writer.apply(eq(batch), any())).thenReturn(java.util.List.of(root.resolve("specs/a/spec.md")));
        try (MockedStatic<ProgressManager> progress = platformProgress();
             MockedConstruction<CodexAppServerBackend> local = mockConstruction(CodexAppServerBackend.class)) {
            assertEquals(java.util.List.of(root.resolve("specs/a/spec.md")), execution.generateAndApply(instruction));
            verify(writer).prepare(snapshot, envelope); verify(writer).apply(eq(batch), any());
            verify(rest, never()).generateRaw(anyString(), any(AiProvider.class), anyString(), any());
            assertTrue(local.constructed().isEmpty());
        }
    }

    @Test void openRouterPolicyChangeAfterReviewBlocksRequest() throws Exception {
        configureOpenRouter();
        ReviewedContext context = mock(ReviewedContext.class);
        when(review.review(anyString(), anyString(), anyInt())).thenAnswer(call -> {
            settings.setOpenRouterPolicy(new OpenRouterPolicy(java.util.List.of("different"), java.util.List.of(), false, "deny", true, 100));
            return context;
        });
        try (MockedStatic<ProgressManager> progress = platformProgress()) {
            assertTrue(assertThrows(AiApiException.class, () -> execution.generateRaw("synthetic")).getMessage().contains("changed"));
            verify(rest, never()).generateOpenRouter(any(), anyString(), any(), any(), any());
            verify(context).close();
        }
    }

    @Test void openRouterCancellationAndDisposalAfterStreamPreventSuccessfulReturn() throws Exception {
        configureOpenRouter();
        when(rest.generateOpenRouter(any(), anyString(), any(), any(), any())).thenAnswer(call -> {
            execution.cancelActive();
            assertTrue(((java.util.function.BooleanSupplier) call.getArgument(3)).getAsBoolean());
            return new OpenRouterProtocol.Completion("late", "model", "provider");
        });
        try (MockedStatic<ProgressManager> progress = platformProgress()) {
            assertThrows(ProcessCanceledException.class, () -> execution.generateRaw("cancel synthetic"));
            doAnswer(call -> {
                execution.dispose();
                return new OpenRouterProtocol.Completion("late", "model", "provider");
            }).when(rest).generateOpenRouter(any(), anyString(), any(), any(), any());
            assertThrows(ProcessCanceledException.class, () -> execution.generateRaw("dispose synthetic"));
            assertFalse(execution.hasActiveRequest());
        }
    }

    @Test void openRouterConsoleReceiptPreservesActualRouteAndRejectsStaleClosedOrCanceledCallbacks() throws Exception {
        configureOpenRouter();
        when(rest.generateOpenRouter(any(), anyString(), any(), any(), any()))
                .thenReturn(new OpenRouterProtocol.Completion("answer", "actual/model", "actual-provider"));
        var application = mock(com.intellij.openapi.application.Application.class);
        java.util.List<Runnable> callbacks = new java.util.ArrayList<>();
        doAnswer(call -> { callbacks.add(call.getArgument(0)); return null; }).when(application).invokeLater(any(Runnable.class));
        var consoleService = mock(com.johnnyblabs.openspec.toolwindow.OpenSpecConsoleService.class);
        var console = mock(com.johnnyblabs.openspec.toolwindow.OpenSpecConsolePanel.class);
        when(project.getService(com.johnnyblabs.openspec.toolwindow.OpenSpecConsoleService.class)).thenReturn(consoleService);
        when(consoleService.getConsolePanel()).thenReturn(console);
        var manager = mock(ProgressManager.class);
        var indicator = mock(com.intellij.openapi.progress.ProgressIndicator.class);
        when(manager.getProgressIndicator()).thenReturn(indicator);
        try (MockedStatic<ProgressManager> progress = mockStatic(ProgressManager.class);
             MockedStatic<com.intellij.openapi.application.ApplicationManager> applications = mockStatic(com.intellij.openapi.application.ApplicationManager.class)) {
            progress.when(ProgressManager::getInstance).thenReturn(manager);
            applications.when(com.intellij.openapi.application.ApplicationManager::getApplication).thenReturn(application);
            assertEquals("answer", execution.generateRaw("older synthetic"));
            assertEquals("answer", execution.generateRaw("current synthetic"));
            assertEquals(2, callbacks.size());
            callbacks.get(0).run(); verifyNoInteractions(console);
            callbacks.get(1).run();
            verify(console).printSystem("OpenRouter completed · model: actual/model · provider: actual-provider");
            clearInvocations(console);
            execution.generateRaw("closed synthetic");
            when(project.isDisposed()).thenReturn(true);
            callbacks.get(2).run(); verifyNoInteractions(console);
            when(project.isDisposed()).thenReturn(false);
            execution.generateRaw("canceled synthetic");
            when(indicator.isCanceled()).thenReturn(true);
            callbacks.get(3).run(); verifyNoInteractions(console);
        }
    }
    private Project project;
    private OpenSpecSettings settings;
    private ContextReviewService review;
    private DirectApiService rest;
    private AiExecutionService execution;

    @BeforeEach
    void setUp() {
        project = mock(Project.class);
        settings = new OpenSpecSettings();
        settings.setAiBackend("LOCAL_CODEX");
        settings.setCodexExecutable("/unused/mock-codex");
        review = mock(ContextReviewService.class);
        rest = mock(DirectApiService.class);
        when(project.getService(OpenSpecSettings.class)).thenReturn(settings);
        when(project.getService(ContextReviewService.class)).thenReturn(review);
        when(project.getService(DirectApiService.class)).thenReturn(rest);
        execution = new AiExecutionService(project);
    }

    @Test
    void apiBillingMustBeAcknowledgedBeforeContextReviewOrInference() throws Exception {
        try (MockedStatic<ProgressManager> progress = platformProgress();
             MockedConstruction<CodexAppServerBackend> local = localBackend("apikey")) {
            AiApiException error = assertThrows(AiApiException.class, () -> execution.generateRaw("private prompt"));
            assertTrue(error.getMessage().contains("API billing"));
            assertEquals(1, local.constructed().size());
            verify(local.constructed().getFirst()).probe(any());
            verify(local.constructed().getFirst(), never()).generate(any(), any(), any());
            verifyNoInteractions(review, rest);
        }
    }

    @Test
    void unknownBackendFailsWithoutConstructingCodexOrFallingBackToRest() {
        settings.setAiBackend("future-unsupported-backend");
        try (MockedStatic<ProgressManager> progress = platformProgress();
             MockedConstruction<CodexAppServerBackend> local = mockConstruction(CodexAppServerBackend.class)) {
            AiApiException error = assertThrows(AiApiException.class, () -> execution.generateRaw("prompt"));
            assertTrue(error.getMessage().contains("Unsupported AI backend"));
            assertTrue(local.constructed().isEmpty());
            verifyNoInteractions(review, rest);
        }
    }

    @Test
    void cancellationDuringContextPreviewPreventsInferenceAndReleasesRequestSlot() throws Exception {
        ReviewedContext context = mock(ReviewedContext.class);
        when(review.review(anyString(), anyString(), anyInt())).thenAnswer(call -> {
            execution.cancelActive();
            return context;
        });
        try (MockedStatic<ProgressManager> progress = platformProgress();
             MockedConstruction<CodexAppServerBackend> local = localBackend("chatgpt")) {
            assertThrows(ProcessCanceledException.class, () -> execution.generateRaw("prompt"));
            verify(local.constructed().getFirst(), never()).generate(any(), any(), any());
            verify(context).close();
            verifyNoInteractions(rest);
            // A subsequent request reaches backend validation instead of an "already active" error.
            settings.setAiBackend("unknown");
            AiApiException next = assertThrows(AiApiException.class, () -> execution.generateRaw("next"));
            assertTrue(next.getMessage().contains("Unsupported AI backend"));
        }
    }

    @Test
    void changingDestinationDuringPreviewRequiresNewReviewAndMakesNoRequest() throws Exception {
        settings.setAiBackend("REST");
        settings.setAiProvider("OPENAI");
        settings.setAiModel("reviewed-model");
        ReviewedContext context = mock(ReviewedContext.class);
        when(review.review(anyString(), anyString(), anyInt())).thenAnswer(call -> {
            settings.setAiModel("different-model");
            return context;
        });
        try (MockedStatic<ProgressManager> progress = platformProgress();
             MockedConstruction<CodexAppServerBackend> local = mockConstruction(CodexAppServerBackend.class)) {
            AiApiException error = assertThrows(AiApiException.class, () -> execution.generateRaw("prompt"));
            assertTrue(error.getMessage().contains("changed"));
            verify(review).review(eq("prompt"), contains("reviewed-model"), eq(settings.getAiContextMaxBytes()));
            verify(context).close();
            verify(rest).isConfigured();
            verifyNoMoreInteractions(rest);
            assertTrue(local.constructed().isEmpty());
        }
    }

    @Test
    void manualRunOverrideCannotStartAnyBackendEvenWithSavedCodex() {
        var route = new com.johnnyblabs.openspec.ai.routing.AiRoutingPolicy.RoutingSnapshot(
                DeliveryMode.CLIPBOARD, null,
                new com.johnnyblabs.openspec.ai.routing.AiRoutingPolicy.BackendReadiness(true, "Manual"),
                com.johnnyblabs.openspec.ai.routing.AiRoutingPolicy.Source.RUN_OVERRIDE, "Clipboard");
        try (MockedConstruction<CodexAppServerBackend> local = mockConstruction(CodexAppServerBackend.class)) {
            assertThrows(AiApiException.class, () -> execution.generateRaw("private", ignored -> {}, route));
            assertFalse(execution.hasActiveRequest());
            assertTrue(local.constructed().isEmpty());
            verifyNoInteractions(review, rest);
        }
    }

    @Test
    void explicitBackendOverrideUsesFrozenDestinationWithoutChangingSavedSettings() throws Exception {
        ReviewedContext context = mock(ReviewedContext.class);
        when(context.prompt()).thenReturn("reviewed");
        when(context.root()).thenReturn(java.nio.file.Path.of("/unused-context"));
        when(review.review(anyString(), anyString(), anyInt())).thenReturn(context);
        when(rest.isConfigured(AiProvider.OPENAI)).thenReturn(true);
        when(rest.generateRaw(eq("reviewed"), eq(AiProvider.OPENAI), eq("override-model"), any())).thenReturn("answer");
        var route = new com.johnnyblabs.openspec.ai.routing.AiRoutingPolicy.RoutingSnapshot(
                DeliveryMode.DIRECT_API,
                new com.johnnyblabs.openspec.ai.routing.AiRoutingPolicy.BackendSelection("REST", "OPENAI", "override-model", "", ""),
                new com.johnnyblabs.openspec.ai.routing.AiRoutingPolicy.BackendReadiness(false, "Unprobed override"),
                com.johnnyblabs.openspec.ai.routing.AiRoutingPolicy.Source.RUN_OVERRIDE, "Override");
        try (MockedStatic<ProgressManager> progress = platformProgress();
             MockedConstruction<CodexAppServerBackend> local = mockConstruction(CodexAppServerBackend.class)) {
            assertEquals("answer", execution.generateRaw("prompt", ignored -> {}, route));
            assertEquals("LOCAL_CODEX", settings.getAiBackend());
            assertFalse(execution.hasActiveRequest());
            assertTrue(local.constructed().isEmpty());
            verify(review).review(eq("prompt"), contains("override-model"), anyInt());
            verify(rest).generateRaw(eq("reviewed"), eq(AiProvider.OPENAI), eq("override-model"), any());
        }
    }

    @Test
    void openRouterUsesSharedReviewedExecutionWithoutConstructingCodex() throws Exception {
        ReviewedContext context = mock(ReviewedContext.class);
        when(context.prompt()).thenReturn("reviewed");
        when(context.root()).thenReturn(java.nio.file.Path.of("/unused-context"));
        when(review.review(anyString(), anyString(), anyInt())).thenReturn(context);
        when(rest.isConfigured(AiProvider.OPENROUTER)).thenReturn(true);
        when(rest.generateOpenRouter(any(), eq("override-model"), eq(settings.getOpenRouterPolicy()), any(), any()))
                .thenReturn(new OpenRouterProtocol.Completion("answer", "actual/model", "actual-provider"));
        var route = new com.johnnyblabs.openspec.ai.routing.AiRoutingPolicy.RoutingSnapshot(
                DeliveryMode.DIRECT_API,
                new com.johnnyblabs.openspec.ai.routing.AiRoutingPolicy.BackendSelection("REST", "OPENROUTER", "override-model", "", ""),
                new com.johnnyblabs.openspec.ai.routing.AiRoutingPolicy.BackendReadiness(false, "Unprobed override"),
                com.johnnyblabs.openspec.ai.routing.AiRoutingPolicy.Source.RUN_OVERRIDE, "Override");
        try (MockedStatic<ProgressManager> progress = platformProgress();
             MockedConstruction<CodexAppServerBackend> local = mockConstruction(CodexAppServerBackend.class)) {
            assertEquals("answer", execution.generateRaw("prompt", ignored -> {}, route));
            assertEquals("LOCAL_CODEX", settings.getAiBackend());
            assertFalse(execution.hasActiveRequest());
            assertTrue(local.constructed().isEmpty());
            verify(review).review(eq("prompt"), contains("override-model"), anyInt());
            verify(review).review(eq("prompt"), contains(settings.getOpenRouterPolicy().reviewSummary()), anyInt());
            verify(rest).generateOpenRouter(argThat(request -> "reviewed".equals(request.prompt())),
                    eq("override-model"), eq(settings.getOpenRouterPolicy()), any(), any());
            verify(rest, never()).generateRaw(anyString(), any(AiProvider.class), anyString(), any());
        }
    }

    @Test
    void inputTokenBudgetChangedAfterPreviewPreventsInference() throws Exception {
        ReviewedContext context = mock(ReviewedContext.class);
        when(review.review(anyString(), anyString(), anyInt())).thenAnswer(call -> {
            settings.setAiContextMaxInputTokens(2048);
            return context;
        });
        try (MockedStatic<ProgressManager> progress = platformProgress();
             MockedConstruction<CodexAppServerBackend> local = localBackend("chatgpt")) {
            assertThrows(AiApiException.class, () -> execution.generateRaw("prompt"));
            verify(local.constructed().getFirst(), never()).generate(any(), any(), any());
            verifyNoInteractions(rest);
            verify(context).close();
        }
    }

    private static MockedStatic<ProgressManager> platformProgress() {
        MockedStatic<ProgressManager> progress = mockStatic(ProgressManager.class);
        progress.when(ProgressManager::getInstance).thenReturn(mock(ProgressManager.class));
        return progress;
    }

    @Test
    void newExploreConversationDoesNotCancelArtifactOrVerifyRequest() throws Exception {
        ReviewedContext context = mock(ReviewedContext.class);
        when(context.prompt()).thenReturn("reviewed");
        when(context.root()).thenReturn(java.nio.file.Path.of("/unused-reviewed-context"));
        when(review.review(anyString(), anyString(), anyInt())).thenAnswer(call -> {
            execution.resetExploreConversation();
            return context;
        });
        try (MockedStatic<ProgressManager> progress = platformProgress();
             MockedConstruction<CodexAppServerBackend> local = mockConstruction(CodexAppServerBackend.class, (backend, construction) -> {
                 when(backend.probe(any())).thenReturn(new BackendStatus(true, "chatgpt", "Mock", "fixture"));
                 catalog(backend);
                 when(backend.generate(any(), any(), any())).thenReturn(new AiResult("answer", "local-codex", "model"));
             })) {
            assertEquals("answer", execution.generateRaw("prompt"));
            verify(local.constructed().getFirst()).generate(any(), any(), any());
            verify(context).close();
            verifyNoInteractions(rest);
        }
    }

    @Test
    void newConversationDuringExplorePreviewPreventsOldTurn() throws Exception {
        ReviewedContext context = mock(ReviewedContext.class);
        when(review.review(anyString(), anyString(), anyInt())).thenAnswer(call -> {
            execution.resetExploreConversation();
            return context;
        });
        try (MockedStatic<ProgressManager> progress = platformProgress();
             MockedConstruction<CodexAppServerBackend> local = localBackend("chatgpt")) {
            assertThrows(ProcessCanceledException.class, () -> execution.generateExplore("prompt", "scope", ignored -> {}));
            verify(local.constructed().getFirst(), never()).generateConversation(any(), any(), any(), anyString(), anyBoolean(), anyInt());
            verify(context).close();
            verifyNoInteractions(rest);
        }
    }

    @Test
    void reviewBillingUsesExecutionSnapshotEvenWhenStatusCacheChanges() throws Exception {
        settings.setCodexApiBillingAcknowledged(true);
        settings = spy(settings);
        when(project.getService(OpenSpecSettings.class)).thenReturn(settings);
        doAnswer(call -> {
            var cache = AiExecutionService.class.getDeclaredField("codexStatus");
            cache.setAccessible(true);
            cache.set(execution, new BackendStatus(true, "chatgpt", "Stale concurrent probe", "fixture"));
            return true;
        }).when(settings).isCodexApiBillingAcknowledged();
        when(review.review(anyString(), anyString(), anyInt())).thenThrow(new ProcessCanceledException());
        try (MockedStatic<ProgressManager> progress = platformProgress();
             MockedConstruction<CodexAppServerBackend> local = localBackend("apikey")) {
            assertThrows(ProcessCanceledException.class, () -> execution.generateRaw("prompt"));
            verify(review).review(eq("prompt"), contains("API billing"), anyInt());
            verify(local.constructed().getFirst(), never()).generate(any(), any(), any());
            verifyNoInteractions(rest);
        }
    }

    private static MockedConstruction<CodexAppServerBackend> localBackend(String auth) {
        return mockConstruction(CodexAppServerBackend.class, (backend, construction) -> {
                when(backend.probe(any())).thenReturn(new BackendStatus(true, auth, "Mock account status", "fixture"));
                catalog(backend);
        });
    }

    private static void catalog(CodexAppServerBackend backend) throws AiApiException {
        when(backend.catalog(any(), anyBoolean(), any())).thenReturn(new ModelCatalogSnapshot(
                java.util.List.of(new ModelDescriptor("fixture-model", "Fixture model", "Synthetic service mock", true)),
                java.time.Instant.now(), false, true, "Synthetic service mock"));
    }

    @Test
    void changedEffortDuringPreviewRequiresReviewAgainWithoutInference() throws Exception {
        ReviewedContext context = mock(ReviewedContext.class);
        when(review.review(anyString(), anyString(), anyInt())).thenAnswer(call -> {
            settings.setCodexReasoningEffort("high");
            return context;
        });
        try (MockedStatic<ProgressManager> progress = platformProgress();
             MockedConstruction<CodexAppServerBackend> local = localBackend("chatgpt")) {
            AiApiException error = assertThrows(AiApiException.class, () -> execution.generateRaw("prompt"));
            assertTrue(error.getMessage().contains("changed"));
            verify(local.constructed().getFirst(), never()).generate(any(), any(), any());
            verify(context).close();
            verifyNoInteractions(rest);
        }
    }

    @Test
    void defaultModelIsPinnedToReviewedCatalogSelection() throws Exception {
        ReviewedContext context = mock(ReviewedContext.class);
        when(context.prompt()).thenReturn("reviewed prompt");
        when(context.root()).thenReturn(java.nio.file.Path.of("/unused-reviewed-context"));
        when(review.review(anyString(), anyString(), anyInt())).thenReturn(context);
        try (MockedStatic<ProgressManager> progress = platformProgress();
             MockedConstruction<CodexAppServerBackend> local = mockConstruction(CodexAppServerBackend.class, (backend, construction) -> {
                 when(backend.probe(any())).thenReturn(new BackendStatus(true, "chatgpt", "Mock", "fixture"));
                 catalog(backend);
                 when(backend.generate(any(), any(), any())).thenReturn(new AiResult("answer", "local-codex", "fixture-model"));
             })) {
            assertEquals("answer", execution.generateRaw("prompt"));
            verify(review).review(eq("prompt"), contains("fixture-model (account default)"), anyInt());
            verify(local.constructed().getFirst()).generate(argThat(request -> "fixture-model".equals(request.model())), any(), any());
            verifyNoInteractions(rest);
        }
    }
    @Test
    void stopDuringProbeOrCatalogPreventsReviewAndInference() throws Exception {
        for (boolean cancelProbe : new boolean[]{true, false}) {
            try (MockedStatic<ProgressManager> progress = platformProgress();
                 MockedConstruction<CodexAppServerBackend> local = mockConstruction(CodexAppServerBackend.class, (backend, construction) -> {
                     when(backend.probe(any())).thenAnswer(call -> {
                         if (cancelProbe) {
                             execution.cancelActive();
                             assertTrue(((CancellationToken) call.getArgument(0)).isCancelled());
                             throw new AiOperationCancelledException();
                         }
                         return new BackendStatus(true, "chatgpt", "Mock", "fixture");
                     });
                     when(backend.catalog(any(), anyBoolean(), any())).thenAnswer(call -> {
                         execution.cancelActive();
                         assertTrue(((CancellationToken) call.getArgument(2)).isCancelled());
                         throw new AiOperationCancelledException();
                     });
                 })) {
                assertThrows(ProcessCanceledException.class, () -> execution.generateRaw("prompt"));
                verify(local.constructed().getFirst(), never()).generate(any(), any(), any());
                verifyNoInteractions(review, rest);
            }
        }
    }

    @Test
    void executableChangeDuringDiscoveryCancelsBeforeReview() throws Exception {
        try (MockedStatic<ProgressManager> progress = platformProgress();
             MockedConstruction<CodexAppServerBackend> local = mockConstruction(CodexAppServerBackend.class, (backend, construction) -> {
                 when(backend.probe(any())).thenAnswer(call -> {
                     settings.setCodexExecutable("/different/mock-codex");
                     assertTrue(((CancellationToken) call.getArgument(0)).isCancelled());
                     throw new AiOperationCancelledException();
                 });
             })) {
            assertThrows(ProcessCanceledException.class, () -> execution.generateRaw("prompt"));
            verify(local.constructed().getFirst(), never()).catalog(any(), anyBoolean(), any());
            verify(local.constructed().getFirst(), never()).generate(any(), any(), any());
            verifyNoInteractions(review, rest);
        }
    }

}
