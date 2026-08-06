package com.johnnyblabs.openspec.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Forward-compat POLICY test (not a CLI-shape claim): starting from a real captured baseline file, a
 * synthetic key the plugin does not model must still parse cleanly. This is the guard against a "fix"
 * that models only today's known keys with a strict constructor — which would re-trigger the parse-error
 * balloon for the next upstream key (a 1.8 field, say). Upstream's schema strips unknown keys; the plugin
 * must mirror that for any key, not just the four it currently reads.
 */
class ChangeMetadataParserToleranceTest {

    private static String baseline() {
        String path = "/fixtures/cli/1.7.0/change-metadata/baseline-schema-created.openspec.yaml";
        try (InputStream is = ChangeMetadataParserToleranceTest.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void toleratesUnknownFutureKey() {
        // Real baseline bytes + a synthetic future key (labeled: a policy probe, not a captured shape).
        String yaml = baseline() + "zzz_future_key: whatever\n";

        ChangeMetadataParser.ParseResult r = ChangeMetadataParser.parse(yaml);

        assertFalse(r.isMalformed(), "an unknown key is not a parse error (upstream strip contract)");
        ChangeMetadata m = r.metadata();
        assertNotNull(m);
        assertEquals("spec-driven", m.getSchema());
        assertEquals("2026-08-05", m.getCreated());
    }

    @Test
    void emptyDocumentParsesToEmptyMetadataWithoutWarning() {
        ChangeMetadataParser.ParseResult r = ChangeMetadataParser.parse("");
        assertFalse(r.isMalformed());
        assertNotNull(r.metadata());
        assertNull(r.metadata().getSchema());
    }

    @Test
    void nonMappingTopLevelIsNotAnError() {
        // A valid-YAML scalar/list at the top level is unusual but not malformed -> empty metadata, no warning.
        ChangeMetadataParser.ParseResult r = ChangeMetadataParser.parse("just a scalar\n");
        assertFalse(r.isMalformed());
        assertNotNull(r.metadata());
        assertNull(r.metadata().getSchema());
    }
}
