package com.johnnyblabs.openspec.ai;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.johnnyblabs.openspec.ai.backend.AiRequest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpenRouterStreamingServiceTest {
    @SuppressWarnings("unchecked")
    private static <T> HttpResponse<T> response(T body, String type) {
        HttpResponse<T> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200); when(response.body()).thenReturn(body);
        if (type != null) when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of(type)), (a,b) -> true));
        return response;
    }
    private static HttpClient streamingClient() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);
        String catalog = OpenRouterProtocolTest.fixture("models.json"), stream = OpenRouterProtocolTest.fixture("completion-stream.sse");
        doAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            Object body = request.uri().getPath().endsWith("models") ? catalog : new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8));
            return CompletableFuture.completedFuture(response(body, body instanceof String ? null : "text/event-stream"));
        }).when(client).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        return client;
    }
    private static AiRequest request() { return new AiRequest("synthetic prompt", "", Path.of("."), Duration.ofSeconds(5)); }
    @Test void generationReadsOnlyOpenRouterPasswordSafeKeyCachesCatalogAndCapsOutput() throws Exception {
        HttpClient client = streamingClient(); var service = new DirectApiService(mock(Project.class), client);
        var policy = new OpenRouterPolicy(List.of("Liquid"), List.of("Liquid"), false, "deny", true, 16000);
        try (var credentials = mockStatic(AiCredentialStore.class)) {
            credentials.when(() -> AiCredentialStore.getApiKey(AiProvider.OPENROUTER)).thenReturn("provider-scoped-key");
            for (int i=0; i<2; i++) assertEquals("OK", service.generateOpenRouter(request(), "liquid/lfm-2.5-2.6b:free", policy, () -> false, ignored -> {}).text().trim());
            credentials.verify(() -> AiCredentialStore.getApiKey(AiProvider.OPENROUTER), times(2)); credentials.verifyNoMoreInteractions();
        }
        var capture = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(3)).sendAsync(capture.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals(1, capture.getAllValues().stream().filter(r -> r.uri().getPath().endsWith("models")).count());
        for (var sent : capture.getAllValues()) {
            if (sent.uri().getPath().endsWith("models")) assertTrue(sent.headers().firstValue("Authorization").isEmpty());
            else {
                assertEquals("Bearer provider-scoped-key", sent.headers().firstValue("Authorization").orElseThrow());
                var body = com.google.gson.JsonParser.parseString(OpenRouterProtocolTest.requestBody(sent)).getAsJsonObject();
                assertEquals(8192, body.get("max_tokens").getAsInt());
                assertEquals("liquid/lfm-2.5-2.6b:free", body.get("model").getAsString());
                assertEquals("deny", body.getAsJsonObject("provider").get("data_collection").getAsString());
            }
        }
    }
    @Test void streamingConnectionTestUsesUnsavedKeyAndCappedSelectedPolicyWithoutPasswordSafeWrite() throws Exception {
        HttpClient client = streamingClient(); var service = new DirectApiService(mock(Project.class), client);
        var policy = new OpenRouterPolicy(List.of("Liquid"), List.of(), false, "deny", true, 1000);
        try (var credentials = mockStatic(AiCredentialStore.class)) {
            String status = service.testConnection(AiProvider.OPENROUTER, "unsaved-key", "liquid/lfm-2.5-2.6b:free", policy, () -> false);
            assertTrue(status.contains("Liquid")); assertTrue(status.contains("liquid/lfm-2.5-2.6b:free"));
            credentials.verifyNoInteractions();
        }
        var capture = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(2)).sendAsync(capture.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest sent = capture.getAllValues().getLast();
        assertEquals("Bearer unsaved-key", sent.headers().firstValue("Authorization").orElseThrow());
        var body = com.google.gson.JsonParser.parseString(OpenRouterProtocolTest.requestBody(sent)).getAsJsonObject();
        assertEquals(256, body.get("max_tokens").getAsInt()); assertTrue(body.get("stream").getAsBoolean());
        assertFalse(body.getAsJsonObject("provider").get("allow_fallbacks").getAsBoolean());
        assertEquals("Respond with exactly: OK", body.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString());
    }
    @Test void optionalKeyStatusUsesSuppliedBearerOnlyAndNeverPersistsIdentity() throws Exception {
        HttpClient client = mock(HttpClient.class);
        doReturn(CompletableFuture.completedFuture(response(OpenRouterProtocolTest.fixture("key-status.json"), null)))
                .when(client).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        var service = new DirectApiService(mock(Project.class), client);
        try (var credentials = mockStatic(AiCredentialStore.class)) {
            String status = service.checkOpenRouterKeyStatus("unsaved-key", () -> false);
            assertTrue(status.contains("Key valid")); assertFalse(status.contains("sanitized")); assertFalse(status.contains("unsaved-key"));
            credentials.verifyNoInteractions();
        }
        var capture = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).sendAsync(capture.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("https://openrouter.ai/api/v1/key", capture.getValue().uri().toString());
        assertEquals("GET", capture.getValue().method()); assertTrue(capture.getValue().bodyPublisher().isEmpty());
        assertEquals("Bearer unsaved-key", capture.getValue().headers().firstValue("Authorization").orElseThrow());
        verifyNoMoreInteractions(client);
    }
    @Test void canceledKeyStatusCancelsRequestAndRemovesCancellationScope() throws Exception {
        HttpClient client = mock(HttpClient.class); var pending = new CompletableFuture<HttpResponse<String>>();
        doReturn(pending).when(client).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        var service = new DirectApiService(mock(Project.class), client); var polls = new AtomicInteger();
        assertThrows(ProcessCanceledException.class, () -> service.checkOpenRouterKeyStatus("unsaved-key", () -> polls.incrementAndGet() > 2));
        assertTrue(pending.isCancelled());
        doReturn(CompletableFuture.completedFuture(response(OpenRouterProtocolTest.fixture("key-status.json"), null)))
                .when(client).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        assertTrue(service.checkOpenRouterKeyStatus("unsaved-key", () -> false).contains("Key valid"));
    }
    @Test void missingOpenRouterKeyPreventsCatalogAndInferenceRequests() {
        HttpClient client = mock(HttpClient.class); var service = new DirectApiService(mock(Project.class), client);
        try (var credentials = mockStatic(AiCredentialStore.class)) {
            credentials.when(() -> AiCredentialStore.getApiKey(AiProvider.OPENROUTER)).thenReturn(null);
            assertThrows(AiApiException.class, () -> service.generateOpenRouter(request(), "model", OpenRouterPolicy.defaults(), () -> false, ignored -> {}));
            verifyNoInteractions(client); credentials.verify(() -> AiCredentialStore.getApiKey(AiProvider.OPENROUTER)); credentials.verifyNoMoreInteractions();
        }
    }
}
