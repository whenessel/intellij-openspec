package com.johnnyblabs.openspec.ai.safety;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Pure payload policy; limits reject essential-content overflow without silent truncation. */
public final class ContextPayloadPolicy {
    private ContextPayloadPolicy() { }
    // Absolute payload byte ceiling; a separate conservative token admission budget also applies.
    public static final int MAX_PROMPT_BYTES = 48 * 1024;
    private static final Pattern PRIVATE_KEY = Pattern.compile("(?s)-----BEGIN [^-\\r\\n]*PRIVATE KEY-----.*?-----END [^-\\r\\n]*PRIVATE KEY-----");
    private static final Pattern TOKEN = Pattern.compile("(?i)\\b(?:sk-[a-z0-9_-]{12,}|gh[pousr]_[a-z0-9_]{12,}|github_pat_[a-z0-9_]{12,}|AKIA[A-Z0-9]{16})\\b");
    private static final Pattern ASSIGNMENT = Pattern.compile("(?im)([\\\"']?(?:api[_-]?key|access[_-]?token|refresh[_-]?token|client[_-]?secret|password|authorization)[\\\"']?\\s*[:=]\\s*)(?:Bearer\\s+[^\\s,;\\r\\n]+|[\\\"'][^\\\"'\\r\\n]*[\\\"']|[^\\s,;\\r\\n]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[a-z0-9._~+/=-]{8,}");
    public record Redaction(String text, int count) { }
    public static Redaction redact(String text) {
        String safe = text == null ? "" : text;
        int count = 0;
        for (Pattern pattern : new Pattern[]{PRIVATE_KEY, BEARER, ASSIGNMENT, TOKEN}) {
            var matcher = pattern.matcher(safe);
            while (matcher.find()) {
                if (pattern != ASSIGNMENT || !matcher.group().equals(matcher.group(1) + "[REDACTED]")) count++;
            }
            String replacement = pattern == PRIVATE_KEY ? "[REDACTED PRIVATE KEY]"
                    : pattern == TOKEN ? "[REDACTED TOKEN]"
                    : pattern == BEARER ? "Bearer [REDACTED]" : "$1[REDACTED]";
            safe = pattern.matcher(safe).replaceAll(replacement);
        }
        return new Redaction(safe, count);
    }
    public static String redactAndBound(String prompt) throws IOException {
        return redactAndBound(prompt, ContextManifest.Budget.defaults());
    }
    public static String redactAndBound(String prompt, ContextManifest.Budget budget) throws IOException {
        if (prompt == null || prompt.isBlank()) throw new IOException("AI context is empty");
        if (prompt.length() > budget.maxBytes() || prompt.getBytes(StandardCharsets.UTF_8).length > budget.maxBytes())
            throw new IOException("Essential AI context exceeds byte review budget. Reduce selected scope; no content was sent.");
        String safe = redact(prompt).text();
        if (safe.getBytes(StandardCharsets.UTF_8).length > budget.effectiveBytes())
            throw new IOException("Essential AI context exceeds effective byte/token budget. Tokenizer unknown; conservative UTF-8 byte admission estimate applies. Reduce selected scope; no content was sent.");
        return safe;
    }
}
