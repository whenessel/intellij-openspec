package com.johnnyblabs.openspec.ai.safety;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SafeResponseMarkdownTest {
    @Test void rawHtmlAndStyleCannotLoadRemoteContent() {
        String result = SafeResponseMarkdown.render("<img src='https://example.test/tracker'>\n<style>@import 'https://example.test/style';</style>");
        assertFalse(result.contains("<img"));
        assertFalse(result.contains("<style>"));
        assertTrue(result.contains("&lt;img"));
    }
    @Test void markdownImagesAreRemovedIncludingReferenceImages() {
        String result = SafeResponseMarkdown.render("![remote](https://example.test/image) ![file](file:///private/file)\n\n![ref][r]\n\n[r]: https://example.test/reference");
        assertFalse(result.contains("<img"));
        assertFalse(result.contains("src="));
        assertTrue(result.contains("[Image omitted]"));
    }
    @Test void unsafeUrlsAreSanitizedAndFormattingIsPreserved() {
        String result = SafeResponseMarkdown.render("**Answer** [bad](javascript:alert(1))\n\n- first\n- second");
        assertTrue(result.contains("<strong>Answer</strong>"));
        assertTrue(result.contains("<li>first</li>"));
        assertFalse(result.contains("href=\"javascript:"));
    }
}
