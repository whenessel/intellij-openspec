package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.coordination.CoordinationService;
import com.johnnyblabs.openspec.coordination.CoordinationService.WriteResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Panel-free unit tests for the store-register probe→confirm→retry orchestration
 * ({@link CoordinationPanel#orchestrateRegister}). The static seam mirrors
 * {@code CoordinationPanel.storeHealthMarkers}: it takes the {@link CoordinationService}, the resolved
 * path, and two callbacks (the EDT Yes/No decision and the shared write completion), so the whole
 * branch matrix is exercised without constructing a {@link CoordinationPanel} (whose constructor needs
 * a live {@code ActionManager}, installs a popup menu, builds a platform {@code Tree}, and starts a
 * reload — hostile to headless tests).
 *
 * <p>Probe/retry {@link WriteResult}s are produced by {@link CoordinationService#parseWriteEnvelope}
 * over the committed real 1.10.0 fixtures (and a 1.6.0 pointer-declared refusal for the non-gate
 * failure), never hand-built, so the branch decisions key on the real CLI envelope shapes.
 */
class CoordinationPanelRegisterFlowTest {

    private static final String PATH = "/fixture/healthy-root";

    private static String fixture(String cliVersion, String name) {
        String path = "/fixtures/cli/" + cliVersion + "/" + name;
        try (InputStream is = CoordinationPanelRegisterFlowTest.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fixture: " + path, e);
        }
    }

    private static WriteResult confirmationGateProbe() {
        return CoordinationService.parseWriteEnvelope(
                false, fixture("1.10.0", "store-register-confirmation-required.json"), "ok", false);
    }

    private static WriteResult yesSuccess() {
        return CoordinationService.parseWriteEnvelope(
                true, fixture("1.10.0", "store-register-yes-success.json"), "ok", false);
    }

    private static WriteResult pointerDeclaredFailure() {
        return CoordinationService.parseWriteEnvelope(
                false, fixture("1.6.0", "store-register-pointer-declared.json"), "ok", false);
    }

    @SuppressWarnings("unchecked")
    private static Predicate<WriteResult> confirmMock() {
        return mock(Predicate.class);
    }

    @SuppressWarnings("unchecked")
    private static Consumer<WriteResult> completeMock() {
        return mock(Consumer.class);
    }

    @Test
    void noGateSuccessCompletesWithProbeAndNeverConfirmsOrRetries() {
        // A probe that succeeds with no confirmation gate (e.g. re-registering an already-a-store root):
        // it flows straight to completion. No dialog, no --yes retry.
        CoordinationService svc = mock(CoordinationService.class);
        WriteResult probe = yesSuccess();
        when(svc.registerStore(PATH)).thenReturn(probe);
        Predicate<WriteResult> confirm = confirmMock();
        Consumer<WriteResult> complete = completeMock();

        CoordinationPanel.orchestrateRegister(svc, PATH, confirm, complete);

        verify(confirm, never()).test(any());
        verify(svc, never()).registerStore(eq(PATH), eq(true));
        verify(complete).accept(probe);
    }

    @Test
    void gateThenYesConfirmsOnceRetriesWithYesAndCompletesWithCreatedIdentity() {
        CoordinationService svc = mock(CoordinationService.class);
        WriteResult probe = confirmationGateProbe();
        WriteResult retry = yesSuccess();
        when(svc.registerStore(PATH)).thenReturn(probe);
        when(svc.registerStore(PATH, true)).thenReturn(retry);
        Predicate<WriteResult> confirm = confirmMock();
        when(confirm.test(probe)).thenReturn(true);
        Consumer<WriteResult> complete = completeMock();

        CoordinationPanel.orchestrateRegister(svc, PATH, confirm, complete);

        verify(confirm).test(probe);
        verify(svc).registerStore(PATH, true);
        verify(complete).accept(retry);
        assertTrue(retry.createdFiles().contains(".openspec-store/store.yaml"),
                "the confirmed retry completes with the identity-created success");
    }

    @Test
    void gateThenNoAbortsSilentlyWithoutRetryOrCompletion() {
        CoordinationService svc = mock(CoordinationService.class);
        WriteResult probe = confirmationGateProbe();
        when(svc.registerStore(PATH)).thenReturn(probe);
        Predicate<WriteResult> confirm = confirmMock();
        when(confirm.test(probe)).thenReturn(false);
        Consumer<WriteResult> complete = completeMock();

        CoordinationPanel.orchestrateRegister(svc, PATH, confirm, complete);

        verify(confirm).test(probe);
        verify(svc, never()).registerStore(eq(PATH), eq(true));
        verify(complete, never()).accept(any());
    }

    @Test
    void nonGateFailureCompletesWithFailureAndNeverConfirms() {
        // A different-code refusal (pointer-declared) is NOT the identity gate: it must be surfaced as a
        // failure through completion, never routed through the confirmation dialog.
        CoordinationService svc = mock(CoordinationService.class);
        WriteResult probe = pointerDeclaredFailure();
        when(svc.registerStore(PATH)).thenReturn(probe);
        Predicate<WriteResult> confirm = confirmMock();
        Consumer<WriteResult> complete = completeMock();

        CoordinationPanel.orchestrateRegister(svc, PATH, confirm, complete);

        verify(confirm, never()).test(any());
        verify(svc, never()).registerStore(eq(PATH), eq(true));
        verify(complete).accept(probe);
    }
}
