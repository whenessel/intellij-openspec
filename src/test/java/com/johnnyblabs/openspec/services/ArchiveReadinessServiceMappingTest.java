package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.model.ArchiveReadinessResult;
import com.johnnyblabs.openspec.model.ArchiveReadinessResult.ArchiveReadiness;
import com.johnnyblabs.openspec.model.VerificationFinding;
import com.johnnyblabs.openspec.model.VerificationFinding.Dimension;
import com.johnnyblabs.openspec.model.VerificationReport;
import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the load-bearing service seam the classifier tests can't reach: which category
 * {@link ArchiveReadinessService} assigns a finding to decides its soft/hard gate kind. The
 * classifier tests seed categories directly, so a regression where {@code foldCompleteness} wrote
 * {@code VALIDATION} instead of {@code ARTIFACT_COMPLETENESS} would hard-BLOCK every incomplete change
 * yet leave those tests green. This test exercises the real fold — the exact regression this refactor
 * exists to prevent — through pure static methods (no running IDE, no CLI/AI).
 */
class ArchiveReadinessServiceMappingTest {

    @Test
    void incompleteTasks_foldToSoft_soVerdictIsInProgress_notBlocked() {
        // A completeness CRITICAL is what VerificationService emits for incomplete tasks.
        VerificationReport report = new VerificationReport("wip-change");
        report.addFinding(new VerificationFinding(
                VerificationFinding.Severity.CRITICAL, Dimension.COMPLETENESS,
                "9 incomplete task(s) out of 11", "tasks.md", -1));

        ArchiveReadinessResult.Builder builder = ArchiveReadinessResult.builder("wip-change");
        ArchiveReadinessService.foldCompleteness(report, builder);
        ArchiveReadinessResult result = builder.build();

        assertEquals(ArchiveReadiness.IN_PROGRESS, result.classify(),
                "incomplete tasks must fold to the SOFT/IN_PROGRESS gate, not a hard BLOCK — "
                        + "if foldCompleteness wrote VALIDATION this would be BLOCKED");
        assertFalse(result.isHardBlocked());
    }

    @Test
    void completenessWarning_foldsToNonGatingWarning() {
        VerificationReport report = new VerificationReport("c");
        report.addFinding(new VerificationFinding(
                VerificationFinding.Severity.WARNING, Dimension.CORRECTNESS,
                "advisory note", null, -1));

        ArchiveReadinessResult.Builder builder = ArchiveReadinessResult.builder("c");
        ArchiveReadinessService.foldCompleteness(report, builder);
        ArchiveReadinessResult result = builder.build();

        assertEquals(ArchiveReadiness.READY, result.classify(), "a warning never gates archiving");
        assertEquals(1, result.warningCount());
    }

    @Test
    void validationError_foldsToHard_soVerdictIsBlocked() {
        ValidationResult vr = new ValidationResult(false,
                List.of(new ValidationIssue(ValidationIssue.Severity.ERROR,
                        "specs/foo/spec.md", 3, "ADDED requirement must include at least one scenario", "scenario-required")),
                "built-in");

        ArchiveReadinessResult.Builder builder = ArchiveReadinessResult.builder("bad-change");
        ArchiveReadinessService.foldValidation(vr, builder);
        ArchiveReadinessResult result = builder.build();

        assertEquals(ArchiveReadiness.BLOCKED, result.classify(),
                "a validation error must fold to the HARD/BLOCKED gate");
        assertTrue(result.isHardBlocked());
    }

    @Test
    void validationWarning_doesNotBlock() {
        ValidationResult vr = new ValidationResult(true,
                List.of(new ValidationIssue(ValidationIssue.Severity.WARNING,
                        "specs/foo/spec.md", 1, "soft advisory", "some-rule")),
                "built-in");

        ArchiveReadinessResult.Builder builder = ArchiveReadinessResult.builder("c");
        ArchiveReadinessService.foldValidation(vr, builder);
        ArchiveReadinessResult result = builder.build();

        assertEquals(ArchiveReadiness.READY, result.classify());
        assertFalse(result.isHardBlocked());
    }

    @Test
    void incompleteTasks_plus_validationError_hardDominates() {
        VerificationReport report = new VerificationReport("both");
        report.addFinding(new VerificationFinding(
                VerificationFinding.Severity.CRITICAL, Dimension.COMPLETENESS,
                "3 incomplete task(s) out of 10", "tasks.md", -1));
        ValidationResult vr = new ValidationResult(false,
                List.of(new ValidationIssue(ValidationIssue.Severity.ERROR,
                        "specs/foo/spec.md", 3, "No scenarios", "scenario-required")),
                "built-in");

        ArchiveReadinessResult.Builder builder = ArchiveReadinessResult.builder("both");
        ArchiveReadinessService.foldCompleteness(report, builder);
        ArchiveReadinessService.foldValidation(vr, builder);

        assertEquals(ArchiveReadiness.BLOCKED, builder.build().classify(),
                "hard validation block dominates the soft incomplete-tasks gate");
    }
}
