package com.johnnyblabs.openspec.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.actions.ExploreContextAction;
import com.johnnyblabs.openspec.util.MarkdownHtmlRenderer;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * Thinking-space panel for explore results: inline topic input at the bottom,
 * markdown-rendered AI response in the center, and a topic header at the top.
 * <p>
 * Embodies the explore stance: the input area is always visible, inviting
 * the user to think. Responses render as styled HTML so diagrams, tables,
 * and emphasis display properly.
 */
public class ExplorePanel extends JPanel implements Disposable {

    private final Project project;
    private final JEditorPane responsePane;
    private final JBLabel topicLabel;
    private final JBTextArea inputArea;
    private final JButton sendButton;
    private final JButton copyButton;
    private final JButton clearButton;
    private final JButton newConversationButton;
    private final java.util.concurrent.atomic.AtomicLong requestEpoch = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicReference<com.intellij.openapi.progress.ProgressIndicator> runIndicator =
            new java.util.concurrent.atomic.AtomicReference<>();
    private final JButton cancelButton;
    private final StringBuilder streamedResponse = new StringBuilder();
    private final java.util.concurrent.ConcurrentLinkedQueue<StreamDelta> pendingDeltas = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final Timer streamTimer = new Timer(75, e -> renderPendingDeltas());
    private volatile boolean disposed;

    private String lastTopic;
    private String lastResponse;

    public ExplorePanel(Project project) {
        super(new BorderLayout());
        this.project = project;

        // --- Toolbar (NORTH of top panel) ---
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        toolbar.setOpaque(false);

        copyButton = new JButton("Copy Response", AllIcons.Actions.Copy);
        copyButton.setEnabled(false);
        copyButton.addActionListener(e -> copyResponse());
        toolbar.add(copyButton);

        clearButton = new JButton("Clear display", AllIcons.Actions.GC);
        clearButton.addActionListener(e -> clearPanel());
        toolbar.add(clearButton);
        clearButton.setToolTipText("Clear the displayed response; backend conversation history is retained.");
        newConversationButton = new JButton("New conversation");
        newConversationButton.addActionListener(e -> {
            invalidateRequest();
            project.getService(com.johnnyblabs.openspec.ai.AiExecutionService.class).resetExploreConversation();
            clearPanel();
        });
        toolbar.add(newConversationButton);
        cancelButton = new JButton("Cancel");
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(e -> {
            project.getService(com.johnnyblabs.openspec.ai.AiExecutionService.class).cancelExplore();
            long epoch = invalidateRequest();
            showError(epoch, lastTopic, "Exploration cancelled. Start a New conversation before continuing.");
        });
        toolbar.add(cancelButton);
        toolbar.add(new JBLabel("Codex: conversation history retained; API-key sessions unsupported"));

        // --- Topic header ---
        topicLabel = new JBLabel();
        topicLabel.setFont(JBUI.Fonts.label().asBold());
        topicLabel.setBorder(JBUI.Borders.empty(6, 4, 4, 4));
        topicLabel.setVisible(false);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(toolbar, BorderLayout.NORTH);
        topPanel.add(topicLabel, BorderLayout.SOUTH);

        // --- Response area (CENTER) ---
        responsePane = new JEditorPane();
        responsePane.setEditorKit(new javax.swing.text.html.HTMLEditorKit());
        responsePane.setEditable(false);
        responsePane.setContentType("text/html");
        responsePane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        JBScrollPane responseScroll = new JBScrollPane(responsePane);
        responseScroll.setBorder(JBUI.Borders.empty());

        // --- Input area (SOUTH) ---
        inputArea = new JBTextArea(3, 40);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.getEmptyText().setText("What would you like to explore?");

        // Ctrl+Enter / Cmd+Enter submits
        KeyStroke submitKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,
                GraphicsEnvironment.isHeadless() ? java.awt.event.InputEvent.CTRL_DOWN_MASK
                        : Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        inputArea.getInputMap(JComponent.WHEN_FOCUSED)
                .put(submitKey, "submitExplore");
        inputArea.getActionMap().put("submitExplore", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                submitTopic();
            }
        });

        JBScrollPane inputScroll = new JBScrollPane(inputArea);
        inputScroll.setMinimumSize(new Dimension(0, 60));

        sendButton = new JButton("Send", AllIcons.Actions.Execute);
        sendButton.addActionListener(e -> submitTopic());

        JPanel inputPanel = new JPanel(new BorderLayout(4, 0));
        inputPanel.setBorder(JBUI.Borders.empty(4));
        inputPanel.add(inputScroll, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        // --- Assemble ---
        add(topPanel, BorderLayout.NORTH);
        add(responseScroll, BorderLayout.CENTER);
        add(inputPanel, BorderLayout.SOUTH);

        // Show invitation
        showInvitation();
    }

    /**
     * Displays the invitation empty state in the response pane.
     */
    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }

    private void showInvitation() {
        String css = MarkdownHtmlRenderer.buildThemeStylesheet();
        String muted = colorToHex(JBColor.GRAY);
        String html = "<div style='text-align:center; padding-top:40px; color:" + muted + ";'>"
                + "<p style='font-size:14pt;'>Explore</p>"
                + "<p>Ask a question, describe a problem, or just think out loud.</p>"
                + "<p style='font-size:9pt; margin-top:16px;'>Ctrl+Enter to send</p>"
                + "</div>";
        responsePane.setText(MarkdownHtmlRenderer.wrapInHtml(css, html));
    }

    /**
     * Submits the current input text as an explore topic.
     */
    private void submitTopic() {
        String topic = inputArea.getText() == null ? "" : inputArea.getText().trim();
        ExploreContextAction.runExploreDirect(project, topic);
    }

    /**
     * Displays a successful explore result with markdown rendering.
     */
    public void showResult(long epoch, String topic, String response) {

        onEdt(() -> {
            if (!isCurrent(epoch)) return;
            this.lastTopic = topic;
            stopStreaming();
            setTopicHeader(topic);
            topicLabel.setForeground(JBColor.foreground());

            String css = MarkdownHtmlRenderer.buildThemeStylesheet();
            this.lastResponse = response;
            String htmlFragment = com.johnnyblabs.openspec.ai.safety.SafeResponseMarkdown.render(response);
            responsePane.setText(MarkdownHtmlRenderer.wrapInHtml(css, htmlFragment));
            responsePane.setCaretPosition(0);

            copyButton.setEnabled(true);
            setInputEnabled(true);
        });
    }

    /**
     * Displays an error from the explore API call.
     */
    public void showError(long epoch, String topic, String error) {

        onEdt(() -> {
            if (!isCurrent(epoch)) return;
            this.lastTopic = topic;
            stopStreaming();
            setTopicHeader(topic);
            this.lastResponse = null;
            topicLabel.setForeground(JBColor.RED);

            String css = MarkdownHtmlRenderer.buildThemeStylesheet();
            String html = "<div style='color:red; padding:8px;'>"
                    + "<b>Error:</b> " + escapeHtml(error) + "</div>";
            responsePane.setText(MarkdownHtmlRenderer.wrapInHtml(css, html));
            responsePane.setCaretPosition(0);

            copyButton.setEnabled(false);
            setInputEnabled(true);
        });
    }

    /**
     * Shows a loading state while the API call is in progress.
     */
    public void showLoading(long epoch, String topic) {

        onEdt(() -> {
            if (!isCurrent(epoch)) return;
            this.lastTopic = topic;
            stopStreaming();
            streamedResponse.setLength(0);
            streamTimer.start();
            setTopicHeader(topic);
            topicLabel.setForeground(JBColor.foreground());

            String css = MarkdownHtmlRenderer.buildThemeStylesheet();
            String muted = colorToHex(JBColor.GRAY);
            String html = "<div style='text-align:center; padding-top:40px; color:" + muted + ";'>"
                    + "<p>Thinking...</p></div>";
            responsePane.setText(MarkdownHtmlRenderer.wrapInHtml(css, html));

            copyButton.setEnabled(false);
            setInputEnabled(false);
        });
    }

    /**
     * Focuses the inline input area. Called when Direct API mode activates the panel.
     */
    public void focusInput() {
        SwingUtilities.invokeLater(() -> inputArea.requestFocusInWindow());
    }

    private void copyResponse() {
        if (lastResponse != null && !lastResponse.isBlank()) {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(lastResponse), null);
            OpenSpecNotifier.info(project, "Explore", "Response copied to clipboard.");
        }
    }

    private void clearPanel() {
        invalidateRequest();
        stopStreaming();
        lastTopic = null;
        lastResponse = null;
        inputArea.setText("");
        topicLabel.setVisible(false);
        copyButton.setEnabled(false);
        showInvitation();
        setInputEnabled(true);
    }

    private void setTopicHeader(String topic) {
        String display = (topic == null || topic.isBlank()) ? "Open exploration" : topic;
        // Truncate long topics for the header
        if (display.length() > 80) {
            display = display.substring(0, 77) + "...";
        }
        topicLabel.setText("Explore: " + display);
        topicLabel.setVisible(true);
    }

    private synchronized long invalidateRequest() {
        long epoch = requestEpoch.incrementAndGet();
        var indicator = runIndicator.getAndSet(null);
        if (indicator != null) indicator.cancel();
        return epoch;
    }

    public synchronized void attachIndicator(long epoch, com.intellij.openapi.progress.ProgressIndicator indicator) {
        if (!isCurrent(epoch)) { indicator.cancel(); return; }
        runIndicator.set(indicator);
        if (!isCurrent(epoch)) indicator.cancel();
    }

    public long beginRequest() {
        long epoch = invalidateRequest();
        onEdt(() -> { if (isCurrent(epoch)) setInputEnabled(false); });
        return epoch;
    }

    public boolean isCurrent(long epoch) { return !disposed && requestEpoch.get() == epoch; }

    public void restoreInput(long epoch) {
        onEdt(() -> { if (isCurrent(epoch)) setInputEnabled(true); });
    }

    private record StreamDelta(long epoch, String text) {}

    public void appendDelta(long epoch, String delta) {
        if (isCurrent(epoch)) pendingDeltas.add(new StreamDelta(epoch, delta));
    }

    private void renderPendingDeltas() {
        if (disposed || pendingDeltas.isEmpty()) return;
        StreamDelta delta;
        boolean changed = false;
        while ((delta = pendingDeltas.poll()) != null) {
            if (isCurrent(delta.epoch())) { streamedResponse.append(delta.text()); changed = true; }
        }
        if (changed) responsePane.setText(MarkdownHtmlRenderer.wrapInHtml(MarkdownHtmlRenderer.buildThemeStylesheet(),
                com.johnnyblabs.openspec.ai.safety.SafeResponseMarkdown.render(streamedResponse.toString())));
    }

    private void stopStreaming() {
        streamTimer.stop();
        pendingDeltas.clear();
    }

    private void setInputEnabled(boolean enabled) {
        inputArea.setEnabled(enabled);
        sendButton.setEnabled(enabled);
        cancelButton.setEnabled(!enabled);
        clearButton.setEnabled(enabled);
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String colorToHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    @Override
    public void dispose() {
        disposed = true;
        invalidateRequest();
        SwingUtilities.invokeLater(this::stopStreaming);
        project.getService(com.johnnyblabs.openspec.ai.AiExecutionService.class).resetExploreConversation();
    }
}
