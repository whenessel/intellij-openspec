package com.johnnyblabs.openspec.ai.safety;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ui.JBUI;
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
    public ContextReviewService(Project project) {
        this(project, (prompt, backend) -> {
            ContextDialog dialog = new ContextDialog(project, prompt, backend);
            return dialog.showAndGet() ? dialog.text() : null;
        });
    }
    ContextReviewService(Project project, BiFunction<String, String, String> reviewer) { this.project = project; this.reviewer = reviewer; }

    public ReviewedContext review(String prompt, String backendLabel) throws IOException {
        return review(prompt, backendLabel, MAX_PROMPT_BYTES);
    }
    public ReviewedContext review(String prompt, String backendLabel, int configuredByteBudget) throws IOException {
        ProgressManager.checkCanceled();
        if (configuredByteBudget <= 0) throw new IOException("Invalid AI context budget");
        int effectiveBudget = Math.min(configuredByteBudget, MAX_PROMPT_BYTES);
        String safe = redactAndBound(prompt);
        checkBudget(safe, effectiveBudget);
        AtomicReference<String> approved = new AtomicReference<>();
        Runnable review = () -> {
            if (project != null && project.isDisposed()) throw new ProcessCanceledException();
            approved.set(reviewer.apply(safe, backendLabel + "\nPayload: " + safe.getBytes(StandardCharsets.UTF_8).length
                    + " UTF-8 bytes; effective budget " + effectiveBudget + " bytes (token estimate only)."));
        };
        var app = ApplicationManager.getApplication();
        if (app == null || app.isDispatchThread()) review.run(); else app.invokeAndWait(review);
        if (approved.get() == null) throw new ProcessCanceledException();
        ProgressManager.checkCanceled();
        // The text editor may only reduce/refine scope; redaction and limits also apply after edits.
        String reviewed = redactAndBound(approved.get());
        checkBudget(reviewed, effectiveBudget);
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
        ContextDialog(Project project, String prompt, String backend) {
            super(project, true);
            this.prompt = prompt;
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
            return panel;
        }
        String text() { return editor.getText(); }
    }
}
