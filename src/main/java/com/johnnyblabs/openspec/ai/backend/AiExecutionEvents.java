package com.johnnyblabs.openspec.ai.backend;

import com.johnnyblabs.openspec.ai.AiApiException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Shared typed event boundary for REST, one-shot Codex and persistent conversation operations. */
public final class AiExecutionEvents {
    private AiExecutionEvents() { }
    @FunctionalInterface public interface Operation {
        AiResult run(Consumer<String> onDelta) throws AiApiException;
    }
    public static AiResult run(Operation operation, CancellationToken cancellation, Consumer<AiEvent> listener) throws AiApiException {
        Objects.requireNonNull(operation); Objects.requireNonNull(cancellation); Objects.requireNonNull(listener);
        AtomicBoolean terminal = new AtomicBoolean();
        Object dispatch = new Object();
        try {
            if (cancellation.isCancelled() || Thread.currentThread().isInterrupted()) throw new AiOperationCancelledException();
            AiResult result = operation.run(text -> {
                synchronized (dispatch) {
                    if (!terminal.get() && !cancellation.isCancelled()) listener.accept(new AiEvent.Delta(text));
                }
            });
            if (cancellation.isCancelled() || Thread.currentThread().isInterrupted()) throw new AiOperationCancelledException();
            if (result == null) throw new AiApiException("AI backend returned no successful result.");
            synchronized (dispatch) {
                if (cancellation.isCancelled() || Thread.currentThread().isInterrupted()) throw new AiOperationCancelledException();
                if (terminal.compareAndSet(false, true)) listener.accept(new AiEvent.Completed(result));
            }
            return result;
        } catch (AiApiException | RuntimeException failure) {
            boolean cancelled = failure instanceof AiOperationCancelledException || cancellation.isCancelled() || Thread.currentThread().isInterrupted();
            synchronized (dispatch) {
                if (terminal.compareAndSet(false, true)) listener.accept(cancelled ? new AiEvent.Cancelled()
                        : new AiEvent.Failed("AI operation failed; no result was applied."));
            }
            if (cancelled) throw new AiOperationCancelledException();
            if (failure instanceof AiApiException api) throw api;
            throw new AiApiException("AI event handling failed; no result was applied.", failure);
        }
    }
}
