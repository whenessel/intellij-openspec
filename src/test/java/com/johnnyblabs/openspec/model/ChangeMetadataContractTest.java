package com.johnnyblabs.openspec.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link ChangeMetadataParser#parse(String)} against REAL {@code .openspec.yaml}
 * files from the 1.7.0 CLI — never hand-authored shapes. Fixtures live under
 * {@code fixtures/cli/1.7.0/change-metadata/} (see that dir's manifest for the capture recipe and the
 * {@code openspec validate --strict} acceptance oracle that proves the derived files are reader-valid).
 *
 * <p>The model/parse layer emits no validation issues — these tests assert only the parsed field values
 * and the ok-vs-malformed classification. Whether a non-null-metadata file then trips a downstream lint
 * is the validator's concern, exercised elsewhere.
 */
class ChangeMetadataContractTest {

    private static String fixture(String name) {
        String path = "/fixtures/cli/1.7.0/change-metadata/" + name;
        try (InputStream is = ChangeMetadataContractTest.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fixture: " + path, e);
        }
    }

    @Test
    void parsesRealNewChangeGoalFile() {
        // The exact bug repro: `openspec new change x --goal` writes this; the old strict parse threw.
        ChangeMetadataParser.ParseResult r = ChangeMetadataParser.parse(fixture("new-change-goal.openspec.yaml"));
        assertFalse(r.isMalformed());
        ChangeMetadata m = r.metadata();
        assertNotNull(m);
        assertEquals("spec-driven", m.getSchema());
        // The single most important assertion: `created:` is an unquoted date scalar that SnakeYAML
        // resolves to a Date — a naive String-only or local-tz path would drop it or day-shift it.
        assertEquals("2026-08-05", m.getCreated());
        assertEquals("Honor upstream change metadata so a CLI-valid file never red-flags", m.getGoal());
        assertNull(m.getSkipSpecs());
    }

    @Test
    void parsesBaselineSchemaCreatedOnly() {
        ChangeMetadata m = ChangeMetadataParser.parse(fixture("baseline-schema-created.openspec.yaml")).metadata();
        assertNotNull(m);
        assertEquals("spec-driven", m.getSchema());
        assertEquals("2026-08-05", m.getCreated());
        assertNull(m.getGoal());
        assertNull(m.getSkipSpecs());
        assertTrue(m.getAffectedAreas().isEmpty());
        assertTrue(m.getInitiative().isEmpty());
    }

    @Test
    void parsesRichAllReadAcceptedFields() {
        ChangeMetadata m = ChangeMetadataParser.parse(fixture("rich-all-fields.openspec.yaml")).metadata();
        assertNotNull(m);
        assertEquals("2026-08-05", m.getCreated());
        // affected_areas is a LIST (catches a naive String model)
        assertEquals(List.of("services", "model"), m.getAffectedAreas());
        // skip_specs is a boolean (catches a "true" String model)
        assertTrue(m.isSkipSpecs());
        assertEquals(Boolean.TRUE, m.getSkipSpecs());
        // initiative is a nested OBJECT (catches a typed-String model that would have thrown)
        assertEquals("my-store", m.getInitiative().get("store"));
        assertEquals("my-initiative", m.getInitiative().get("id"));
    }

    @Test
    void parsesSkipSpecsOnlyChange() {
        ChangeMetadata m = ChangeMetadataParser.parse(fixture("skip-specs-only.openspec.yaml")).metadata();
        assertNotNull(m);
        assertTrue(m.isSkipSpecs());
        assertNull(m.getGoal());
        assertTrue(m.getAffectedAreas().isEmpty());
    }

    @Test
    void parsesLegacyStatusFileToleratingIgnoredStatusKey() {
        // Backward-compat anchor: a legacy plugin-scaffolded file that still carries the retired
        // `status:` key parses without error — status is now just an ignored unknown key (strip
        // contract), which is exactly why retiring the reader was safe.
        ChangeMetadataParser.ParseResult r = ChangeMetadataParser.parse(fixture("legacy-status-proposed.openspec.yaml"));
        assertFalse(r.isMalformed());
        ChangeMetadata m = r.metadata();
        assertNotNull(m);
        assertEquals("openspec-change", m.getSchema());
        assertEquals("2026-03-18", m.getCreated()); // quoted in the file -> String, passed through
    }

    @Test
    void classifiesGenuinelyMalformedYamlAsMalformed() {
        ChangeMetadataParser.ParseResult r = ChangeMetadataParser.parse(fixture("malformed.openspec.yaml"));
        assertTrue(r.isMalformed(), "a genuine YAML syntax error must still be flagged");
        assertNull(r.metadata());
        assertNotNull(r.problem());
    }
}
