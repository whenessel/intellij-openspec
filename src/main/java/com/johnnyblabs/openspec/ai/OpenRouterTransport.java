package com.johnnyblabs.openspec.ai;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.function.*;

/** Bounded streaming transport. Only explicit pre-stream rejection is retryable. */
public final class OpenRouterTransport {
    private final HttpClient client;
    private final Clock clock;
    public OpenRouterTransport(HttpClient client) { this(client, Clock.systemUTC()); }
    OpenRouterTransport(HttpClient client, Clock clock) {
        if (client.followRedirects() != HttpClient.Redirect.NEVER)
            throw new IllegalArgumentException("OpenRouter requires redirects disabled to protect credentials");
        this.client = client; this.clock = clock;
    }

    public OpenRouterProtocol.Completion stream(HttpRequest request, Duration timeout,
                                               BooleanSupplier canceled, Consumer<String> delta) throws AiApiException {
        long deadline = System.nanoTime() + timeout.toNanos();
        for (int attempt = 0; attempt < 3; attempt++) {
            CompletableFuture<HttpResponse<InputStream>> future = null;
            InputStream body = null;
            ExecutorService reader = null;
            Future<OpenRouterProtocol.Completion> read = null;
            try {
                check(canceled, deadline);
                future = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
                HttpResponse<InputStream> response = await(future, canceled, deadline);
                body = response.body();
                if (response.statusCode() != 200) {
                    body.close(); body = null;
                    long wait = retryDelay(response.statusCode(), response.headers().firstValue("Retry-After").orElse(""), clock);
                    if (attempt < 2 && wait >= 0 && wait <= 10000 && System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(wait) < deadline) {
                        waitFor(wait, canceled, deadline); continue;
                    }
                    throw OpenRouterProtocol.error(response.statusCode());
                }
                String type = response.headers().firstValue("Content-Type").orElse("");
                if (!type.toLowerCase(java.util.Locale.ROOT).startsWith("text/event-stream"))
                    throw new AiApiException("OpenRouter did not return an SSE stream; no result was applied");
                InputStream acceptedBody = body;
                reader = Executors.newSingleThreadExecutor(r -> { Thread thread = new Thread(r, "OpenRouter stream reader"); thread.setDaemon(true); return thread; });
                read = reader.submit(() -> readStream(acceptedBody, canceled, deadline, delta));
                return await(read, canceled, deadline);
            } catch (AiApiException | com.intellij.openapi.progress.ProcessCanceledException e) { throw e; }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new com.intellij.openapi.progress.ProcessCanceledException(); }
            catch (Exception e) {
                Throwable cause = e instanceof ExecutionException ? e.getCause() : e;
                if (cause instanceof AiApiException api) throw api;
                if (cause instanceof com.intellij.openapi.progress.ProcessCanceledException cancel) throw cancel;
                // Do not retry ambiguous network failures or any accepted HTTP 200 response.
                if (cause instanceof DeadlineExceeded) throw new AiApiException("OpenRouter request timed out; no result was applied");
                throw new AiApiException("OpenRouter stream failed or disconnected. Check network/proxy access to openrouter.ai; no result was applied.");
            } finally {
                if (body == null && future != null && future.isDone() && !future.isCompletedExceptionally() && !future.isCancelled()) {
                    var received = future.getNow(null);
                    if (received != null) body = received.body();
                }
                if (future != null && !future.isDone()) future.cancel(true);
                if (body != null) try { body.close(); } catch (Exception ignored) { }
                if (read != null) read.cancel(true);
                if (reader != null) reader.shutdownNow();
            }
        }
        throw new AiApiException("OpenRouter retry limit reached");
    }

    static OpenRouterProtocol.Completion readStream(InputStream body, BooleanSupplier canceled, long deadline,
                                                   Consumer<String> delta) throws Exception {
        var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
        var reader = new InputStreamReader(body, decoder);
        var parser = new OpenRouterProtocol.StreamParser();
        StringBuilder line = new StringBuilder(), event = new StringBuilder();
        int total = 0;
        boolean cr = false;
        while (true) {
            check(canceled, deadline);
            int value = reader.read();
            if (value < 0) break;
            if (++total > 4 * 1024 * 1024) throw new AiApiException("OpenRouter stream exceeds safety limit");
            if (value == '\n' && cr) { cr = false; continue; }
            cr = value == '\r';
            if (value == '\n' || value == '\r') {
                if (line.isEmpty()) {
                    if (!event.isEmpty()) {
                        String data = event.substring(0, event.length() - 1);
                        parser.accept(data, piece -> { check(canceled, deadline); delta.accept(piece); });
                        if ("[DONE]".equals(data)) return parser.finish();
                        event.setLength(0);
                    }
                } else if (line.toString().startsWith("data:")) {
                    String data = line.substring(5); if (data.startsWith(" ")) data = data.substring(1);
                    event.append(data).append('\n');
                    if (event.length() > 256 * 1024) throw new AiApiException("OpenRouter SSE event exceeds safety limit");
                }
                line.setLength(0);
            } else {
                line.append((char) value);
                if (line.length() > 256 * 1024) throw new AiApiException("OpenRouter SSE line exceeds safety limit");
            }
        }
        check(canceled, deadline);
        if (!line.isEmpty() || !event.isEmpty()) throw new AiApiException("OpenRouter SSE frame truncated");
        return parser.finish();
    }
    /** Negative means no safe automatic retry. Respect long Retry-After by returning an error. */
    static long retryDelay(int status, String header, Clock clock) {
        if (status != 429 && status != 503 || header.isBlank()) return -1;
        try {
            long seconds = Long.parseLong(header.trim());
            return seconds >= 0 && seconds <= 10 ? seconds * 1000 : -1;
        } catch (NumberFormatException ignored) {
            try {
                long delay = Duration.between(clock.instant(), ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis();
                return delay >= 0 && delay <= 10000 ? delay : -1;
            } catch (RuntimeException e) { return -1; }
        }
    }
    private static <T> T await(Future<T> future, BooleanSupplier canceled, long deadline) throws Exception {
        while (true) {
            check(canceled, deadline);
            try { return future.get(100, TimeUnit.MILLISECONDS); }
            catch (TimeoutException ignored) { }
        }
    }
    private static void waitFor(long milliseconds, BooleanSupplier canceled, long deadline) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(milliseconds);
        while (System.nanoTime() < until) { check(canceled, deadline); Thread.sleep(Math.min(50, Math.max(1, TimeUnit.NANOSECONDS.toMillis(until - System.nanoTime())))); }
        check(canceled, deadline);
    }
    private static void check(BooleanSupplier canceled, long deadline) {
        com.intellij.openapi.progress.ProgressManager.checkCanceled();
        if (Thread.currentThread().isInterrupted() || canceled.getAsBoolean()) throw new com.intellij.openapi.progress.ProcessCanceledException();
        if (System.nanoTime() > deadline) throw new DeadlineExceeded();
    }
    private static final class DeadlineExceeded extends RuntimeException { }
}
