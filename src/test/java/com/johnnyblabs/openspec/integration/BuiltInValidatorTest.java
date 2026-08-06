package com.johnnyblabs.openspec.integration;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ServiceContainerUtil;
import com.johnnyblabs.openspec.services.SchemaService;
import com.johnnyblabs.openspec.validation.BuiltInValidator;
import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for BuiltInValidator.
 * Each test creates its own fixture files to isolate validation rules.
 */
public class BuiltInValidatorTest extends OpenSpecIntegrationTestBase {

    private BuiltInValidator validator;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        validator = getProject().getService(BuiltInValidator.class);
    }

    // ---------------------------------------------------------------
    // Spec Validation
    // ---------------------------------------------------------------

    public void testValidSpecProducesNoErrors() {
        ValidationResult result = validator.validateSpecs();
        List<ValidationIssue> errors = result.issues().stream()
                .filter(i -> i.severity() == ValidationIssue.Severity.ERROR)
                .toList();
        assertTrue("Valid specs should produce no errors, got: " + errors, errors.isEmpty());
    }

    public void testMissingTitleTriggersWarning() {
        // The CLI requires no `# Title` H1 (it derives the name from the directory), so a missing
        // title is a WARNING, not an ERROR — the plugin must not exceed the CLI's default verdict.
        myFixture.addFileToProject("openspec/specs/bad-title/spec.md",
                "## No title heading here\n\n### Requirement: Something\n\nThe system SHALL work.\n");
        refreshVfs();

        ValidationResult result = validator.validateSpecs();
        assertTrue("spec-title-required should be a WARNING, not an ERROR",
                result.issues().stream().anyMatch(i ->
                        "spec-title-required".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.WARNING &&
                        i.filePath().contains("bad-title")));
    }

    public void testUntitledButOtherwiseValidSpecPasses() {
        // A spec with no `# Title` but a well-formed requirement + scenario is CLI-valid, so the
        // built-in fallback must pass it (the title WARNING does not fail the verdict).
        myFixture.addFileToProject("openspec/specs/untitled-ok/spec.md",
                "## Purpose\n\nDoes a thing.\n\n### Requirement: Something\n\nThe system SHALL work.\n\n" +
                "#### Scenario: Works\n- **WHEN** invoked\n- **THEN** it works\n");
        refreshVfs();

        ValidationResult result = validator.validateSpecs();
        assertTrue("untitled-but-valid spec must pass (title is only a WARNING)", result.passed());
        assertTrue("the sole issue is the spec-title-required WARNING",
                result.issues().stream().anyMatch(i ->
                        "spec-title-required".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.WARNING &&
                        i.filePath().contains("untitled-ok")));
    }

    public void testMissingRequirementTriggersError() {
        myFixture.addFileToProject("openspec/specs/bad-req/spec.md",
                "# Title Only\n\nSome description but no requirements.\n");
        refreshVfs();

        ValidationResult result = validator.validateSpecs();
        assertTrue("Should have spec-requirement-required issue",
                result.issues().stream().anyMatch(i ->
                        "spec-requirement-required".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.ERROR &&
                        i.filePath().contains("bad-req")));
    }

    public void testMissingKeywordTriggersError() {
        myFixture.addFileToProject("openspec/specs/bad-kw/spec.md",
                "# Keywords Spec\n\n### Requirement: No Keywords\n\nThis has no RFC 2119 keywords at all.\n\n#### Scenario: Test\n- **WHEN** triggered\n- **THEN** nothing\n");
        refreshVfs();

        ValidationResult result = validator.validateSpecs();
        assertTrue("Should have spec-rfc-keywords ERROR issue",
                result.issues().stream().anyMatch(i ->
                        "spec-rfc-keywords".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.ERROR &&
                        i.filePath().contains("bad-kw")));
    }

    public void testEmptyScenarioClausesAreInfoAndPass() {
        // The CLI performs no WHEN/THEN clause validation (a scenario need only be non-empty), so a
        // missing-clause scenario is an INFO hint that does not fail the verdict.
        myFixture.addFileToProject("openspec/specs/bad-scenario/spec.md",
                "# Scenario Spec\n\n### Requirement: Bad Scenario\n\nThe system SHALL work.\n\n" +
                "#### Scenario: Missing clauses\nJust a description with no structured clauses.\n");
        refreshVfs();

        ValidationResult result = validator.validateSpecs();
        assertTrue("spec-scenario-clauses should be INFO, not ERROR",
                result.issues().stream().anyMatch(i ->
                        "spec-scenario-clauses".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.INFO &&
                        i.filePath().contains("bad-scenario")));
        assertTrue("a clauseless scenario must not fail the verdict", result.passed());
    }

    public void testScenarolessRequirementStillErrors() {
        // Corrected after empirical CLI capture: a scenarioless main-spec requirement is CLI
        // valid:false (Zod .min(1) schema error), so it stays an ERROR — demoting would make the
        // plugin laxer than the CLI.
        myFixture.addFileToProject("openspec/specs/no-scenario/spec.md",
                "# No Scenario Spec\n\n### Requirement: Needs A Scenario\n\nThe system SHALL work.\n");
        refreshVfs();

        ValidationResult result = validator.validateSpecs();
        assertTrue("spec-scenario-required must remain an ERROR",
                result.issues().stream().anyMatch(i ->
                        "spec-scenario-required".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.ERROR &&
                        i.filePath().contains("no-scenario")));
        assertFalse("a scenarioless requirement must fail the verdict", result.passed());
    }

    // ---------------------------------------------------------------
    // Config Validation
    // ---------------------------------------------------------------

    public void testValidConfigProducesNoErrors() {
        ValidationResult result = validator.validateConfig();
        List<ValidationIssue> errors = result.issues().stream()
                .filter(i -> i.severity() == ValidationIssue.Severity.ERROR)
                .toList();
        assertTrue("Valid config should produce no errors, got: " + errors, errors.isEmpty());
        assertTrue(result.passed());
    }

    public void testMissingSchemaIsANonFailingInfoNudge() throws Exception {
        // `openspec validate` is clean on a missing schema (upstream defaults to spec-driven), so a
        // WARNING squiggle would be stricter than the CLI — the plugin emits an INFO advisory only.
        overwriteFile("openspec/config.yaml",
                "version: \"1.2.0\"\n\nprofile:\n  name: Test\n");

        ValidationResult result = validator.validateConfig();
        assertTrue("config-schema-required must be an INFO advisory, not a WARNING/ERROR",
                result.issues().stream().anyMatch(i ->
                        "config-schema-required".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.INFO));
        assertTrue("a missing schema must NOT fail the verdict", result.passed());
        // Config validation produces no ERRORs at all — it never fails the verdict.
        assertTrue("config validation must emit no ERROR-severity issues",
                result.issues().stream().noneMatch(i -> i.severity() == ValidationIssue.Severity.ERROR));
        // The removed required-fields loop must not re-emit a duplicate config-field-required.
        assertTrue("config-field-required must no longer be emitted",
                result.issues().stream().noneMatch(i -> "config-field-required".equals(i.rule())));
    }

    public void testUnknownSchemaWithCliUnavailableIsClean() throws Exception {
        // Guard: with the CLI unavailable (the default test env), the known-set collapses to the
        // built-in floor, so the plugin can't see project-local forks. A non-built-in schema must NOT
        // warn — the real CLI never rejects a schema name, and a legit fork would otherwise falsely red.
        overwriteFile("openspec/config.yaml",
                "schema: my-team-flow\nversion: \"1.2.0\"\n\nprofile:\n  name: Test\n");

        ValidationResult result = validator.validateConfig();
        assertTrue("config-schema-invalid must NOT fire when the known-set is non-authoritative (CLI down)",
                result.issues().stream().noneMatch(i -> "config-schema-invalid".equals(i.rule())));
    }

    public void testUnknownSchemaWithCliAvailableStillWarns() throws Exception {
        // With an AUTHORITATIVE known-set (CLI available + schema-supported), a genuine unknown schema
        // still warns — the guard relaxes CLI-down false-positives without masking real typos.
        SchemaService authoritative = mock(SchemaService.class);
        when(authoritative.isSchemaSupported()).thenReturn(true);
        when(authoritative.getKnownSchemaNames()).thenReturn(Set.of("spec-driven"));
        ServiceContainerUtil.replaceService(getProject(), SchemaService.class, authoritative, getTestRootDisposable());

        overwriteFile("openspec/config.yaml",
                "schema: bogus-schema\nversion: \"1.2.0\"\n\nprofile:\n  name: Test\n");

        ValidationResult result = validator.validateConfig();
        assertTrue("config-schema-invalid must still fire on an unknown schema when the known-set is authoritative",
                result.issues().stream().anyMatch(i ->
                        "config-schema-invalid".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.WARNING));
    }

    public void testMissingProfileIsClean() throws Exception {
        // Upstream OpenSpec's Zod schema doesn't define profile; it's a plugin-internal
        // display field. Absence is no longer a validation issue. (Was config-profile-recommended.)
        overwriteFile("openspec/config.yaml",
                "schema: spec-driven\nversion: \"1.2.0\"\n");

        ValidationResult result = validator.validateConfig();
        assertTrue("profile absence should produce no issue",
                result.issues().stream().noneMatch(i ->
                        "config-profile-recommended".equals(i.rule())));
    }

    public void testMissingVersionIsClean() throws Exception {
        // The version: field is plugin-internal — upstream's Zod schema strips it.
        // Absence is no longer required-field or recommended-field. (Was config-version-required
        // WARNING + config-field-required ERROR.)
        overwriteFile("openspec/config.yaml",
                "schema: spec-driven\n\nprofile:\n  name: Test\n");

        ValidationResult result = validator.validateConfig();
        assertTrue("version absence should produce no version-required or field-required issue",
                result.issues().stream().noneMatch(i ->
                        "config-version-required".equals(i.rule())
                                || "config-field-required".equals(i.rule())));
        assertTrue("version absence should not be an ERROR (only schema is required)",
                result.passed());
    }

    public void testMissingConfigYamlIsClean() throws Exception {
        // Upstream OpenSpec treats openspec/config.yaml as optional — its readProjectConfig
        // returns null with the comment "No config is OK". The plugin matches that contract.
        // (Was config-missing ERROR + short-circuit.)
        deleteFile("openspec/config.yaml");

        ValidationResult result = validator.validateConfig();
        assertTrue("config absence should produce no config-missing issue",
                result.issues().stream().noneMatch(i ->
                        "config-missing".equals(i.rule())));
        assertTrue("config absence should not fail validation", result.passed());
    }

    public void testUnknownVersionIsClean() throws Exception {
        // `version:` is plugin-internal; the CLI strips it and validates clean for ANY value. The
        // plugin no longer warns on an unrecognized version — doing so was stricter than the CLI.
        overwriteFile("openspec/config.yaml",
                "schema: spec-driven\nversion: \"9.9.9\"\n\nprofile:\n  name: Test\n");

        ValidationResult result = validator.validateConfig();
        assertTrue("config-version-unknown must no longer be emitted (the CLI never reads version:)",
                result.issues().stream().noneMatch(i -> "config-version-unknown".equals(i.rule())));
        assertTrue("an unrecognized version must not fail the verdict", result.passed());
    }

    public void testValidVersionProducesNoVersionIssues() throws Exception {
        overwriteFile("openspec/config.yaml",
                "schema: spec-driven\nversion: \"1.2.0\"\n\nprofile:\n  name: Test\n");

        ValidationResult result = validator.validateConfig();
        assertTrue("Valid version should produce no version issues",
                result.issues().stream().noneMatch(i ->
                        "config-version-required".equals(i.rule()) ||
                        "config-version-unknown".equals(i.rule()) ||
                        "config-field-required".equals(i.rule())));
    }

    // ---------------------------------------------------------------
    // Change Validation
    // ---------------------------------------------------------------

    public void testValidChangeProducesNoProposalError() {
        // The test-change fixture already has proposal.md
        ValidationResult result = validator.validateChanges();
        List<ValidationIssue> proposalErrors = result.issues().stream()
                .filter(i -> "change-proposal-required".equals(i.rule()))
                .toList();
        assertTrue("Valid change should not have proposal-required error, got: " + proposalErrors,
                proposalErrors.isEmpty());
    }

    public void testMissingProposalTriggersError() {
        // Create a change without proposal.md (only .openspec.yaml)
        myFixture.addFileToProject("openspec/changes/bad-change/.openspec.yaml",
                "schema: spec-driven\nstatus: proposed\n");
        myFixture.addFileToProject("openspec/changes/bad-change/design.md",
                "## Design\n\nSome design.\n");
        refreshVfs();

        ValidationResult result = validator.validateChanges();
        assertTrue("Should have change-proposal-required issue",
                result.issues().stream().anyMatch(i ->
                        "change-proposal-required".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.ERROR &&
                        i.message().contains("bad-change")));
    }

    public void testMissingArtifactTriggersWarning() {
        // test-change has proposal but no design.md or tasks.md. change-artifact-missing is ALWAYS a
        // non-failing WARNING (a plugin-invented lint the CLI never checks) — never escalated to ERROR
        // by any setting. A per-run strict validation fails the verdict on it via the strict
        // warnings-count rule (OpenSpecValidateAction), not by re-severity-ing it here.
        ValidationResult result = validator.validateChanges();
        assertTrue("change-artifact-missing must be a non-failing WARNING (never ERROR)",
                result.issues().stream().anyMatch(i ->
                        "change-artifact-missing".equals(i.rule())
                                && i.severity() == ValidationIssue.Severity.WARNING));
        assertTrue("change-artifact-missing must never be emitted as an ERROR",
                result.issues().stream().noneMatch(i ->
                        "change-artifact-missing".equals(i.rule())
                                && i.severity() == ValidationIssue.Severity.ERROR));
    }

    public void testChangeSchemaIncompatibleGuardedWhenCliUnavailable() throws Exception {
        // Guard: with the CLI unavailable (default test env) the known-set collapses to the built-in
        // floor, so a change schema the plugin can't verify must NOT warn — the real CLI never rejects
        // a schema name, and a legit fork would otherwise falsely red.
        overwriteFile("openspec/config.yaml",
                "schema: spec-driven\nversion: \"1.0.0\"\n\nprofile:\n  name: Test\n");
        myFixture.addFileToProject("openspec/changes/incompat-change/.openspec.yaml",
                "schema: tdd\n");
        myFixture.addFileToProject("openspec/changes/incompat-change/proposal.md",
                "## Why\n\nTest\n");
        refreshVfs();

        ValidationResult result = validator.validateChanges();
        assertTrue("change-schema-incompatible must NOT fire when the known-set is non-authoritative",
                result.issues().stream().noneMatch(i -> "change-schema-incompatible".equals(i.rule())));
    }

    public void testChangeSchemaIncompatibleStillWarnsWhenCliAuthoritative() throws Exception {
        SchemaService authoritative = mock(SchemaService.class);
        when(authoritative.isSchemaSupported()).thenReturn(true);
        when(authoritative.getKnownSchemaNames()).thenReturn(Set.of("spec-driven"));
        ServiceContainerUtil.replaceService(getProject(), SchemaService.class, authoritative, getTestRootDisposable());

        overwriteFile("openspec/config.yaml",
                "schema: spec-driven\nversion: \"1.0.0\"\n\nprofile:\n  name: Test\n");
        myFixture.addFileToProject("openspec/changes/incompat-change/.openspec.yaml",
                "schema: tdd\n");
        myFixture.addFileToProject("openspec/changes/incompat-change/proposal.md",
                "## Why\n\nTest\n");
        refreshVfs();

        ValidationResult result = validator.validateChanges();
        assertTrue("change-schema-incompatible must still fire on an unknown change schema when authoritative",
                result.issues().stream().anyMatch(i ->
                        "change-schema-incompatible".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.WARNING &&
                        i.message().contains("incompat-change")));
    }

    public void testDeltaSpecWithoutSectionsTriggersWarning() {
        // Delta spec validation uses LocalFileSystem which doesn't work in temp VFS.
        // This test verifies the validator handles it gracefully (no crash).
        // The actual delta spec validation is covered by the regex pattern test below.
        myFixture.addFileToProject("openspec/changes/delta-test/.openspec.yaml",
                "schema: spec-driven\nstatus: proposed\n");
        myFixture.addFileToProject("openspec/changes/delta-test/proposal.md",
                "## Why\n\nTest\n");
        refreshVfs();

        // Should not throw — gracefully handles missing LocalFileSystem paths
        ValidationResult result = validator.validateChanges();
        assertNotNull(result);
    }

    // ---------------------------------------------------------------
    // ---------------------------------------------------------------
    // CLI 1.6 parity semantics (fence masking, SHALL/MUST-only, INFO tier)
    // ---------------------------------------------------------------

    public void testShouldOnlyRequirementTriggersError() {
        // SHOULD/MAY never satisfied `openspec validate` on any generation.
        myFixture.addFileToProject("openspec/specs/should-kw/spec.md",
                "# Should Spec\n\n### Requirement: Soft wording\n\nThe system SHOULD work and MAY retry.\n\n#### Scenario: T\n- **WHEN** x\n- **THEN** y\n");
        refreshVfs();

        assertTrue("SHOULD-only requirement must be flagged (CLI accepts only SHALL/MUST)",
                validator.validateSpecs().issues().stream().anyMatch(i ->
                        "spec-rfc-keywords".equals(i.rule()) && i.filePath().contains("should-kw")));
    }

    public void testKeywordOnlyInsideFenceTriggersError() {
        myFixture.addFileToProject("openspec/specs/fenced-kw/spec.md",
                "# Fenced Spec\n\n### Requirement: Fenced keyword\n\nBody without the magic word.\n\n```\nThe system SHALL work.\n```\n\n#### Scenario: T\n- **WHEN** x\n- **THEN** y\n");
        refreshVfs();

        assertTrue("keyword only inside a code fence must not satisfy the check (1.6 fence masking)",
                validator.validateSpecs().issues().stream().anyMatch(i ->
                        "spec-rfc-keywords".equals(i.rule()) && i.filePath().contains("fenced-kw")));
    }

    public void testScenarioOnlyInsideFenceTriggersError() {
        myFixture.addFileToProject("openspec/specs/fenced-scen/spec.md",
                "# Fenced Scenario Spec\n\n### Requirement: Fenced scenario\n\nThe system SHALL work.\n\n```\n#### Scenario: only an example\n- **WHEN** x\n- **THEN** y\n```\n");
        refreshVfs();

        assertTrue("scenario only inside a code fence must not count (1.6 fence-aware counting)",
                validator.validateSpecs().issues().stream().anyMatch(i ->
                        "spec-scenario-required".equals(i.rule()) && i.filePath().contains("fenced-scen")));
    }

    public void testSkippedDeltaHeaderEmitsInfoWithoutFlippingVerdict() {
        myFixture.addFileToProject("openspec/changes/info-change/proposal.md", "## Why\n\nBecause.\n");
        myFixture.addFileToProject("openspec/changes/info-change/specs/demo/spec.md",
                "## ADDED Requirements\n\n### Requirement: Real one\nThe system SHALL work.\n\n#### Scenario: T\n- **WHEN** x\n- **THEN** y\n\n### Implementation notes\n\nProse the parser skips.\n");
        refreshVfs();

        ValidationResult result = validator.validateChanges();
        ValidationIssue info = result.issues().stream()
                .filter(i -> "delta-skipped-header".equals(i.rule())
                        && i.filePath().contains("info-change"))
                .findFirst().orElse(null);
        assertNotNull("non-canonical level-3 header in ADDED must emit the INFO hint", info);
        assertEquals(ValidationIssue.Severity.INFO, info.severity());
        assertTrue("INFO message names the skipped header",
                info.message().contains("Implementation notes"));
        assertTrue("INFO anchors to the header line", info.line() > 1);
        assertTrue("INFO must never flip the verdict",
                result.issues().stream()
                        .filter(i -> i.filePath().contains("info-change"))
                        .noneMatch(i -> i.severity() == ValidationIssue.Severity.ERROR));
    }

    public void testNamelessRequirementHeaderEmitsNamingHint() {
        myFixture.addFileToProject("openspec/changes/nameless-change/proposal.md", "## Why\n\nBecause.\n");
        myFixture.addFileToProject("openspec/changes/nameless-change/specs/demo/spec.md",
                "## MODIFIED Requirements\n\n### Requirement:\nThe system SHALL work.\n\n#### Scenario: T\n- **WHEN** x\n- **THEN** y\n");
        refreshVfs();

        assertTrue("nameless '### Requirement:' must emit the add-a-name INFO variant",
                validator.validateChanges().issues().stream().anyMatch(i ->
                        "delta-skipped-header".equals(i.rule())
                                && i.severity() == ValidationIssue.Severity.INFO
                                && i.message().contains("missing a requirement name")));
    }

    // Helpers
    // ---------------------------------------------------------------

    private void refreshVfs() {
        VirtualFile root = myFixture.findFileInTempDir("openspec");
        if (root != null) {
            VfsUtil.markDirtyAndRefresh(false, true, true, root);
        }
    }

    private void overwriteFile(String relativePath, String content) throws Exception {
        VirtualFile file = myFixture.findFileInTempDir(relativePath);
        assertNotNull("File should exist: " + relativePath, file);
        WriteAction.run(() ->
                file.setBinaryContent(content.getBytes(StandardCharsets.UTF_8)));
        refreshVfs();
    }

    private void deleteFile(String relativePath) throws Exception {
        VirtualFile file = myFixture.findFileInTempDir(relativePath);
        assertNotNull("File should exist before delete: " + relativePath, file);
        WriteAction.run(() -> file.delete(this));
        refreshVfs();
    }

    // ---------------------------------------------------------------
    // CLI-authoritative merge (OpenSpecValidateAction.combineWithCli)
    // ---------------------------------------------------------------

    /** A clean CLI verdict, as if `openspec validate` reported every spec/change valid. */
    private static ValidationResult cleanCli() {
        return new ValidationResult(true, List.of(), "cli");
    }

    public void testCliAuthoritativeCleanCliIsNotOverriddenByBuiltInSpecErrors() throws Exception {
        // Anti-vacuous: the project has a genuinely broken spec (no `### Requirement:` block →
        // spec-requirement-required ERROR in the built-in validator) but a clean config. With the CLI
        // reporting specs valid, the whole-project verdict must PASS — the built-in validator's own
        // spec ERROR must not leak in. This fails if the old `merge(validateAll(), cli)` (which runs
        // the full built-in over specs) is restored.
        overwriteFile("openspec/config.yaml", "schema: spec-driven\n");
        myFixture.addFileToProject("openspec/specs/broken/spec.md",
                "# Broken\n\nNo requirements section at all.\n");
        refreshVfs();

        ValidationResult result = com.johnnyblabs.openspec.actions.OpenSpecValidateAction
                .combineWithCli(validator, com.johnnyblabs.openspec.actions.ValidateTarget.wholeProject(), cleanCli());

        assertTrue("clean CLI + clean config must pass despite a built-in spec ERROR", result.passed());
        assertTrue("the built-in's spec-requirement-required must NOT enter the verdict",
                result.issues().stream().noneMatch(i -> "spec-requirement-required".equals(i.rule())));
    }

    public void testCliAuthoritativeSurfacesConfigWarningsWithoutFailingWhenCliClean() throws Exception {
        // The built-in validator still OWNS config.yaml when the CLI is present — but config checks are
        // non-failing (the CLI never fails on config). A missing schema surfaces as an INFO advisory
        // alongside a clean CLI verdict; it does not red the whole-project result.
        overwriteFile("openspec/config.yaml", "version: \"1.2.0\"\n");  // no schema
        refreshVfs();

        ValidationResult result = com.johnnyblabs.openspec.actions.OpenSpecValidateAction
                .combineWithCli(validator, com.johnnyblabs.openspec.actions.ValidateTarget.wholeProject(), cleanCli());

        assertTrue("a config advisory must NOT fail a clean-CLI verdict", result.passed());
        assertTrue("the config-schema-required INFO advisory is still surfaced for display",
                result.issues().stream().anyMatch(i ->
                        "config-schema-required".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.INFO));
    }

    public void testCliAuthoritativeSingleItemDefersEntirelyToCli() throws Exception {
        // For a single spec/change target there is no config component: a broken config must not
        // affect a single-item verdict that defers to the CLI.
        overwriteFile("openspec/config.yaml", "version: \"1.2.0\"\n");  // no schema — broken
        refreshVfs();

        ValidationResult result = com.johnnyblabs.openspec.actions.OpenSpecValidateAction
                .combineWithCli(validator, com.johnnyblabs.openspec.actions.ValidateTarget.spec("anything"), cleanCli());

        assertTrue("single-item target defers to the CLI; broken config is not merged in", result.passed());
        assertTrue("no config issue is merged for a single-item target",
                result.issues().stream().noneMatch(i -> i.rule() != null && i.rule().startsWith("config-")));
    }
}
