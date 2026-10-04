package com.johnnyblabs.openspec.ai.safety;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Topic text may evolve; a changed reviewed context/destination requires a new conversation. */
public final class ExploreConversationScope {
    private static final String TOPIC_SEPARATOR = "\n\n---\n\n**Topic:** ";
    private ExploreConversationScope() {}

    public static String key(String project, String executable, String model, String contextScope,
                             String reviewedPrompt, int budget) {
        int topicStart = reviewedPrompt.lastIndexOf(TOPIC_SEPARATOR);
        String reviewedContext = topicStart < 0 ? reviewedPrompt : reviewedPrompt.substring(0, topicStart);
        String[] parts = {"prompt-only-v1", project, executable, model, contextScope, reviewedContext, String.valueOf(budget)};
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                byte[] bytes = String.valueOf(part).getBytes(StandardCharsets.UTF_8);
                digest.update(String.valueOf(bytes.length).getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) ':');
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
