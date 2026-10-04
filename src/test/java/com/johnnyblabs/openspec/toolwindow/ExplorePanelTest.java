package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.AiExecutionService;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Swing panel behavior; requires the project's IntelliJ test classpath. */
class ExplorePanelTest {
    @Test
    void lateResultsCannotReplaceNewerRequestAndOldIndicatorIsCancelled() throws Exception {
        Project project = mock(Project.class);
        ProgressIndicator previous = mock(ProgressIndicator.class);
        SwingUtilities.invokeAndWait(() -> {
            ExplorePanel panel = new ExplorePanel(project);
            long old = panel.beginRequest();
            panel.attachIndicator(old, previous);
            long current = panel.beginRequest();
            verify(previous).cancel();
            panel.showResult(current, "current", "current response");
            panel.appendDelta(old, "stale streamed response");
            panel.showResult(old, "old", "stale final response");
            panel.showError(old, "old", "stale error");
            String html = find(panel, JEditorPane.class).getText();
            assertTrue(html.contains("current response"));
            assertFalse(html.contains("stale"));
            assertFalse(panel.isCurrent(old));
            assertTrue(panel.isCurrent(current));
        });
    }

    @Test
    void clearRetainsBackendHistoryAndNewResetsIt() throws Exception {
        Project project = mock(Project.class);
        AiExecutionService backend = mock(AiExecutionService.class);
        when(project.getService(AiExecutionService.class)).thenReturn(backend);
        SwingUtilities.invokeAndWait(() -> {
            ExplorePanel panel = new ExplorePanel(project);
            long epoch = panel.beginRequest();
            panel.showResult(epoch, "topic", "visible response");
            button(panel, "Clear display").doClick();
            verifyNoInteractions(backend);
            assertFalse(find(panel, JEditorPane.class).getText().contains("visible response"));
            button(panel, "New conversation").doClick();
            verify(backend).resetExploreConversation();
            assertFalse(panel.isCurrent(epoch));
        });
    }

    @Test
    void rejectedOldIndicatorCannotReplaceNewRunCancellationTarget() throws Exception {
        Project project = mock(Project.class);
        ProgressIndicator old = mock(ProgressIndicator.class);
        ProgressIndicator current = mock(ProgressIndicator.class);
        SwingUtilities.invokeAndWait(() -> {
            ExplorePanel panel = new ExplorePanel(project);
            long first = panel.beginRequest();
            long second = panel.beginRequest();
            panel.attachIndicator(second, current);
            panel.attachIndicator(first, old);
            verify(old).cancel();
            panel.beginRequest();
            verify(current).cancel();
        });
    }

    private static JButton button(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button && text.equals(button.getText())) return button;
            if (child instanceof Container container) {
                JButton result = buttonOrNull(container, text);
                if (result != null) return result;
            }
        }
        throw new AssertionError("Missing button: " + text);
    }
    private static JButton buttonOrNull(Container root, String text) {
        try { return button(root, text); } catch (AssertionError missing) { return null; }
    }
    private static <T extends Component> T find(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container container) {
                try { return find(container, type); } catch (AssertionError ignored) { }
            }
        }
        throw new AssertionError("Missing component: " + type.getSimpleName());
    }
}
