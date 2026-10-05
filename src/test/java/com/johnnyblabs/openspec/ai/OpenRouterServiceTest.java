package com.johnnyblabs.openspec.ai;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.http.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OpenRouterServiceTest {
    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        if (status == 200) when(response.body()).thenReturn(body);
        return response;
    }
    @Test void capturedProviderUsesOnlyItsOwnKeyAndModel() throws Exception {
        HttpClient client = mock(HttpClient.class);
        doReturn(CompletableFuture.completedFuture(response(200, OpenRouterProtocolTest.fixture("completion.json")))).when(client).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        DirectApiService service = new DirectApiService(mock(Project.class), client);
        try (var keys = mockStatic(AiCredentialStore.class)) {
            keys.when(() -> AiCredentialStore.getApiKey(AiProvider.OPENROUTER)).thenReturn("synthetic-key");
            assertEquals("OK", service.generateRaw("hello", AiProvider.OPENROUTER, "reviewed/model", () -> false));
            keys.verify(() -> AiCredentialStore.getApiKey(AiProvider.OPENROUTER));
            keys.verifyNoMoreInteractions();
        }
        var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).sendAsync(request.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("openrouter.ai", request.getValue().uri().getHost());
        assertEquals("Bearer synthetic-key", request.getValue().headers().firstValue("Authorization").orElseThrow());
        var body = com.google.gson.JsonParser.parseString(OpenRouterProtocolTest.requestBody(request.getValue())).getAsJsonObject();
        assertEquals("reviewed/model", body.get("model").getAsString());
        assertEquals("hello", body.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString());
        assertEquals(4096, body.get("max_tokens").getAsInt());
        verifyNoMoreInteractions(client);
    }
    @Test void connectionTestUsesSelectedModelAndShortOutputLimit() throws Exception {
        HttpClient client = mock(HttpClient.class);
        doReturn(CompletableFuture.completedFuture(response(200, OpenRouterProtocolTest.fixture("completion.json"))))
                .when(client).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        var service = new DirectApiService(mock(Project.class), client);
        assertTrue(service.testConnection(AiProvider.OPENROUTER, "synthetic-key", "selected/model").contains("selected/model"));
        var capture = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).sendAsync(capture.capture(), any(HttpResponse.BodyHandler.class));
        var body = com.google.gson.JsonParser.parseString(OpenRouterProtocolTest.requestBody(capture.getValue())).getAsJsonObject();
        assertEquals("selected/model", body.get("model").getAsString());
        assertEquals(256, body.get("max_tokens").getAsInt());
    }
    @Test void successfulHttpWithEmbeddedErrorStillFails() {
        HttpClient client = mock(HttpClient.class);
        doReturn(CompletableFuture.completedFuture(response(200, "{\"error\":{\"code\":402,\"message\":\"synthetic-secret\"}}")))
                .when(client).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        var service = new DirectApiService(mock(Project.class), client);
        var error = assertThrows(AiApiException.class, () -> service.testConnection(AiProvider.OPENROUTER, "synthetic-key", "selected/model"));
        assertFalse(error.getMessage().contains("synthetic-secret"));
        verify(client).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verifyNoMoreInteractions(client);
    }
    @ParameterizedTest @ValueSource(ints={400,401,402,403,404,408,429,500,502,503})
    void httpFailureIsActionableSanitizedAndNeverFallsBack(int status) {
        HttpClient client = mock(HttpClient.class);
        doReturn(CompletableFuture.completedFuture(response(status, "synthetic-secret"))).when(client).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        var service = new DirectApiService(mock(Project.class), client);
        var error = assertThrows(AiApiException.class, () -> service.testConnection(AiProvider.OPENROUTER, "synthetic-key", "selected/model"));
        assertFalse(error.getMessage().contains("synthetic-secret"));
        assertTrue(error.getMessage().contains("OpenRouter"));
        assertEquals(status, ((AiApiException)error.getCause()).getStatusCode());
        verify(client).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verifyNoMoreInteractions(client);
    }
    @Test void cancellationBeforeRequestDoesNotSend() {
        HttpClient client = mock(HttpClient.class);
        try (var keys = mockStatic(AiCredentialStore.class)) {
            keys.when(() -> AiCredentialStore.getApiKey(AiProvider.OPENROUTER)).thenReturn("synthetic-key");
            var service = new DirectApiService(mock(Project.class), client);
            assertThrows(ProcessCanceledException.class, () -> service.generateRaw("hello", AiProvider.OPENROUTER, "selected/model", () -> true));
            verifyNoInteractions(client);
        }
    }
    @Test void cancellationDuringRequestCancelsFutureAndCleansThreadLocal() throws Exception {
        HttpClient client = mock(HttpClient.class);
        CompletableFuture<HttpResponse<String>> pending = new CompletableFuture<>();
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(pending);
        var service = new DirectApiService(mock(Project.class), client);
        AtomicInteger polls = new AtomicInteger();
        try (var keys = mockStatic(AiCredentialStore.class)) {
            keys.when(() -> AiCredentialStore.getApiKey(AiProvider.OPENROUTER)).thenReturn("synthetic-key");
            assertThrows(ProcessCanceledException.class, () -> service.generateRaw("hello", AiProvider.OPENROUTER, "selected/model", () -> polls.incrementAndGet() > 2));
            assertTrue(pending.isCancelled());
            doReturn(CompletableFuture.completedFuture(response(200, OpenRouterProtocolTest.fixture("completion.json")))).when(client).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
            assertEquals("OK", service.generateRaw("hello", AiProvider.OPENROUTER, "selected/model", () -> false));
        }
    }
    @Test void catalogIsPublicAndUsesCancelableSharedTransport() throws Exception {
        HttpClient client = mock(HttpClient.class);
        doReturn(CompletableFuture.completedFuture(response(200, OpenRouterProtocolTest.fixture("models.json")))).when(client).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        var service = new DirectApiService(mock(Project.class), client);
        try (var keys = mockStatic(AiCredentialStore.class)) {
            assertFalse(service.refreshOpenRouterModels(() -> false).isEmpty());
            keys.verifyNoInteractions();
        }
        var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).sendAsync(request.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("GET", request.getValue().method());
        assertTrue(request.getValue().headers().firstValue("Authorization").isEmpty());
    }
    @Test void interruptionWhileWaitingCancelsFutureAndPreservesInterrupt() {
        HttpClient client = mock(HttpClient.class);
        CompletableFuture<HttpResponse<String>> future = new CompletableFuture<>() {
            @Override public HttpResponse<String> get(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
                throw new InterruptedException();
            }
        };
        doReturn(future).when(client).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        var service = new DirectApiService(mock(Project.class), client);
        try {
            assertThrows(ProcessCanceledException.class, () -> service.testConnection(AiProvider.OPENROUTER, "synthetic-key", "model"));
            assertTrue(future.isCancelled());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
    @Test void completedResponseAfterCancellationIsNotReturned() throws Exception {
        HttpClient client = mock(HttpClient.class);
        doReturn(CompletableFuture.completedFuture(response(200, OpenRouterProtocolTest.fixture("completion.json"))))
                .when(client).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        var service = new DirectApiService(mock(Project.class), client);
        var polls = new AtomicInteger();
        try (var keys = mockStatic(AiCredentialStore.class)) {
            keys.when(() -> AiCredentialStore.getApiKey(AiProvider.OPENROUTER)).thenReturn("synthetic-key");
            assertThrows(ProcessCanceledException.class,
                    () -> service.generateRaw("hello", AiProvider.OPENROUTER, "model", () -> polls.incrementAndGet() > 2));
        }
    }
    @Test void networkExceptionDoesNotReflectCredentialBearingCause() {
        HttpClient client = mock(HttpClient.class);
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IllegalArgumentException("synthetic-key"));
        var service = new DirectApiService(mock(Project.class), client);
        var error = assertThrows(AiApiException.class, () -> service.testConnection(AiProvider.OPENROUTER, "synthetic-key", "selected/model"));
        assertFalse(error.toString().contains("synthetic-key"));
        assertFalse(error.getCause().toString().contains("synthetic-key"));
    }
}
