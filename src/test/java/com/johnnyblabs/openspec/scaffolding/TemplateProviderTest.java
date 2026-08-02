package com.johnnyblabs.openspec.scaffolding;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that templates produce content conforming to OpenSpec 1.2.0 structure.
 * These tests ensure that built-in scaffolding creates artifacts the CLI and
 * validator will accept.
 */
class TemplateProviderTest {

    // --- Proposal template ---

    @Test
    void proposalTemplate_hasWhySection() {
        String result = TemplateProvider.proposalTemplate("my-feature", "Add feature X", null);
        assertTrue(result.contains("## Why"));
        assertTrue(result.contains("Add feature X"),
                "Why text must appear in the proposal body");
    }

    @Test
    void proposalTemplate_hasWhatChangesSection() {
        String result = TemplateProvider.proposalTemplate("my-feature", null, "- Change A\n- Change B");
        assertTrue(result.contains("## What Changes"));
        assertTrue(result.contains("- Change A"),
                "What Changes text must appear in the proposal body");
    }

    @Test
    void proposalTemplate_hasCapabilitiesAndImpact() {
        String result = TemplateProvider.proposalTemplate("my-feature", "why", "what");
        assertTrue(result.contains("## Capabilities"));
        assertTrue(result.contains("### New Capabilities"));
        assertTrue(result.contains("### Modified Capabilities"));
        assertTrue(result.contains("## Impact"));
    }

    @Test
    void proposalTemplate_usesPlaceholdersWhenFieldsBlank() {
        String result = TemplateProvider.proposalTemplate("my-feature", "", "");
        assertTrue(result.contains("<!-- Explain the motivation"),
                "Blank why should produce placeholder comment");
        assertTrue(result.contains("<!-- Describe what will change"),
                "Blank whatChanges should produce placeholder comment");
    }

    @Test
    void proposalTemplate_substitutesSpecialCharacters() {
        String result = TemplateProvider.proposalTemplate("add-auth/login", "Login & OAuth 2.0", null);
        assertTrue(result.contains("Login & OAuth 2.0"));
    }

    // --- Design template ---

    @Test
    void designTemplate_hasMarkdownTitle() {
        String result = TemplateProvider.designTemplate("my-feature");
        assertTrue(result.contains("# Design: my-feature"));
    }

    @Test
    void designTemplate_hasSections() {
        String result = TemplateProvider.designTemplate("my-feature");
        assertTrue(result.contains("## Approach"));
        assertTrue(result.contains("## Components Affected"));
        assertTrue(result.contains("## Trade-offs"));
    }

    // --- Tasks template ---

    @Test
    void tasksTemplate_hasMarkdownTitle() {
        String result = TemplateProvider.tasksTemplate("my-feature");
        assertTrue(result.contains("# Tasks: my-feature"));
    }

    @Test
    void tasksTemplate_hasImplementationAndTestingSections() {
        String result = TemplateProvider.tasksTemplate("my-feature");
        assertTrue(result.contains("## Implementation Tasks"));
        assertTrue(result.contains("## Testing Tasks"));
    }

    @Test
    void tasksTemplate_hasCheckboxItems() {
        String result = TemplateProvider.tasksTemplate("my-feature");
        assertTrue(result.contains("- [ ] "), "Tasks must have checkbox items");
    }

    // --- Delta spec template ---

    @Test
    void deltaSpecTemplate_hasMarkdownTitle() {
        String result = TemplateProvider.deltaSpecTemplate("authentication");
        assertTrue(result.contains("# Delta Spec: authentication"));
    }

    @Test
    void deltaSpecTemplate_hasAllFourSections() {
        String result = TemplateProvider.deltaSpecTemplate("auth");
        assertTrue(result.contains("## ADDED"),
                "Delta spec must have ## ADDED section");
        assertTrue(result.contains("## MODIFIED"),
                "Delta spec must have ## MODIFIED section");
        assertTrue(result.contains("## REMOVED"),
                "Delta spec must have ## REMOVED section");
        assertTrue(result.contains("## RENAMED Requirements"),
                "Delta spec must emit ## RENAMED Requirements with the full suffix — the validator's "
                        + "structural regex (^## ... Requirements) only engages when the suffix is present");
    }

    // --- .openspec.yaml template ---

    @Test
    void openspecYamlTemplate_isValidYaml() {
        String result = TemplateProvider.openspecYamlTemplate("proposed");
        Yaml yaml = new Yaml();
        Map<String, Object> parsed = yaml.load(result);
        assertNotNull(parsed, "Must produce valid YAML");
    }

    @Test
    void openspecYamlTemplate_hasRequiredFields() {
        String result = TemplateProvider.openspecYamlTemplate("proposed");
        Yaml yaml = new Yaml();
        Map<String, Object> parsed = yaml.load(result);

        assertEquals("openspec-change", parsed.get("schema"),
                "schema must be 'openspec-change'");
        assertEquals("proposed", parsed.get("status"));
        assertNotNull(parsed.get("created"), "created date must be present");
    }

    @Test
    void openspecYamlTemplate_usesTodayDate() {
        String result = TemplateProvider.openspecYamlTemplate("proposed");
        assertTrue(result.contains(LocalDate.now().toString()),
                "created date must be today's date");
    }

    @Test
    void openspecYamlTemplate_statusReflectsParameter() {
        String proposed = TemplateProvider.openspecYamlTemplate("proposed");
        assertTrue(proposed.contains("status: proposed"));

        String applied = TemplateProvider.openspecYamlTemplate("applied");
        assertTrue(applied.contains("status: applied"));
    }

    // --- config.yaml template ---

    @Test
    void configYamlTemplate_isValidYaml() {
        String result = TemplateProvider.configYamlTemplate("spec-driven");
        Yaml yaml = new Yaml();
        Map<String, Object> parsed = yaml.load(result);
        assertNotNull(parsed, "Must produce valid YAML");
    }

    @Test
    void configYamlTemplate_isSchemaOnly() {
        // Matches upstream `openspec init` — a single schema: line and nothing else. The plugin no
        // longer writes the plugin-invented version:/profile: or empty context:/rules: into a fresh
        // config.yaml (they were display-only fields upstream ignores).
        String result = TemplateProvider.configYamlTemplate("spec-driven");
        Yaml yaml = new Yaml();
        Map<String, Object> parsed = yaml.load(result);

        assertEquals("spec-driven", parsed.get("schema"));
        assertEquals(1, parsed.size(), "schema-only config has exactly one key");
        assertFalse(parsed.containsKey("version"), "no plugin-invented version: is written");
        assertFalse(parsed.containsKey("profile"), "no plugin-invented profile: is written");
        assertFalse(parsed.containsKey("context"), "no empty context: is written");
        assertFalse(parsed.containsKey("rules"), "no empty rules: is written");
    }

    @Test
    void configYamlTemplate_schemaParameterApplied() {
        String tdd = TemplateProvider.configYamlTemplate("tdd");
        Yaml yaml = new Yaml();
        Map<String, Object> parsed = yaml.load(tdd);
        assertEquals("tdd", parsed.get("schema"));
    }
}
