package com.johnnyblabs.openspec.ai.safety;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/** All destinations and exact immutable contents are reviewed in one batch. */
final class ArtifactPreviewDialog extends DialogWrapper {
    private final Project project;
    private final ReviewedArtifactBatch batch;
    ArtifactPreviewDialog(Project project, ReviewedArtifactBatch batch) {
        super(project, true);
        this.project = project;
        this.batch = batch;
        setTitle("Review Generated " + batch.artifactId());
        setOKButtonText("Apply Reviewed Files");
        init();
    }
    @Override protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(JBUI.size(800, 600));
        JTextArea scope = new JTextArea("Planning root: " + batch.root() + "\n"
                + batch.files().size() + " files. Review every tab before applying. Existing edits invalidate this batch.");
        scope.setEditable(false);
        scope.setLineWrap(true);
        panel.add(scope, BorderLayout.NORTH);
        JBTabbedPane tabs = new JBTabbedPane();
        for (ReviewedArtifactBatch.FileEdit edit : batch.files()) {
            var factory = DiffContentFactory.getInstance();
            var diff = DiffManager.getInstance().createRequestPanel(project, getDisposable(), null);
            diff.setRequest(new SimpleDiffRequest(edit.relativePath(),
                    factory.create(project, edit.original() == null ? "" : edit.original().replace("\r\n", "\n"), PlainTextFileType.INSTANCE),
                    factory.create(project, edit.content(), PlainTextFileType.INSTANCE), "Current", "Generated " + edit.operation()));
            tabs.addTab(edit.relativePath(), diff.getComponent());
        }
        panel.add(tabs, BorderLayout.CENTER);
        return panel;
    }
}
