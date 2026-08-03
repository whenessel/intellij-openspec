package com.johnnyblabs.openspec.dialogs;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.model.ArchiveReadinessResult;
import com.johnnyblabs.openspec.model.ArchiveReadinessResult.Category;
import com.johnnyblabs.openspec.model.ArchiveReadinessResult.Finding;
import com.johnnyblabs.openspec.model.ArchiveReadinessResult.Severity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * The single pre-archive <em>Verify</em> dialog (upstream's word — the plugin's former "Compliance
 * Pre-Flight"). It renders the three native archive-readiness states from {@link ArchiveReadinessResult}:
 * <ul>
 *   <li>{@code READY} — green "ready to archive"; OK = <b>Archive</b>, enabled.</li>
 *   <li>{@code IN_PROGRESS} — <b>neutral</b> (Information icon, foreground color, NOT red)
 *       "in progress … archive anyway"; OK = <b>Archive anyway</b>, <b>enabled</b> — matching
 *       upstream's soft, bypassable {@code --yes} task gate.</li>
 *   <li>{@code BLOCKED} — red "validation failed"; OK = <b>Archive</b>, <b>disabled</b> — the only
 *       hard block.</li>
 * </ul>
 */
public class VerifyDialog extends DialogWrapper {

    private final ArchiveReadinessResult result;

    public VerifyDialog(@NotNull Project project, @NotNull ArchiveReadinessResult result) {
        super(project, false);
        this.result = result;

        setTitle("Verify — " + result.getChangeName());
        setOKButtonText(okButtonText(result));
        setCancelButtonText("Cancel");

        init();

        // Only a genuine (hard) validation block disables Archive. An in-progress change is
        // archivable-anyway, mirroring the upstream `--yes` bypass — do not disable it.
        if (result.isHardBlocked()) {
            setOKActionEnabled(false);
        }
    }

    /**
     * The OK-button label for a result. IN_PROGRESS reads "Archive anyway" (the bypassable soft
     * gate); every other state reads "Archive". Pure so the dialog's contract is unit-testable
     * without a running IDE. Enablement is separate: OK is disabled iff {@link ArchiveReadinessResult#isHardBlocked()}.
     */
    static String okButtonText(ArchiveReadinessResult result) {
        return result.classify() == ArchiveReadinessResult.ArchiveReadiness.IN_PROGRESS
                ? "Archive anyway" : "Archive";
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(JBUI.Borders.empty(8));

        // Status header — native state message + color (IN_PROGRESS is neutral, never red).
        JBLabel statusLabel = new JBLabel(result.stateMessage());
        statusLabel.setIcon(stateIcon(result));
        statusLabel.setForeground(stateColor(result));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 13f));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLabel.setBorder(JBUI.Borders.emptyBottom(12));
        panel.add(statusLabel);

        // Category sections
        for (Category category : Category.values()) {
            panel.add(createCategorySection(category));
        }

        JBScrollPane scrollPane = new JBScrollPane(panel);
        scrollPane.setPreferredSize(new Dimension(500, 350));
        scrollPane.setBorder(JBUI.Borders.empty());
        return scrollPane;
    }

    /** Header icon per state: OK for ready, a neutral Information "i" for in-progress, Error for blocked. */
    private static Icon stateIcon(ArchiveReadinessResult result) {
        return switch (result.classify()) {
            case READY -> AllIcons.General.InspectionsOK;
            case IN_PROGRESS -> AllIcons.General.Information;
            case BLOCKED -> AllIcons.General.Error;
        };
    }

    /**
     * Header color per state. The load-bearing choice: {@code IN_PROGRESS} is <b>neutral</b>
     * (foreground), never red — a valid-but-unfinished change is not a defect. Lives here (the sole
     * consumer) rather than on the model, keeping {@link ArchiveReadinessResult} UI-free.
     */
    static Color stateColor(ArchiveReadinessResult result) {
        return switch (result.classify()) {
            case READY -> new JBColor(new Color(0, 128, 0), new Color(100, 210, 100));
            case IN_PROGRESS -> JBColor.foreground();
            case BLOCKED -> JBColor.RED;
        };
    }

    private JPanel createCategorySection(Category category) {
        JPanel section = new JPanel(new BorderLayout());
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setBorder(JBUI.Borders.emptyBottom(8));

        List<Finding> findings = result.getFindings(category);
        boolean passes = result.categoryPasses(category);

        // Header with pass/fail icon
        String icon = passes ? "✓" : "✗"; // ✓ or ✗
        JBColor color = passes ? new JBColor(new java.awt.Color(0, 128, 0), new java.awt.Color(100, 210, 100)) : JBColor.RED;
        JBLabel header = new JBLabel(icon + " " + category.getDisplayName());
        header.setForeground(color);
        header.setFont(header.getFont().deriveFont(Font.BOLD));
        section.add(header, BorderLayout.NORTH);

        if (!findings.isEmpty()) {
            JPanel findingsList = new JPanel();
            findingsList.setLayout(new BoxLayout(findingsList, BoxLayout.Y_AXIS));
            findingsList.setBorder(JBUI.Borders.emptyLeft(16));

            for (Finding finding : findings) {
                String prefix = finding.severity() == Severity.ERROR ? "ERROR: " : "WARNING: ";
                JBLabel findingLabel = new JBLabel(prefix + finding.message());
                findingLabel.setForeground(finding.severity() == Severity.ERROR
                        ? JBColor.RED : JBColor.ORANGE);
                findingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                findingsList.add(findingLabel);
            }
            section.add(findingsList, BorderLayout.CENTER);
        }

        return section;
    }
}
