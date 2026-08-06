package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.model.SchemaInfo;
import com.johnnyblabs.openspec.util.CliOutputParser;
import com.johnnyblabs.openspec.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test backing the config-validator relaxations with REAL captured {@code openspec} 1.7.0
 * output. Each fixture proves the CLI is clean on a config state the plugin used to warn on — so the
 * plugin removing/guarding that warning is genuinely "no stricter than the CLI," not an assumption.
 *
 * <p>This is the re-capture tripwire: if a future CLI starts rejecting one of these shapes, a test here
 * fails and tells us to re-add the corresponding plugin warning rather than silently drift. Fixtures +
 * capture recipe: {@code fixtures/cli/1.7.0/config-validation/} and the manifest.
 */
class CliConfigToleranceContractTest {

    private static String fixture(String name) {
        String path = "/fixtures/cli/1.7.0/config-validation/" + name;
        try (InputStream is = CliConfigToleranceContractTest.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fixture: " + path, e);
        }
    }

    private static void assertCliCleanOnConfig(String fixtureName, String mustNotMention) {
        ValidationResult r = CliOutputParser.parseJsonOutput(fixture(fixtureName));
        assertTrue(r.passed(), fixtureName + ": the CLI verdict must be clean");
        assertTrue(r.issues().stream().noneMatch(i ->
                        (i.rule() != null && i.rule().toLowerCase().contains(mustNotMention))
                        || (i.message() != null && i.message().toLowerCase().contains(mustNotMention))),
                fixtureName + ": the CLI must emit no issue mentioning '" + mustNotMention + "'");
    }

    @Test
    void cliIsCleanOnUnknownVersionValue() {
        // Backs deleting config-version-unknown: a `version: 9.9.9` config.yaml still validates clean.
        assertCliCleanOnConfig("version-unknown-value.validate.json", "version");
    }

    @Test
    void cliIsCleanOnMissingSchema() {
        // Backs demoting config-schema-required to INFO: no `schema:` still validates clean
        // (upstream defaults to spec-driven).
        assertCliCleanOnConfig("schema-absent.validate.json", "schema");
    }

    @Test
    void cliIsCleanOnCustomForkSchema() {
        // Backs guarding config-schema-invalid: a custom-fork `schema:` value still validates clean.
        assertCliCleanOnConfig("schema-custom-fork.validate.json", "schema");
    }

    @Test
    void schemasListIncludesProjectFork() {
        // Backs the guard's premise: the CLI's `openspec schemas --json` DOES list a project-local fork,
        // so the known-set is authoritative ONLY when the CLI supplies it — offline it collapses to the
        // built-in floor and a legitimate fork would falsely warn.
        List<SchemaInfo> schemas = SchemaService.parseSchemaList(fixture("schemas-list-with-fork.json"));
        assertTrue(schemas.stream().anyMatch(s -> "my-custom-fork".equals(s.name())),
                "the project fork must appear in the CLI's schemas --json");
        assertTrue(schemas.stream().anyMatch(s -> "spec-driven".equals(s.name())),
                "the built-in must also appear");
    }
}
