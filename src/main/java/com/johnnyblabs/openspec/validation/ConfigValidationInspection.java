package com.johnnyblabs.openspec.validation;

import com.intellij.codeInspection.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.MarkedYAMLException;

import java.util.ArrayList;
import java.util.List;

public class ConfigValidationInspection extends LocalInspectionTool {

    @Override
    public ProblemDescriptor @NotNull [] checkFile(@NotNull PsiFile file,
                                                     @NotNull InspectionManager manager,
                                                     boolean isOnTheFly) {
        if (file.getVirtualFile() == null) return ProblemDescriptor.EMPTY_ARRAY;

        String fileName = file.getVirtualFile().getName();
        boolean isConfig = "config.yaml".equals(fileName)
                && file.getVirtualFile().getParent() != null
                && "openspec".equals(file.getVirtualFile().getParent().getName());
        boolean isChangeMetadata = ".openspec.yaml".equals(fileName);

        if (!isConfig && !isChangeMetadata) return ProblemDescriptor.EMPTY_ARRAY;

        String text = file.getText();
        List<ProblemDescriptor> problems = new ArrayList<>();

        // YAML syntax validation — applies to both config.yaml and .openspec.yaml
        try {
            new Yaml(new LoaderOptions()).load(text);
        } catch (MarkedYAMLException e) {
            String problem = e.getProblem() != null ? e.getProblem() : "invalid YAML syntax";
            String location = "";
            if (e.getProblemMark() != null) {
                location = " (line " + (e.getProblemMark().getLine() + 1)
                        + ", column " + (e.getProblemMark().getColumn() + 1) + ")";
            }

            // Try to highlight near the error location
            int offset = 0;
            if (e.getProblemMark() != null) {
                int markOffset = e.getProblemMark().getIndex();
                if (markOffset >= 0 && markOffset < text.length()) {
                    offset = markOffset;
                }
            }
            PsiElement target = findNonEmptyElement(file, offset);
            if (target != null) {
                problems.add(manager.createProblemDescriptor(
                        target,
                        "YAML syntax error: " + problem + location,
                        (LocalQuickFix) null,
                        ProblemHighlightType.ERROR,
                        isOnTheFly));
            }
            // Return early — no point checking fields if YAML is unparseable
            return problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
        }

        // Field-level validation — only for config.yaml
        if (isConfig) {
            if (!text.contains("schema:")) {
                PsiElement element = findNonEmptyElement(file, 0);
                if (element != null) {
                    // INFORMATION, not WARNING: `openspec validate` is clean on a missing schema
                    // (upstream defaults to 'spec-driven'), so a warning squiggle would be stricter than
                    // the CLI. This is an advisory-only nudge, mirroring the built-in validator's
                    // config-schema-required INFO.
                    problems.add(manager.createProblemDescriptor(
                            element,
                            "OpenSpec config.yaml has no 'schema' field; it defaults to 'spec-driven' "
                                    + "— add one to be explicit",
                            (LocalQuickFix) null,
                            ProblemHighlightType.INFORMATION,
                            isOnTheFly));
                }
            }
            // No `profile:` nag: `profile` is the GLOBAL workflow profile (`openspec config profile`),
            // never a project config.yaml field, and it's absent from a clean `openspec init` config and
            // from upstream's Zod schema. Nagging for it warned on a CLI-clean file (and implied an
            // off-model concept). The "Profile field absent is accepted" validation scenario already
            // documents that absence is a non-issue.
        }

        return problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
    }

    private static PsiElement findNonEmptyElement(PsiFile file, int offset) {
        PsiElement element = file.findElementAt(offset);
        if (element == null) element = file.getFirstChild();
        while (element != null && element.getTextLength() == 0) {
            element = element.getParent();
        }
        return element;
    }
}
