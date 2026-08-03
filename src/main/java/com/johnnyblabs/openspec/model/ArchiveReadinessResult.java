package com.johnnyblabs.openspec.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of the pre-archive <em>verify</em> check (completeness + validation + spec-sync). Named for
 * what it computes — a change's readiness to archive — while the user-facing action/button is
 * "Verify" (upstream OpenSpec's own word; the plugin does not use the invented "compliance" term).
 *
 * <p><b>Why three states, not a boolean.</b> Upstream {@code openspec archive} splits the gate into
 * two differently-severe conditions that both carry {@code severity:"error"} in its JSON — the split
 * is the {@code code}, not the severity:
 * <ul>
 *   <li>{@code archive_tasks_incomplete} — a <b>soft, bypassable</b> gate (archivable with
 *       {@code --yes}; interactively a <i>Warning… Continue?</i>). → {@link ArchiveReadiness#IN_PROGRESS}.</li>
 *   <li>{@code archive_validation_failed} — a <b>hard</b> gate (bypass only {@code --no-validate}).
 *       → {@link ArchiveReadiness#BLOCKED}.</li>
 * </ul>
 * Rendering unfinished-but-valid work in red "error — blocked" language over-states the task case and
 * makes the plugin stricter than the client it wraps. So hardness is modeled <b>intrinsically</b> on
 * each finding ({@link BlockKind}) and {@link #classify()} folds over it — a boolean keyed on
 * error-count cannot tell {@code IN_PROGRESS} from {@code BLOCKED}.
 */
public class ArchiveReadinessResult {

    /** The three native pre-archive states, mirroring upstream's two archive-gate codes + success. */
    public enum ArchiveReadiness { READY, IN_PROGRESS, BLOCKED }

    /** How strongly a finding gates archiving — the intrinsic soft/hard axis {@link #classify()} folds over. */
    public enum BlockKind { HARD, SOFT, NONE }

    public enum Status { COMPLIANT, NOT_COMPLIANT }
    public enum Severity { ERROR, WARNING }

    public enum Category {
        ARTIFACT_COMPLETENESS("Completeness"),
        VALIDATION("Validation"),
        SYNC_READINESS("Spec Sync");

        private final String displayName;
        Category(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    public record Finding(Category category, Severity severity, String message) {
        /**
         * Intrinsic gate hardness, derived once from (category, severity):
         * a {@code VALIDATION} error is the hard {@code archive_validation_failed} analogue;
         * any other error (incomplete tasks / correctness) is the soft, bypassable
         * {@code archive_tasks_incomplete} analogue; a warning never gates.
         */
        public BlockKind blockKind() {
            if (severity == Severity.WARNING) return BlockKind.NONE;
            return category == Category.VALIDATION ? BlockKind.HARD : BlockKind.SOFT;
        }
    }

    private final String changeName;
    private final List<Finding> findings;

    public ArchiveReadinessResult(String changeName, List<Finding> findings) {
        this.changeName = changeName;
        this.findings = List.copyOf(findings);
    }

    public String getChangeName() { return changeName; }

    public List<Finding> getFindings() { return findings; }

    public List<Finding> getFindings(Category category) {
        return findings.stream().filter(f -> f.category() == category).toList();
    }

    // --- Three-state classification (the load-bearing pure unit) ---

    /**
     * Fold the findings into one of the three native states. A HARD finding dominates (BLOCKED);
     * otherwise any SOFT finding means work remains (IN_PROGRESS); otherwise READY. Warnings alone
     * never gate — a change with only spec-sync warnings is READY.
     */
    public ArchiveReadiness classify() {
        boolean soft = false;
        for (Finding f : findings) {
            BlockKind kind = f.blockKind();
            if (kind == BlockKind.HARD) return ArchiveReadiness.BLOCKED;
            if (kind == BlockKind.SOFT) soft = true;
        }
        return soft ? ArchiveReadiness.IN_PROGRESS : ArchiveReadiness.READY;
    }

    /** True only for a genuine (hard) validation block — the sole state that disables Archive. */
    public boolean isHardBlocked() { return classify() == ArchiveReadiness.BLOCKED; }

    /** Human-readable header for the current state, in OpenSpec-native vocabulary. */
    public String stateMessage() {
        return switch (classify()) {
            case READY -> "Ready to archive — all tasks complete.";
            case IN_PROGRESS -> {
                int soft = (int) findings.stream().filter(f -> f.blockKind() == BlockKind.SOFT).count();
                yield "In Progress — " + soft + " incomplete task(s) remain. "
                        + "Complete before archiving, or archive anyway.";
            }
            case BLOCKED -> "Validation failed — archive blocked.";
        };
    }

    // --- Retained accessors (unchanged semantics; used by callers/tests) ---

    public Status getStatus() {
        return findings.stream().anyMatch(f -> f.severity() == Severity.ERROR)
                ? Status.NOT_COMPLIANT : Status.COMPLIANT;
    }

    public boolean isCompliant() { return getStatus() == Status.COMPLIANT; }

    public int errorCount() {
        return (int) findings.stream().filter(f -> f.severity() == Severity.ERROR).count();
    }

    public int warningCount() {
        return (int) findings.stream().filter(f -> f.severity() == Severity.WARNING).count();
    }

    public boolean categoryPasses(Category category) {
        return findings.stream()
                .filter(f -> f.category() == category)
                .noneMatch(f -> f.severity() == Severity.ERROR);
    }

    public static Builder builder(String changeName) {
        return new Builder(changeName);
    }

    public static class Builder {
        private final String changeName;
        private final List<Finding> findings = new ArrayList<>();

        private Builder(String changeName) { this.changeName = changeName; }

        public Builder addError(Category category, String message) {
            findings.add(new Finding(category, Severity.ERROR, message));
            return this;
        }

        public Builder addWarning(Category category, String message) {
            findings.add(new Finding(category, Severity.WARNING, message));
            return this;
        }

        public ArchiveReadinessResult build() {
            return new ArchiveReadinessResult(changeName, findings);
        }
    }
}
