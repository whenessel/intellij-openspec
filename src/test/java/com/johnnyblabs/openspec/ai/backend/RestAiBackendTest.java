package com.johnnyblabs.openspec.ai.backend;

import com.johnnyblabs.openspec.ai.AiApiException;
import com.johnnyblabs.openspec.ai.AiProvider;
import com.johnnyblabs.openspec.ai.DirectApiService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** SDK-bound adapter checks: mocks replace transport calls, never paid HTTP inference. */
class RestAiBackendTest {
    private static AiRequest request() {
        return new AiRequest("reviewed prompt", "unrelated request model", Path.of("."), Duration.ofSeconds(1));
    }

    @Test void executionUsesCapturedProviderAndModelRatherThanMutableSettingsRoute() throws Exception {
        DirectApiService transport = mock(DirectApiService.class);
        when(transport.isConfigured(AiProvider.CLAUDE)).thenReturn(true);
        when(transport.generateRaw(eq("reviewed prompt"), eq(AiProvider.CLAUDE), eq("reviewed-model"), any(BooleanSupplier.class)))
                .thenReturn("answer");
        RestAiBackend backend = new RestAiBackend(transport, AiProvider.CLAUDE, "reviewed-model");
        ArrayList<String> deltas = new ArrayList<>();
        AiResult result = backend.generate(request(), CancellationToken.NONE, deltas::add);
        assertEquals("REST:CLAUDE", result.backendId());
        assertEquals("reviewed-model", result.model());
        assertEquals("answer", result.text());
        assertEquals(java.util.List.of("answer"), deltas);
        verify(transport).generateRaw(eq("reviewed prompt"), eq(AiProvider.CLAUDE), eq("reviewed-model"), any(BooleanSupplier.class));
        verify(transport, never()).isConfigured();
        verify(transport, never()).generateRaw(anyString(), any(BooleanSupplier.class));
    }

    @Test void defaultModelAndReadinessStayScopedToCapturedProvider() {
        DirectApiService transport = mock(DirectApiService.class);
        when(transport.isConfigured(AiProvider.GEMINI)).thenReturn(true);
        RestAiBackend backend = new RestAiBackend(transport, AiProvider.GEMINI, "");
        assertTrue(backend.probe().available());
        assertEquals("apikey", backend.probe().authMode());
        assertTrue(backend.models().stream().anyMatch(model -> model.isDefault() && model.id().equals(AiProvider.GEMINI.getDefaultModel())));
        verify(transport, never()).isConfigured();
    }

    @Test void cancellationAfterTransportCannotEmitOrReturnResult() throws Exception {
        DirectApiService transport = mock(DirectApiService.class);
        when(transport.isConfigured(AiProvider.OPENAI)).thenReturn(true);
        AtomicBoolean canceled = new AtomicBoolean();
        when(transport.generateRaw(anyString(), eq(AiProvider.OPENAI), anyString(), any(BooleanSupplier.class)))
                .thenAnswer(invocation -> { canceled.set(true); return "late answer"; });
        RestAiBackend backend = new RestAiBackend(transport, AiProvider.OPENAI, "gpt-4o");
        ArrayList<String> deltas = new ArrayList<>();
        assertThrows(AiApiException.class, () -> backend.generate(request(), canceled::get, deltas::add));
        assertTrue(deltas.isEmpty());
    }

    @Test void unsupportedRequiredWorkspaceWritesFailBeforeReadinessOrTransport() {
        DirectApiService transport = mock(DirectApiService.class);
        RestAiBackend backend = new RestAiBackend(transport, AiProvider.OPENAI, "gpt-4o");
        AiRequest required = new AiRequest("prompt", null, Path.of("."), Duration.ofSeconds(1),
                null, null, null, "", java.util.Set.of(BackendCapability.WORKSPACE_WRITES));
        AiApiException error = assertThrows(AiApiException.class,
                () -> backend.generate(required, CancellationToken.NONE, ignored -> fail("No output expected")));
        assertTrue(error.getMessage().contains("WORKSPACE_WRITES"));
        verifyNoInteractions(transport);
    }

    @Test void unsupportedEffortFailsBeforeReadinessOrTransport() {
        DirectApiService transport = mock(DirectApiService.class);
        RestAiBackend backend = new RestAiBackend(transport, AiProvider.OPENAI, "gpt-4o");
        AiRequest effort = new AiRequest("prompt", null, Path.of("."), Duration.ofSeconds(1), null, null, null, "medium");
        AiApiException error = assertThrows(AiApiException.class,
                () -> backend.generate(effort, CancellationToken.NONE, ignored -> fail("No output expected")));
        assertTrue(error.getMessage().contains("effort"));
        verifyNoInteractions(transport);
    }
}
