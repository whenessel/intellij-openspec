package com.johnnyblabs.openspec.ai;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for the three Direct-API response parsers. Each parser is driven by the
 * corresponding provider's published example response shape (see
 * {@code src/test/resources/fixtures/ai/README.md} for the doc-sourced provenance and why these
 * are documented shapes rather than live captures). The asserted text is read from the committed
 * fixture, so these are real contract checks against the provider's stated shape — not the
 * hand-authored inline literals the repo's contract-test discipline forbids.
 */
class DirectApiResponseContractTest {

    private static String loadFixture(String path) {
        try (InputStream in = DirectApiResponseContractTest.class.getResourceAsStream("/fixtures/ai/" + path)) {
            assertNotNull(in, "fixture not found: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("failed to load fixture " + path, e);
        }
    }

    @Nested
    class ClaudeParserContract {
        @Test
        void extractsTextFromDocumentedShape() throws Exception {
            String text = DirectApiService.parseClaudeResponse(loadFixture("claude/messages-response.json"));
            assertEquals("Hi! My name is Claude.", text);
        }

        @Test
        void emptyContentThrows() {
            AiApiException ex = assertThrows(AiApiException.class,
                    () -> DirectApiService.parseClaudeResponse("{\"content\":[]}"));
            assertTrue(ex.getMessage().contains("Empty response"));
        }
    }

    @Nested
    class OpenAiParserContract {
        @Test
        void extractsContentFromDocumentedShape() throws Exception {
            String text = DirectApiService.parseOpenAiResponse(loadFixture("openai/chat-completion-response.json"));
            assertEquals("\n\nHello there, how may I assist you today?", text);
        }

        @Test
        void emptyChoicesThrows() {
            AiApiException ex = assertThrows(AiApiException.class,
                    () -> DirectApiService.parseOpenAiResponse("{\"choices\":[]}"));
            assertTrue(ex.getMessage().contains("Empty response"));
        }
    }

    @Nested
    class GeminiParserContract {
        @Test
        void extractsTextFromDocumentedShape() throws Exception {
            String text = DirectApiService.parseGeminiResponse(loadFixture("gemini/generatecontent-response.json"));
            assertEquals(
                    "AI works by learning patterns from large amounts of data, then using those patterns to make predictions or generate new content.",
                    text);
        }

        @Test
        void emptyCandidatesThrows() {
            AiApiException ex = assertThrows(AiApiException.class,
                    () -> DirectApiService.parseGeminiResponse("{\"candidates\":[]}"));
            assertTrue(ex.getMessage().contains("Empty response"));
        }
    }
}
