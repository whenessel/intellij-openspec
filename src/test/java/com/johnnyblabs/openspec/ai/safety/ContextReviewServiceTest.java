package com.johnnyblabs.openspec.ai.safety;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ContextReviewServiceTest {
    @Test void knownSecretsRedactedWithoutChangingOrdinaryContext() throws Exception {
        String input = "# Proposal\napi_key=\"sk-testabcdefghijklmnop\"\nAuthorization: Bearer abcdefghijklmnop\n"
                + "refresh_token: \"private-value\"\n-----BEGIN PRIVATE KEY-----\nabcdef\n-----END PRIVATE KEY-----\nordinary";
        String reviewed = ContextPayloadPolicy.redactAndBound(input);
        assertFalse(reviewed.contains("sk-test"));
        assertFalse(reviewed.contains("abcdefghijklmnop"));
        assertFalse(reviewed.contains("private-value"));
        assertFalse(reviewed.contains("abcdef\n"));
        assertTrue(reviewed.startsWith("# Proposal"));
        assertTrue(reviewed.endsWith("ordinary"));
    }
    @Test void budgetRejectsWholePayloadWithoutSilentTruncation() {
        assertThrows(IOException.class, () -> ContextPayloadPolicy.redactAndBound("a".repeat(ContextPayloadPolicy.MAX_PROMPT_BYTES + 1)));
        assertThrows(IOException.class, () -> ContextPayloadPolicy.redactAndBound("€".repeat(ContextPayloadPolicy.MAX_PROMPT_BYTES / 2)));
    }
    @Test void temporaryContextCleanupDoesNotFollowSymlinks() throws Exception {
        Path root = Files.createTempDirectory("openspec-reviewed-test-");
        Path outside = Files.createTempFile("openspec-unrelated-", ".md");
        Files.writeString(outside, "outside");
        Files.writeString(root.resolve("reviewed-context.md"), "approved");
        try {
            try { Files.createSymbolicLink(root.resolve("link"), outside); }
            catch (UnsupportedOperationException | IOException ignored) { }
            try (var context = new ReviewedContext("approved", root)) {
                assertEquals("approved", context.prompt());
                assertEquals(root, context.root());
            }
            assertFalse(Files.exists(root));
            assertEquals("outside", Files.readString(outside));
        } finally { Files.deleteIfExists(outside); }
    }
}
