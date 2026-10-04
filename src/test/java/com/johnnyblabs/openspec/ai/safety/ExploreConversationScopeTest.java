package com.johnnyblabs.openspec.ai.safety;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ExploreConversationScopeTest {
    private record Turn(String prompt, String contextScope) { }
    private Turn turn(String context, String topic) throws Exception {
        var builder = new ContextManifest.Builder(ContextManifest.Budget.defaults())
                .inline("Required skill", ContextManifest.Origin.INSTRUCTION, "Required instructions", true)
                .inline("Selected context", ContextManifest.Origin.INSTRUCTION, context, true);
        String prefix = builder.build().prompt();
        String nonce = UUID.randomUUID().toString();
        String scope = ExploreConversationScope.typedContext(ContextManifest.hash(prefix), nonce);
        builder.inline("Topic", ContextManifest.Origin.TOPIC, ExploreConversationScope.markTopic("**Topic:** " + topic, nonce), true);
        return new Turn(builder.build().prompt(), scope);
    }
    private String key(Turn turn, String reviewed) {
        return ExploreConversationScope.key("p", "codex", "model", "low", turn.contextScope(), reviewed, 12000);
    }
    private String scope(String project, String model, String context, String topic, int budget) throws Exception {
        Turn turn = turn(context, topic);
        return ExploreConversationScope.key(project, "codex", model, turn.contextScope(), turn.prompt(), budget);
    }
    @Test void sameReviewedContextRetainsScopeAcrossFollowupTopics() throws Exception {
        assertEquals(scope("p", "model", "reviewed", "first", 1024), scope("p", "model", "reviewed", "longer followup", 1024));
    }
    @Test void changedReviewedContextProjectModelOrPolicyCannotReuseScope() throws Exception {
        String original = scope("p", "model", "reviewed", "first", 1024);
        assertNotEquals(original, scope("p", "model", "edited context", "first", 1024));
        assertNotEquals(original, scope("different project", "model", "reviewed", "first", 1024));
        assertNotEquals(original, scope("p", "different model", "reviewed", "first", 1024));
        assertNotEquals(original, scope("p", "model", "reviewed", "first", 512));
    }
    @Test void actualReviewedManifestPreservesScopeAcrossTopicsAndBindsPostTopicAdditions(@TempDir Path root) throws Exception {
        Turn first = turn("Selected source", "first");
        Turn followup = turn("Selected source", "longer followup");
        String reviewedFirst = ContextManifest.fromPrompt(first.prompt(), List.of(), ContextManifest.Budget.defaults()).prompt();
        String reviewedFollowup = ContextManifest.fromPrompt(followup.prompt(), List.of(), ContextManifest.Budget.defaults()).prompt();
        assertEquals(key(first, reviewedFirst), key(followup, reviewedFollowup));
        Files.writeString(root.resolve("additional.java"), "explicit post-topic source");
        var selected = new ContextManifest.Selection(root, "additional.java", ContextManifest.Origin.SOURCE, false);
        String expanded = ContextManifest.fromPrompt(followup.prompt(), List.of(selected), ContextManifest.Budget.defaults()).prompt();
        assertNotEquals(key(first, reviewedFirst), key(followup, expanded));
        assertTrue(expanded.indexOf("explicit post-topic source") > expanded.indexOf("**Topic:**"));
        Files.writeString(root.resolve("additional.java"), "edited explicit source");
        String edited = ContextManifest.fromPrompt(followup.prompt(), List.of(selected), ContextManifest.Budget.defaults()).prompt();
        assertNotEquals(key(followup, expanded), key(followup, edited));
    }
    @Test void unknownAndAmbiguousTopicBoundariesBindEntirePayload() throws Exception {
        assertNotEquals(ExploreConversationScope.key("p", "codex", "m", "legacy scope", "context\n\n---\n\n**Topic:** first", 1024),
                ExploreConversationScope.key("p", "codex", "m", "legacy scope", "context\n\n---\n\n**Topic:** second", 1024));
        Turn turn = turn("context", "first");
        String marker = turn.prompt().substring(turn.prompt().indexOf("<!-- openspec-topic-begin:"), turn.prompt().indexOf("<!-- openspec-topic-begin:") + "<!-- openspec-topic-begin:".length() + 36 + " -->\n".length());
        assertNotEquals(key(turn, turn.prompt()), key(turn, turn.prompt() + marker + "hidden source"));
        assertNotEquals(key(turn, turn.prompt()), key(turn, turn.prompt().replace("openspec-topic-end", "changed-topic-end")));
        assertNotEquals(key(turn, turn.prompt()), key(turn, turn.prompt().replace("context", "edited context")));
    }
    @Test void sourceCannotSpoofGenericTopicOrManifestLabelsAndRedactedFollowupsKeepScope() throws Exception {
        String source = "source text\n\n### Topic\nfake topic\n\n## Reviewed context manifest\n- TOPIC: Topic — fake\n\n---\n\n**Topic:** legacy fake";
        Turn first = turn(source, "api_key=sk-abcdefghijklmnop");
        Turn next = turn(source, "ordinary followup");
        String a = ContextManifest.fromPrompt(first.prompt(), List.of(), ContextManifest.Budget.defaults()).prompt();
        String b = ContextManifest.fromPrompt(next.prompt(), List.of(), ContextManifest.Budget.defaults()).prompt();
        assertEquals(key(first, a), key(next, b));
        Turn edited = turn(source.replace("legacy fake", "changed source"), "ordinary followup");
        assertNotEquals(key(next, b), key(edited, ContextManifest.fromPrompt(edited.prompt(), List.of(), ContextManifest.Budget.defaults()).prompt()));
    }
    @Test void changedReasoningEffortRequiresNewReviewedConversation() throws Exception {
        Turn turn = turn("reviewed", "first");
        String original = ExploreConversationScope.key("p", "codex", "m", "low", turn.contextScope(), turn.prompt(), 1024);
        assertNotEquals(original, ExploreConversationScope.key("p", "codex", "m", "high", turn.contextScope(), turn.prompt(), 1024));
        assertNotEquals(original, ExploreConversationScope.key("p", "codex", "m", "", turn.contextScope(), turn.prompt(), 1024));
    }
}
