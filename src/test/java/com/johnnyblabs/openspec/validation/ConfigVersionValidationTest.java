package com.johnnyblabs.openspec.validation;

import com.johnnyblabs.openspec.version.VersionSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for config version validation and change schema cross-validation logic.
 * Mirrors the validation rules in BuiltInValidator without requiring IntelliJ services.
 */
class ConfigVersionValidationTest {

    // --- Config version validation: intentionally not mirrored here ---
    //
    // The config-version rules this section used to reimplement inline (config-version-required,
    // config-version-unknown, config-field-required) are no longer emitted by production:
    // align-config-contract-with-cli retired the required-version rules, and the config-validator
    // relaxation removed config-version-unknown (the CLI never reads `version:`). Mirroring them here
    // was a false-green —
    // the reimplemented rules would pass even with the production change reverted. The real,
    // service-driven coverage now lives in BuiltInValidatorTest (testUnknownVersionIsClean,
    // testMissingSchemaIsANonFailingInfoNudge) and CliConfigToleranceContractTest.

    // --- Change schema cross-validation ---
    //
    // V1_0 and V1_1 were removed in v0.3.0 (bump-cli-floor-to-1-3). Legacy version strings
    // route to V1_2 via VersionSupport.fromString. Tests below cover V1_2 only and assert
    // the legacy-routing behavior at the boundary.

    @Test
    void changeWithIncompatibleSchema_producesWarning() {
        // tdd is not in V1_2's valid schemas
        List<ValidationIssue> issues = validateChangeSchema("tdd", VersionSupport.V1_2);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.WARNING
                        && i.rule().equals("change-schema-incompatible")),
                "Change with schema not valid for project version should produce WARNING");
    }

    @Test
    void legacyVersion_1_0_0_routesToV1_2_andAcceptsSpecDriven() {
        // Confirms the legacy-routing contract: a project declaring `version: 1.0.0` resolves
        // to V1_2 (since V1_0 was deleted) and applies V1_2's valid-schema set.
        VersionSupport resolved = VersionSupport.fromString("1.0.0");
        assertEquals(VersionSupport.V1_2, resolved);
        List<ValidationIssue> issues = validateChangeSchema("spec-driven", resolved);
        assertTrue(issues.stream().noneMatch(i ->
                i.rule().equals("change-schema-incompatible")),
                "Legacy 1.0.0 routes to V1_2; spec-driven is in V1_2's valid schemas");
    }

    @Test
    void legacyVersion_1_1_0_routesToV1_2() {
        // A legacy 1.1.0 config routes to V1_2 (the floor bump collapsed V1_0/V1_1). spec-driven —
        // the sole built-in schema after CLI 1.5.0 removed workspace-planning — validates cleanly.
        VersionSupport resolved = VersionSupport.fromString("1.1.0");
        assertEquals(VersionSupport.V1_2, resolved);
        List<ValidationIssue> issues = validateChangeSchema("spec-driven", resolved);
        assertTrue(issues.stream().noneMatch(i ->
                i.rule().equals("change-schema-incompatible")));
    }

    @Test
    void v1_2_rapid_warns() {
        List<ValidationIssue> issues = validateChangeSchema("rapid", VersionSupport.V1_2);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.WARNING
                        && i.rule().equals("change-schema-incompatible")),
                "rapid schema is not supported by CLI — should warn");
    }

    @Test
    void v1_2_specDriven_passes() {
        List<ValidationIssue> issues = validateChangeSchema("spec-driven", VersionSupport.V1_2);
        assertTrue(issues.stream().noneMatch(i ->
                i.rule().equals("change-schema-incompatible")),
                "1.2.0 project with spec-driven should pass");
    }

    @Test
    void v1_2_workspacePlanning_warnsUnderBuiltInsOnly() {
        // workspace-planning was removed in CLI 1.5.0, so it is no longer a built-in schema. With no
        // live CLI list to supply it, the built-in fallback warns; it stays valid on a 1.4.x CLI via
        // the known-set path (see the known-set tests below that pass it explicitly).
        List<ValidationIssue> issues = validateChangeSchema("workspace-planning", VersionSupport.V1_2);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.WARNING
                        && i.rule().equals("change-schema-incompatible")),
                "workspace-planning is no longer a built-in after 1.5.0 removed it");
    }

    // --- v0.3.0 schema-validation-cli-runtime-driven: known-set semantics ---

    @Test
    void customForkedSchema_inKnownSet_passes() {
        // When the user has run `openspec schema fork spec-driven my-team-flow`,
        // SchemaService.getKnownSchemaNames() returns built-ins + the fork.
        // The validator should not warn on the custom name.
        java.util.Set<String> known = java.util.Set.of("spec-driven", "workspace-planning", "my-team-flow");
        List<ValidationIssue> issues = validateChangeSchema("my-team-flow", VersionSupport.V1_2, known);
        assertTrue(issues.stream().noneMatch(i ->
                i.rule().equals("change-schema-incompatible")),
                "Custom-forked schema present in known-set must not trigger change-schema-incompatible");
    }

    @Test
    void typoSchema_notInKnownSet_warns() {
        // Regression test: a typo like "spec-drivenn" must still warn under the new
        // known-set semantics — the broader recognition does not mask typos.
        java.util.Set<String> known = java.util.Set.of("spec-driven", "workspace-planning");
        List<ValidationIssue> issues = validateChangeSchema("spec-drivenn", VersionSupport.V1_2, known);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.WARNING
                        && i.rule().equals("change-schema-incompatible")),
                "Typo schema name not in known-set must still produce change-schema-incompatible warning");
    }

    // NOTE: the CLI-unavailable behavior is no longer "custom schema warns" — the validator now GUARDS the
    // known-set check so it only fires when the CLI supplies an authoritative set. That guarded
    // behavior (CLI down → no warning; CLI authoritative → still warns) is covered service-driven in
    // BuiltInValidatorTest.testChangeSchemaIncompatibleGuardedWhenCliUnavailable /
    // testChangeSchemaIncompatibleStillWarnsWhenCliAuthoritative. The tests here cover only the pure
    // known-set MEMBERSHIP layer (given a set, is a name in it), which is unchanged.

    @Test
    void builtInSchema_inKnownSet_passes() {
        // Sanity: built-in schemas always pass regardless of how the known-set was assembled.
        java.util.Set<String> known = java.util.Set.of("spec-driven", "workspace-planning");
        assertTrue(validateChangeSchema("spec-driven", VersionSupport.V1_2, known).stream()
                .noneMatch(i -> i.rule().equals("change-schema-incompatible")));
        assertTrue(validateChangeSchema("workspace-planning", VersionSupport.V1_2, known).stream()
                .noneMatch(i -> i.rule().equals("change-schema-incompatible")));
    }

    // --- Helpers mirroring BuiltInValidator logic ---

    /**
     * Backward-compatible helper — defaults the known-set to the built-in
     * {@code VersionSupport.getValidSchemas()}, matching the validator's
     * behavior before the v0.3.0 schema-validation-cli-runtime-driven change.
     */
    private List<ValidationIssue> validateChangeSchema(String changeSchema, VersionSupport version) {
        return validateChangeSchema(changeSchema, version, version.getValidSchemas());
    }

    /**
     * New helper mirroring the post-v0.3.0 validator behavior: the known-set is
     * supplied by the caller (built-ins ∪ CLI runtime list, per
     * {@code SchemaService.getKnownSchemaNames()}). The schema is "valid" when it
     * appears in that union.
     */
    private List<ValidationIssue> validateChangeSchema(String changeSchema, VersionSupport version,
                                                       java.util.Set<String> knownSet) {
        List<ValidationIssue> issues = new ArrayList<>();
        String path = "test-change";

        if (!knownSet.contains(changeSchema)) {
            issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, path, 1,
                    "Change uses schema '" + changeSchema + "' which is not recognized. Known schemas: " +
                            knownSet,
                    "change-schema-incompatible"));
        }
        return issues;
    }
}
