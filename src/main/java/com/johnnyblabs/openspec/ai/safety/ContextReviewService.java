package com.johnnyblabs.openspec.ai.safety;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import java.util.List;
import java.util.ArrayList;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

/** A final payload review applies equally to REST and the restricted local Codex context. */
public final class ContextReviewService {
    public static final int MAX_PROMPT_BYTES = ContextPayloadPolicy.MAX_PROMPT_BYTES;
    private final Project project;
    private final BiFunction<String, String, String> reviewer;
    public ContextReviewService(Project project) { this.project = project; this.reviewer = null; }
    ContextReviewService(Project project, BiFunction<String, String, String> reviewer) { this.project = project; this.reviewer = reviewer; }

    public ReviewedContext review(String prompt, String backendLabel) throws IOException {
        return review(prompt, backendLabel, MAX_PROMPT_BYTES);
    }
    public ReviewedContext review(String prompt, String backendLabel, int configuredByteBudget) throws IOException {
        ProgressManager.checkCanceled();
        if (configuredByteBudget <= 0) throw new IOException("Invalid AI context budget");
        OpenSpecSettings settings = project == null ? null : OpenSpecSettings.getInstance(project);
        int tokens = settings == null ? 12000 : settings.getAiContextMaxInputTokens();
        ContextManifest.Budget budget = new ContextManifest.Budget(64, 32768,
                Math.min(configuredByteBudget, MAX_PROMPT_BYTES), tokens, null, 4096, 1024);
        String safe = ContextManifest.fromPrompt(prompt, List.of(), budget).prompt();
        ContextPayloadPolicy.redactAndBound(safe, budget);
        AtomicReference<String> approved = new AtomicReference<>();
        Runnable review = () -> {
            if (project != null && project.isDisposed()) throw new ProcessCanceledException();
            String destination = backendLabel + "\nPayload: " + safe.getBytes(StandardCharsets.UTF_8).length
                    + " UTF-8 bytes. " + budget.disclosure();
            if (reviewer != null) approved.set(reviewer.apply(safe, destination));
            else {
                ContextDialog dialog = new ContextDialog(project, safe, destination, budget);
                approved.set(dialog.showAndGet() ? dialog.text() : null);
            }
        };
        var app = ApplicationManager.getApplication();
        if (app == null || app.isDispatchThread()) review.run(); else app.invokeAndWait(review);
        if (approved.get() == null) throw new ProcessCanceledException();
        ProgressManager.checkCanceled();
        // The text editor may only reduce/refine scope; redaction and limits also apply after edits.
        String reviewed = ContextPayloadPolicy.redactAndBound(approved.get(), budget);
        Path root = Files.createTempDirectory("openspec-reviewed-context-");
        try {
            Files.writeString(root.resolve("reviewed-context.md"), reviewed, StandardCharsets.UTF_8);
            return new ReviewedContext(reviewed, root);
        } catch (IOException ex) {
            Files.deleteIfExists(root.resolve("reviewed-context.md"));
            Files.deleteIfExists(root);
            throw ex;
        }
    }

    public static String redactAndBound(String prompt) throws IOException {
        return ContextPayloadPolicy.redactAndBound(prompt);
    }

    private static void checkBudget(String prompt, int budget) throws IOException {
        if (prompt.getBytes(StandardCharsets.UTF_8).length > budget) {
            throw new IOException("AI context exceeds configured budget of " + budget + " bytes. Reduce selected context; no content was sent.");
        }
    }

    private static final class ContextDialog extends DialogWrapper {
        private final String prompt;
        private final String backend;
        private JTextArea editor;
        private final Project contextProject;
        private final ContextManifest.Budget budget;
        private SwingWorker<String, Void> inclusionWorker;
        private volatile boolean closed;
        ContextDialog(Project project, String prompt, String backend, ContextManifest.Budget budget) {
            super(project, true);
            this.prompt = prompt;
            this.contextProject = project;
            this.budget = budget;
            this.backend = backend == null ? "Selected AI backend" : backend;
            setTitle("Review AI Context and Destination");
            setOKButtonText("Send Reviewed Context");
            init();
        }
        @Override protected @Nullable JComponent createCenterPanel() {
            JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.setPreferredSize(JBUI.size(800, 600));
            JTextArea summary = new JTextArea(backend + "\n"
                    + "Only this reviewed text is sent as project context through stdin.\n"
                    + "Codex native tools are disabled (environments=[]); no workspace files are accessible to the model.\n"
                    + "Inference is online; billing and limits follow the selected authentication mode.\n"
                    + "No workspace files are added automatically. Dependency reads exclude hidden/credential paths and symlinks.\n"
                    + "Known secret patterns were redacted; review for additional sensitive data. You may edit before sending.");
            summary.setEditable(false);
            summary.setLineWrap(true);
            summary.setWrapStyleWord(true);
            panel.add(summary, BorderLayout.NORTH);
            editor = new JTextArea(prompt);
            editor.setLineWrap(true);
            editor.setWrapStyleWord(true);
            panel.add(new JScrollPane(editor), BorderLayout.CENTER);
            JButton include = new JButton("Include files once (multiple selection)...");
            include.setMnemonic('I');
            include.getAccessibleContext().setAccessibleDescription("Explicitly select additional files for this reviewed request");
            JLabel inclusionStatus = new JLabel("Additional files: none. Hidden, credential, ignored and binary files are excluded.");
            include.setEnabled(contextProject != null && contextProject.getBasePath() != null);
            include.addActionListener(event -> {
                Path root = Path.of(contextProject.getBasePath()).toAbsolutePath().normalize();
                JFileChooser chooser = new JFileChooser(root.toFile());
                chooser.setMultiSelectionEnabled(true);
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                if (chooser.showOpenDialog(panel) != JFileChooser.APPROVE_OPTION) return;
                List<ContextManifest.Selection> selections = new ArrayList<>();
                for (java.io.File file : chooser.getSelectedFiles()) {
                    Path path = file.toPath().toAbsolutePath().normalize();
                    if (!path.startsWith(root)) { inclusionStatus.setText("Choose files within this project; no content was added."); return; }
                    String relative = root.relativize(path).toString().replace('\\', '/');
                    selections.add(new ContextManifest.Selection(root, relative,
                            relative.startsWith("openspec/changes/") ? ContextManifest.Origin.ADDITIONAL_CHANGE : ContextManifest.Origin.SOURCE, false));
                }
                if (selections.isEmpty()) return;
                String currentText = editor.getText();
                include.setEnabled(false); editor.setEditable(false); setOKActionEnabled(false);
                inclusionStatus.setText("Reading explicitly selected files...");
                inclusionWorker = new SwingWorker<>() {
                    @Override protected String doInBackground() throws Exception {
                        return ContextManifest.fromPrompt(currentText, selections, budget).prompt();
                    }
                    @Override protected void done() {
                        if (closed || isCancelled() || contextProject.isDisposed()) return;
                        try {
                            editor.setText(get());
                            inclusionStatus.setText("Selected files were evaluated; included files and omissions are listed in the preview.");
                        } catch (Exception ignored) {
                            inclusionStatus.setText("Files could not fit safely; reduce scope and review again. No request was sent.");
                            include.setEnabled(true);
                        } finally { editor.setEditable(true); setOKActionEnabled(true); }
                    }
                };
                inclusionWorker.execute();
            });
            JPanel additions = new JPanel(new BorderLayout(0, 4));
            additions.add(include, BorderLayout.NORTH); additions.add(inclusionStatus, BorderLayout.SOUTH);
            panel.add(additions, BorderLayout.SOUTH);
            return panel;
        }
        @Override protected void dispose() {
            closed = true;
            if (inclusionWorker != null) inclusionWorker.cancel(true);
            super.dispose();
        }
        String text() { return editor.getText(); }
    }
}
