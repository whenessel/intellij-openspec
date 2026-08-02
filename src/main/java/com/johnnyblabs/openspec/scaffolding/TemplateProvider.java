package com.johnnyblabs.openspec.scaffolding;

import com.johnnyblabs.openspec.version.VersionSupport;

import java.time.LocalDate;

public final class TemplateProvider {

    private TemplateProvider() {
    }

    public static String proposalTemplate(String changeName, String why, String whatChanges) {
        String whyContent = (why == null || why.isBlank())
                ? "<!-- Explain the motivation for this change. What problem does this solve? Why now? -->"
                : why;
        String whatChangesContent = (whatChanges == null || whatChanges.isBlank())
                ? "<!-- Describe what will change. Be specific about new capabilities, modifications, or removals. -->"
                : whatChanges;

        return """
                ## Why

                %s

                ## What Changes

                %s

                ## Capabilities

                ### New Capabilities
                <!-- Capabilities being introduced. Use kebab-case identifiers (e.g., user-auth, data-export). Each creates specs/<name>/spec.md -->

                ### Modified Capabilities
                <!-- Existing capabilities whose REQUIREMENTS are changing. Use existing spec names from openspec/specs/. -->

                ## Impact

                <!-- Affected code, APIs, dependencies, systems -->
                """.formatted(whyContent, whatChangesContent);
    }

    public static String designTemplate(String changeName) {
        return """
                # Design: %s

                ## Approach

                <!-- Describe the technical approach -->

                ## Components Affected

                <!-- List affected components -->

                ## Trade-offs

                <!-- Document any trade-offs -->
                """.formatted(changeName);
    }

    public static String tasksTemplate(String changeName) {
        return """
                # Tasks: %s

                ## Implementation Tasks

                - [ ] Task 1
                - [ ] Task 2
                - [ ] Task 3

                ## Testing Tasks

                - [ ] Write unit tests
                - [ ] Integration testing
                """.formatted(changeName);
    }

    public static String deltaSpecTemplate(String domain) {
        return """
                # Delta Spec: %s

                ## ADDED

                <!-- New requirements -->

                ## MODIFIED

                <!-- Changed requirements -->

                ## REMOVED

                <!-- Removed requirements -->

                ## RENAMED Requirements

                <!-- Renamed requirements as FROM:/TO: pairs. Each entry needs two lines: one starting "- FROM:" with the old requirement name, then one starting "- TO:" with the new name. Delete this comment when adding entries. -->
                """.formatted(domain);
    }

    public static String openspecYamlTemplate(String status) {
        return """
                schema: openspec-change
                status: %s
                created: "%s"
                """.formatted(status, LocalDate.now());
    }

    /**
     * A fresh {@code openspec/config.yaml}, matching upstream {@code openspec init}: a single
     * {@code schema:} line and nothing else. The plugin deliberately does NOT write the plugin-invented
     * {@code version:}/{@code profile:} fields (upstream's Zod schema ignores them; they are cosmetic
     * display-only) or empty {@code context:}/{@code rules:} — that pollutes a file upstream owns.
     */
    public static String configYamlTemplate(String schema) {
        return "schema: %s\n".formatted(schema);
    }

}
