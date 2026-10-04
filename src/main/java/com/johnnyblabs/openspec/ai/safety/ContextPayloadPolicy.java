package com.johnnyblabs.openspec.ai.safety;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Pure payload policy; limits reject essential-content overflow without silent truncation. */
public final class ContextPayloadPolicy {
    private ContextPayloadPolicy() { }
    // Approximate 12k tokens. UTF-8 byte bound remains conservative when tokenization is unknown.
    public static final int MAX_PROMPT_BYTES = 48 * 1024;
    private static final Pattern PRIVATE_KEY = Pattern.compile("(?s)-----BEGIN [^-\\r\\n]*PRIVATE KEY-----.*?-----END [^-\\r\\n]*PRIVATE KEY-----");
    private static final Pattern TOKEN = Pattern.compile("(?i)\\b(?:sk-[a-z0-9_-]{12,}|gh[pousr]_[a-z0-9_]{12,}|github_pat_[a-z0-9_]{12,}|AKIA[A-Z0-9]{16})\\b");
    private static final Pattern ASSIGNMENT = Pattern.compile("(?im)([\\\"']?(?:api[_-]?key|access[_-]?token|refresh[_-]?token|client[_-]?secret|password|authorization)[\\\"']?\\s*[:=]\\s*)(?:[\\\"'][^\\\"'\\r\\n]*[\\\"']|[^\\s,;\\r\\n]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[a-z0-9._~+/=-]{8,}");
    public static String redactAndBound(String prompt) throws IOException {
        if (prompt == null || prompt.isBlank()) throw new IOException("AI context is empty");
        if (prompt.length() > MAX_PROMPT_BYTES || prompt.getBytes(StandardCharsets.UTF_8).length > MAX_PROMPT_BYTES) {
            throw new IOException("AI context exceeds the 48 KiB review budget (approximately 12k tokens). Reduce selected context; no content was sent.");
        }
        String safe = PRIVATE_KEY.matcher(prompt).replaceAll("[REDACTED PRIVATE KEY]");
        safe = TOKEN.matcher(safe).replaceAll("[REDACTED TOKEN]");
        safe = BEARER.matcher(safe).replaceAll("Bearer [REDACTED]");
        return ASSIGNMENT.matcher(safe).replaceAll("$1[REDACTED]");
    }

}
