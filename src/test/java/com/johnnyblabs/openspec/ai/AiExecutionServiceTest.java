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
            verifyNoInteractions(rest);
            assertTrue(local.constructed().isEmpty());
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
            verify(local.constructed().getFirst(), never()).generateConversation(any(), any(), any(), anyString(), anyBoolean());
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
