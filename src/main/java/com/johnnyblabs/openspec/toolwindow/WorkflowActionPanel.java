package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.icons.AllIcons;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.ai.AiApiException;
import com.johnnyblabs.openspec.ai.DeliveryMode;
import com.johnnyblabs.openspec.ai.AiExecutionService;
import com.johnnyblabs.openspec.validation.BuiltInValidator;
import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationResult;
import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.model.ArtifactInstruction;
import com.johnnyblabs.openspec.model.ArtifactStatus;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.services.AiToolDetectionService;
import com.johnnyblabs.openspec.services.ArtifactOrchestrationService;
import com.johnnyblabs.openspec.services.WorkflowSchemaContextService;
import com.johnnyblabs.openspec.model.WorkflowSchemaContext;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.services.GenerateAllListener;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.util.ArtifactFileWatcher;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;

import com.johnnyblabs.openspec.util.ApplyPromptBuilder;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Workflow action panel displayed below the tree in the tool window.
 * Shows a change selector, interactive artifact pipeline chips, a compact icon bar,
 * and a single-line status strip. Pipeline chips are the primary action surface.
 */
public class WorkflowActionPanel extends JPanel {

    // --- Color constants (light, dark) ---
    private static final JBColor COLOR_DONE = new JBColor(new Color(0, 128, 0), new Color(100, 210, 100));
    private static final JBColor COLOR_READY = JBColor.BLUE;
    private static final JBColor COLOR_GENERATING = new JBColor(new Color(60, 130, 230), new Color(80, 150, 250));
    private static final JBColor COLOR_ERROR = JBColor.RED;
    private static final JBColor COLOR_BLOCKED = new JBColor(new Color(128, 128, 128), new Color(140, 140, 140));
    private static final JBColor COLOR_CHIP_BG_TRANSPARENT = new JBColor(new Color(0, 0, 0, 0), new Color(0, 0, 0, 0));
    private static final JBColor COLOR_READY_BG = new JBColor(new Color(220, 235, 255), new Color(35, 50, 75));
    private static final JBColor COLOR_ERROR_BG = new JBColor(new Color(255, 230, 230), new Color(90, 25, 25));
    private static final JBColor COLOR_GENERATING_BORDER_BRIGHT = new JBColor(new Color(60, 130, 230), new Color(80, 150, 250));
    private static final JBColor COLOR_GENERATING_BORDER_DIM = new JBColor(new Color(140, 180, 240), new Color(50, 90, 160));
    private static final JBColor COLOR_SUCCESS = new JBColor(new Color(0, 128, 0), new Color(100, 210, 100));
    private static final JBColor COLOR_FLASH_GREEN = new JBColor(new Color(0, 180, 0), new Color(80, 220, 80));

    private static final Map<String, String> ARTIFACT_DESCRIPTIONS = Map.of(
            "proposal", "Why this change is needed",
            "design", "How to implement it",
            "specs", "What to build (requirements)",
            "tasks", "Implementation checklist"
    );

    private static final String SEPARATOR_ITEM = "───────────────";

    // CardLayout card names
    private static final String CARD_NO_CHANGES = "noChanges";
    private static final String CARD_FF_INPUT = "ffInput";
    private static final String CARD_PIPELINE = "pipeline";

    private final Project project;

    // Change selector
    private final JPanel changeSelectorPanel;
    private final JBLabel singleChangeLabel;
    private final JComboBox<String> changeCombo;

    // Pipeline visualization
    private final JPanel pipelinePanel;

    // Tool selector
    private final JComboBox<String> toolSelector;
    private final JBLabel noToolsLabel;

    // Icon action bar
    private final JButton applyIconButton;
    private final JButton verifyIconButton;
    private final JButton syncSpecsIconButton;
    private final JButton archiveIconButton;
    private final JButton cancelGenerationButton;
    private final JPanel iconBar;
    private final JBLabel iconBarChangeLabel;

    // Status strip
    private final JPanel statusStrip;
    private final JBLabel verifyStatusLabel;
    private final JBLabel taskProgressStatusLabel;
    private final JBLabel deliveryModeStatusLabel;
    private final JBLabel schemaModeStatusLabel;

    // Animation state
    private javax.swing.Timer pulseTimer;
    private javax.swing.Timer spinnerTimer;
    private javax.swing.Timer elapsedTimer;
    private String generatingArtifactId;
    private String errorArtifactId;
    private int spinnerStep;

    // Generate All state
    private long generateAllStartNanos;
    private volatile boolean generateAllInProgress;

    // CardLayout for content area
    private final CardLayout contentCardLayout;
    private final JPanel contentCards;

    // FF input form
    private final JBTextArea ffDescriptionField;
    private final JBTextField ffNameOverrideField;
    private JComboBox<String> ffSchemaCombo;
    private boolean ffSchemaComboVisible;
    private final JButton ffGoButton;
    private final JButton ffCancelButton;
    private final JBLabel ffStatusLabel;
    private boolean ffInputActive = false;

    private volatile String activeChangeName;
    private volatile String nextArtifactId;
    private volatile String lastPrompt;
    private volatile String lastOutputPath;
    private Runnable onRefreshRequested;
    private boolean updatingCombo;
    private boolean updatingToolSelector;
    private ArtifactFileWatcher activeWatcher;
    private Component lastClickedChip;
    private JBPopup activeGuidancePopup;
    private volatile boolean hasDeltaSpecs;
    private boolean allArtifactsComplete;
    private boolean hasTasksRemaining;

    public WorkflowActionPanel(Project project) {
        this.project = project;
        setLayout(new BorderLayout(4, 2));
        setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
                JBUI.Borders.empty(JBUI.scale(4), JBUI.scale(6))
        ));
        setOpaque(false);

        // --- Change selector ---
        changeSelectorPanel = new JPanel(new CardLayout());
        changeSelectorPanel.setOpaque(false);

        singleChangeLabel = new JBLabel("No active change");
        singleChangeLabel.setFont(singleChangeLabel.getFont().deriveFont(Font.BOLD, 13f));

        changeCombo = new JComboBox<>();
        changeCombo.setFont(changeCombo.getFont().deriveFont(Font.BOLD, 13f));
        changeCombo.addActionListener(e -> {
            if (updatingCombo) return;
            if (ffInputActive) onFfCancel();
            String selected = (String) changeCombo.getSelectedItem();
            if (selected != null && !selected.equals(activeChangeName)) {
                setSelectedChange(selected);
                resetVerifyStatus();
                disposeWatcher();
                refreshForChange(selected);
            }
        });

        changeSelectorPanel.add(singleChangeLabel, "label");
        changeSelectorPanel.add(changeCombo, "combo");

        // Pipeline chips
        pipelinePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0));
        pipelinePanel.setOpaque(false);

        // --- Icon action bar ---
        iconBar = new JPanel(new BorderLayout(JBUI.scale(4), 0));
        iconBar.setOpaque(false);
        iconBar.setAlignmentX(Component.LEFT_ALIGNMENT);

        iconBarChangeLabel = new JBLabel();
        iconBarChangeLabel.setFont(iconBarChangeLabel.getFont().deriveFont(Font.PLAIN, 11f));
        iconBarChangeLabel.setForeground(JBColor.GRAY);

        applyIconButton = createIconButton(AllIcons.Actions.Execute, "Apply", this::onApplyTasks);
        verifyIconButton = createIconButton(AllIcons.Actions.ProjectWideAnalysisOn, "Verify", this::onVerify);
        syncSpecsIconButton = createIconButton(AllIcons.Actions.Download, "Sync Specs", this::onSyncSpecs);
        archiveIconButton = createIconButton(AllIcons.Actions.Checked, "Archive", this::onArchive);
        cancelGenerationButton = createIconButton(AllIcons.Actions.Suspend, "Cancel Generation", this::onCancelGenerateAll);
        cancelGenerationButton.setVisible(false);

        applyIconButton.setEnabled(false);
        verifyIconButton.setEnabled(false);
        syncSpecsIconButton.setEnabled(false);
        archiveIconButton.setEnabled(false);

        JPanel iconButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(2), 0));
        iconButtons.setOpaque(false);
        iconButtons.add(applyIconButton);
        iconButtons.add(verifyIconButton);
        iconButtons.add(syncSpecsIconButton);
        iconButtons.add(archiveIconButton);
        iconButtons.add(cancelGenerationButton);

        iconBar.add(iconBarChangeLabel, BorderLayout.WEST);
        iconBar.add(iconButtons, BorderLayout.EAST);

        // --- Status strip ---
        statusStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        statusStrip.setOpaque(false);
        statusStrip.setAlignmentX(Component.LEFT_ALIGNMENT);

        verifyStatusLabel = new JBLabel("Not verified");
        verifyStatusLabel.setFont(verifyStatusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        verifyStatusLabel.setForeground(COLOR_BLOCKED);
        verifyStatusLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        verifyStatusLabel.setToolTipText("Click to verify readiness");
        verifyStatusLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onVerify();
            }
        });

        taskProgressStatusLabel = new JBLabel();
        taskProgressStatusLabel.setFont(taskProgressStatusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        taskProgressStatusLabel.setForeground(JBColor.GRAY);
        taskProgressStatusLabel.setVisible(false);

        deliveryModeStatusLabel = new JBLabel();
        deliveryModeStatusLabel.setFont(deliveryModeStatusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        deliveryModeStatusLabel.setForeground(JBColor.GRAY);

        schemaModeStatusLabel = new JBLabel();
        schemaModeStatusLabel.setFont(schemaModeStatusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        schemaModeStatusLabel.setForeground(JBColor.GRAY);
        schemaModeStatusLabel.setVisible(false);

        statusStrip.add(verifyStatusLabel);
        statusStrip.add(taskProgressStatusLabel);
        statusStrip.add(deliveryModeStatusLabel);
        statusStrip.add(schemaModeStatusLabel);

        // Tool selector
        toolSelector = new JComboBox<>();
        toolSelector.addActionListener(e -> onToolSelectionChanged());

        noToolsLabel = new JBLabel(
                "<html><small>Configure an AI tool or API key to get started.</small></html>");
        noToolsLabel.setForeground(JBColor.GRAY);
        noToolsLabel.setVisible(false);

        // FF input form
        ffDescriptionField = new JBTextArea(4, 50);
        ffDescriptionField.setLineWrap(true);
        ffDescriptionField.setWrapStyleWord(true);
        ffDescriptionField.getEmptyText().setText("Describe what you want to build or fix...");
        ffDescriptionField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateFfGoEnabled(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateFfGoEnabled(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateFfGoEnabled(); }
        });

        ffNameOverrideField = new JBTextField();
        ffNameOverrideField.getEmptyText().setText("(optional) e.g., add-user-auth");

        // Schema selector for FF
        ffSchemaComboVisible = false;
        ffSchemaCombo = new JComboBox<>();
        com.johnnyblabs.openspec.services.SchemaService schemaService = project.getService(com.johnnyblabs.openspec.services.SchemaService.class);
        if (schemaService != null) {
            List<com.johnnyblabs.openspec.model.SchemaInfo> schemas = schemaService.listSchemas();
            if (schemas.size() > 1) {
                ffSchemaComboVisible = true;
                for (com.johnnyblabs.openspec.model.SchemaInfo info : schemas) {
                    ffSchemaCombo.addItem(info.name());
                }
                // getDefaultSchema (raw), NOT getEffectiveSchema — see OpenSpecSettings.getDefaultSchema Javadoc.
                String defaultSchema = OpenSpecSettings.getInstance(project).getDefaultSchema();
                if (defaultSchema != null && !defaultSchema.isEmpty()) {
                    ffSchemaCombo.setSelectedItem(defaultSchema);
                }
            }
        }

        ffGoButton = new JButton("Go");
        ffGoButton.setIcon(AllIcons.Actions.Execute);
        ffGoButton.setFont(ffGoButton.getFont().deriveFont(Font.BOLD));
        ffGoButton.setEnabled(false);
        ffGoButton.addActionListener(e -> onFfGo());

        ffCancelButton = new JButton("Cancel");
        ffCancelButton.addActionListener(e -> onFfCancel());

        ffStatusLabel = new JBLabel("");
        ffStatusLabel.setForeground(JBColor.GRAY);

        // --- Layout ---

        // Header row: change selector (left) + tool dropdown (right)
        JPanel headerRow = new JPanel(new BorderLayout(JBUI.scale(4), 0));
        headerRow.setOpaque(false);
        headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        changeSelectorPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerRow.add(changeSelectorPanel, BorderLayout.CENTER);

        toolSelector.setMaximumRowCount(10);
        toolSelector.setPrototypeDisplayValue("Direct API  [API]xx");

        JPanel toolRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0));
        toolRow.setOpaque(false);
        toolRow.add(noToolsLabel);
        toolRow.add(toolSelector);
        headerRow.add(toolRow, BorderLayout.EAST);

        // Pipeline row — no separator, just minimal vertical padding
        pipelinePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        pipelinePanel.setBorder(JBUI.Borders.empty(JBUI.scale(2), 0, 0, 0));

        // --- CardLayout for main content area ---
        contentCardLayout = new CardLayout();
        contentCards = new JPanel(contentCardLayout);
        contentCards.setOpaque(false);

        // Card 1: No changes
        JPanel noChangesCard = buildNoChangesCard();
        contentCards.add(noChangesCard, CARD_NO_CHANGES);

        // Card 2: FF input
        JPanel ffInputCard = buildFfInputCard();
        contentCards.add(ffInputCard, CARD_FF_INPUT);

        // Card 3: Pipeline (chips → icon bar → status strip)
        // Use GridBagLayout so each row fills the full width
        JPanel pipelineCard = new JPanel(new GridBagLayout());
        pipelineCard.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridy = 0;
        pipelineCard.add(pipelinePanel, gbc);
        gbc.gridy = 1;
        pipelineCard.add(iconBar, gbc);
        gbc.gridy = 2;
        pipelineCard.add(statusStrip, gbc);
        contentCards.add(pipelineCard, CARD_PIPELINE);

        // Use BorderLayout for contentPanel so it fills the parent width
        JPanel contentPanel = new JPanel(new BorderLayout(0, 0));
        contentPanel.setOpaque(false);
        contentPanel.add(headerRow, BorderLayout.NORTH);
        contentPanel.add(contentCards, BorderLayout.CENTER);

        add(contentPanel, BorderLayout.CENTER);

        // Populate tool selector
        populateToolSelector();
        updateDeliveryModeLabel();
    }

    private JButton createIconButton(Icon icon, String tooltip, Runnable action) {
        JButton btn = new JButton(icon);
        btn.setToolTipText(tooltip);
        // Icon-only buttons are invisible to screen readers and fragile to locate in headful tests;
        // give each a stable accessible name (the tooltip is a good default label).
        btn.getAccessibleContext().setAccessibleName(tooltip);
        btn.putClientProperty("JButton.buttonType", "borderless");
        btn.setPreferredSize(new Dimension(JBUI.scale(24), JBUI.scale(24)));
        btn.setMaximumSize(new Dimension(JBUI.scale(24), JBUI.scale(24)));
        btn.addActionListener(e -> action.run());
        return btn;
    }

    private void setSelectedChange(String changeName) {
        activeChangeName = changeName;
        com.johnnyblabs.openspec.services.ExploreContextService context =
                project.getService(com.johnnyblabs.openspec.services.ExploreContextService.class);
        if (context != null) context.setSelectedChange(changeName);
    }

    public void setOnRefreshRequested(Runnable callback) {
        this.onRefreshRequested = callback;
    }

    public void refresh() {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed()) populateToolSelector();
        });
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ChangeService changeService = project.getService(ChangeService.class);
            List<Change> active = changeService.getActiveChanges();
            String[] names = active.stream().map(Change::getName).toArray(String[]::new);

            if (active.isEmpty()) {
                updateChangeSelector(names);
                updatePipelineAndButton(null, null);
                return;
            }

            String currentSelection = activeChangeName;
            String selected;
            if (currentSelection != null && active.stream().anyMatch(c -> c.getName().equals(currentSelection))) {
                selected = currentSelection;
            } else {
                selected = names[0];
            }

            updateChangeSelector(names);
            refreshForChangeOnPool(selected);
        });
    }

    /**
     * Selects the given change and triggers generation.
     */
    public void selectChangeAndGenerate(String changeName) {
        setSelectedChange(changeName);
        populateToolSelector();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            refreshForChangeOnPool(changeName);
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!project.isDisposed() && changeName.equals(activeChangeName)) onGenerate();
            });
        });
    }

    /**
     * Selects the given change without triggering generation.
     * Used by ProposeAction to auto-focus a newly created change.
     */
    public void selectChange(String changeName) {
        setSelectedChange(changeName);
        refresh();
    }

    /**
     * Sets the active change from external sources (e.g., tree selection).
     * Updates the combo box if visible, refreshes the pipeline display.
     */
    public void setActiveChange(String changeName) {
        if (changeName == null || changeName.equals(activeChangeName)) return;
        setSelectedChange(changeName);
        if (changeCombo != null && changeCombo.isVisible()) {
            changeCombo.setSelectedItem(changeName);
        }
        refreshForChange(changeName);
    }

    // --- Tool selector ---

    private void populateToolSelector() {
        updatingToolSelector = true;
        toolSelector.removeAllItems();

        AiToolDetectionService detection = project.getService(AiToolDetectionService.class);
        if (detection != null) {
            detection.detect();
            List<String> tools = detection.getDetectedTools();
            for (String tool : tools) {
                AiToolDetectionService.ToolType type = AiToolDetectionService.getToolType(tool);
                String label = tool + "  [" + (type == AiToolDetectionService.ToolType.CLI ? "CLI" : "IDE") + "]";
                toolSelector.addItem(label);
            }
        }

        AiExecutionService apiService = project.getService(AiExecutionService.class);
        boolean hasApi = "LOCAL_CODEX".equals(OpenSpecSettings.getInstance(project).getAiBackend())
                || "DIRECT_API".equals(OpenSpecSettings.getInstance(project).getPreferredDeliveryMethod())
                || (apiService != null && apiService.isConfigured());

        if (hasApi) {
            if (toolSelector.getItemCount() > 0) {
                toolSelector.addItem(SEPARATOR_ITEM);
            }
            toolSelector.addItem("LOCAL_CODEX".equals(OpenSpecSettings.getInstance(project).getAiBackend())
                    ? "Local Codex  [integrated]" : "Direct API  [API]");
        }

        if (toolSelector.getItemCount() > 0) {
            toolSelector.addItem(SEPARATOR_ITEM);
        }
        toolSelector.addItem("Editor Tab");
        toolSelector.addItem("Clipboard");

        // Show/hide based on whether any real tools are available
        boolean hasTools = (detection != null && detection.hasDetectedTools()) || hasApi;
        noToolsLabel.setVisible(!hasTools && (detection == null || !detection.hasDetectedTools()));

        // Restore saved selection
        restoreToolSelection();
        updatingToolSelector = false;
    }

    private void restoreToolSelection() {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        String savedTool = settings.getPreferredTool();
        String savedMethod = settings.getPreferredDeliveryMethod();

        // Try to match saved tool name in the selector items
        if (savedTool != null && !savedTool.isBlank()) {
            for (int i = 0; i < toolSelector.getItemCount(); i++) {
                String item = toolSelector.getItemAt(i);
                if (item.startsWith(savedTool)) {
                    toolSelector.setSelectedIndex(i);
                    return;
                }
            }
        }

        // Try to match by delivery method
        if (savedMethod != null && !savedMethod.isBlank()) {
            String match = switch (savedMethod) {
                case "DIRECT_API" -> "LOCAL_CODEX".equals(settings.getAiBackend()) ? "Local Codex" : "Direct API";
                case "EDITOR_TAB" -> "Editor Tab";
                case "CLIPBOARD" -> "Clipboard";
                default -> null;
            };
            if (match != null) {
                for (int i = 0; i < toolSelector.getItemCount(); i++) {
                    String item = toolSelector.getItemAt(i);
                    if (item.startsWith(match)) {
                        toolSelector.setSelectedIndex(i);
                        return;
                    }
                }
            }
        }

        // Defaults follow the same resolver as menu actions; detected tools do not override a backend.
        com.johnnyblabs.openspec.services.DeliveryMethodResolver resolver =
                project.getService(com.johnnyblabs.openspec.services.DeliveryMethodResolver.class);
        DeliveryMode mode = resolver == null ? DeliveryMode.CLIPBOARD : resolver.resolve().mode();
        String prefix = mode == DeliveryMode.DIRECT_API
                ? ("LOCAL_CODEX".equals(settings.getAiBackend()) ? "Local Codex" : "Direct API")
                : mode == DeliveryMode.EDITOR_TAB ? "Editor Tab" : "Clipboard";
        for (int i = 0; i < toolSelector.getItemCount(); i++) {
            if (toolSelector.getItemAt(i).startsWith(prefix)) { toolSelector.setSelectedIndex(i); return; }
        }
        if (toolSelector.getItemCount() > 0) toolSelector.setSelectedIndex(0);
    }

    private void onToolSelectionChanged() {
        if (updatingToolSelector) return;
        String selected = (String) toolSelector.getSelectedItem();
        if (selected == null || selected.equals(SEPARATOR_ITEM)) return;

        // Save preference
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        ToolSelection ts = parseToolSelection(selected);
        settings.setPreferredTool(ts.toolName);
        settings.setPreferredDeliveryMethod(ts.mode.name());

        // Update status strip delivery mode
        updateDeliveryModeLabel();
    }

    private record ToolSelection(String toolName, DeliveryMode mode) {}

    private ToolSelection parseToolSelection(String selectorItem) {
        if (selectorItem == null || selectorItem.equals(SEPARATOR_ITEM)) {
            return new ToolSelection("", DeliveryMode.CLIPBOARD);
        }
        if (selectorItem.startsWith("Direct API") || selectorItem.startsWith("Local Codex")) {
            return new ToolSelection("", DeliveryMode.DIRECT_API);
        }
        if (selectorItem.equals("Editor Tab")) {
            return new ToolSelection("", DeliveryMode.EDITOR_TAB);
        }
        if (selectorItem.equals("Clipboard")) {
            return new ToolSelection("", DeliveryMode.CLIPBOARD);
        }
        // Tool with type label, e.g. "Claude Code  [CLI]"
        String toolName = selectorItem.replaceAll("\\s+\\[(?:CLI|IDE)]$", "").trim();
        return new ToolSelection(toolName, DeliveryMode.CLIPBOARD);
    }

    private DeliveryMode getSelectedDeliveryMode() {
        String selected = (String) toolSelector.getSelectedItem();
        return parseToolSelection(selected).mode;
    }

    private String getSelectedToolName() {
        String selected = (String) toolSelector.getSelectedItem();
        return parseToolSelection(selected).toolName;
    }

    private String getDeliveryModeLabel() {
        String selected = (String) toolSelector.getSelectedItem();
        ToolSelection ts = parseToolSelection(selected);
        return switch (ts.mode) {
            case DIRECT_API -> project.getService(AiExecutionService.class).getBackendLabel();
            case EDITOR_TAB -> "Editor Tab";
            case CLIPBOARD -> ts.toolName.isBlank() ? "Clipboard" : "Clipboard: " + ts.toolName;
        };
    }

    private void updateDeliveryModeLabel() {
        deliveryModeStatusLabel.setText(" \u00B7 " + getDeliveryModeLabel());
    }

    // --- Change selector ---

    private void updateChangeSelector(String[] names) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (names.length == 0) {
                singleChangeLabel.setText("No active change");
                ((CardLayout) changeSelectorPanel.getLayout()).show(changeSelectorPanel, "label");
                return;
            }

            if (names.length == 1) {
                singleChangeLabel.setText(names[0]);
                singleChangeLabel.setFont(singleChangeLabel.getFont().deriveFont(Font.BOLD));
                ((CardLayout) changeSelectorPanel.getLayout()).show(changeSelectorPanel, "label");
                setSelectedChange(names[0]);
            } else {
                updatingCombo = true;
                changeCombo.removeAllItems();
                for (String name : names) {
                    changeCombo.addItem(name);
                }
                if (activeChangeName != null) {
                    changeCombo.setSelectedItem(activeChangeName);
                }
                updatingCombo = false;
                ((CardLayout) changeSelectorPanel.getLayout()).show(changeSelectorPanel, "combo");
            }
        });
    }

    // --- Pipeline ---

    private void refreshForChange(String changeName) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> refreshForChangeOnPool(changeName));
    }

    private void refreshForChangeOnPool(String changeName) {
        ArtifactOrchestrationService orchestration = project.getService(ArtifactOrchestrationService.class);
        ChangeArtifactDag dag = orchestration.getArtifactStatus(changeName);
        // Resolve the schema/version context off-EDT so workflow surfaces adapt to the
        // active mode (actionContext.mode) instead of assuming a spec-driven layout.
        // Snapshot it (like dag) and pass it through, so overlapping refreshes can't
        // desync the rendered pipeline from the mode label.
        WorkflowSchemaContextService contextService = project.getService(WorkflowSchemaContextService.class);
        WorkflowSchemaContext ctx = contextService != null ? contextService.getContext(changeName) : null;
        updatePipelineAndButton(dag, ctx);
    }

    /**
     * Reflects a non-default workflow mode (e.g. {@code workspace-planning}) in the status
     * strip. For the default spec-driven repo-local case the label stays hidden, so existing
     * behavior is unchanged. Reads the snapshotted context, not the on-disk layout.
     */
    private void updateSchemaModeLabel(WorkflowSchemaContext ctx) {
        if (ctx != null && ctx.isNonDefaultMode()) {
            schemaModeStatusLabel.setText(" · mode: " + ctx.mode());
            schemaModeStatusLabel.setToolTipText("OpenSpec mode: " + ctx.mode()
                    + " (schema: " + ctx.schemaName() + ")");
            schemaModeStatusLabel.setVisible(true);
        } else {
            schemaModeStatusLabel.setVisible(false);
        }
    }

    private void updatePipelineAndButton(ChangeArtifactDag dag, WorkflowSchemaContext ctx) {
        ApplicationManager.getApplication().invokeLater(() -> {
            pipelinePanel.removeAll();

            if (dag == null) {
                setSelectedChange(null);
                allArtifactsComplete = false;
                hasTasksRemaining = false;
                hasDeltaSpecs = false;
                updateIconBarState();
                schemaModeStatusLabel.setVisible(false);
                contentCardLayout.show(contentCards, CARD_NO_CHANGES);
                return;
            }

            setSelectedChange(dag.getChangeName());
            contentCardLayout.show(contentCards, CARD_PIPELINE);

            // Build interactive pipeline chips
            List<ArtifactInfo> artifacts = dag.getArtifacts();
            for (int i = 0; i < artifacts.size(); i++) {
                ArtifactInfo a = artifacts.get(i);
                pipelinePanel.add(createPipelineChip(a));
                if (i < artifacts.size() - 1) {
                    JBLabel arrow = new JBLabel("\u2192");
                    arrow.setForeground(JBColor.GRAY);
                    pipelinePanel.add(arrow);
                }
            }
            pipelinePanel.revalidate();
            pipelinePanel.repaint();

            // Determine next artifact
            List<ArtifactInfo> ready = dag.getReadyArtifacts();
            nextArtifactId = ready.isEmpty() ? null : ready.getFirst().id();

            allArtifactsComplete = dag.isComplete();

            if (allArtifactsComplete) {
                showApplyState(dag);
            } else {
                hasTasksRemaining = false;
                taskProgressStatusLabel.setVisible(false);
            }

            // Update icon bar and status strip
            updateIconBarState();
            checkDeltaSpecs();
            updateDeliveryModeLabel();
            updateSchemaModeLabel(ctx);

            if (allArtifactsComplete) {
                runChangeValidation();
            }
        });
    }

    private void updateIconBarState() {
        applyIconButton.setEnabled(allArtifactsComplete);
        verifyIconButton.setEnabled(allArtifactsComplete);
        syncSpecsIconButton.setEnabled(hasDeltaSpecs);
        archiveIconButton.setEnabled(allArtifactsComplete && !hasTasksRemaining);

        // Update change-name badge
        iconBarChangeLabel.setText(activeChangeName != null ? activeChangeName : "");

        // Contextual tooltips with change name and disabled reasons
        if (activeChangeName != null) {
            applyIconButton.setToolTipText(allArtifactsComplete
                    ? "Apply: " + activeChangeName
                    : "Apply (complete all artifacts first)");
            verifyIconButton.setToolTipText(allArtifactsComplete
                    ? "Verify: " + activeChangeName
                    : "Verify (complete all artifacts first)");
            syncSpecsIconButton.setToolTipText(hasDeltaSpecs
                    ? "Sync Specs: " + activeChangeName
                    : "Sync Specs (no delta specs)");
            archiveIconButton.setToolTipText(allArtifactsComplete && !hasTasksRemaining
                    ? "Archive: " + activeChangeName
                    : "Archive (complete all artifacts and tasks first)");
        }
    }

    private void checkDeltaSpecs() {
        if (activeChangeName == null) {
            hasDeltaSpecs = false;
            return;
        }
        String changeName = activeChangeName;
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            com.johnnyblabs.openspec.services.SpecSyncService syncService =
                    project.getService(com.johnnyblabs.openspec.services.SpecSyncService.class);
            hasDeltaSpecs = syncService.hasDeltaSpecs(changeName);
        });
    }

    private JPanel createPipelineChip(ArtifactInfo artifact) {
        JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 1));
        chip.setOpaque(true);

        Color color;
        switch (artifact.status()) {
            case DONE -> {
                color = COLOR_DONE;
                chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                chip.setBackground(COLOR_CHIP_BG_TRANSPARENT);
                chip.setBorder(JBUI.Borders.empty(1));
            }
            case READY -> {
                color = COLOR_READY;
                chip.setBackground(COLOR_READY_BG);
                chip.setBorder(BorderFactory.createLineBorder(COLOR_READY, 1, true));
            }
            case GENERATING -> {
                color = COLOR_GENERATING;
                chip.setBackground(COLOR_READY_BG);
                boolean bright = (pulseTimer != null && System.currentTimeMillis() % 1200 < 600);
                Color borderColor = bright ? COLOR_GENERATING_BORDER_BRIGHT : COLOR_GENERATING_BORDER_DIM;
                chip.setBorder(BorderFactory.createLineBorder(borderColor, 2, true));
            }
            case ERROR -> {
                color = COLOR_ERROR;
                chip.setBackground(COLOR_ERROR_BG);
                chip.setBorder(BorderFactory.createLineBorder(COLOR_ERROR, 2, true));
            }
            default -> {
                color = COLOR_BLOCKED;
                chip.setBackground(COLOR_CHIP_BG_TRANSPARENT);
                chip.setBorder(JBUI.Borders.empty(1));
            }
        }

        // Icon
        JBLabel iconLabel;
        if (artifact.status() == ArtifactStatus.GENERATING) {
            iconLabel = new JBLabel(getSpinnerIcon());
        } else if (artifact.status() == ArtifactStatus.ERROR) {
            iconLabel = new JBLabel(AllIcons.General.Error);
        } else if (artifact.status() == ArtifactStatus.DONE) {
            iconLabel = new JBLabel(AllIcons.Actions.Checked);
        } else {
            String iconText = artifact.status() == ArtifactStatus.READY ? "\u25CF" : "\u25CB";
            iconLabel = new JBLabel(iconText);
            iconLabel.setForeground(color);
            iconLabel.setFont(iconLabel.getFont().deriveFont(12f));
        }

        JBLabel nameLabel = new JBLabel(artifact.id());
        nameLabel.setForeground(color);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 12f));

        chip.add(iconLabel);
        chip.add(nameLabel);

        // State-aware tooltips
        switch (artifact.status()) {
            case READY -> chip.setToolTipText("Click to generate \u00B7 Right-click for options");
            case DONE -> chip.setToolTipText("Click to open \u00B7 Right-click for options");
            case GENERATING -> chip.setToolTipText("Generating...");
            case SKIPPED -> chip.setToolTipText("Skipped by CLI workflow");
            case BLOCKED -> {
                String desc = ARTIFACT_DESCRIPTIONS.getOrDefault(artifact.id(), artifact.id());
                String deps = artifact.missingDeps() != null && !artifact.missingDeps().isEmpty()
                        ? "Waiting on: " + String.join(", ", artifact.missingDeps())
                        : desc;
                chip.setToolTipText(deps);
            }
            default -> chip.setToolTipText(ARTIFACT_DESCRIPTIONS.getOrDefault(artifact.id(), artifact.id()));
        }

        // Cursors
        if (artifact.status() == ArtifactStatus.READY || artifact.status() == ArtifactStatus.DONE) {
            chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        // Hover effect for READY chips
        if (artifact.status() == ArtifactStatus.READY) {
            Color normalBg = COLOR_READY_BG;
            Color hoverBg = new JBColor(new Color(200, 220, 255), new Color(45, 60, 90));
            chip.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    chip.setBackground(hoverBg);
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    chip.setBackground(normalBg);
                }
            });
        }

        // Click and context menu handlers
        chip.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    switch (artifact.status()) {
                        case READY -> {
                            nextArtifactId = artifact.id();
                            lastClickedChip = chip;
                            onGenerate();
                        }
                        case DONE -> openArtifactFile(artifact);
                        default -> { /* BLOCKED, GENERATING: no click action */ }
                    }
                }
            }
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showChipContextMenu(e, artifact);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showChipContextMenu(e, artifact);
            }
        });

        return chip;
    }

    private void openArtifactFile(ArtifactInfo artifact) {
        if (activeChangeName == null || artifact.outputPath() == null) return;
        String basePath = project.getBasePath();
        if (basePath == null) return;

        String outputPath = artifact.outputPath();
        if (outputPath.contains("*")) {
            outputPath = "";
        }
        String filePath = basePath + "/openspec/changes/" + activeChangeName + "/" + outputPath;
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath);
        if (file != null) {
            FileEditorManager.getInstance(project).openFile(file, true);
        }
    }

    private void showChipContextMenu(MouseEvent e, ArtifactInfo artifact) {
        JPopupMenu menu = new JPopupMenu();

        switch (artifact.status()) {
            case DONE -> {
                JMenuItem openItem = new JMenuItem("Open file");
                openItem.addActionListener(ev -> openArtifactFile(artifact));
                menu.add(openItem);

                JMenuItem regenItem = new JMenuItem("Regenerate");
                regenItem.addActionListener(ev -> onRegenerateArtifact(artifact));
                menu.add(regenItem);

                JMenuItem copyPromptItem = new JMenuItem("Copy prompt");
                copyPromptItem.addActionListener(ev -> onCopyPromptForArtifact(artifact));
                menu.add(copyPromptItem);
            }
            case READY -> {
                JMenuItem genItem = new JMenuItem("Generate");
                genItem.addActionListener(ev -> {
                    nextArtifactId = artifact.id();
                    lastClickedChip = (Component) e.getSource();
                    onGenerate();
                });
                menu.add(genItem);

                // Generate All Remaining
                if (getSelectedDeliveryMode() == DeliveryMode.DIRECT_API && countReadyArtifacts() >= 2) {
                    JMenuItem genAllItem = new JMenuItem("Generate All Remaining");
                    genAllItem.addActionListener(ev -> onGenerateAll());
                    menu.add(genAllItem);
                }

                JMenuItem copyPromptItem = new JMenuItem("Copy prompt");
                copyPromptItem.addActionListener(ev -> onCopyPromptForArtifact(artifact));
                menu.add(copyPromptItem);
            }
            case GENERATING -> {
                JMenuItem cancelItem = new JMenuItem("Cancel");
                cancelItem.addActionListener(ev -> onCancelGeneration(artifact.id()));
                menu.add(cancelItem);
            }
            default -> {
                return;
            }
        }

        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void onRegenerateArtifact(ArtifactInfo artifact) {
        if (activeChangeName == null) return;

        ArtifactOrchestrationService orchestration = project.getService(ArtifactOrchestrationService.class);
        List<String> downstream = orchestration.getCompletedDownstream(activeChangeName, artifact.id());

        if (!downstream.isEmpty()) {
            int result = Messages.showYesNoDialog(
                    project,
                    "Regenerating \"" + artifact.id() + "\" may make these downstream artifacts inconsistent:\n"
                            + String.join(", ", downstream) + "\n\nContinue?",
                    "Regenerate Artifact",
                    Messages.getWarningIcon());
            if (result != Messages.YES) return;
        }

        nextArtifactId = artifact.id();
        executeGeneration(getSelectedDeliveryMode());
    }

    private void onCopyPromptForArtifact(ArtifactInfo artifact) {
        if (activeChangeName == null) return;
        nextArtifactId = artifact.id();
        executeGeneration(DeliveryMode.CLIPBOARD);
    }

    private int countReadyArtifacts() {
        int count = 0;
        for (Component comp : pipelinePanel.getComponents()) {
            if (comp instanceof JPanel chipPanel) {
                String tooltip = ((JComponent) chipPanel).getToolTipText();
                if (tooltip != null && tooltip.startsWith("Click to generate")) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * In-flight single-artifact generations, keyed by artifact id, so each chip's Cancel targets its
     * own run. A single shared field would mis-target: right-clicking Generate on two chips in a row
     * (each mutates {@code nextArtifactId}) runs both concurrently, and one indicator would overwrite
     * the other — cancelling chip A would then cancel B. Entries are removed when their run ends.
     */
    private final java.util.Map<String, com.intellij.openapi.progress.ProgressIndicator> activeGenerations =
            new java.util.concurrent.ConcurrentHashMap<>();

    private void onCancelGeneration(String artifactId) {
        // Cancel this artifact's own generation indicator (not the Generate-All flag); the isCanceled()
        // guard in executeGeneration then discards the result — no file written, no success UI.
        com.intellij.openapi.progress.ProgressIndicator ind = activeGenerations.get(artifactId);
        if (ind != null) {
            ind.cancel();
            project.getService(AiExecutionService.class).cancelActive();
        }
    }

    // --- Apply ---

    /**
     * Selects the given change and triggers apply.
     */
    public void selectChangeAndApply(String changeName) {
        setSelectedChange(changeName);
        refresh();
        ApplicationManager.getApplication().invokeLater(this::onApplyTasks);
    }

    private void showApplyState(ChangeArtifactDag dag) {
        String changeName = activeChangeName;
        String changeDir = project.getBasePath() + "/openspec/changes/" + changeName;

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String tasksContent = null;
            try {
                Path tasksPath = Path.of(changeDir, "tasks.md");
                if (Files.exists(tasksPath)) {
                    tasksContent = Files.readString(tasksPath);
                }
            } catch (IOException ignored) {
            }

            String finalTasksContent = tasksContent;
            ApplicationManager.getApplication().invokeLater(() -> applyStateToUi(finalTasksContent));
        });
    }

    private void applyStateToUi(String tasksContent) {
        if (tasksContent == null) {
            hasTasksRemaining = false;
            taskProgressStatusLabel.setVisible(false);
            updateIconBarState();
            return;
        }

        int[] counts = ApplyPromptBuilder.countTasks(tasksContent);
        int complete = counts[0];
        int total = counts[1];
        int remaining = total - complete;

        if (total == 0 || remaining == 0) {
            hasTasksRemaining = false;
            if (total > 0) {
                taskProgressStatusLabel.setText(" \u00B7 " + complete + "/" + total + " tasks");
                taskProgressStatusLabel.setVisible(true);
            } else {
                taskProgressStatusLabel.setVisible(false);
            }
            updateIconBarState();
            return;
        }

        // Tasks remaining
        hasTasksRemaining = true;
        taskProgressStatusLabel.setText(" \u00B7 " + complete + "/" + total + " tasks");
        taskProgressStatusLabel.setVisible(true);
        updateIconBarState();
    }

    private void onApplyTasks() {
        if (activeChangeName == null) return;
        String changeName = activeChangeName;
        String changeDir = project.getBasePath() + "/openspec/changes/" + changeName;

        // Capture UI state on EDT before dispatching to background thread
        DeliveryMode mode = getSelectedDeliveryMode();
        String toolName = getSelectedToolName();

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String prompt = ApplyPromptBuilder.build(changeName, changeDir);

            lastPrompt = prompt;

            switch (mode) {
                case CLIPBOARD -> {
                    String clipboardPrompt = prompt;
                    if (!toolName.isBlank() && AiToolDetectionService.isCliTool(toolName)) {
                        clipboardPrompt = ApplyPromptBuilder.appendSavePathHint(prompt, changeDir);
                    }
                    lastPrompt = clipboardPrompt;
                    Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(new StringSelection(clipboardPrompt), null);
                    ApplicationManager.getApplication().invokeLater(() -> {
                        String toolLabel = toolName.isBlank() ? null : toolName;
                        AiToolDetectionService.ToolGuidance guidance =
                                AiToolDetectionService.getToolGuidance(toolLabel != null ? toolLabel : "your AI tool");
                        String detail = guidance.canAutoSave()
                                ? guidance.pasteAction() + " \u2014 watching tasks.md for progress..."
                                : guidance.pasteAction() + ". Save tasks.md when done.";
                        showGuidancePopover(archiveIconButton,
                                "\u2713 Implementation prompt copied", detail,
                                () -> {
                                    Toolkit.getDefaultToolkit().getSystemClipboard()
                                            .setContents(new StringSelection(lastPrompt), null);
                                    OpenSpecNotifier.info(project, "Generate", "Prompt re-copied to clipboard");
                                });
                        startTaskWatcher(changeName, changeDir);
                    });
                }
                case EDITOR_TAB -> ApplicationManager.getApplication().invokeLater(() -> {
                    try {
                        VirtualFile scratch = com.intellij.openapi.application.WriteAction.compute(() -> {
                            VirtualFile tmp = LocalFileSystem.getInstance()
                                    .findFileByPath(System.getProperty("java.io.tmpdir"));
                            if (tmp == null) return null;
                            String name = "openspec-apply-" + changeName + "-prompt.md";
                            VirtualFile file = tmp.findChild(name);
                            if (file != null) file.delete(this);
                            file = tmp.createChildData(this, name);
                            file.setBinaryContent(prompt.getBytes(StandardCharsets.UTF_8));
                            return file;
                        });
                        if (scratch != null) {
                            FileEditorManager.getInstance(project).openFile(scratch, true);
                        }
                        showGuidancePopover(archiveIconButton,
                                "\u2713 Opened in editor tab",
                                "Copy the prompt to your AI tool, then save tasks.md when done.",
                                null);
                        startTaskWatcher(changeName, changeDir);
                    } catch (IOException ex) {
                        OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_SYSTEM, "Apply",
                                "Failed to open prompt: " + ex.getMessage(), com.intellij.notification.NotificationType.ERROR);
                    }
                });
                case DIRECT_API -> {
                    OpenSpecNotifier.warn(project, "Apply",
                            "Apply delivers a prompt for your AI tool to implement. Use Clipboard or Editor Tab to copy the prompt, then paste it into your AI tool.");
                }
            }
        });
    }

    private void startTaskWatcher(String changeName, String changeDir) {
        disposeWatcher();
        activeWatcher = new ArtifactFileWatcher(
                changeDir, "tasks.md",
                () -> onTaskFileChanged(changeName, changeDir),
                () -> OpenSpecNotifier.info(project, "Apply", "No task progress detected yet. Check tasks.md manually.")
        );
        activeWatcher.start();
    }

    private void onTaskFileChanged(String changeName, String changeDir) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                String content = Files.readString(Path.of(changeDir, "tasks.md"));
                int[] counts = ApplyPromptBuilder.countTasks(content);
                int complete = counts[0];
                int total = counts[1];

                ApplicationManager.getApplication().invokeLater(() -> {
                    taskProgressStatusLabel.setText(" \u00B7 " + complete + "/" + total + " tasks");
                    taskProgressStatusLabel.setVisible(true);

                    if (total > 0 && complete == total) {
                        hasTasksRemaining = false;
                        updateIconBarState();
                        runChangeValidation();
                        if (onRefreshRequested != null) onRefreshRequested.run();
                    } else {
                        hasTasksRemaining = true;
                        updateIconBarState();
                    }
                });
            } catch (IOException ignored) {
            }
        });
    }

    // --- Sync Specs ---

    private void onSyncSpecs() {
        if (activeChangeName == null) return;
        String changeName = activeChangeName;

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Computing spec sync...", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                com.johnnyblabs.openspec.services.SpecSyncService syncService =
                        project.getService(com.johnnyblabs.openspec.services.SpecSyncService.class);
                java.util.List<com.johnnyblabs.openspec.model.SpecSyncResult> results =
                        syncService.computeSync(changeName);

                ApplicationManager.getApplication().invokeLater(() -> {
                    if (results.isEmpty()) {
                        OpenSpecNotifier.info(project, "Sync Specs",
                                "No delta specs to sync for \"" + changeName + "\"");
                        return;
                    }

                    com.johnnyblabs.openspec.dialogs.SyncPreviewDialog dialog =
                            new com.johnnyblabs.openspec.dialogs.SyncPreviewDialog(project, results);
                    if (dialog.showAndGet()) {
                        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Applying spec sync...", false) {
                            @Override
                            public void run(@NotNull ProgressIndicator ind) {
                                try {
                                    syncService.applySync(results);
                                    OpenSpecNotifier.info(project, "Sync Specs",
                                            "Specs synced for \"" + changeName + "\"");
                                    if (onRefreshRequested != null) {
                                        ApplicationManager.getApplication().invokeLater(onRefreshRequested);
                                    }
                                } catch (java.io.IOException ex) {
                                    OpenSpecNotifier.error(project, "Sync Specs",
                                            "Failed to apply sync: " + ex.getMessage());
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    // --- Fast-Forward (inline) ---

    private JPanel buildNoChangesCard() {
        JPanel card = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4)));
        card.setOpaque(false);

        JBLabel emptyLabel = new JBLabel("No changes yet.");
        emptyLabel.setForeground(JBColor.GRAY);
        card.add(emptyLabel);

        com.intellij.ui.HyperlinkLabel proposeLink = new com.intellij.ui.HyperlinkLabel("Propose a change");
        proposeLink.addHyperlinkListener(ev -> {
            AnAction action = ActionManager.getInstance().getAction("OpenSpec.Propose");
            if (action != null) {
                DataContext ctx = dataId -> com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT.is(dataId) ? project : null;
                com.intellij.openapi.actionSystem.ex.ActionUtil.invokeAction(action, ctx, "WorkflowPanel", null, null);
            }
        });
        card.add(proposeLink);

        {
            JBLabel orLabel = new JBLabel(" or ");
            orLabel.setForeground(JBColor.GRAY);
            card.add(orLabel);

            com.intellij.ui.HyperlinkLabel ffLink = new com.intellij.ui.HyperlinkLabel("Fast-Forward");
            ffLink.setToolTipText("Create a change; generate all with an integrated backend or deliver the first prompt manually");
            ffLink.addHyperlinkListener(ev -> activateFfInput());
            card.add(ffLink);
        }

        return card;
    }

    private JPanel buildFfInputCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setBorder(JBUI.Borders.empty(JBUI.scale(2), 0));

        JBLabel hint = new JBLabel("<html><body style='width:" + JBUI.scale(380) + "px'>" +
                "Describe what you want to build. A change will be created and artifacts " +
                "generated using your selected tool." +
                "</body></html>");
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setBorder(JBUI.Borders.emptyBottom(4));
        card.add(hint);

        // Description
        JBLabel descLabel = new JBLabel("Description:");
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(descLabel);

        JBScrollPane descScroll = new JBScrollPane(ffDescriptionField);
        descScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        descScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(60)));
        card.add(descScroll);
        card.add(Box.createVerticalStrut(JBUI.scale(2)));

        // Name override
        JBLabel nameLabel = new JBLabel("Name override:");
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(nameLabel);

        ffNameOverrideField.setAlignmentX(Component.LEFT_ALIGNMENT);
        ffNameOverrideField.setMaximumSize(new Dimension(Integer.MAX_VALUE, ffNameOverrideField.getPreferredSize().height));
        card.add(ffNameOverrideField);
        card.add(Box.createVerticalStrut(JBUI.scale(2)));

        // Schema combo (conditional)
        if (ffSchemaComboVisible) {
            JBLabel schemaLabel = new JBLabel("Schema:");
            schemaLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(schemaLabel);
            ffSchemaCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
            ffSchemaCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, ffSchemaCombo.getPreferredSize().height));
            card.add(ffSchemaCombo);
            card.add(Box.createVerticalStrut(JBUI.scale(2)));
        }

        // Status label
        ffStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(ffStatusLabel);

        // Buttons
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0));
        buttonRow.setOpaque(false);
        buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonRow.add(ffGoButton);
        buttonRow.add(ffCancelButton);
        card.add(buttonRow);

        return card;
    }

    /**
     * Activates the FF input form in the panel. Can be called externally
     * (e.g., from OpenSpecFfAction) to show the FF form without opening a dialog.
     */
    public void activateFfInput() {
        ffInputActive = true;
        ffDescriptionField.setText("");
        ffNameOverrideField.setText("");
        ffStatusLabel.setText("");
        ffGoButton.setEnabled(false);
        ffCancelButton.setEnabled(true);
        contentCardLayout.show(contentCards, CARD_FF_INPUT);
        ffDescriptionField.requestFocusInWindow();
    }

    private void onFfCancel() {
        ffInputActive = false;
        ChangeService changeService = project.getService(ChangeService.class);
        if (changeService.getActiveChanges().isEmpty()) {
            contentCardLayout.show(contentCards, CARD_NO_CHANGES);
        } else {
            contentCardLayout.show(contentCards, CARD_PIPELINE);
        }
    }

    private void resetVerifyStatus() {
        verifyStatusLabel.setText("Not verified");
        verifyStatusLabel.setForeground(COLOR_BLOCKED);
    }

    /** Reflect a readiness result in the status strip, in the three native verify states. */
    private void applyVerifyStatus(com.johnnyblabs.openspec.model.ArchiveReadinessResult result) {
        switch (result.classify()) {
            case READY -> {
                verifyStatusLabel.setText("\u2713 Ready to archive");
                verifyStatusLabel.setForeground(COLOR_DONE);
            }
            case IN_PROGRESS -> {
                // Neutral, NOT red/orange \u2014 a valid-but-unfinished change is not a defect.
                verifyStatusLabel.setText("In progress \u00b7 archive anyway");
                verifyStatusLabel.setForeground(JBColor.foreground());
            }
            case BLOCKED -> {
                int errors = result.errorCount();
                verifyStatusLabel.setText("\u2717 " + errors + " issue" + (errors > 1 ? "s" : "") + " \u2014 blocked");
                verifyStatusLabel.setForeground(COLOR_ERROR);
            }
        }
    }

    private void updateFfGoEnabled() {
        ffGoButton.setEnabled(!ffDescriptionField.getText().isBlank());
    }

    private void onFfGo() {
        ffInputActive = false;
        String description = ffDescriptionField.getText().trim();
        String changeName = ffNameOverrideField.getText().isBlank()
                ? deriveKebabName(description)
                : ffNameOverrideField.getText().trim();

        // Disable form
        ffGoButton.setEnabled(false);
        ffCancelButton.setEnabled(false);
        ffDescriptionField.setEnabled(false);
        ffNameOverrideField.setEnabled(false);
        ffStatusLabel.setText("Creating change '" + changeName + "'...");
        ffStatusLabel.setForeground(JBColor.GRAY);

        String schema = ffSchemaComboVisible && ffSchemaCombo.getSelectedItem() != null
                ? ffSchemaCombo.getSelectedItem().toString() : null;

        String finalChangeName = changeName;
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Fast-Forward: " + changeName, false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    com.johnnyblabs.openspec.util.CliRunner.CliResult result;
                    if (schema != null && !schema.isEmpty()) {
                        result = com.johnnyblabs.openspec.util.CliRunner.run(project,
                                "new", "change", finalChangeName, "--schema", schema);
                    } else {
                        result = com.johnnyblabs.openspec.util.CliRunner.run(project,
                                "new", "change", finalChangeName);
                    }

                    if (!result.isSuccess()) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            ffStatusLabel.setText("Error: " + result.stderr());
                            ffStatusLabel.setForeground(COLOR_ERROR);
                            ffGoButton.setEnabled(true);
                            ffCancelButton.setEnabled(true);
                            ffDescriptionField.setEnabled(true);
                            ffNameOverrideField.setEnabled(true);
                        });
                        return;
                    }

                    // Success: switch to pipeline and trigger generation
                    ApplicationManager.getApplication().invokeLater(() -> {
                        setSelectedChange(finalChangeName);

                        // Reset FF form for next use
                        ffDescriptionField.setEnabled(true);
                        ffNameOverrideField.setEnabled(true);
                        ffGoButton.setEnabled(false);
                        ffCancelButton.setEnabled(true);

                        if (onRefreshRequested != null) onRefreshRequested.run();

                        ApplicationManager.getApplication().executeOnPooledThread(() -> {
                            refreshForChangeOnPool(finalChangeName);
                            ApplicationManager.getApplication().invokeLater(() -> {
                                DeliveryMode mode = getSelectedDeliveryMode();
                                if (mode == DeliveryMode.DIRECT_API) {
                                    onGenerateAll();
                                } else {
                                    onGenerate();
                                }
                            });
                        });
                    });
                } catch (com.johnnyblabs.openspec.util.CliRunner.CliException e) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        ffStatusLabel.setText("CLI error: " + e.getMessage());
                        ffStatusLabel.setForeground(COLOR_ERROR);
                        ffGoButton.setEnabled(true);
                        ffCancelButton.setEnabled(true);
                        ffDescriptionField.setEnabled(true);
                        ffNameOverrideField.setEnabled(true);
                    });
                }
            }
        });
    }

    public static String deriveKebabName(String description) {
        if (description == null || description.isBlank()) return "unnamed-change";
        String kebab = description.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-");
        String[] parts = kebab.split("-");
        if (parts.length > 5) {
            kebab = String.join("-", java.util.Arrays.copyOf(parts, 5));
        }
        return kebab.isEmpty() ? "unnamed-change" : kebab;
    }

    // --- Verify ---

    private void onVerify() {
        if (activeChangeName == null) return;
        String changeName = activeChangeName;
        verifyStatusLabel.setText("Verifying…");
        verifyStatusLabel.setForeground(JBColor.GRAY);

        // Verify (completeness + validation + spec-sync) spawns the CLI + a blocking AI call — off the EDT.
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Verifying " + changeName, true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                com.johnnyblabs.openspec.services.ArchiveReadinessService readinessService =
                        project.getService(com.johnnyblabs.openspec.services.ArchiveReadinessService.class);
                com.johnnyblabs.openspec.model.ArchiveReadinessResult result =
                        readinessService.verify(changeName);

                ApplicationManager.getApplication().invokeLater(() -> {
                    applyVerifyStatus(result);
                    // The dialog's OK archives (enabled for READY and bypassable IN_PROGRESS,
                    // disabled for a hard validation BLOCK), so Verify flows to archive from one surface.
                    if (new com.johnnyblabs.openspec.dialogs.VerifyDialog(project, result).showAndGet()) {
                        onArchive();
                    }
                });
            }
        });
    }

    // --- Archive ---

    private void onArchive() {
        if (activeChangeName == null) return;
        String changeName = activeChangeName;

        if (hasDeltaSpecs) {
            int choice = Messages.showYesNoCancelDialog(
                    project,
                    "\"" + changeName + "\" has delta specs that haven't been synced to main specs.\n" +
                            "Syncing ensures your spec changes are preserved before archiving.",
                    "Unsynced Delta Specs",
                    "Sync First",
                    "Archive Without Syncing",
                    "Cancel",
                    Messages.getWarningIcon()
            );
            if (choice == Messages.YES) {
                onSyncSpecs();
                return;
            } else if (choice == Messages.CANCEL) {
                return;
            }
            // Messages.NO falls through to archive
        }

        archiveIconButton.setEnabled(false);

        ChangeService changeService = project.getService(ChangeService.class);
        Change target = null;
        for (Change c : changeService.getActiveChanges()) {
            if (changeName.equals(c.getName())) {
                target = c;
                break;
            }
        }
        if (target == null) {
            archiveIconButton.setEnabled(true);
            OpenSpecNotifier.error(project, "Archive", "Change not found: " + changeName);
            return;
        }

        Change finalTarget = target;
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                changeService.archiveChange(finalTarget);
                showGuidancePopover(archiveIconButton,
                        "\u2713 " + changeName + " archived", null, null);
                if (onRefreshRequested != null) onRefreshRequested.run();
                refresh();
            } catch (Exception ex) {
                archiveIconButton.setEnabled(true);
                OpenSpecNotifier.error(project, "Archive", "Archive failed: " + ex.getMessage());
            }
        });
    }

    // --- Change-level validation ---

    private void runChangeValidation() {
        if (activeChangeName == null) return;
        String changeName = activeChangeName;

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Validating " + changeName, false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                BuiltInValidator validator = project.getService(BuiltInValidator.class);
                ValidationResult result = validator.validateChange(changeName);

                SwingUtilities.invokeLater(() -> {
                    long errorCount = result.issues().stream()
                            .filter(i -> i.severity() == ValidationIssue.Severity.ERROR).count();
                    long warnCount = result.issues().stream()
                            .filter(i -> i.severity() == ValidationIssue.Severity.WARNING).count();

                    if (errorCount > 0) {
                        verifyStatusLabel.setText("\u2717 " + errorCount + " error" + (errorCount > 1 ? "s" : ""));
                        verifyStatusLabel.setForeground(COLOR_ERROR);
                    } else if (warnCount > 0) {
                        verifyStatusLabel.setText("\u26A0 " + warnCount + " warning" + (warnCount > 1 ? "s" : ""));
                        verifyStatusLabel.setForeground(JBColor.ORANGE);
                    } else {
                        verifyStatusLabel.setText("\u2713 Valid");
                        verifyStatusLabel.setForeground(COLOR_SUCCESS);
                    }
                });
            }
        });
    }

    // --- Generation ---

    private void onGenerate() {
        if (activeChangeName == null || nextArtifactId == null) return;
        executeGeneration(getSelectedDeliveryMode());
    }

    private void executeGeneration(DeliveryMode mode) {
        if (activeChangeName == null || nextArtifactId == null) return;

        String changeName = activeChangeName;
        String artifactId = nextArtifactId;
        Component chipAnchor = lastClickedChip;

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Generating " + artifactId, true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                activeGenerations.put(artifactId, indicator);
                try {
                    ArtifactOrchestrationService orchestration = project.getService(ArtifactOrchestrationService.class);
                    ArtifactInstruction instruction = orchestration.getInstruction(changeName, artifactId);
                    String prompt = instruction.buildPrompt();

                    lastPrompt = prompt;
                    lastOutputPath = instruction.outputPath();

                    switch (mode) {
                        case CLIPBOARD -> {
                            String toolName = getSelectedToolName();
                            String clipboardPrompt = prompt;
                            if (!toolName.isBlank() && AiToolDetectionService.isCliTool(toolName)
                                    && instruction.changeDir() != null && lastOutputPath != null) {
                                clipboardPrompt = prompt + "\n\nSave your response to: "
                                        + instruction.changeDir() + "/" + lastOutputPath;
                            }
                            lastPrompt = clipboardPrompt;
                            Toolkit.getDefaultToolkit().getSystemClipboard()
                                    .setContents(new StringSelection(clipboardPrompt), null);
                            ApplicationManager.getApplication().invokeLater(() -> {
                                String toolLabel = toolName.isBlank() ? null : toolName;
                                AiToolDetectionService.ToolGuidance guidance =
                                        AiToolDetectionService.getToolGuidance(toolLabel != null ? toolLabel : "your AI tool");
                                String savePath = (instruction.changeDir() != null && lastOutputPath != null)
                                        ? instruction.changeDir() + "/" + lastOutputPath : lastOutputPath;
                                String detail = guidance.canAutoSave()
                                        ? guidance.pasteAction() + " \u2014 it will save automatically."
                                        : guidance.pasteAction() + ". Save to: " + savePath;
                                showGuidancePopover(chipAnchor, "\u2713 Copied to clipboard", detail,
                                        () -> {
                                            Toolkit.getDefaultToolkit().getSystemClipboard()
                                                    .setContents(new StringSelection(lastPrompt), null);
                                            OpenSpecNotifier.info(project, "Generate", "Prompt re-copied to clipboard");
                                        });
                                if (instruction.changeDir() != null && lastOutputPath != null) {
                                    startFileWatcher(instruction.changeDir(), lastOutputPath);
                                }
                            });
                        }
                        case EDITOR_TAB -> ApplicationManager.getApplication().invokeLater(() -> {
                            try {
                                VirtualFile scratch = com.intellij.openapi.application.WriteAction.compute(() -> {
                                    VirtualFile tmp = LocalFileSystem.getInstance()
                                            .findFileByPath(System.getProperty("java.io.tmpdir"));
                                    if (tmp == null) return null;
                                    String name = "openspec-" + artifactId + "-prompt.md";
                                    VirtualFile file = tmp.findChild(name);
                                    if (file != null) file.delete(this);
                                    file = tmp.createChildData(this, name);
                                    file.setBinaryContent(prompt.getBytes(StandardCharsets.UTF_8));
                                    return file;
                                });
                                if (scratch != null) {
                                    FileEditorManager.getInstance(project).openFile(scratch, true);
                                }
                                String savePath = (instruction.changeDir() != null && lastOutputPath != null)
                                        ? instruction.changeDir() + "/" + lastOutputPath : lastOutputPath;
                                showGuidancePopover(chipAnchor, "\u2713 Opened in editor tab",
                                        "Copy to your AI tool. Save response to: " + savePath, null);
                                if (instruction.changeDir() != null && lastOutputPath != null) {
                                    startFileWatcher(instruction.changeDir(), lastOutputPath);
                                }
                            } catch (IOException ex) {
                                OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_GENERATION, "Generate",
                                        "Failed to open prompt: " + ex.getMessage(), com.intellij.notification.NotificationType.ERROR);
                            }
                        });
                        case DIRECT_API -> {
                            AiExecutionService apiService = project.getService(AiExecutionService.class);
                            if (apiService == null || !apiService.isConfigured()) {
                                throw new IllegalStateException("Selected AI backend is not ready. Check OpenSpec settings.");
                            }
                            List<java.nio.file.Path> written = apiService.generateAndApply(instruction);
                            indicator.checkCanceled();
                            orchestration.invalidateCache(changeName);
                            ApplicationManager.getApplication().invokeLater(() -> {
                                if (project.isDisposed()) return;
                                OpenSpecNotifier.info(project, "Generate", "Generated " + artifactId);
                                showGuidancePopover(chipAnchor, "\u2713 Generated " + artifactId,
                                        "Saved: " + written, null);
                                refresh();
                                if (onRefreshRequested != null) onRefreshRequested.run();
                            });
                        }
                    }
                } catch (AiApiException ex) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        String content = "AI generation failed: " + ex.getMessage();
                        if (ex.getSuggestion() != null) {
                            content += "\n" + ex.getSuggestion();
                        }
                        OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_GENERATION, "Generate",
                                content, com.intellij.notification.NotificationType.ERROR,
                                OpenSpecNotifier.openSettingsAction());
                    });
                } catch (com.intellij.openapi.progress.ProcessCanceledException pce) {
                    throw pce; // never swallow cancellation — let the platform complete it
                } catch (Exception ex) {
                    ApplicationManager.getApplication().invokeLater(() ->
                            OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_GENERATION, "Generate",
                                    "Generation failed: " + ex.getMessage(), com.intellij.notification.NotificationType.ERROR));
                } finally {
                    // Deregister only if still ours — a newer run for the same artifact may have replaced it.
                    activeGenerations.remove(artifactId, indicator);
                }
            }
        });
    }

    // --- Guidance popover ---

    private void showGuidancePopover(Component anchor, String title, String detail, Runnable copyAction) {
        dismissActivePopup();

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(JBUI.Borders.empty(8));

        JBLabel titleLabel = new JBLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
        titleLabel.setForeground(title.contains("\u2717") ? COLOR_ERROR : COLOR_SUCCESS);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(titleLabel);

        if (detail != null) {
            JBLabel detailLabel = new JBLabel("<html><body style='width:250px'>" + detail + "</body></html>");
            detailLabel.setFont(detailLabel.getFont().deriveFont(Font.PLAIN, 11f));
            detailLabel.setForeground(JBColor.GRAY);
            detailLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(Box.createVerticalStrut(JBUI.scale(4)));
            content.add(detailLabel);
        }

        if (copyAction != null) {
            com.intellij.ui.HyperlinkLabel copyLink = new com.intellij.ui.HyperlinkLabel("Copy again");
            copyLink.setAlignmentX(Component.LEFT_ALIGNMENT);
            copyLink.addHyperlinkListener(ev -> copyAction.run());
            content.add(Box.createVerticalStrut(JBUI.scale(4)));
            content.add(copyLink);
        }

        activeGuidancePopup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(content, null)
                .setResizable(false)
                .setMovable(false)
                .setRequestFocus(false)
                .setCancelOnClickOutside(true)
                .setCancelOnOtherWindowOpen(true)
                .createPopup();

        if (anchor != null && anchor.isShowing()) {
            activeGuidancePopup.showUnderneathOf(anchor);
        } else {
            activeGuidancePopup.showInFocusCenter();
        }

        // Auto-dismiss after 8 seconds
        javax.swing.Timer dismissTimer = new javax.swing.Timer(8000, e -> dismissActivePopup());
        dismissTimer.setRepeats(false);
        dismissTimer.start();
    }

    private void dismissActivePopup() {
        if (activeGuidancePopup != null && !activeGuidancePopup.isDisposed()) {
            activeGuidancePopup.cancel();
        }
        activeGuidancePopup = null;
    }

    // --- File watcher ---

    private void startFileWatcher(String changeDir, String outputPath) {
        disposeWatcher();
        activeWatcher = new ArtifactFileWatcher(
                changeDir, outputPath,
                () -> {
                    ArtifactOrchestrationService orchestration = project.getService(ArtifactOrchestrationService.class);
                    if (activeChangeName != null) {
                        orchestration.invalidateCache(activeChangeName);
                    }
                    refresh();
                    if (onRefreshRequested != null) onRefreshRequested.run();
                },
                () -> OpenSpecNotifier.info(project, "Generate",
                        "Artifact not detected yet. Right-click chip for options.")
        );
        activeWatcher.start();
    }

    private void disposeWatcher() {
        if (activeWatcher != null) {
            activeWatcher.dispose();
            activeWatcher = null;
        }
    }

    // --- Generate All ---

    private void onGenerateAll() {
        if (activeChangeName == null) return;
        AiExecutionService service = project.getService(AiExecutionService.class);
        if (getSelectedDeliveryMode() != DeliveryMode.DIRECT_API || service == null || !service.isConfigured()) {
            OpenSpecNotifier.warn(project, "Generate All", "Choose a ready integrated backend for automatic generation. Manual delivery generates one prompt at a time.");
            return;
        }

        String changeName = activeChangeName;

        // Reset UI state
        disposeAnimations();
        generatingArtifactId = null;
        generateAllInProgress = true;
        cancelGenerationButton.setVisible(true);

        // Count remaining artifacts
        ArtifactOrchestrationService orch = project.getService(ArtifactOrchestrationService.class);
        ChangeArtifactDag currentDag = orch.getCachedArtifactStatus(changeName);
        int totalRemaining = currentDag != null
                ? (int) currentDag.getArtifacts().stream().filter(a -> a.status() != ArtifactStatus.DONE && a.status() != ArtifactStatus.SKIPPED).count()
                : 4;

        // Start elapsed timer
        generateAllStartNanos = System.nanoTime();
        elapsedTimer = new javax.swing.Timer(1000, e -> updateGenerateAllStatus());
        elapsedTimer.start();

        // Update status strip for generation progress
        updateGenerateAllStatusText(0, totalRemaining);

        final int totalCount = totalRemaining;

        GenerateAllListener listener = new GenerateAllListener() {
            private int completedCount = 0;

            @Override
            public void onArtifactStarted(String artifactId, int index, int total) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!isDisplayable()) return;
                    generatingArtifactId = artifactId;
                    updateGenerateAllStatusText(completedCount, totalCount);
                    ArtifactOrchestrationService o = project.getService(ArtifactOrchestrationService.class);
                    ChangeArtifactDag dag = o.getCachedArtifactStatus(changeName);
                    if (dag != null) {
                        refreshPipelineChips(dag);
                    }
                    startPulseAnimation();
                });
            }

            @Override
            public void onArtifactCompleted(String artifactId) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!isDisplayable()) return;
                    completedCount++;
                    generatingArtifactId = null;
                    stopPulseAnimation();
                    updateGenerateAllStatusText(completedCount, totalCount);
                    ArtifactOrchestrationService o = project.getService(ArtifactOrchestrationService.class);
                    ChangeArtifactDag dag = o.getCachedArtifactStatus(changeName);
                    if (dag != null) {
                        refreshPipelineChips(dag);
                    }
                });
            }

            @Override
            public void onAllComplete() {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!isDisplayable()) return;
                    disposeAnimations();
                    generatingArtifactId = null;
                    generateAllInProgress = false;
                    cancelGenerationButton.setVisible(false);

                    long elapsedSeconds = (System.nanoTime() - generateAllStartNanos) / 1_000_000_000L;

                    // Flash chips green
                    flashPipelineChipsGreen(changeName);

                    // Show completion in status strip briefly
                    verifyStatusLabel.setText("\u2713 All generated");
                    verifyStatusLabel.setForeground(COLOR_SUCCESS);
                    taskProgressStatusLabel.setText(" \u00B7 " + formatElapsed(elapsedSeconds));
                    taskProgressStatusLabel.setVisible(true);

                    // Notify
                    OpenSpecNotifier.generateAllSummary(project, totalCount, elapsedSeconds);

                    // Restore normal state after 3 seconds
                    javax.swing.Timer restoreTimer = new javax.swing.Timer(3000, ev -> {
                        resetVerifyStatus();
                        refresh();
                        if (onRefreshRequested != null) onRefreshRequested.run();
                    });
                    restoreTimer.setRepeats(false);
                    restoreTimer.start();
                });
            }

            @Override
            public void onError(String artifactId, Exception exception) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!isDisplayable()) return;
                    disposeAnimations();
                    generatingArtifactId = null;
                    generateAllInProgress = false;
                    cancelGenerationButton.setVisible(false);

                    // Show error in status strip
                    String msg = artifactId != null ? artifactId + " failed" : "Generation failed";
                    verifyStatusLabel.setText("\u2717 " + msg);
                    verifyStatusLabel.setForeground(COLOR_ERROR);

                    // Show error chip
                    errorArtifactId = artifactId;
                    ArtifactOrchestrationService o = project.getService(ArtifactOrchestrationService.class);
                    ChangeArtifactDag dag = o.getCachedArtifactStatus(changeName);
                    if (dag != null) {
                        refreshPipelineChips(dag);
                    }

                    // Show error details via notification
                    String detail = exception.getMessage();
                    if (exception instanceof AiApiException apiEx && apiEx.getSuggestion() != null) {
                        detail += " \u2014 " + apiEx.getSuggestion();
                    }
                    OpenSpecNotifier.error(project, "Generate All", msg + ": " + detail);
                });
            }

            @Override
            public void onCancelled(String artifactId) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!isDisplayable()) return;
                    disposeAnimations();
                    generatingArtifactId = null;
                    generateAllInProgress = false;
                    cancelGenerationButton.setVisible(false);
                    OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_GENERATION, "Generate All",
                            "Generation cancelled", com.intellij.notification.NotificationType.INFORMATION);
                    refresh();
                    if (onRefreshRequested != null) onRefreshRequested.run();
                });
            }
        };

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ArtifactOrchestrationService orchestration = project.getService(ArtifactOrchestrationService.class);
            AiExecutionService apiService = project.getService(AiExecutionService.class);
            orchestration.generateAllRemaining(changeName, apiService, listener);
        });
    }

    private void updateGenerateAllStatus() {
        // Called by elapsedTimer every second during Generate All
        if (!generateAllInProgress) return;
        // Status strip is already being updated by the listener callbacks
        // Just update the elapsed time portion
        long elapsed = (System.nanoTime() - generateAllStartNanos) / 1_000_000_000L;
        deliveryModeStatusLabel.setText(" \u00B7 " + formatElapsed(elapsed) + " \u00B7 " + getDeliveryModeLabel());
    }

    private void updateGenerateAllStatusText(int completed, int total) {
        long elapsed = (System.nanoTime() - generateAllStartNanos) / 1_000_000_000L;
        verifyStatusLabel.setText("Generating " + (completed + 1) + "/" + total + "...");
        verifyStatusLabel.setForeground(COLOR_GENERATING);
        taskProgressStatusLabel.setVisible(false);
        deliveryModeStatusLabel.setText(" \u00B7 " + formatElapsed(elapsed) + " \u00B7 " + getDeliveryModeLabel());
    }

    private String formatElapsed(long seconds) {
        if (seconds < 60) return seconds + "s";
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    private void onCancelGenerateAll() {
        ArtifactOrchestrationService orchestration = project.getService(ArtifactOrchestrationService.class);
        orchestration.cancelGenerateAll();
        project.getService(AiExecutionService.class).cancelActive();
    }

    private void refreshPipelineChips(ChangeArtifactDag dag) {
        pipelinePanel.removeAll();
        List<ArtifactInfo> artifacts = dag.getArtifacts();
        for (int i = 0; i < artifacts.size(); i++) {
            ArtifactInfo a = artifacts.get(i);
            ArtifactInfo displayArtifact = a;
            if (a.id().equals(generatingArtifactId) && a.status() != ArtifactStatus.DONE) {
                displayArtifact = new ArtifactInfo(a.id(), a.outputPath(), ArtifactStatus.GENERATING, a.missingDeps());
            } else if (a.id().equals(errorArtifactId) && a.status() != ArtifactStatus.DONE) {
                displayArtifact = new ArtifactInfo(a.id(), a.outputPath(), ArtifactStatus.ERROR, a.missingDeps());
            }
            pipelinePanel.add(createPipelineChip(displayArtifact));
            if (i < artifacts.size() - 1) {
                JBLabel arrow = new JBLabel("\u2192");
                arrow.setForeground(JBColor.GRAY);
                pipelinePanel.add(arrow);
            }
        }
        pipelinePanel.revalidate();
        pipelinePanel.repaint();
    }

    // --- Animation helpers ---

    private void startPulseAnimation() {
        stopPulseAnimation();
        spinnerStep = 0;
        spinnerTimer = new javax.swing.Timer(100, e -> {
            spinnerStep = (spinnerStep + 1) % 12;
            pipelinePanel.repaint();
        });
        spinnerTimer.start();

        pulseTimer = new javax.swing.Timer(600, e -> pipelinePanel.repaint());
        pulseTimer.start();
    }

    private void stopPulseAnimation() {
        if (pulseTimer != null) {
            pulseTimer.stop();
            pulseTimer = null;
        }
        if (spinnerTimer != null) {
            spinnerTimer.stop();
            spinnerTimer = null;
        }
    }

    private void flashPipelineChipsGreen(String changeName) {
        Color flashColor = COLOR_FLASH_GREEN;
        for (Component comp : pipelinePanel.getComponents()) {
            if (comp instanceof JPanel chip) {
                chip.setBorder(BorderFactory.createLineBorder(flashColor, 2, true));
            }
        }
        pipelinePanel.repaint();

        javax.swing.Timer flashTimer = new javax.swing.Timer(300, e -> {
            ArtifactOrchestrationService o = project.getService(ArtifactOrchestrationService.class);
            ChangeArtifactDag dag = o.getCachedArtifactStatus(changeName);
            if (dag != null) {
                refreshPipelineChips(dag);
            }
        });
        flashTimer.setRepeats(false);
        flashTimer.start();
    }

    private void disposeAnimations() {
        stopPulseAnimation();
        if (elapsedTimer != null) {
            elapsedTimer.stop();
            elapsedTimer = null;
        }
        errorArtifactId = null;
    }

    @Override
    public void removeNotify() {
        disposeAnimations();
        disposeWatcher();
        dismissActivePopup();
        super.removeNotify();
    }

    Icon getSpinnerIcon() {
        return switch (spinnerStep) {
            case 0 -> AllIcons.Process.Step_1;
            case 1 -> AllIcons.Process.Step_2;
            case 2 -> AllIcons.Process.Step_3;
            case 3 -> AllIcons.Process.Step_4;
            case 4 -> AllIcons.Process.Step_5;
            case 5 -> AllIcons.Process.Step_6;
            case 6 -> AllIcons.Process.Step_7;
            case 7 -> AllIcons.Process.Step_8;
            default -> AllIcons.Process.Step_1;
        };
    }
}
