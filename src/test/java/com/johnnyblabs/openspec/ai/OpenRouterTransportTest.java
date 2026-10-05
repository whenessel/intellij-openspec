package com.johnnyblabs.openspec.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OpenRouterTransportTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC);
    private static HttpClient client() { return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(); }
    private static HttpServer server() throws IOException {
        return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }
    private static HttpRequest request(HttpServer server, String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path))
                .header("Authorization", "Bearer synthetic-key").GET().build();
    }
    private static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
    @Test void capturedSseThroughRealHttpPreservesTextAndRoute() throws Exception {
        var server = server();
        String fixture = OpenRouterProtocolTest.fixture("completion-stream.sse");
        server.createContext("/stream", exchange -> respond(exchange, 200, "text/event-stream; charset=utf-8", fixture));
        server.start();
        try (var client = client()) {
            var deltas = new ArrayList<String>();
            var receipt = new OpenRouterTransport(client).stream(request(server, "/stream"), Duration.ofSeconds(5), () -> false, deltas::add);
            assertEquals("OK", receipt.text().trim());
            assertEquals(receipt.text(), String.join("", deltas));
            assertEquals("Liquid", receipt.provider());
        } finally { server.stop(0); }
    }
    @Test void explicitRejectionsRetryAtMostThreeTimesAndKeepCredentials() throws Exception {
        for (int status : List.of(429, 503)) {
            var server = server(); var attempts = new AtomicInteger();
            var keys = new CopyOnWriteArrayList<String>();
            String fixture = OpenRouterProtocolTest.fixture("completion-stream.sse");
            server.createContext("/stream", exchange -> {
                keys.add(exchange.getRequestHeaders().getFirst("Authorization"));
                if (attempts.incrementAndGet() < 3) {
                    exchange.getResponseHeaders().set("Retry-After", "0");
                    respond(exchange, status, "text/plain", "rejected");
                } else respond(exchange, 200, "text/event-stream", fixture);
            });
            server.start();
            try (var client = client()) {
                assertEquals("OK", new OpenRouterTransport(client).stream(request(server, "/stream"), Duration.ofSeconds(5), () -> false, ignored -> {}).text().trim());
                assertEquals(3, attempts.get());
                assertEquals(List.of("Bearer synthetic-key", "Bearer synthetic-key", "Bearer synthetic-key"), keys);
            } finally { server.stop(0); }
        }
    }
    @Test void persistent429EndsAfterThreeAttempts() throws Exception {
        var server = server(); var attempts = new AtomicInteger();
        server.createContext("/stream", exchange -> {
            attempts.incrementAndGet(); exchange.getResponseHeaders().set("Retry-After", "0");
            respond(exchange, 429, "text/plain", "synthetic-secret");
        }); server.start();
        try (var client = client()) {
            var error = assertThrows(AiApiException.class, () -> new OpenRouterTransport(client).stream(request(server, "/stream"), Duration.ofSeconds(5), () -> false, ignored -> {}));
            assertEquals(3, attempts.get()); assertFalse(error.toString().contains("synthetic-secret"));
        } finally { server.stop(0); }
    }
    @Test void acceptedMalformedDisconnectOrHttp200BodyErrorNeverRetries() throws Exception {
        String capture = OpenRouterProtocolTest.fixture("completion-stream.sse");
        var first = capture.lines().filter(line -> line.startsWith("data: ") && !line.contains("[DONE]")).findFirst().orElseThrow();
        var errorBody = JsonParser.parseString(first.substring(6)).getAsJsonObject();
        var error = new JsonObject(); error.addProperty("code", 503); error.addProperty("message", "synthetic-secret"); errorBody.add("error", error);
        for (String body : List.of(capture.replace("data: [DONE]", ""), "data: " + errorBody + "\n\n", "data: {\n\n")) {
            var server = server(); var attempts = new AtomicInteger();
            server.createContext("/stream", exchange -> { attempts.incrementAndGet(); respond(exchange, 200, "text/event-stream", body); }); server.start();
            try (var client = client()) {
                var failed = assertThrows(AiApiException.class, () -> new OpenRouterTransport(client).stream(request(server, "/stream"), Duration.ofSeconds(5), () -> false, ignored -> {}));
                assertEquals(1, attempts.get()); assertFalse(failed.toString().contains("synthetic-secret"));
            } finally { server.stop(0); }
        }
    }
    @Test void cancellationInterruptsRetryWaitWithoutSendingNextAttempt() throws Exception {
        var server = server(); var attempts = new AtomicInteger(); var canceled = new AtomicBoolean();
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        server.createContext("/stream", exchange -> {
            attempts.incrementAndGet(); exchange.getResponseHeaders().set("Retry-After", "10");
            respond(exchange, 429, "text/plain", "rejected");
            scheduler.schedule(() -> canceled.set(true), 200, TimeUnit.MILLISECONDS);
        }); server.start();
        try (var client = client()) {
            long start = System.nanoTime();
            assertThrows(ProcessCanceledException.class, () -> new OpenRouterTransport(client).stream(request(server, "/stream"), Duration.ofSeconds(15), canceled::get, ignored -> {}));
            assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 3000);
            assertEquals(1, attempts.get());
        } finally { server.stop(0); scheduler.shutdownNow(); }
    }
    @Test void cancellationAfterDeltaStopsDelayedStreamAndCallbacks() throws Exception {
        var server = server(); var canceled = new AtomicBoolean(); var attempts = new AtomicInteger();
        var release = new CountDownLatch(1); var callbacks = new AtomicInteger();
        String prefix = OpenRouterProtocolTest.fixture("completion-stream.sse").split("data: \\[DONE\\]")[0];
        server.createContext("/stream", exchange -> {
            attempts.incrementAndGet(); exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                output.write(prefix.getBytes(StandardCharsets.UTF_8)); output.flush();
                try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                output.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) { }
        }); server.start();
        try (var client = client()) {
            assertThrows(ProcessCanceledException.class, () -> new OpenRouterTransport(client).stream(request(server, "/stream"), Duration.ofSeconds(5), canceled::get,
                    text -> { callbacks.incrementAndGet(); canceled.set(true); }));
            assertEquals(1, callbacks.get()); assertEquals(1, attempts.get());
        } finally { release.countDown(); server.stop(0); }
    }
    @Test void byteBoundariesCommentsCrLfAndMultilineFramesDecodeUtf8() throws Exception {
        String fixture = OpenRouterProtocolTest.fixture("completion-stream.sse");
        String mutated = fixture.replace("\"content\":\"OK\"", "\"content\":\"é日OK\"");
        assertNotEquals(fixture, mutated, "Mutation must hit real content delta");
        StringBuilder framed = new StringBuilder(": keepalive\r\n\r\n");
        for (String line : mutated.lines().toList()) {
            if (!line.startsWith("data: ")) continue;
            String data = line.substring(6);
            if (data.equals("[DONE]")) framed.append("data: [DONE]\r\n\r\n");
            else {
                int split = data.indexOf(',') + 1;
                framed.append("event: message\r\ndata: ").append(data, 0, split).append("\r\ndata: ").append(data.substring(split)).append("\r\n\r\n");
            }
        }
        var bytes = new ByteArrayInputStream(framed.toString().getBytes(StandardCharsets.UTF_8)) {
            @Override public synchronized int read(byte[] buffer, int offset, int length) { return super.read(buffer, offset, Math.min(length, 1)); }
        };
        var deltas = new ArrayList<String>();
        var receipt = OpenRouterTransport.readStream(bytes, () -> false, System.nanoTime() + TimeUnit.SECONDS.toNanos(5), deltas::add);
        assertEquals("é日OK", receipt.text().trim()); assertEquals(receipt.text(), String.join("", deltas));
    }
    @Test void redirectIsRejectedWithoutForwardingBearer() throws Exception {
        var server = server(); var redirected = new AtomicInteger();
        server.createContext("/stream", exchange -> {
            exchange.getResponseHeaders().set("Location", "/other"); respond(exchange, 302, "text/plain", "redirect");
        });
        server.createContext("/other", exchange -> { redirected.incrementAndGet(); respond(exchange, 200, "text/plain", "unexpected"); }); server.start();
        try (var client = client()) {
            assertThrows(AiApiException.class, () -> new OpenRouterTransport(client).stream(request(server, "/stream"), Duration.ofSeconds(5), () -> false, ignored -> {}));
            assertEquals(0, redirected.get());
        } finally { server.stop(0); }
    }
    @Test void retryDelayAcceptsOnlyBoundedExplicit429And503Headers() {
        assertEquals(0, OpenRouterTransport.retryDelay(429, "0", CLOCK));
        assertEquals(10000, OpenRouterTransport.retryDelay(503, "10", CLOCK));
        String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(CLOCK.instant().plusSeconds(3).atZone(ZoneOffset.UTC));
        assertEquals(3000, OpenRouterTransport.retryDelay(429, date, CLOCK));
        for (String header : List.of("", "garbage", "-1", "11", "9999999999999999999999")) assertEquals(-1, OpenRouterTransport.retryDelay(429, header, CLOCK));
        for (int status : List.of(400, 401, 402, 403, 408, 500, 502, 504)) assertEquals(-1, OpenRouterTransport.retryDelay(status, "0", CLOCK));
        assertEquals(-1, OpenRouterTransport.retryDelay(429, DateTimeFormatter.RFC_1123_DATE_TIME.format(CLOCK.instant().minusSeconds(1).atZone(ZoneOffset.UTC)), CLOCK));
    }
    @Test void insecureRedirectConfigurationIsRejectedBeforeRequest() {
        try (var following = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()) {
            assertThrows(IllegalArgumentException.class, () -> new OpenRouterTransport(following));
        }
    }
    @Test void canceledBeforeRequestSendsNothing() throws Exception {
        var server = server(); var attempts = new AtomicInteger();
        server.createContext("/stream", exchange -> { attempts.incrementAndGet(); respond(exchange, 500, "text/plain", "unexpected"); }); server.start();
        try (var client = client()) {
            assertThrows(ProcessCanceledException.class, () -> new OpenRouterTransport(client).stream(request(server, "/stream"), Duration.ofSeconds(5), () -> true, ignored -> fail("No callbacks expected")));
            assertEquals(0, attempts.get());
        } finally { server.stop(0); }
    }
    @Test void wrongContentTypeIsRejectedWithoutRetryOrDeltas() throws Exception {
        var server = server(); var attempts = new AtomicInteger();
        String capture = OpenRouterProtocolTest.fixture("completion-stream.sse");
        server.createContext("/stream", exchange -> { attempts.incrementAndGet(); respond(exchange, 200, "application/json", capture); }); server.start();
        try (var client = client()) {
            assertThrows(AiApiException.class, () -> new OpenRouterTransport(client).stream(request(server, "/stream"), Duration.ofSeconds(5), () -> false, ignored -> fail("Content type must be checked before deltas")));
            assertEquals(1, attempts.get());
        } finally { server.stop(0); }
    }
    @Test void streamDeadlineReportsTimeoutRatherThanSuccessfulPartialResult() throws Exception {
        var server = server(); var release = new CountDownLatch(1); var attempts = new AtomicInteger();
        server.createContext("/stream", exchange -> {
            attempts.incrementAndGet(); exchange.getResponseHeaders().set("Content-Type", "text/event-stream"); exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                output.write(": keepalive\n\n".getBytes(StandardCharsets.UTF_8)); output.flush();
                try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }); server.start();
        try (var client = client()) {
            var error = assertThrows(AiApiException.class, () -> new OpenRouterTransport(client).stream(request(server, "/stream"), Duration.ofMillis(200), () -> false, ignored -> fail("No partial success expected")));
            assertTrue(error.getMessage().toLowerCase().contains("timed out")); assertEquals(1, attempts.get());
        } finally { release.countDown(); server.stop(0); }
    }
    @Test void doneCompletesWithoutWaitingForServerToCloseConnection() throws Exception {
        var server = server(); var release = new CountDownLatch(1);
        String capture = OpenRouterProtocolTest.fixture("completion-stream.sse");
        server.createContext("/stream", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream"); exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                output.write(capture.getBytes(StandardCharsets.UTF_8)); output.flush();
                try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }); server.start();
        try (var client = client()) {
            assertEquals("OK", new OpenRouterTransport(client).stream(request(server, "/stream"), Duration.ofSeconds(1), () -> false, ignored -> {}).text().trim());
        } finally { release.countDown(); server.stop(0); }
    }
    @Test void synchronousTransportFailureNeverLeaksCredentialsOrRetries() {
        var client = org.mockito.Mockito.mock(HttpClient.class);
        org.mockito.Mockito.when(client.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);
        org.mockito.Mockito.when(client.sendAsync(org.mockito.ArgumentMatchers.any(HttpRequest.class), org.mockito.ArgumentMatchers.any(java.net.http.HttpResponse.BodyHandler.class)))
                .thenThrow(new IllegalArgumentException("Bearer synthetic-secret"));
        var error = assertThrows(AiApiException.class, () -> new OpenRouterTransport(client).stream(
                HttpRequest.newBuilder(URI.create("https://openrouter.ai/api/v1/chat/completions")).GET().build(), Duration.ofSeconds(5), () -> false, ignored -> {}));
        assertFalse(error.toString().contains("synthetic-secret")); assertNull(error.getCause());
        org.mockito.Mockito.verify(client, org.mockito.Mockito.times(1)).sendAsync(org.mockito.ArgumentMatchers.any(HttpRequest.class), org.mockito.ArgumentMatchers.any(java.net.http.HttpResponse.BodyHandler.class));
    }
    @Test void noEligibleRouteErrorPreservesStrictPolicyAndNeverRetries() throws Exception {
        var client = org.mockito.Mockito.mock(HttpClient.class);
        org.mockito.Mockito.when(client.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);
        @SuppressWarnings("unchecked") java.net.http.HttpResponse<java.io.InputStream> response = org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
        org.mockito.Mockito.when(response.statusCode()).thenReturn(404);
        org.mockito.Mockito.when(response.body()).thenReturn(new ByteArrayInputStream("synthetic-secret".getBytes(StandardCharsets.UTF_8)));
        org.mockito.Mockito.when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(java.util.Map.of("Retry-After", List.of("0")), (a, b) -> true));
        org.mockito.Mockito.doReturn(CompletableFuture.completedFuture(response)).when(client).sendAsync(org.mockito.ArgumentMatchers.any(HttpRequest.class), org.mockito.ArgumentMatchers.any(java.net.http.HttpResponse.BodyHandler.class));
        var policy = new OpenRouterPolicy(List.of("only-reviewed"), List.of(), false, "deny", true, 256);
        var request = OpenRouterProtocol.streaming("reviewed/model", "synthetic-key", "synthetic", 256, policy, null);
        var error = assertThrows(AiApiException.class, () -> new OpenRouterTransport(client).stream(request, Duration.ofSeconds(5), () -> false, ignored -> {}));
        assertEquals(404, error.getStatusCode()); assertFalse(error.toString().contains("synthetic-secret"));
        var capture = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(client, org.mockito.Mockito.times(1)).sendAsync(capture.capture(), org.mockito.ArgumentMatchers.any(java.net.http.HttpResponse.BodyHandler.class));
        assertSame(request, capture.getValue());
        var route = JsonParser.parseString(OpenRouterProtocolTest.requestBody(capture.getValue())).getAsJsonObject().getAsJsonObject("provider");
        assertFalse(route.get("allow_fallbacks").getAsBoolean()); assertEquals("deny", route.get("data_collection").getAsString()); assertTrue(route.get("zdr").getAsBoolean());
    }
    @Test void strictPolicyAndSchemaStayInFixedHttpsRequestWithoutKeyInBody() {
        var policy = new OpenRouterPolicy(List.of("Liquid", "Liquid"), List.of("Liquid"), false, "deny", true, 256);
        var schema = new JsonObject(); schema.addProperty("type", "object");
        var request = OpenRouterProtocol.streaming("liquid/lfm-2.5-2.6b:free", "synthetic-key", "synthetic prompt", 256, policy, schema);
        assertEquals("https://openrouter.ai/api/v1/chat/completions", request.uri().toString());
        assertEquals("Bearer synthetic-key", request.headers().firstValue("Authorization").orElseThrow());
        var raw = OpenRouterProtocolTest.requestBody(request); assertFalse(raw.contains("synthetic-key"));
        var body = JsonParser.parseString(raw).getAsJsonObject(); var route = body.getAsJsonObject("provider");
        assertEquals("Liquid", route.getAsJsonArray("only").get(0).getAsString()); assertEquals(1, route.getAsJsonArray("only").size());
        assertEquals("Liquid", route.getAsJsonArray("order").get(0).getAsString());
        assertFalse(route.get("allow_fallbacks").getAsBoolean()); assertEquals("deny", route.get("data_collection").getAsString());
        assertTrue(route.get("zdr").getAsBoolean()); assertTrue(route.get("require_parameters").getAsBoolean());
        assertTrue(body.get("stream").getAsBoolean()); assertEquals(256, body.get("max_tokens").getAsInt());
        assertEquals("json_schema", body.getAsJsonObject("response_format").get("type").getAsString());
        assertTrue(body.getAsJsonObject("response_format").getAsJsonObject("json_schema").get("strict").getAsBoolean());
    }
}
