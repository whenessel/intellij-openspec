package com.johnnyblabs.openspec.integration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Durable, version-agnostic verdict-stability contract. {@link ValidatorVerdictParityTest} proves the
 * plugin's built-in validator matches the real CLI's verdicts on the parity corpus for the ANCHOR
 * generation (1.6.0, a heavy platform test). This lightweight, platform-free test proves the CLI's
 * <em>own</em> verdicts over that shared corpus did not drift across every OTHER captured generation:
 * it discovers every committed {@code fixtures/cli/<gen>/validate-parity-corpus.json} and asserts each
 * carries the same {@code id → valid} map as the anchor.
 *
 * <p>With both facts — plugin matches the anchor, and the anchor's CLI verdicts equal every captured
 * generation's — plugin/generation parity follows by transitivity, so the heavy platform harness is
 * deliberately NOT cloned per generation. Discovery is the durable part: a future {@code 1.8.0}
 * capture is covered with zero edits here, and any generation that tightens a verdict on the corpus
 * fails this test.
 *
 * <p>Pre-1.6 generations are intentionally absent from the equality set — their CLI validation rules
 * predate this corpus dialect and legitimately verdict it differently (fence-aware scenario counting
 * arrived at 1.4; multi-line requirement-body keyword reading at 1.6), so a frozen "identical map
 * across 1.3–1.7" is impossible, not merely costly. Their {@code validate --json} shape is covered
 * separately by their own era-appropriate fixtures (e.g. {@code fixtures/cli/1.3.0/validate.json}).
 *
 * <p>The same discovery covers the strict twin {@code validate-parity-corpus-strict.json}: each strict
 * map must also equal the non-strict anchor (on this corpus no item is valid-with-only-a-CLI-warning,
 * so strict == default), extending the durable invariant to {@code --strict}.
 */
class ValidatorVerdictVersionStabilityTest {

    private static final String CLI_DIR = "/fixtures/cli";
    private static final String ANCHOR = "1.6.0";
    private static final String NON_STRICT = "validate-parity-corpus.json";
    private static final String STRICT = "validate-parity-corpus-strict.json";
    private static final int CORPUS_SIZE = 13;
    /** Vacuity floor — these two corpora must always be present, or discovery has silently broken. */
    private static final Set<String> FLOOR = Set.of("1.6.0", "1.7.0");

    /** id → valid map parsed from a captured {@code validate --all [--strict] --json} fixture. */
    private static Map<String, Boolean> verdicts(String resourcePath) {
        try (InputStream is = ValidatorVerdictVersionStabilityTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(is, "missing fixture: " + resourcePath);
            JsonObject root = JsonParser.parseString(
                    new String(is.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Boolean> map = new LinkedHashMap<>();
            for (JsonElement el : root.getAsJsonArray("items")) {
                JsonObject item = el.getAsJsonObject();
                map.put(item.get("id").getAsString(), item.get("valid").getAsBoolean());
            }
            return map;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Generations (dir names under {@code fixtures/cli}) that committed a fixture with the exact given
     * name, as a {@code gen -> resource-path} map (sorted for stable messages). The exact-filename
     * match is load-bearing: it keeps {@link #NON_STRICT} and {@link #STRICT} discovery disjoint and
     * excludes unrelated files like {@code 1.3.0/validate.json}. Fails loud if {@code /fixtures/cli}
     * does not resolve (a classpath regression must surface as a failure, never a silent empty glob).
     */
    private static Map<String, String> discover(String fileName) throws Exception {
        var url = ValidatorVerdictVersionStabilityTest.class.getResource(CLI_DIR);
        assertNotNull(url, "test resource " + CLI_DIR + " did not resolve — classpath regression");
        Path cliDir = Path.of(url.toURI());
        Map<String, String> found = new TreeMap<>();
        try (Stream<Path> entries = Files.list(cliDir)) {
            entries.filter(Files::isDirectory).forEach(genDir -> {
                if (Files.exists(genDir.resolve(fileName))) {
                    String gen = genDir.getFileName().toString();
                    found.put(gen, CLI_DIR + "/" + gen + "/" + fileName);
                }
            });
        }
        return found;
    }

    @Test
    void cliVerdictsAreStableAcrossAllCapturedGenerations() throws Exception {
        Map<String, String> corpora = discover(NON_STRICT);

        // Vacuity guards — the invariant must never pass on nothing.
        assertTrue(corpora.containsKey(ANCHOR), "anchor generation " + ANCHOR + " must have a corpus");
        assertTrue(corpora.keySet().containsAll(FLOOR),
                "expected at least the " + FLOOR + " corpora — found " + corpora.keySet());
        assertTrue(corpora.size() >= 2, "need >= 2 captured generations to compare; found " + corpora.keySet());

        Map<String, Boolean> anchor = verdicts(corpora.get(ANCHOR));
        assertEquals(CORPUS_SIZE, anchor.size(), "the anchor parity corpus must have " + CORPUS_SIZE + " items");

        for (Map.Entry<String, String> e : corpora.entrySet()) {
            if (e.getKey().equals(ANCHOR)) continue;
            Map<String, Boolean> gen = verdicts(e.getValue());
            assertEquals(anchor.keySet(), gen.keySet(),
                    "generation " + e.getKey() + " parity corpus has a different item-id set than the "
                            + ANCHOR + " anchor — a corpus drifted; re-capture, don't edit the fixture");
            assertEquals(anchor, gen,
                    "CLI verdicts drifted " + ANCHOR + " -> " + e.getKey() + " over the identical corpus — a "
                            + "tightened verdict would leave the plugin more restrictive than one generation "
                            + "while matching another; investigate the CLI change before shipping");
        }
    }

    @Test
    void strictCliVerdictsAreStableAcrossAllCapturedGenerations() throws Exception {
        Map<String, String> strictCorpora = discover(STRICT);
        assertFalse(strictCorpora.isEmpty(), "expected at least one captured strict corpus (" + STRICT + ")");

        // Symmetric coverage guard: every non-anchor generation with a non-strict corpus must also
        // carry a strict twin (the capture recipe produces both), so the strict dimension can't
        // silently lag when a new generation is added. The anchor (1.6.0) has no strict fixture — its
        // CLI is gone — and serves as the non-strict reference below, so it is excluded.
        Set<String> nonStrictNonAnchor = new TreeSet<>(discover(NON_STRICT).keySet());
        nonStrictNonAnchor.remove(ANCHOR);
        assertEquals(nonStrictNonAnchor, strictCorpora.keySet(),
                "every non-anchor generation with a non-strict parity corpus must also have a strict twin ("
                        + STRICT + ") — capture both per the fixtures README");

        // On this corpus no item is valid-with-only-a-CLI-warning, so a generation's strict map must
        // equal the non-strict anchor's default map — extending the durable invariant to --strict.
        Map<String, Boolean> anchor = verdicts(CLI_DIR + "/" + ANCHOR + "/" + NON_STRICT);
        for (Map.Entry<String, String> e : strictCorpora.entrySet()) {
            Map<String, Boolean> strict = verdicts(e.getValue());
            assertEquals(anchor.keySet(), strict.keySet(),
                    "strict corpus " + e.getKey() + " has a different item-id set than the " + ANCHOR + " anchor");
            assertEquals(anchor, strict,
                    "strict CLI verdicts for " + e.getKey() + " differ from the " + ANCHOR + " default verdicts — "
                            + "an item became valid-with-only-a-CLI-warning (strict-flips) or a verdict drifted; "
                            + "investigate before shipping");
        }
    }
}
