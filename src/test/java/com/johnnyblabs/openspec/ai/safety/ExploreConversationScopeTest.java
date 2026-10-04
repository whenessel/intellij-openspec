package com.johnnyblabs.openspec.ai.safety;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExploreConversationScopeTest {
    private String scope(String project, String model, String context, String topic, int budget) {
        return ExploreConversationScope.key(project, "codex", model, "source-context", context + "\n\n---\n\n**Topic:** " + topic, budget);
    }
    @Test void sameReviewedContextRetainsScopeAcrossFollowupTopics() {
        assertEquals(scope("p", "model", "reviewed", "first", 1024), scope("p", "model", "reviewed", "followup", 1024));
    }
    @Test void changedReviewedContextProjectModelOrPolicyCannotReuseScope() {
        String original = scope("p", "model", "reviewed", "first", 1024);
        assertNotEquals(original, scope("p", "model", "edited context", "first", 1024));
        assertNotEquals(original, scope("different project", "model", "reviewed", "first", 1024));
        assertNotEquals(original, scope("p", "different model", "reviewed", "first", 1024));
        assertNotEquals(original, scope("p", "model", "reviewed", "first", 512));
    }
    @Test void missingTopicMarkerIsConservativelyBoundToEntirePayload() {
        assertNotEquals(ExploreConversationScope.key("p", "codex", "m", "scope", "edited first", 1024),
                ExploreConversationScope.key("p", "codex", "m", "scope", "edited second", 1024));
    }
    @Test void changedReasoningEffortRequiresNewReviewedConversation() {
        String original = ExploreConversationScope.key("p", "codex", "m", "low", "scope", "reviewed", 1024);
        assertNotEquals(original, ExploreConversationScope.key("p", "codex", "m", "high", "scope", "reviewed", 1024));
        assertNotEquals(original, ExploreConversationScope.key("p", "codex", "m", "", "scope", "reviewed", 1024));
    }
}
