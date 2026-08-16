package com.johnnyblabs.openspec.integration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "By construction" guard for the single-sourced top-supported OpenSpec CLI version (spec: `ci`,
 * "Single-sourced top-supported CLI version"). The top-supported version is declared in exactly one
 * place — {@code openspecTargetVersion} in {@code gradle.properties} — and this test proves the two
 * couplings that keep it from silently drifting, reading the real working tree (the same idiom as
 * {@link com.johnnyblabs.openspec.docs.DocumentationHygieneTest}):
 *
 * <ol>
 *   <li><b>Fixtures exist for the target.</b> A captured contract-fixture corpus
 *       ({@code version.txt} + the parity twins) must exist for whatever version the property names,
 *       so "bump the target without capturing that generation's fixtures" fails the build. These are
 *       the same fixtures consumed downstream by {@code CliDetectionServiceTest} ({@code version.txt})
 *       and {@link ValidatorVerdictVersionStabilityTest} (the parity twins).</li>
 *   <li><b>The UI-smoke workflow derives from the source.</b> {@code .forgejo/workflows/ui-smoke.yaml}
 *       installs the OpenSpec CLI at the version read from {@code gradle.properties}, never a
 *       separately-maintained pin — so the smoke suite cannot fall behind the declared support set
 *       (the drift this whole change fixes: the pin had gone three generations stale at
 *       {@code @1.6.0}).</li>
 * </ol>
 *
 * <p>The version is always read from the single source, never hardcoded here, so this test cannot
 * silently agree with a drifted value. The workflow guard is scoped to {@code ui-smoke.yaml} ALONE —
 * {@code .github/workflows/build.yml} pins {@code @latest} on purpose (newest, for the Windows leg)
 * and {@code scripts/seed-lifecycle-demo.sh} pins {@code @1.3.1} on purpose (a legacy demo seed), so
 * a repo-wide "no hardcoded pin" rule would wrongly flag both.
 *
 * <p>This is deliberately NOT collapsed with {@link ValidatorVerdictVersionStabilityTest#FLOOR} or
 * {@code CliVersionAtLeastTest}'s value spread: those are the durable historical <em>floor</em> and
 * boundary enumeration (a lower bound and a semantics probe), distinct from the single "current
 * target," and deriving them from it would make the guard circular and non-monotonic.
 */
class TargetVersionSingleSourceTest {

    /** The single source of truth line, e.g. {@code openspecTargetVersion=1.9.0}. */
    private static final Pattern TARGET_VERSION =
            Pattern.compile("(?m)^openspecTargetVersion=(\\d+\\.\\d+\\.\\d+)\\s*$");

    /** Any {@code @fission-ai/openspec@<digit>...} literal — a hardcoded pin, which the workflow must not carry. */
    private static final Pattern HARDCODED_PIN =
            Pattern.compile("@fission-ai/openspec@\\d");

    private static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("gradle.properties"))
                    && Files.exists(dir.resolve("build.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not locate repo root from " + Paths.get("").toAbsolutePath());
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Parse the single source; fail loudly if the property is absent or renamed. */
    private static String targetVersion(Path root) {
        String props = read(root.resolve("gradle.properties"));
        Matcher m = TARGET_VERSION.matcher(props);
        assertTrue(m.find(),
                "gradle.properties must declare openspecTargetVersion=<x.y.z> — the single source of truth "
                        + "for the top-supported OpenSpec CLI version");
        return m.group(1);
    }

    @Test
    void targetVersionHasACapturedFixtureCorpus() {
        Path root = repoRoot();
        String target = targetVersion(root);

        Path genDir = root.resolve("src/test/resources/fixtures/cli").resolve(target);
        assertTrue(Files.isDirectory(genDir),
                "no captured fixture corpus for the target version " + target + " — expected " + genDir
                        + ". Capture the fixtures before advancing openspecTargetVersion.");

        Path versionTxt = genDir.resolve("version.txt");
        assertTrue(Files.exists(versionTxt), "missing " + versionTxt);
        assertEqualsTrimmed(target, read(versionTxt),
                "fixtures/cli/" + target + "/version.txt content must equal the target version");

        assertTrue(Files.exists(genDir.resolve("validate-parity-corpus.json")),
                "missing parity corpus for " + target + " (consumed by ValidatorVerdictVersionStabilityTest)");
        assertTrue(Files.exists(genDir.resolve("validate-parity-corpus-strict.json")),
                "missing strict parity twin for " + target);
    }

    @Test
    void uiSmokeWorkflowInstallsTheSingleSourcedVersion() {
        Path root = repoRoot();

        Path workflow = root.resolve(".forgejo/workflows/ui-smoke.yaml");
        assertTrue(Files.exists(workflow), "missing " + workflow);
        String yaml = read(workflow);

        // Positive: the CLI install derives the version from the single source.
        assertTrue(yaml.contains("openspecTargetVersion"),
                "ui-smoke.yaml must read the CLI version from gradle.properties' openspecTargetVersion, "
                        + "not a separately-maintained literal");

        // Negative (scoped to THIS file only): no reintroduced hardcoded pin.
        Matcher pin = HARDCODED_PIN.matcher(yaml);
        assertFalse(pin.find(),
                "ui-smoke.yaml must not hardcode a @fission-ai/openspec@<version> pin — it re-drifts. "
                        + "Derive it from gradle.properties' openspecTargetVersion instead.");
    }

    private static void assertEqualsTrimmed(String expected, String actual, String message) {
        assertNotNull(actual, message);
        assertTrue(expected.equals(actual.trim()),
                message + " (expected \"" + expected + "\", got \"" + actual.trim() + "\")");
    }
}
