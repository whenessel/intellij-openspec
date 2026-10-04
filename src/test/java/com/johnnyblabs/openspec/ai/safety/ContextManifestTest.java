package com.johnnyblabs.openspec.ai.safety;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ContextManifestTest {
    @TempDir Path root;
    private ContextManifest.Selection source(String path, boolean essential) {
        return new ContextManifest.Selection(root, path, ContextManifest.Origin.SOURCE, essential);
    }
    @Test void onlyExplicitSelectionsReadAndOrderingAndHashesAreImmutable() throws Exception {
        Files.createDirectories(root.resolve("changes/chosen"));
        Files.createDirectories(root.resolve("changes/other"));
        Files.writeString(root.resolve("changes/chosen/proposal.md"), "chosen content");
        Files.writeString(root.resolve("changes/other/proposal.md"), "unselected other content");
        Files.writeString(root.resolve("source.java"), "explicit source");
        var selected = new ContextManifest.Selection(root, "changes/chosen/proposal.md", ContextManifest.Origin.SELECTED_CHANGE, true);
        var first = ContextManifest.fromPrompt("Required first", List.of(selected), ContextManifest.Budget.defaults());
        assertFalse(first.prompt().contains("unselected other content"));
        assertFalse(first.prompt().contains("explicit source"));
        assertTrue(first.prompt().indexOf("Required first") < first.prompt().indexOf("chosen content"));
        var other = new ContextManifest.Selection(root, "changes/other/proposal.md", ContextManifest.Origin.ADDITIONAL_CHANGE, false);
        var expanded = ContextManifest.fromPrompt("Required first", List.of(selected, other, source("source.java", false)), ContextManifest.Budget.defaults());
        assertTrue(expanded.prompt().contains("unselected other content"));
        assertTrue(expanded.prompt().contains("explicit source"));
        assertNotEquals(ContextManifest.hash(first.prompt()), ContextManifest.hash(expanded.prompt()));
        assertThrows(UnsupportedOperationException.class, () -> expanded.entries().clear());
        String before = expanded.prompt(); Files.writeString(root.resolve("source.java"), "changed source");
        assertEquals(before, expanded.prompt());
        assertEquals(ContextManifest.hash("explicit source"), expanded.entries().getLast().contentHash());
    }
    @Test void secretsAreExcludedBeforeReadingAndInlineSecretsRedactedBeforeHashing() throws Exception {
        Files.createDirectories(root.resolve(".codex"));
        Files.writeString(root.resolve(".codex/auth.json"), "never send credential contents");
        var manifest = ContextManifest.fromPrompt("api_key=sk-abcdefghijklmnop", List.of(source(".codex/auth.json", false)), ContextManifest.Budget.defaults());
        assertFalse(manifest.prompt().contains("abcdefghijklmnop"));
        assertFalse(manifest.prompt().contains("never send credential contents"));
        assertTrue(manifest.prompt().contains("OMITTED: Credential"));
        assertTrue(manifest.entries().getFirst().redactions() > 0);
        assertEquals(ContextManifest.hash("api_key=[REDACTED]"), manifest.entries().getFirst().contentHash());
        assertThrows(IOException.class, () -> ContextManifest.fromPrompt("required", List.of(source(".codex/auth.json", true)), ContextManifest.Budget.defaults()));
    }
    @Test void binaryInvalidUtf8GeneratedIgnoredAndSymlinkFilesHaveVisibleOmissions() throws Exception {
        Files.write(root.resolve("binary.txt"), new byte[]{1,0,2});
        Files.write(root.resolve("invalid.txt"), new byte[]{(byte)0xff});
        Files.createDirectories(root.resolve("build")); Files.writeString(root.resolve("build/generated.java"), "generated");
        Files.writeString(root.resolve(".gitignore"), "ignored*.txt\n!ignored-public.txt\n");
        Files.writeString(root.resolve("ignored-public.txt"), "ignored despite negation conservative policy");
        var manifest = ContextManifest.fromPrompt("required", List.of(source("binary.txt", false), source("invalid.txt", false),
                source("build/generated.java", false), source("ignored-public.txt", false)), ContextManifest.Budget.defaults());
        assertEquals(4, manifest.entries().stream().filter(entry -> !entry.included()).count());
        assertTrue(manifest.prompt().contains("Ignored file excluded"));
        assertFalse(manifest.prompt().contains("ignored despite"));
        Path external = Files.createTempFile("context-external", ".txt");
        try {
            Files.writeString(external, "outside hidden text");
            Files.createSymbolicLink(root.resolve("link.txt"), external);
            var linked = ContextManifest.fromPrompt("required", List.of(source("link.txt", false)), ContextManifest.Budget.defaults());
            assertFalse(linked.prompt().contains("outside hidden text"));
            assertTrue(linked.prompt().contains("OMITTED:"));
        } finally { Files.deleteIfExists(external); }
    }
    @Test void budgetsAccountForUtf8AndManifestAndDistinguishOptionalFromEssential() throws Exception {
        Files.writeString(root.resolve("large.txt"), "é".repeat(1024));
        var perFile = new ContextManifest.Budget(1, 2047, 49152, 12000, null, 4096, 1024);
        var omitted = ContextManifest.fromPrompt("required", List.of(source("large.txt", false)), perFile);
        assertTrue(omitted.prompt().contains("Per-file byte budget"));
        assertThrows(IOException.class, () -> ContextManifest.fromPrompt("required", List.of(source("large.txt", true)), perFile));
        var exact = new ContextManifest.Budget(1, 2048, 49152, 12000, null, 4096, 1024);
        assertTrue(ContextManifest.fromPrompt("required", List.of(source("large.txt", true)), exact).entries().getLast().included());
        Files.writeString(root.resolve("second.txt"), "second");
        var count = ContextManifest.fromPrompt("required", List.of(source("large.txt", true), source("second.txt", false)), exact);
        assertTrue(count.prompt().contains("File-count budget"));
        assertThrows(IOException.class, () -> ContextManifest.fromPrompt("x".repeat(12000), List.of(), ContextManifest.Budget.defaults()), "Manifest overhead must count");
        var base = ContextManifest.fromPrompt("required", List.of(), ContextManifest.Budget.defaults());
        // Assert final UTF-8 admission exactly at the configured byte boundary.
        String payload = "é".repeat(500);
        assertEquals(payload, ContextPayloadPolicy.redactAndBound(payload, new ContextManifest.Budget(64, 32768, 1000, 12000, null, 4096, 1024)));
        assertThrows(IOException.class, () -> ContextPayloadPolicy.redactAndBound(payload + "a", new ContextManifest.Budget(64, 32768, 1000, 12000, null, 4096, 1024)));
        assertEquals(base.prompt().getBytes(StandardCharsets.UTF_8).length, base.utf8Bytes());
        assertEquals(base.utf8Bytes(), base.conservativeInputTokens());
    }
    @Test void unknownBoundsNeverPretendTokenizerAccuracyAndKnownWindowReservesOutput() throws Exception {
        var unknown = ContextManifest.fromPrompt("required", List.of(), ContextManifest.Budget.defaults());
        assertFalse(unknown.modelBoundsKnown());
        assertTrue(unknown.prompt().contains("Tokenizer unknown"));
        assertTrue(unknown.prompt().contains("Model context limit unknown"));
        var known = new ContextManifest.Budget(64, 32768, 49152, 12000, 8192, 4096, 1024);
        assertEquals(3072, known.effectiveInputTokens());
        assertEquals(3072, known.effectiveBytes());
        var bounded = ContextManifest.fromPrompt("required", List.of(), known);
        assertTrue(bounded.modelBoundsKnown());
        assertThrows(IOException.class, () -> ContextManifest.fromPrompt("x".repeat(3072), List.of(), known));
        assertThrows(IOException.class, () -> ContextManifest.fromPrompt("required", List.of(),
                new ContextManifest.Budget(64, 32768, 49152, 12000, 4096, 4096, 1024)));
    }
    @Test void unsafeOptionalNamesCannotExposeAbsolutePathsOrRawSecretsInDiagnostics() throws Exception {
        var manifest = ContextManifest.fromPrompt("required", List.of(source("../outside.txt", false)), ContextManifest.Budget.defaults());
        assertTrue(manifest.prompt().contains("OMITTED:"));
        assertFalse(manifest.prompt().contains(root.toString()));
        assertThrows(IOException.class, () -> ContextManifest.fromPrompt("required", List.of(source("../outside.txt", true)), ContextManifest.Budget.defaults()));
        Files.createDirectories(root.resolve("nested"));
        Files.writeString(root.resolve("nested/.gitignore"), "private.txt\n");
        Files.writeString(root.resolve("nested/private.txt"), "nested ignored");
        assertFalse(ContextManifest.fromPrompt("required", List.of(source("nested/private.txt", false)), ContextManifest.Budget.defaults()).prompt().contains("nested ignored"));
    }
    @Test void repositoryIgnoreRulesApplyToSelectedChangeRootsAndSlashGlobDescendants() throws Exception {
        Files.createDirectory(root.resolve(".git"));
        Files.writeString(root.resolve(".gitignore"), "changes/chosen/private\nchanges/**/hidden*.md\n");
        Path chosen = Files.createDirectories(root.resolve("changes/chosen"));
        Files.createDirectories(chosen.resolve("private"));
        Files.writeString(chosen.resolve("private/proposal.md"), "ignored descendant content");
        Files.writeString(chosen.resolve("hidden-design.md"), "ignored zero-double-star content");
        var selections = List.of(new ContextManifest.Selection(chosen, "private/proposal.md", ContextManifest.Origin.SELECTED_CHANGE, false),
                new ContextManifest.Selection(chosen, "hidden-design.md", ContextManifest.Origin.SELECTED_CHANGE, false));
        var manifest = ContextManifest.fromPrompt("required", selections, ContextManifest.Budget.defaults());
        assertFalse(manifest.prompt().contains("ignored descendant content"));
        assertFalse(manifest.prompt().contains("ignored zero-double-star content"));
        assertEquals(2, manifest.entries().stream().filter(entry -> !entry.included()).count());
    }
    @Test void identicalRelativePathsAndContentsRemainBoundToDistinctResolvedRoots() throws Exception {
        Path one = Files.createDirectories(root.resolve("store-one/same-change"));
        Path two = Files.createDirectories(root.resolve("store-two/same-change"));
        Files.writeString(one.resolve("proposal.md"), "identical content");
        Files.writeString(two.resolve("proposal.md"), "identical content");
        var first = ContextManifest.fromPrompt("required", List.of(new ContextManifest.Selection(one, "proposal.md", ContextManifest.Origin.SELECTED_CHANGE, true)), ContextManifest.Budget.defaults());
        var second = ContextManifest.fromPrompt("required", List.of(new ContextManifest.Selection(two, "proposal.md", ContextManifest.Origin.SELECTED_CHANGE, true)), ContextManifest.Budget.defaults());
        assertNotEquals(first.entries().getLast().scopeId(), second.entries().getLast().scopeId());
        assertNotEquals(ContextManifest.hash(first.prompt()), ContextManifest.hash(second.prompt()));
        assertFalse(first.prompt().contains(one.toString()));
        assertFalse(second.prompt().contains(two.toString()));
    }
}
