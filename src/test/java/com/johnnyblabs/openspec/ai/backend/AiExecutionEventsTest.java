package com.johnnyblabs.openspec.ai.backend;

import com.johnnyblabs.openspec.ai.AiApiException;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class AiExecutionEventsTest {
    @Test void completedResultIsTypedAndLateDeltasAreSuppressed() throws Exception {
        var events = new ArrayList<AiEvent>(); var late = new AtomicReference<Consumer<String>>();
        AiResult expected = new AiResult("complete", "fixture", "model");
        assertSame(expected, AiExecutionEvents.run(delta -> { late.set(delta); delta.accept("first"); return expected; }, CancellationToken.NONE, events::add));
        late.get().accept("late");
        assertEquals(2, events.size()); assertInstanceOf(AiEvent.Delta.class, events.get(0));
        assertSame(expected, assertInstanceOf(AiEvent.Completed.class, events.get(1)).result());
    }
    @Test void failureAfterDeltaEmitsExactlyOneSanitizedTerminalAndNoResult() {
        var events = new ArrayList<AiEvent>(); var late = new AtomicReference<Consumer<String>>();
        assertThrows(AiApiException.class, () -> AiExecutionEvents.run(delta -> {
            late.set(delta); delta.accept("partial"); throw new AiApiException("private provider detail");
        }, CancellationToken.NONE, events::add));
        late.get().accept("late"); assertEquals(2, events.size());
        assertFalse(assertInstanceOf(AiEvent.Failed.class, events.get(1)).detail().contains("private"));
        assertTrue(events.stream().noneMatch(event -> event instanceof AiEvent.Completed));
    }
    @Test void cancellationBeforeReturnedResultWinsWithoutCompletion() {
        var events = new ArrayList<AiEvent>(); var cancelled = new AtomicBoolean();
        assertThrows(AiOperationCancelledException.class, () -> AiExecutionEvents.run(delta -> {
            delta.accept("partial"); cancelled.set(true); return new AiResult("late complete", "fixture", "model");
        }, cancelled::get, events::add));
        assertEquals(2, events.size()); assertInstanceOf(AiEvent.Cancelled.class, events.get(1));
        assertTrue(events.stream().noneMatch(event -> event instanceof AiEvent.Completed));
    }
    @Test void preCancellationStartsNoOperationAndNullResultCannotComplete() {
        var calls = new AtomicBoolean(); var cancelled = new ArrayList<AiEvent>();
        assertThrows(AiOperationCancelledException.class, () -> AiExecutionEvents.run(delta -> {
            calls.set(true); return new AiResult("x", "fixture", "model");
        }, () -> true, cancelled::add));
        assertFalse(calls.get()); assertEquals(1, cancelled.size()); assertInstanceOf(AiEvent.Cancelled.class, cancelled.getFirst());
        var failed = new ArrayList<AiEvent>();
        assertThrows(AiApiException.class, () -> AiExecutionEvents.run(delta -> null, CancellationToken.NONE, failed::add));
        assertEquals(1, failed.size()); assertInstanceOf(AiEvent.Failed.class, failed.getFirst());
    }
    @Test void terminalDispatchWaitsForAnInFlightProducerDeltaAndSuppressesLaterDeltas() throws Exception {
        var events = new CopyOnWriteArrayList<AiEvent>();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var operationReturned = new CountDownLatch(1); var deltaRef = new AtomicReference<Consumer<String>>();
        var result = new AiResult("complete", "fixture", "model");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var run = pool.submit(() -> AiExecutionEvents.run(delta -> {
                deltaRef.set(delta);
                pool.submit(() -> delta.accept("streamed"));
                try { if (!entered.await(2, TimeUnit.SECONDS)) throw new AiApiException("producer did not start"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AiApiException("interrupted", e); }
                operationReturned.countDown(); return result;
            }, CancellationToken.NONE, event -> {
                events.add(event);
                if (event instanceof AiEvent.Delta) {
                    entered.countDown();
                    try { if (!release.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("test release timeout"); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                }
            }));
            try {
                assertTrue(operationReturned.await(2, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> run.get(250, TimeUnit.MILLISECONDS),
                        "Completion must wait for the active delta callback");
                assertEquals(1, events.size());
            } finally { release.countDown(); }
            assertSame(result, run.get(2, TimeUnit.SECONDS));
            deltaRef.get().accept("late");
            assertEquals(2, events.size());
            assertInstanceOf(AiEvent.Delta.class, events.get(0));
            assertInstanceOf(AiEvent.Completed.class, events.get(1));
        }
    }

}
