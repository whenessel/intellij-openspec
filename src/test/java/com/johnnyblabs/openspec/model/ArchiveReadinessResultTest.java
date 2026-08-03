package com.johnnyblabs.openspec.model;

import com.johnnyblabs.openspec.model.ArchiveReadinessResult.ArchiveReadiness;
import com.johnnyblabs.openspec.model.ArchiveReadinessResult.BlockKind;
import com.johnnyblabs.openspec.model.ArchiveReadinessResult.Category;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The load-bearing pure unit of the Verify collapse: the three-state {@link ArchiveReadinessResult#classify()}.
 *
 * <p>Grounded in real {@code openspec archive --json} (CLI 1.6.0): incomplete tasks
 * ({@code archive_tasks_incomplete}) and validation failure ({@code archive_validation_failed})
 * BOTH carry {@code severity:"error"} — the soft/hard split is the code, not the severity. So a
 * classifier keyed on error-count alone cannot tell IN_PROGRESS from BLOCKED; these tests pin that
 * the fold is over the intrinsic {@link BlockKind}, and that IN_PROGRESS is never rendered red.
 */
class ArchiveReadinessResultTest {

    // --- The crux: incomplete tasks are a SOFT, bypassable gate, NOT a hard block ---

    @Test
    void incompleteTasksOnly_isInProgress_notBlocked() {
        ArchiveReadinessResult result = ArchiveReadinessResult.builder("wip-change")
                .addError(Category.ARTIFACT_COMPLETENESS, "9 incomplete task(s) out of 11")
                .build();

        assertEquals(ArchiveReadiness.IN_PROGRESS, result.classify(),
                "unfinished-but-valid work is IN_PROGRESS, not BLOCKED — mirrors the bypassable --yes gate");
        assertFalse(result.isHardBlocked(), "in-progress must not hard-block archive");
        assertEquals(BlockKind.SOFT,
                result.getFindings(Category.ARTIFACT_COMPLETENESS).getFirst().blockKind());
    }

    @Test
    void validationError_isBlocked() {
        ArchiveReadinessResult result = ArchiveReadinessResult.builder("bad-change")
                .addError(Category.VALIDATION, "ADDED requirement must include at least one scenario")
                .build();

        assertEquals(ArchiveReadiness.BLOCKED, result.classify());
        assertTrue(result.isHardBlocked(), "a validation error is the only hard block");
        assertEquals(BlockKind.HARD,
                result.getFindings(Category.VALIDATION).getFirst().blockKind());
    }

    @Test
    void validationError_plus_incompleteTasks_hardDominates() {
        ArchiveReadinessResult result = ArchiveReadinessResult.builder("both")
                .addError(Category.ARTIFACT_COMPLETENESS, "3 incomplete task(s) out of 10")
                .addError(Category.VALIDATION, "No scenarios")
                .build();

        assertEquals(ArchiveReadiness.BLOCKED, result.classify(), "hard block dominates soft");
        assertTrue(result.stateMessage().contains("Validation failed"),
                "the hard message wins, not the neutral in-progress text");
    }

    @Test
    void clean_isReady() {
        ArchiveReadinessResult result = ArchiveReadinessResult.builder("done").build();
        assertEquals(ArchiveReadiness.READY, result.classify());
        assertFalse(result.isHardBlocked());
        assertTrue(result.stateMessage().toLowerCase().contains("ready to archive"));
    }

    @Test
    void specSyncWarningOnly_isReady() {
        ArchiveReadinessResult result = ArchiveReadinessResult.builder("warn-only")
                .addWarning(Category.SYNC_READINESS, "Target capability has no main spec")
                .build();

        assertEquals(ArchiveReadiness.READY, result.classify(),
                "warnings alone never gate archiving");
        assertFalse(result.isHardBlocked());
        assertEquals(BlockKind.NONE,
                result.getFindings(Category.SYNC_READINESS).getFirst().blockKind());
    }

    @Test
    void inProgressMessage_showsIncompleteCount() {
        ArchiveReadinessResult result = ArchiveReadinessResult.builder("wip")
                .addError(Category.ARTIFACT_COMPLETENESS, "task detail A")
                .addError(Category.ARTIFACT_COMPLETENESS, "task detail B")
                .build();
        String msg = result.stateMessage();
        assertTrue(msg.contains("In Progress"), "message names the native In Progress state");
        assertTrue(msg.contains("2 incomplete task(s)"), "message counts the soft findings: " + msg);
    }

    // --- Retained accessors (ported from the former ComplianceResultTest) ---

    @Test
    void errorCount_and_warningCount() {
        ArchiveReadinessResult result = ArchiveReadinessResult.builder("c")
                .addError(Category.VALIDATION, "e1")
                .addWarning(Category.ARTIFACT_COMPLETENESS, "w1")
                .build();
        assertEquals(1, result.errorCount());
        assertEquals(1, result.warningCount());
    }

    @Test
    void getFindings_filtersByCategory() {
        ArchiveReadinessResult result = ArchiveReadinessResult.builder("c")
                .addError(Category.VALIDATION, "Error A")
                .addWarning(Category.ARTIFACT_COMPLETENESS, "Warning B")
                .addError(Category.VALIDATION, "Error C")
                .build();
        assertEquals(2, result.getFindings(Category.VALIDATION).size());
        assertEquals(1, result.getFindings(Category.ARTIFACT_COMPLETENESS).size());
        assertEquals(0, result.getFindings(Category.SYNC_READINESS).size());
    }

    @Test
    void categoryPasses_withNoFindingsInCategory() {
        ArchiveReadinessResult result = ArchiveReadinessResult.builder("c")
                .addError(Category.VALIDATION, "Bad spec")
                .build();
        assertTrue(result.categoryPasses(Category.ARTIFACT_COMPLETENESS));
        assertTrue(result.categoryPasses(Category.SYNC_READINESS));
        assertFalse(result.categoryPasses(Category.VALIDATION));
    }

    @Test
    void changeName_isPreserved() {
        assertEquals("my-change", ArchiveReadinessResult.builder("my-change").build().getChangeName());
    }
}
