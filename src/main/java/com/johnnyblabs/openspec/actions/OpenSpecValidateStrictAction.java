package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Whole-project validation with per-run strict mode — mirrors the OpenSpec CLI's
 * {@code validate --strict} (warnings count as failures). This is the per-invocation
 * counterpart to the default {@link OpenSpecValidateAction}: there is no persistent "strict"
 * setting; the user chooses strict per run (via the toolbar dropdown, the menu leaf, or Find
 * Action). It inherits the base action's OpenSpec-project availability gate unchanged; the only
 * difference is that it passes a strict target into the shared validate pipeline.
 */
public class OpenSpecValidateStrictAction extends OpenSpecValidateAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        runValidation(project, ValidateTarget.wholeProject().withStrict());
    }
}
