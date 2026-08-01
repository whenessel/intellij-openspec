package com.johnnyblabs.openspec.validation;

import java.util.ArrayList;
import java.util.List;

public record ValidationResult(
        boolean passed,
        List<ValidationIssue> issues,
        String source
) {
    public static ValidationResult merge(ValidationResult builtIn, ValidationResult cli) {
        List<ValidationIssue> allIssues = new ArrayList<>(builtIn.issues());
        allIssues.addAll(cli.issues());
        boolean allPassed = builtIn.passed() && cli.passed();
        return new ValidationResult(allPassed, allIssues, "merged");
    }

    /**
     * Combine a CLI verdict with the built-in validator's {@code config.yaml}-only result.
     *
     * <p>The CLI is authoritative for the artifacts it validates (specs and change deltas), so the
     * plugin must never red a clean CLI with the built-in validator's own opinion on those artifacts.
     * The one thing the CLI's {@code validate} never checks is {@code openspec/config.yaml}, so the
     * built-in validator remains the sole owner of config validation. This combine is therefore used
     * only for a whole-project run with the CLI available: {@code cli} carries the spec/change verdict,
     * {@code builtInConfig} carries the config verdict, and the merged pass is the AND of the two.
     * (For a single spec/change target the caller uses the CLI result directly — there is no config
     * component to merge.)
     *
     * <p>Mathematically identical to {@link #merge}, but named and ordered CLI-first to make the
     * authority explicit at the call site; the behavioral change lives in <em>what</em> is passed as
     * {@code builtInConfig} (config-only, not the full built-in validator).
     */
    public static ValidationResult mergeCliAuthoritative(ValidationResult cli, ValidationResult builtInConfig) {
        List<ValidationIssue> allIssues = new ArrayList<>(cli.issues());
        allIssues.addAll(builtInConfig.issues());
        boolean allPassed = cli.passed() && builtInConfig.passed();
        return new ValidationResult(allPassed, allIssues, "merged");
    }

    public int errorCount() {
        return (int) issues.stream()
                .filter(i -> i.severity() == ValidationIssue.Severity.ERROR)
                .count();
    }

    public int warningCount() {
        return (int) issues.stream()
                .filter(i -> i.severity() == ValidationIssue.Severity.WARNING)
                .count();
    }
}
