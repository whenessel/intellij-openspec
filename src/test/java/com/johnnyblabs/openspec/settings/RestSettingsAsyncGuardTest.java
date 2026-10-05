package com.johnnyblabs.openspec.settings;

import org.junit.jupiter.api.Test;
import javax.swing.JPasswordField;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class RestSettingsAsyncGuardTest {
    @Test void staleCatalogCannotReplaceAnotherProviderOrSameProviderAfterRoundTrip() {
        var guard = new RestSettingsAsyncGuard();
        var view = new AtomicReference<>("OpenAI model");
        var first = guard.start(RestSettingsAsyncGuard.Slot.CATALOG);
        guard.providerChanged();
        assertFalse(guard.apply(first, () -> view.set("stale OpenRouter model")));
        guard.providerChanged(); // A -> B -> A still rejects original A.
        assertFalse(guard.apply(first, () -> view.set("stale OpenRouter model")));
        assertEquals("OpenAI model", view.get());
    }
    @Test void latestRequestOwnsCallbackAndDisposalRejectsAllSlots() {
        var guard = new RestSettingsAsyncGuard();
        var view = new AtomicReference<>("original");
        var older = guard.start(RestSettingsAsyncGuard.Slot.CATALOG);
        var newer = guard.start(RestSettingsAsyncGuard.Slot.CATALOG);
        assertTrue(guard.apply(newer, () -> view.set("new catalog")));
        assertFalse(guard.apply(older, () -> view.set("old catalog")));
        assertEquals("new catalog", view.get());
        for (var slot : RestSettingsAsyncGuard.Slot.values()) {
            var ticket = guard.start(slot);
            guard.dispose();
            assertFalse(guard.apply(ticket, () -> view.set("after dispose")));
        }
        assertEquals("new catalog", view.get());
    }
    @Test void changingModelOrKeyInvalidatesTestResultWithoutCancelingCatalog() {
        var guard = new RestSettingsAsyncGuard();
        var result = new AtomicReference<>("unchanged");
        var test = guard.start(RestSettingsAsyncGuard.Slot.TEST);
        var catalog = guard.start(RestSettingsAsyncGuard.Slot.CATALOG);
        guard.invalidate(RestSettingsAsyncGuard.Slot.TEST);
        assertFalse(guard.apply(test, () -> result.set("old connection success")));
        assertTrue(guard.apply(catalog, () -> result.set("new catalog")));
        assertEquals("new catalog", result.get());
    }
    @Test void editorTypingWithoutEnterInvalidatesPendingConnectionTest() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            var guard = new RestSettingsAsyncGuard();
            var combo = new javax.swing.JComboBox<String>();
            combo.setEditable(true);
            var editor = (javax.swing.text.JTextComponent) combo.getEditor().getEditorComponent();
            OpenSpecSettingsPanel.watchRestInput(editor, () -> guard.invalidate(RestSettingsAsyncGuard.Slot.TEST));
            var test = guard.start(RestSettingsAsyncGuard.Slot.TEST);
            var result = new AtomicReference<>("unchanged");
            editor.setText("changed-model");
            assertFalse(guard.apply(test, () -> result.set("stale success")));
            assertEquals("unchanged", result.get());
        });
    }
    @Test void storedKeyCallbackCannotOverwriteTypedKeyOrCrossProviderBoundary() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            var guard = new RestSettingsAsyncGuard();
            var field = new JPasswordField();
            var ticket = guard.start(RestSettingsAsyncGuard.Slot.KEY);
            field.setText("synthetic-new-key");
            guard.apply(ticket, () -> OpenSpecSettingsPanel.applyStoredKeyMask(field, true));
            assertEquals("synthetic-new-key", new String(field.getPassword()));
            field.setText("");
            guard.providerChanged();
            assertFalse(guard.apply(ticket, () -> OpenSpecSettingsPanel.applyStoredKeyMask(field, true)));
            assertEquals("", new String(field.getPassword()));
            var current = guard.start(RestSettingsAsyncGuard.Slot.KEY);
            assertTrue(guard.apply(current, () -> OpenSpecSettingsPanel.applyStoredKeyMask(field, true)));
            assertFalse(new String(field.getPassword()).isBlank());
        });
    }
}
