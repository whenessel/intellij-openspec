package com.johnnyblabs.openspec.ai.backend;

import com.google.gson.JsonObject;
import com.johnnyblabs.openspec.ai.AiApiException;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class BackendNegotiationTest {
    private final ModelDescriptor model = new ModelDescriptor("catalog-id", "Model", "fixture", true, "wire-model",
            List.of(new ReasoningEffortDescriptor("low", "Fast"), new ReasoningEffortDescriptor("high", "Deep")), "low", CapabilitySupport.SUPPORTED);
    private ModelCatalogSnapshot catalog(ModelDescriptor... models) { return new ModelCatalogSnapshot(List.of(models), Instant.EPOCH, false, true, "fixture"); }

    @Test void immutableSchemaAndRequirementsCannotBeMutatedThroughRequest() {
        JsonObject schema = new JsonObject(); schema.addProperty("type", "object");
        Set<BackendCapability> required = new HashSet<>(Set.of(BackendCapability.CANCELLATION));
        AiRequest request = new AiRequest("prompt", "model", Path.of("/reviewed"), Duration.ofSeconds(1), schema, null, null, "high", required);
        schema.addProperty("type", "string"); request.outputSchema().addProperty("type", "array"); required.clear();
        assertEquals("object", request.outputSchema().get("type").getAsString());
        assertEquals(Set.of(BackendCapability.CANCELLATION), request.requiredCapabilities());
        assertThrows(UnsupportedOperationException.class, () -> request.requiredCapabilities().clear());
    }
    @Test void unknownOrUnsupportedRequiredCapabilitiesFailClosed() throws Exception {
        assertThrows(AiApiException.class, () -> BackendCapabilities.unknown().require(Set.of(BackendCapability.STRUCTURED_OUTPUT)));
        assertThrows(AiApiException.class, () -> new BackendCapabilities(true, true, true, false).require(Set.of(BackendCapability.WORKSPACE_WRITES)));
        new BackendCapabilities(true, true, true, false).require(Set.of(BackendCapability.CANCELLATION, BackendCapability.STRUCTURED_OUTPUT));
    }
    @Test void catalogIdResolvesToWireIdWithAdvertisedEffort() throws Exception {
        var selected = ModelSelection.negotiate(catalog(model), "catalog-id", "high", true);
        assertEquals("wire-model", selected.wireModel()); assertEquals("high", selected.effort()); assertTrue(selected.fromCatalog()); assertFalse(selected.defaultSelection());
        assertEquals("wire-model", ModelSelection.negotiate(catalog(model), "wire-model", "", false).wireModel());
    }
    @Test void defaultRequiresUniqueFreshCatalogEntry() throws Exception {
        assertTrue(ModelSelection.negotiate(catalog(model), "", "", false).defaultSelection());
        assertThrows(AiApiException.class, () -> ModelSelection.negotiate(catalog(), "", "", false));
        assertThrows(AiApiException.class, () -> ModelSelection.negotiate(catalog(model, model), "", "", false));
    }
    @Test void manualModelIsPreservedWithoutInventingCapabilitiesOrEffort() throws Exception {
        var manual = ModelSelection.negotiate(catalog(model), "future-model", "", false);
        assertEquals("future-model", manual.wireModel()); assertFalse(manual.fromCatalog());
        assertThrows(AiApiException.class, () -> ModelSelection.negotiate(catalog(model), "future-model", "high", false));
        assertThrows(AiApiException.class, () -> ModelSelection.negotiate(catalog(model), "future-model", "", true));
        assertThrows(AiApiException.class, () -> ModelSelection.negotiate(catalog(model), "catalog-id", "quantum", false));
    }
    @Test void explicitUnsupportedTextRejectedAndUnknownCannotMeetRequiredText() {
        ModelDescriptor unsupported = new ModelDescriptor("id", "Name", "", true, "wire", List.of(), "", CapabilitySupport.UNSUPPORTED);
        assertThrows(AiApiException.class, () -> ModelSelection.negotiate(catalog(unsupported), "id", "", false));
        ModelDescriptor unknown = new ModelDescriptor("id", "Name", "", true);
        assertThrows(AiApiException.class, () -> ModelSelection.negotiate(catalog(unknown), "id", "", true));
    }
    @Test void staleSuggestionsAuthorizeOnlyLiteralManualIdAfterFreshAccountCheck() throws Exception {
        ModelCatalogSnapshot stale = new ModelCatalogSnapshot(List.of(model), Instant.EPOCH, true, true, "stale");
        assertEquals("catalog-id", ModelSelection.negotiate(stale, "catalog-id", "", false).wireModel());
        assertFalse(ModelSelection.negotiate(stale, "catalog-id", "", false).fromCatalog());
        assertThrows(AiApiException.class, () -> ModelSelection.negotiate(stale, "", "", false));
        assertThrows(AiApiException.class, () -> ModelSelection.negotiate(stale, "catalog-id", "high", false));
        ModelCatalogSnapshot offline = new ModelCatalogSnapshot(List.of(model), Instant.EPOCH, true, false, "offline");
        assertThrows(AiApiException.class, () -> ModelSelection.negotiate(offline, "manual", "", false));
    }
    @Test void terminalCancellationOrFailureCannotBeOverwrittenByLateCompletion() throws Exception {
        TurnTerminalState cancelled = new TurnTerminalState(); assertTrue(cancelled.finish(TurnTerminalState.Status.CANCELLED, ""));
        assertFalse(cancelled.finish(TurnTerminalState.Status.COMPLETED, "late")); assertThrows(AiApiException.class, cancelled::requireCompleted);
        TurnTerminalState completed = new TurnTerminalState(); assertTrue(completed.finish(TurnTerminalState.Status.COMPLETED, "accepted"));
        assertFalse(completed.finish(TurnTerminalState.Status.FAILED, "")); assertEquals("accepted", completed.requireCompleted());
    }
    @Test void racingTerminalEventsAcceptExactlyOneOutcome() throws Exception {
        TurnTerminalState state = new TurnTerminalState(); CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> completion = executor.submit(() -> {start.await();return state.finish(TurnTerminalState.Status.COMPLETED, "answer");});
            Future<Boolean> cancellation = executor.submit(() -> {start.await();return state.finish(TurnTerminalState.Status.CANCELLED, "");});
            start.countDown(); assertNotEquals(completion.get(2,TimeUnit.SECONDS), cancellation.get(2,TimeUnit.SECONDS));
            assertNotNull(state.outcome());
        } finally {executor.shutdownNow();}
    }
}
