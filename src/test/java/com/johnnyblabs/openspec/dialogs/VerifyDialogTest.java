package com.johnnyblabs.openspec.dialogs;

import com.intellij.ui.JBColor;
import com.johnnyblabs.openspec.model.ArchiveReadinessResult;
import com.johnnyblabs.openspec.model.ArchiveReadinessResult.Category;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The {@link VerifyDialog}'s archive-gate contract, tested through its pure decisions
 * ({@link VerifyDialog#okButtonText} + the model's {@link ArchiveReadinessResult#isHardBlocked}) so
 * it stays IDE-free. The load-bearing behavior: an IN_PROGRESS change is <b>archivable-anyway</b>
 * (OK enabled, "Archive anyway"), mirroring upstream's bypassable {@code --yes} task gate; only a
 * hard validation BLOCK disables OK.
 */
class VerifyDialogTest {

    @Test
    void inProgress_okIsArchiveAnyway_andEnabled() {
        ArchiveReadinessResult result = ArchiveReadinessResult.builder("wip")
                .addError(Category.ARTIFACT_COMPLETENESS, "5 incomplete task(s) out of 8")
                .build();
        assertEquals("Archive anyway", VerifyDialog.okButtonText(result));
        assertFalse(result.isHardBlocked(), "in-progress must leave Archive enabled (bypassable)");
    }

    @Test
    void blocked_okIsArchive_andDisabled() {
        ArchiveReadinessResult result = ArchiveReadinessResult.builder("bad")
                .addError(Category.VALIDATION, "Missing scenario")
                .build();
        assertEquals("Archive", VerifyDialog.okButtonText(result));
        assertTrue(result.isHardBlocked(), "a validation error blocks archive (OK disabled)");
    }

    @Test
    void ready_okIsArchive_andEnabled() {
        ArchiveReadinessResult result = ArchiveReadinessResult.builder("done").build();
        assertEquals("Archive", VerifyDialog.okButtonText(result));
        assertFalse(result.isHardBlocked());
    }

    @Test
    void warningOnly_okIsArchive_andEnabled() {
        ArchiveReadinessResult result = ArchiveReadinessResult.builder("warn")
                .addWarning(Category.SYNC_READINESS, "Target capability has no main spec")
                .build();
        assertEquals("Archive", VerifyDialog.okButtonText(result));
        assertFalse(result.isHardBlocked(), "warnings never block archive");
    }

    // --- Header color per state: IN_PROGRESS must be neutral (never red) ---

    @Test
    void stateColor_inProgress_isNeutralNotRed() {
        ArchiveReadinessResult result = ArchiveReadinessResult.builder("wip")
                .addError(Category.ARTIFACT_COMPLETENESS, "2 incomplete task(s) out of 5")
                .build();
        assertEquals(JBColor.foreground(), VerifyDialog.stateColor(result));
        assertNotEquals(Color.RED, VerifyDialog.stateColor(result),
                "the in-progress header must NOT be red — a valid-but-unfinished change is not a defect");
    }

    @Test
    void stateColor_blocked_isRed() {
        ArchiveReadinessResult result = ArchiveReadinessResult.builder("bad")
                .addError(Category.VALIDATION, "Missing scenario")
                .build();
        assertEquals(JBColor.RED, VerifyDialog.stateColor(result));
    }

    @Test
    void stateColor_ready_isNotRed() {
        ArchiveReadinessResult result = ArchiveReadinessResult.builder("done").build();
        assertNotEquals(Color.RED, VerifyDialog.stateColor(result));
    }
}
