package com.johnnyblabs.openspec.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigProfileDetailTest {

    @Nested
    class JsonParsing {

        @Test
        void parsesValidJson() {
            String json = """
                    {
                      "name": "spec-driven",
                      "description": "Full spec-driven workflow",
                      "workflows": ["propose", "design", "specs", "tasks"]
                    }
                    """;
            ConfigProfileDetail detail = ConfigProfileDetail.fromJson(json);

            assertEquals("spec-driven", detail.getName());
            assertEquals("Full spec-driven workflow", detail.getDescription());
            assertEquals(List.of("propose", "design", "specs", "tasks"), detail.getWorkflows());
        }

        @Test
        void parsesJsonWithEmptyWorkflows() {
            String json = """
                    {
                      "name": "minimal",
                      "description": "Minimal profile",
                      "workflows": []
                    }
                    """;
            ConfigProfileDetail detail = ConfigProfileDetail.fromJson(json);

            assertEquals("minimal", detail.getName());
            assertEquals("Minimal profile", detail.getDescription());
            assertTrue(detail.getWorkflows().isEmpty());
        }

        @Test
        void parsesJsonWithMissingFields() {
            String json = """
                    {
                      "name": "partial"
                    }
                    """;
            ConfigProfileDetail detail = ConfigProfileDetail.fromJson(json);

            assertEquals("partial", detail.getName());
            assertEquals("", detail.getDescription());
            assertTrue(detail.getWorkflows().isEmpty());
        }

        @Test
        void handlesEmptyString() {
            ConfigProfileDetail detail = ConfigProfileDetail.fromJson("");
            assertEquals("", detail.getName());
            assertEquals("", detail.getDescription());
            assertTrue(detail.getWorkflows().isEmpty());
        }

        @Test
        void handlesNull() {
            ConfigProfileDetail detail = ConfigProfileDetail.fromJson(null);
            assertEquals("", detail.getName());
            assertEquals("", detail.getDescription());
            assertTrue(detail.getWorkflows().isEmpty());
        }

        @Test
        void handlesMalformedJson() {
            ConfigProfileDetail detail = ConfigProfileDetail.fromJson("{not valid json");
            assertEquals("", detail.getName());
            assertEquals("", detail.getDescription());
            assertTrue(detail.getWorkflows().isEmpty());
        }

        @Test
        void handlesJsonWithExtraFields() {
            String json = """
                    {
                      "name": "tdd",
                      "description": "TDD workflow",
                      "workflows": ["specs", "tasks"],
                      "extraField": "ignored"
                    }
                    """;
            ConfigProfileDetail detail = ConfigProfileDetail.fromJson(json);

            assertEquals("tdd", detail.getName());
            assertEquals("TDD workflow", detail.getDescription());
            assertEquals(List.of("specs", "tasks"), detail.getWorkflows());
        }
    }

    @Nested
    class Fallback {

        @Test
        void fallbackWithName() {
            ConfigProfileDetail detail = ConfigProfileDetail.fallback("spec-driven");

            assertEquals("spec-driven", detail.getName());
            assertEquals("", detail.getDescription());
            assertTrue(detail.getWorkflows().isEmpty());
        }

        @Test
        void fallbackWithNull() {
            ConfigProfileDetail detail = ConfigProfileDetail.fallback(null);

            assertEquals("", detail.getName());
            assertEquals("", detail.getDescription());
            assertTrue(detail.getWorkflows().isEmpty());
        }

        @Test
        void fallbackWithEmpty() {
            ConfigProfileDetail detail = ConfigProfileDetail.fallback("");

            assertEquals("", detail.getName());
            assertEquals("", detail.getDescription());
            assertTrue(detail.getWorkflows().isEmpty());
        }
    }

    @Nested
    class Constructor {

        @Test
        void defaultConstructor() {
            ConfigProfileDetail detail = new ConfigProfileDetail();

            assertEquals("", detail.getName());
            assertEquals("", detail.getDescription());
            assertTrue(detail.getWorkflows().isEmpty());
        }

        @Test
        void fullConstructor() {
            ConfigProfileDetail detail = new ConfigProfileDetail(
                    "rapid", "Rapid development", List.of("propose", "tasks"));

            assertEquals("rapid", detail.getName());
            assertEquals("Rapid development", detail.getDescription());
            assertEquals(List.of("propose", "tasks"), detail.getWorkflows());
        }

        @Test
        void fullConstructorNullSafe() {
            ConfigProfileDetail detail = new ConfigProfileDetail(null, null, null);

            assertEquals("", detail.getName());
            assertEquals("", detail.getDescription());
            assertTrue(detail.getWorkflows().isEmpty());
        }
    }

    /**
     * {@code openspec config profile --json} is rejected on the current CLI ("unknown option
     * '--json'"), so the command produces no JSON for {@code fromJson} to parse — the source this
     * parser was written against no longer exists. Documented here and pinned in the fixture manifest.
     *
     * <p>On rejection the Settings panel takes its non-success branch ({@code ConfigProfileDetail
     * .fallback(profileName)}) and never passes the error text to {@code fromJson}, so this does not
     * cover the panel's degraded rendering — re-sourcing that section from {@code config list --json}
     * is a separate change. What it does pin (non-vacuously — Gson would throw without the catch) is
     * that {@code fromJson} itself degrades to an empty detail on non-JSON input rather than
     * propagating an exception.
     */
    @Test
    void configProfileJsonRejectedOnCurrentCli_fromJsonDegradesGracefully() {
        String rejection = fixture("1.7.0/config-profile-json-rejected.txt");
        assertTrue(rejection.contains("unknown option"),
                "the captured output must be the CLI's rejection of --json, not JSON");

        ConfigProfileDetail detail = ConfigProfileDetail.fromJson(rejection);
        assertEquals("", detail.getName());
        assertTrue(detail.getWorkflows().isEmpty());
    }

    private static String fixture(String name) {
        String path = "/fixtures/cli/" + name;
        try (java.io.InputStream is = ConfigProfileDetailTest.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }
}
