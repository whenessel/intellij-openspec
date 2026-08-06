package com.johnnyblabs.openspec.integration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Cross-version verdict-stability contract. {@link ValidatorVerdictParityTest} proves the plugin's
 * built-in validator matches the real CLI's verdicts on the 1.6.0 parity corpus. This lightweight,
 * platform-free test proves the CLI's <em>own</em> verdicts did not drift 1.6.0 → 1.7.0 over the
 * identical corpus: the two captured {@code validate --all --json} fixtures must carry the same
 * id → valid map.
 *
 * <p>With both facts — plugin matches 1.6, and 1.6 CLI verdicts equal 1.7 CLI verdicts — plugin/1.7
 * parity follows by transitivity, so the heavy {@link ValidatorVerdictParityTest} platform harness is
 * deliberately <em>not</em> cloned for 1.7. And any future CLI generation that tightens a verdict on
 * this corpus fails here — the durable evidence base for the invariant that the plugin is never more
 * restrictive than the CLI. (A future change may generalize this across every captured generation.)
 */
class ValidatorVerdictVersionStabilityTest {

    private static Map<String, Boolean> verdicts(String fixture) {
        try (InputStream is = ValidatorVerdictVersionStabilityTest.class.getResourceAsStream(fixture)) {
            assertNotNull(is, "missing fixture: " + fixture);
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

    @Test
    void cliVerdictsAreStableFrom16To17() {
        Map<String, Boolean> v16 = verdicts("/fixtures/cli/1.6.0/validate-parity-corpus.json");
        Map<String, Boolean> v17 = verdicts("/fixtures/cli/1.7.0/validate-parity-corpus.json");
        assertEquals(13, v16.size(), "the 1.6 parity corpus must have 13 items");
        assertEquals(v16, v17,
                "CLI verdicts drifted 1.6 → 1.7 over the identical corpus — a tightened verdict would put "
                        + "the plugin at risk of being more restrictive than one generation while matching "
                        + "another; investigate before shipping");
    }
}
