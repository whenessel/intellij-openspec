package com.johnnyblabs.openspec.ai.safety;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Only the typed, uniquely marked topic can evolve without changing reviewed context scope. */
public final class ExploreConversationScope {
    private static final String PREFIX = "reviewed-explore-topic-v2:";
    private static final Pattern NONCE = Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
    private static final Pattern HASH = Pattern.compile("[a-f0-9]{64}");
    private ExploreConversationScope() {}
    public static String typedContext(String sourceContextHash, String topicNonce) {
        if (!HASH.matcher(sourceContextHash).matches() || !NONCE.matcher(topicNonce).matches())
            throw new IllegalArgumentException("Invalid typed Explore context");
        return PREFIX + sourceContextHash + ":" + topicNonce;
    }
    public static String markTopic(String topic, String nonce) {
        if (!NONCE.matcher(nonce).matches() || topic.contains(nonce)) throw new IllegalArgumentException("Invalid topic boundary");
        return begin(nonce) + topic + end(nonce);
    }
    private static String begin(String nonce) { return "<!-- openspec-topic-begin:" + nonce + " -->\n"; }
    private static String end(String nonce) { return "\n<!-- openspec-topic-end:" + nonce + " -->"; }
    public static String key(String project, String executable, String model, String contextScope,
                             String reviewedPrompt, int budget) {
        return key(project, executable, model, "", contextScope, reviewedPrompt, budget);
    }
    public static String key(String project, String executable, String model, String effort, String contextScope,
                             String reviewedPrompt, int budget) {
        String stableSource = contextScope;
        String reviewedContext = reviewedPrompt;
        if (contextScope != null && contextScope.startsWith(PREFIX)) {
            String[] fields = contextScope.substring(PREFIX.length()).split(":", -1);
            if (fields.length == 2 && HASH.matcher(fields[0]).matches() && NONCE.matcher(fields[1]).matches()) {
                String start = begin(fields[1]), finish = end(fields[1]);
                int from = reviewedPrompt.indexOf(start), to = reviewedPrompt.indexOf(finish);
                // Repeated/missing/reordered markers are ambiguous. Bind the complete payload instead.
                if (from >= 0 && to >= from + start.length()
                        && from == reviewedPrompt.lastIndexOf(start) && to == reviewedPrompt.lastIndexOf(finish)) {
                    reviewedContext = reviewedPrompt.substring(0, from) + "[reviewed Explore topic]"
                            + reviewedPrompt.substring(to + finish.length());
                    stableSource = PREFIX + fields[0];
                }
            }
        }
        String[] parts = {"prompt-only-v2", project, executable, model, effort, stableSource, reviewedContext, String.valueOf(budget)};
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                byte[] bytes = String.valueOf(part).getBytes(StandardCharsets.UTF_8);
                digest.update(String.valueOf(bytes.length).getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) ':'); digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
