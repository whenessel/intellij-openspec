package com.johnnyblabs.openspec.ai.routing;

import com.johnnyblabs.openspec.ai.DeliveryMode;
import com.johnnyblabs.openspec.ai.routing.AiRoutingPolicy.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiRoutingPolicyTest {
    private final BackendSelection rest = new BackendSelection("REST", "CLAUDE", "saved-model", "", "");
    private final BackendSelection codex = new BackendSelection("LOCAL_CODEX", "NONE", "account-model", "codex path", "medium");
    private final BackendReadiness ready = new BackendReadiness(true, "Cached ready");
    private final BackendReadiness unavailable = new BackendReadiness(false, "Executable missing");
    private Inputs input(DeliveryMode saved, BackendSelection selection, BackendReadiness status, boolean explicit, boolean legacy, String detected) {
        return new Inputs(saved, selection, status, explicit, legacy, "Selected backend", detected);
    }
    @Test void manualRunOverrideWinsOverSavedIntegratedAndConfiguredRest() {
        var route = AiRoutingPolicy.resolve(input(DeliveryMode.DIRECT_API, rest, ready, false, true, "Detected CLI"),
                new RunOverride(DeliveryMode.EDITOR_TAB), null, "");
        assertEquals(DeliveryMode.EDITOR_TAB, route.mode());
        assertEquals(Source.RUN_OVERRIDE, route.source());
        assertNull(route.backend());
        assertFalse(route.executesBackend());
    }
    @Test void backendRunOverrideWinsOverSavedManualWithoutMutatingConfiguration() {
        var configured = input(DeliveryMode.CLIPBOARD, rest, ready, false, true, "Detected CLI");
        var route = AiRoutingPolicy.resolve(configured, new RunOverride(DeliveryMode.DIRECT_API, codex), ready, "Codex override");
        assertEquals(codex, route.backend());
        assertEquals(Source.RUN_OVERRIDE, route.source());
        assertEquals(rest, configured.configuredBackend());
        assertEquals(DeliveryMode.CLIPBOARD, configured.savedPreference());
    }
    @Test void savedManualWinsOverBackendAndDetectedSuggestion() {
        var route = AiRoutingPolicy.resolve(input(DeliveryMode.CLIPBOARD, codex, ready, true, true, "Detected CLI"), null, null, "");
        assertEquals(DeliveryMode.CLIPBOARD, route.mode());
        assertEquals(Source.SAVED_PREFERENCE, route.source());
        assertNull(route.backend());
    }
    @Test void legacyRestWinsOnlyWithoutExplicitPreference() {
        var route = AiRoutingPolicy.resolve(input(null, rest, ready, false, true, "Detected CLI"), null, null, "");
        assertEquals(DeliveryMode.DIRECT_API, route.mode());
        assertEquals(Source.LEGACY_REST, route.source());
        assertEquals(rest, route.backend());
    }
    @Test void detectedToolIsOnlyManualSuggestionAndBareProjectUsesClipboard() {
        var detected = AiRoutingPolicy.resolve(input(null, rest, unavailable, false, false, "Codex CLI"), null, null, "");
        assertEquals(DeliveryMode.CLIPBOARD, detected.mode());
        assertEquals(Source.DETECTED_TOOL, detected.source());
        assertEquals("Copy for Codex CLI", detected.label());
        var bare = AiRoutingPolicy.resolve(input(null, rest, unavailable, false, false, ""), null, null, "");
        assertEquals(Source.CLIPBOARD, bare.source());
        assertEquals(DeliveryMode.CLIPBOARD, bare.mode());
    }
    @Test void unavailableExplicitBackendAndOverrideNeverFallBackToReadyRestOrDetectedTool() {
        var selected = AiRoutingPolicy.resolve(input(null, codex, unavailable, true, true, "Detected CLI"), null, null, "");
        assertEquals(DeliveryMode.DIRECT_API, selected.mode());
        assertEquals(codex, selected.backend());
        assertFalse(selected.available());
        assertEquals("Executable missing", selected.readiness().detail());
        var overridden = AiRoutingPolicy.resolve(input(DeliveryMode.CLIPBOARD, rest, ready, false, true, "Detected CLI"),
                new RunOverride(DeliveryMode.DIRECT_API, codex), unavailable, "Codex override");
        assertEquals(codex, overridden.backend());
        assertFalse(overridden.available());
        assertEquals(Source.RUN_OVERRIDE, overridden.source());
    }
    @Test void frozenSnapshotIsUnaffectedByLaterSettingsAndReadiness() {
        var original = AiRoutingPolicy.resolve(input(DeliveryMode.DIRECT_API, codex, ready, true, false, ""), null, null, "");
        var later = AiRoutingPolicy.resolve(input(DeliveryMode.CLIPBOARD, rest, unavailable, false, true, ""), null, null, "");
        assertEquals(codex, original.backend());
        assertEquals(ready, original.readiness());
        assertTrue(original.executesBackend());
        assertFalse(later.executesBackend());
    }
}
