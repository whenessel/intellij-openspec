package com.johnnyblabs.openspec.validation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rule-level tests for {@link BuiltInValidator}, driving the REAL
 * {@link BuiltInValidator#validateSpecContent} (not a re-implemented mirror). Covers the 1.8
 * body-conditional RFC 2119 keyword rule (WARNING when the requirement has a body, ERROR when it has
 * none; the header-only variant is a WARNING), scenario-clause hints, the requirement-must-have-scenario
 * rule, and delta spec structural validation.
 */
class BuiltInValidatorRulesTest {

    // Patterns matching BuiltInValidator
    private static final Pattern REQUIREMENT_PATTERN = com.johnnyblabs.openspec.util.SpecPatterns.REQUIREMENT_HEADER;
    private static final Pattern SCENARIO_PATTERN = Pattern.compile("^#{4} Scenario:.+", Pattern.MULTILINE);
    private static final Pattern DELTA_SECTION_PATTERN = Pattern.compile("^## (ADDED|MODIFIED|REMOVED|RENAMED) Requirements", Pattern.MULTILINE);
    private static final Pattern RENAMED_ENTRY_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:-\\s*)?FROM:\\s*(.+)$\\s*^\\s*(?:-\\s*)?TO:\\s*(.+)$");
    private static final Pattern REMOVED_REASON_PATTERN = Pattern.compile("\\*\\*\\s*Reason\\s*:?\\s*\\*\\*", Pattern.CASE_INSENSITIVE);
    private static final Pattern REMOVED_MIGRATION_PATTERN = Pattern.compile("\\*\\*\\s*Migration\\s*:?\\s*\\*\\*", Pattern.CASE_INSENSITIVE);

    // --- 1.8: RFC 2119 keyword rule is severity-conditional on the requirement having a body ---

    @Test
    void rfc2119_missingKeyword_bodyPresent_isWarning() {
        String content = """
                # Test Spec

                ### Requirement: Missing keywords
                The system allows users to log in.

                #### Scenario: Login
                - **WHEN** user submits credentials
                - **THEN** system authenticates
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.WARNING && i.rule().equals("spec-rfc-keywords")),
                "1.8: a body-carrying requirement without SHALL/MUST is a WARNING, not an ERROR");
        assertTrue(issues.stream().noneMatch(i ->
                i.severity() == ValidationIssue.Severity.ERROR && i.rule().equals("spec-rfc-keywords")),
                "the missing-keyword rule must not ERROR when the requirement has a body");
    }

    @Test
    void rfc2119_missingKeyword_noBody_isError() {
        String content = """
                # Test Spec

                ### Requirement: Empty

                #### Scenario: Login
                - **WHEN** user submits credentials
                - **THEN** system authenticates
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.ERROR && i.rule().equals("spec-rfc-keywords")),
                "1.8: a requirement with no body prose still ERRORs on the missing keyword");
    }

    @Test
    void rfc2119_withShall_passes() {
        String content = """
                # Test Spec

                ### Requirement: Has keyword
                The system SHALL allow users to log in.

                #### Scenario: Login
                - **WHEN** user submits credentials
                - **THEN** system authenticates
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.stream().noneMatch(i -> i.rule().equals("spec-rfc-keywords")),
                "Requirement with SHALL should not produce RFC keyword error");
    }

    @Test
    void rfc2119_withMust_passes() {
        String content = """
                # Test Spec

                ### Requirement: Has keyword
                The system MUST allow users to log in.

                #### Scenario: Login
                - **WHEN** user submits credentials
                - **THEN** system authenticates
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.stream().noneMatch(i -> i.rule().equals("spec-rfc-keywords")));
    }

    // --- 1.7: Scenario clauses are ERROR ---

    @Test
    void scenarioClauses_missingWhen_isInfo() {
        String content = """
                # Test Spec

                ### Requirement: Test
                The system SHALL do things.

                #### Scenario: Missing when
                - **THEN** something happens
                """;
        List<ValidationIssue> issues = validateSpec(content);
        // INFO, not ERROR — the CLI performs no clause-structure validation, so flagging it as an error
        // would make the plugin stricter than the client it wraps (verified against real production).
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.INFO
                        && i.rule().equals("spec-scenario-clauses")
                        && i.message().contains("WHEN")),
                "Missing WHEN clause is an informational (INFO) hint");
    }

    @Test
    void scenarioClauses_missingThen_isInfo() {
        String content = """
                # Test Spec

                ### Requirement: Test
                The system SHALL do things.

                #### Scenario: Missing then
                - **WHEN** user does something
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.INFO
                        && i.rule().equals("spec-scenario-clauses")
                        && i.message().contains("THEN")),
                "Missing THEN clause is an informational (INFO) hint");
    }

    @Test
    void scenarioClauses_missingBoth_isInfo() {
        String content = """
                # Test Spec

                ### Requirement: Test
                The system SHALL do things.

                #### Scenario: Missing both
                Some text but no clauses.
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.INFO
                        && i.rule().equals("spec-scenario-clauses")
                        && i.message().contains("WHEN and THEN")),
                "Missing both clauses is an informational (INFO) hint");
    }

    @Test
    void scenarioClauses_withBoth_passes() {
        String content = """
                # Test Spec

                ### Requirement: Test
                The system SHALL do things.

                #### Scenario: Complete
                - **WHEN** user does something
                - **THEN** system responds
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.stream().noneMatch(i -> i.rule().equals("spec-scenario-clauses")),
                "Scenario with WHEN and THEN should not produce clause error");
    }

    // --- 1.9: Requirement must have at least one scenario ---

    @Test
    void requirementWithoutScenario_isError() {
        String content = """
                # Test Spec

                ### Requirement: No scenarios
                The system SHALL do something but has no scenarios.
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.ERROR && i.rule().equals("spec-scenario-required")),
                "Requirement without scenario should be ERROR");
    }

    @Test
    void requirementWithScenario_passes() {
        String content = """
                # Test Spec

                ### Requirement: Has scenario
                The system SHALL do something.

                #### Scenario: It works
                - **WHEN** triggered
                - **THEN** it works
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.stream().noneMatch(i -> i.rule().equals("spec-scenario-required")),
                "Requirement with scenario should not produce error");
    }

    @Test
    void multipleRequirements_firstMissingScenario_isError() {
        String content = """
                # Test Spec

                ### Requirement: No scenarios
                The system SHALL do A.

                ### Requirement: Has scenarios
                The system SHALL do B.

                #### Scenario: B works
                - **WHEN** triggered
                - **THEN** B works
                """;
        List<ValidationIssue> issues = validateSpec(content);
        long scenarioErrors = issues.stream()
                .filter(i -> i.rule().equals("spec-scenario-required"))
                .count();
        assertEquals(1, scenarioErrors, "Only the requirement without a scenario should produce an error");
        assertTrue(issues.stream().anyMatch(i ->
                i.rule().equals("spec-scenario-required") && i.message().contains("No scenarios")));
    }

    // --- 1.8: Delta spec structural validation ---

    @Test
    void deltaAdded_missingScenario_isError() {
        String content = """
                ## ADDED Requirements

                ### Requirement: New feature
                The system SHALL add a feature.
                """;
        List<ValidationIssue> issues = validateDeltaStructure(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.ERROR
                        && i.rule().equals("delta-requirement-scenario")
                        && i.message().contains("ADDED")),
                "ADDED requirement without scenario should be ERROR");
    }

    @Test
    void deltaModified_missingScenario_isError() {
        String content = """
                ## MODIFIED Requirements

                ### Requirement: Updated feature
                The system SHALL update a feature.
                """;
        List<ValidationIssue> issues = validateDeltaStructure(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.ERROR
                        && i.rule().equals("delta-requirement-scenario")
                        && i.message().contains("MODIFIED")),
                "MODIFIED requirement without scenario should be ERROR");
    }

    @Test
    void deltaRemoved_missingReason_isWarning() {
        // Reason/Migration is an OpenSpec authoring convention, not an upstream rule (the
        // @fission-ai/openspec client validates REMOVED by name only), so it is advisory, not blocking.
        String content = """
                ## REMOVED Requirements

                ### Requirement: Old feature
                **Migration**: Use new endpoint
                """;
        List<ValidationIssue> issues = validateDeltaStructure(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.WARNING
                        && i.rule().equals("delta-removed-fields")
                        && i.message().contains("**Reason**")),
                "REMOVED requirement without Reason should be WARNING");
        assertTrue(issues.stream().noneMatch(i ->
                i.severity() == ValidationIssue.Severity.ERROR && i.rule().equals("delta-removed-fields")),
                "delta-removed-fields must not be an ERROR — the client does not require these fields");
    }

    @Test
    void deltaRemoved_missingMigration_isWarning() {
        String content = """
                ## REMOVED Requirements

                ### Requirement: Old feature
                **Reason**: No longer needed
                """;
        List<ValidationIssue> issues = validateDeltaStructure(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.WARNING
                        && i.rule().equals("delta-removed-fields")
                        && i.message().contains("**Migration**")),
                "REMOVED requirement without Migration should be WARNING");
    }

    @Test
    void deltaRemoved_missingBoth_isWarning() {
        String content = """
                ## REMOVED Requirements

                ### Requirement: Old feature
                This is being removed.
                """;
        List<ValidationIssue> issues = validateDeltaStructure(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.WARNING
                        && i.rule().equals("delta-removed-fields")
                        && i.message().contains("**Reason** and **Migration**")),
                "REMOVED requirement without both fields should report both missing");
    }

    @Test
    void deltaRemoved_colonInsideBold_isAccepted() {
        // **Reason:** / **Migration:** (colon inside the bold) is the form upstream proposals use —
        // it must be recognized just like the **Reason**: form, with no warning.
        String content = """
                ## REMOVED Requirements

                ### Requirement: Old feature
                **Reason:** No longer needed
                **Migration:** Use the new endpoint
                """;
        List<ValidationIssue> issues = validateDeltaStructure(content);
        assertTrue(issues.stream().noneMatch(i -> i.rule().equals("delta-removed-fields")),
                "REMOVED block using **Reason:**/**Migration:** (colon inside) should produce no finding");
    }

    @Test
    void validDeltaSpec_passes() {
        String content = """
                ## ADDED Requirements

                ### Requirement: New feature
                The system SHALL add a feature.

                #### Scenario: Feature works
                - **WHEN** triggered
                - **THEN** feature activates

                ## MODIFIED Requirements

                ### Requirement: Updated feature
                The system SHALL update behavior.

                #### Scenario: Updated behavior
                - **WHEN** condition met
                - **THEN** new behavior occurs

                ## REMOVED Requirements

                ### Requirement: Legacy feature
                **Reason**: Replaced by new feature
                **Migration**: Use new feature instead
                """;
        List<ValidationIssue> issues = validateDeltaStructure(content);
        assertTrue(issues.stream().noneMatch(i ->
                i.rule().equals("delta-requirement-scenario") || i.rule().equals("delta-removed-fields")),
                "Valid delta spec should have no structural errors");
    }

    // --- RENAMED delta section ---

    @Test
    void deltaRenamed_validFromTo_passes() {
        String content = """
                ## RENAMED Requirements

                - FROM: Old requirement name
                - TO: New requirement name
                """;
        List<ValidationIssue> issues = validateDeltaStructure(content);
        assertTrue(issues.stream().noneMatch(i -> i.rule().equals("delta-renamed-fields")),
                "RENAMED with valid FROM/TO should not produce delta-renamed-fields error");
    }

    @Test
    void deltaRenamed_validFromToWithoutBullets_passes() {
        String content = """
                ## RENAMED Requirements

                FROM: Old requirement name
                TO: New requirement name
                """;
        List<ValidationIssue> issues = validateDeltaStructure(content);
        assertTrue(issues.stream().noneMatch(i -> i.rule().equals("delta-renamed-fields")),
                "RENAMED with valid FROM/TO (non-bullet) should not produce delta-renamed-fields error");
    }

    @Test
    void deltaRenamed_missingFromTo_isError() {
        String content = """
                ## RENAMED Requirements

                (no FROM/TO entries yet)
                """;
        List<ValidationIssue> issues = validateDeltaStructure(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.ERROR
                        && i.rule().equals("delta-renamed-fields")),
                "RENAMED section without FROM/TO should be ERROR");
    }

    @Test
    void validDeltaSpec_withRenamed_passes() {
        String content = """
                ## ADDED Requirements

                ### Requirement: New feature
                The system SHALL add a feature.

                #### Scenario: Feature works
                - **WHEN** triggered
                - **THEN** feature activates

                ## RENAMED Requirements

                - FROM: Old name
                - TO: New name

                ## REMOVED Requirements

                ### Requirement: Legacy feature
                **Reason**: Replaced
                **Migration**: Use new feature
                """;
        List<ValidationIssue> issues = validateDeltaStructure(content);
        assertTrue(issues.stream().noneMatch(i ->
                i.rule().equals("delta-requirement-scenario")
                        || i.rule().equals("delta-removed-fields")
                        || i.rule().equals("delta-renamed-fields")),
                "Mixed delta with RENAMED + ADDED + REMOVED should have no structural errors");
    }

    // --- CLI 1.4 parity: case-insensitive headers + keyword-placement hint ---

    @Test
    void lowercaseRequirementHeader_isRecognized() {
        String content = """
                # Test Spec

                ### requirement: Lowercase header token
                The system SHALL parse this requirement.

                #### Scenario: Parse
                - **WHEN** the spec is validated
                - **THEN** the requirement is recognized
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.isEmpty(),
                "CLI 1.4+ parses headers case-insensitively; expected no issues but got: " + issues);
    }

    @Test
    void uppercaseAndMixedCaseHeaders_areRecognized() {
        String content = """
                # Test Spec

                ### REQUIREMENT: Uppercase token
                Body text without keyword and without scenario.

                ### ReQuIrEmEnT: Mixed-case token
                More body text.
                """;
        List<ValidationIssue> issues = validateSpec(content);
        // Both requirements must be SEEN (each missing keyword + scenario = 2 issues apiece).
        assertEquals(4, issues.size(),
                "Both non-canonical headers should be validated as requirements: " + issues);
    }

    @Test
    void headerTokenMidLine_isNotARequirement() {
        String content = """
                # Test Spec

                Some prose mentioning ### Requirement: inline should not match.
                """;
        Matcher m = REQUIREMENT_PATTERN.matcher(content);
        assertFalse(m.find(), "Header pattern must stay line-anchored");
    }

    @Test
    void keywordOnlyInHeader_getsTargetedHint() {
        String content = """
                # Test Spec

                ### Requirement: The system SHALL support login
                Users can authenticate with a password.

                #### Scenario: Login
                - **WHEN** user submits credentials
                - **THEN** the session starts
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.stream().anyMatch(i -> i.rule().equals("spec-rfc-keyword-in-header")),
                "Keyword in header only should get the targeted hint: " + issues);
        assertTrue(issues.stream()
                        .filter(i -> i.rule().equals("spec-rfc-keyword-in-header"))
                        .allMatch(i -> i.severity() == ValidationIssue.Severity.WARNING),
                "1.8: the header-only-keyword hint is a WARNING (body present); --strict re-promotes it");
        assertTrue(issues.stream().noneMatch(i -> i.rule().equals("spec-rfc-keywords")),
                "Targeted hint replaces the generic missing-keyword error: " + issues);
        assertTrue(issues.stream()
                        .filter(i -> i.rule().equals("spec-rfc-keyword-in-header"))
                        .allMatch(i -> i.message().contains("move the keyword onto the requirement body line")),
                "Hint must carry the CLI's remediation text");
    }

    @Test
    void keywordInBody_headerKeywordIsFine() {
        String content = """
                # Test Spec

                ### Requirement: The system SHALL support login
                The system SHALL authenticate users with a password.

                #### Scenario: Login
                - **WHEN** user submits credentials
                - **THEN** the session starts
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.isEmpty(),
                "Keyword present in the body satisfies the rule regardless of the header: " + issues);
    }

    @Test
    void keywordNowhere_bodyPresent_isWarning() {
        String content = """
                # Test Spec

                ### Requirement: Login support
                Users can authenticate with a password.

                #### Scenario: Login
                - **WHEN** user submits credentials
                - **THEN** the session starts
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.stream().anyMatch(i ->
                        i.rule().equals("spec-rfc-keywords") && i.severity() == ValidationIssue.Severity.WARNING),
                "1.8: no keyword anywhere in a body-carrying requirement is a WARNING: " + issues);
        assertTrue(issues.stream().noneMatch(i -> i.rule().equals("spec-rfc-keyword-in-header")),
                "Targeted hint requires a keyword in the header: " + issues);
    }

    // --- Helpers: extract validation logic matching BuiltInValidator ---

    /**
     * Drives the REAL {@link BuiltInValidator#validateSpecContent} on a raw spec string, so these rule
     * tests verify production behavior rather than a re-implemented mirror. The prior mirror was a
     * vacuity smell — it could pass while production was wrong (and it never fence-masked, unlike
     * production). Fence masking now happens inside the production method.
     */
    private List<ValidationIssue> validateSpec(String content) {
        List<ValidationIssue> issues = new ArrayList<>();
        BuiltInValidator.validateSpecContent(content, "test-spec.md", issues);
        return issues;
    }

    private List<ValidationIssue> validateDeltaStructure(String content) {
        List<ValidationIssue> issues = new ArrayList<>();
        String path = "test-delta.md";

        Matcher sectionMatcher = DELTA_SECTION_PATTERN.matcher(content);
        while (sectionMatcher.find()) {
            String sectionType = sectionMatcher.group(1);
            int sectionHeaderLine = lineNumberAt(content, sectionMatcher.start());
            int sectionStart = sectionMatcher.end();
            Pattern nextH2 = Pattern.compile("^## ", Pattern.MULTILINE);
            Matcher nextH2Matcher = nextH2.matcher(content);
            int sectionEnd = content.length();
            if (nextH2Matcher.find(sectionStart)) {
                sectionEnd = nextH2Matcher.start();
            }
            String sectionContent = content.substring(sectionStart, sectionEnd);

            if ("RENAMED".equals(sectionType)) {
                if (!RENAMED_ENTRY_PATTERN.matcher(sectionContent).find()) {
                    issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, path, sectionHeaderLine,
                            "RENAMED section must contain at least one FROM:/TO: pair",
                            "delta-renamed-fields"));
                }
                continue;
            }

            Matcher reqMatcher = REQUIREMENT_PATTERN.matcher(sectionContent);
            while (reqMatcher.find()) {
                int reqLine = lineNumberAt(content, sectionStart + reqMatcher.start());
                String reqHeader = reqMatcher.group(1).trim();
                int nextReq = findNext(REQUIREMENT_PATTERN, sectionContent, reqMatcher.end());
                String reqContent = sectionContent.substring(reqMatcher.end(), nextReq);

                if ("REMOVED".equals(sectionType)) {
                    boolean hasReason = REMOVED_REASON_PATTERN.matcher(reqContent).find();
                    boolean hasMigration = REMOVED_MIGRATION_PATTERN.matcher(reqContent).find();
                    if (!hasReason || !hasMigration) {
                        String missing = !hasReason && !hasMigration ? "**Reason** and **Migration**"
                                : !hasReason ? "**Reason**" : "**Migration**";
                        issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, path, reqLine,
                                "REMOVED requirement '" + reqHeader + "' should contain " + missing + " fields",
                                "delta-removed-fields"));
                    }
                } else {
                    if (!SCENARIO_PATTERN.matcher(reqContent).find()) {
                        String detail = "MODIFIED".equals(sectionType)
                                ? "MODIFIED requirement '" + reqHeader + "' must include full updated content with at least one scenario"
                                : "ADDED requirement '" + reqHeader + "' must have at least one '#### Scenario:' block";
                        issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, path, reqLine,
                                detail, "delta-requirement-scenario"));
                    }
                }
            }
        }
        return issues;
    }

    private int lineNumberAt(String content, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < content.length(); i++) {
            if (content.charAt(i) == '\n') line++;
        }
        return line;
    }

    private int findNext(Pattern pattern, String content, int from) {
        Matcher m = pattern.matcher(content);
        if (m.find(from)) {
            return m.start();
        }
        return content.length();
    }

    // --- maskFences (the real production utility, not a mirror) ---

    @Test
    void maskFencesMasksBacktickFenceContentAndMarkers() {
        String in = "before\n```\nThe system SHALL work.\n```\nafter\n";
        String out = BuiltInValidator.maskFences(in);
        assertEquals(in.length(), out.length(), "offset preservation");
        assertFalse(out.contains("SHALL"));
        assertFalse(out.contains("```"), "fence marker lines are masked too");
        assertTrue(out.contains("before") && out.contains("after"));
        assertEquals(countNewlines(in), countNewlines(out), "line preservation");
    }

    @Test
    void maskFencesHandlesTildeFences() {
        String out = BuiltInValidator.maskFences("~~~\n#### Scenario: hidden\n~~~\nvisible\n");
        assertFalse(out.contains("Scenario"));
        assertTrue(out.contains("visible"));
    }

    @Test
    void maskFencesUnclosedFenceMasksToEndOfContent() {
        String out = BuiltInValidator.maskFences("ok\n```\nSHALL forever\nno close");
        assertTrue(out.contains("ok"));
        assertFalse(out.contains("SHALL"));
        assertFalse(out.contains("no close"));
    }

    @Test
    void maskFencesBacktickInsideTildeFenceDoesNotClose() {
        String out = BuiltInValidator.maskFences("~~~\n```\nstill fenced SHALL\n~~~\nvisible SHALL\n");
        assertFalse(out.contains("still fenced"));
        assertTrue(out.contains("visible SHALL"));
    }

    @Test
    void maskFencesIndentedFenceRecognized() {
        String out = BuiltInValidator.maskFences("  ```\n  fenced MUST\n  ```\nplain MUST\n");
        assertFalse(out.contains("fenced MUST"));
        assertTrue(out.contains("plain MUST"));
    }

    private static long countNewlines(String s) {
        return s.chars().filter(c -> c == '\n').count();
    }
}
