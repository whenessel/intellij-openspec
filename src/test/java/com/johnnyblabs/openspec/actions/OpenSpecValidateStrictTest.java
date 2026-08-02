package com.johnnyblabs.openspec.actions;

import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for the per-run strict plumbing in {@link OpenSpecValidateAction}: the CLI arg
 * vector, the console command echo, the balloon summary disclosure, and the CLI-absent verdict flip.
 * All are package-private static, so no IDE fixture is needed.
 */
class OpenSpecValidateStrictTest {

    private static ValidationIssue warning(String rule) {
        return new ValidationIssue(ValidationIssue.Severity.WARNING, "spec/x", 1, "w", rule);
    }

    private static ValidationIssue error(String rule) {
        return new ValidationIssue(ValidationIssue.Severity.ERROR, "spec/x", 1, "e", rule);
    }

    // --- cliArgs / commandLine: both polarities (trap #1: prove --strict is actually appended) ---

    @Test
    void cliArgs_appendsStrict_onlyWhenStrict() {
        List<String> plain = Arrays.asList(OpenSpecValidateAction.cliArgs(ValidateTarget.wholeProject()));
        assertFalse(plain.contains("--strict"), "a non-strict run must NOT pass --strict");
        assertTrue(plain.contains("--all") && plain.contains("--json"));

        List<String> strict = Arrays.asList(
                OpenSpecValidateAction.cliArgs(ValidateTarget.wholeProject().withStrict()));
        assertTrue(strict.contains("--strict"), "a strict run must pass --strict");
    }

    @Test
    void commandLine_echoesStrict_onlyWhenStrict() {
        assertEquals("openspec validate --all",
                OpenSpecValidateAction.commandLine(ValidateTarget.wholeProject()));
        assertEquals("openspec validate --all --strict",
                OpenSpecValidateAction.commandLine(ValidateTarget.wholeProject().withStrict()));
        // Single-item strict still appends the flag after the --type.
        assertEquals("openspec validate demo --type spec --strict",
                OpenSpecValidateAction.commandLine(ValidateTarget.spec("demo").withStrict()));
    }

    // --- summaryText disclosure (preserve the passed(/failed( tokens; strict adds title+suffix) ---

    @Test
    void summaryText_preservesTokens_andDisclosesStrictOnWarningOnlyFailure() {
        // A warnings-only strict failure: the `failed (` token stays intact, the suffix follows it.
        ValidationResult warnFail = new ValidationResult(false, List.of(warning("spec-title-required")), "built-in");
        String strict = OpenSpecValidateAction.summaryText("whole project", warnFail, true);
        assertTrue(strict.contains("failed ("), "the failed( token must be preserved for UI assertions");
        assertTrue(strict.endsWith("— strict: warnings count as failures"), "strict discloses the reason");

        // Non-strict: no suffix.
        String normal = OpenSpecValidateAction.summaryText("whole project", warnFail, false);
        assertTrue(normal.contains("failed ("));
        assertFalse(normal.contains("strict"), "a non-strict summary never mentions strict");

        // A pass keeps the passed( token in both modes.
        ValidationResult pass = new ValidationResult(true, List.of(warning("x")), "built-in");
        assertTrue(OpenSpecValidateAction.summaryText("whole project", pass, true).contains("passed ("));
    }

    // --- CLI-absent verdict flip (trap #2: pure flip, warning STAYS a WARNING; config excluded) ---

    @Test
    void strictFallback_flipsVerdictOnWarnings_withoutReseveritying() {
        ValidationResult warnOnly = new ValidationResult(true, List.of(warning("spec-title-required")), "built-in");
        ValidationResult flipped = OpenSpecValidateAction.applyStrictFallbackVerdict(warnOnly, true);

        assertFalse(flipped.passed(), "strict flips a warning-only result to FAILED");
        assertEquals(0, flipped.errorCount(), "no errors were invented");
        assertEquals(ValidationIssue.Severity.WARNING, flipped.issues().get(0).severity(),
                "the issue STAYS a WARNING — the flip is a verdict change, not a re-severity");
    }

    @Test
    void strictFallback_doesNotFailOnConfigWarnings() {
        // config.yaml is display-only (the CLI never fails on it) — config warnings do not fail even
        // under strict, keeping strict's meaning consistent between CLI-present and CLI-absent.
        ValidationResult configWarn = new ValidationResult(true, List.of(warning("config-schema-required")), "built-in");
        assertTrue(OpenSpecValidateAction.applyStrictFallbackVerdict(configWarn, true).passed(),
                "a config warning must not fail a strict verdict");
    }

    @Test
    void strictFallback_isNoOpWhenNotStrictOrAlreadyFailing() {
        ValidationResult warnOnly = new ValidationResult(true, List.of(warning("spec-title-required")), "built-in");
        assertSame(warnOnly, OpenSpecValidateAction.applyStrictFallbackVerdict(warnOnly, false),
                "non-strict: unchanged");
        ValidationResult errored = new ValidationResult(false, List.of(error("spec-requirement-required")), "built-in");
        assertSame(errored, OpenSpecValidateAction.applyStrictFallbackVerdict(errored, true),
                "already failing on an ERROR: nothing to flip");
    }
}
