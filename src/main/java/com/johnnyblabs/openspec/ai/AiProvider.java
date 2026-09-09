package com.johnnyblabs.openspec.ai;

import java.util.List;

public enum AiProvider {
    NONE("None", List.of()),
    // Dateless aliases: providers resolve these to the current snapshot server-side, so the
    // default (models.get(0)) is always a live model and the list does not need a date bump each
    // release. Pinning a dated snapshot here previously shipped a fabricated ID that 404'd.
    CLAUDE("Claude", List.of("claude-sonnet-5", "claude-opus-5", "claude-haiku-4-5")),
    // OpenAI list is pending a key-verified refresh (GET /v1/models) — do not guess IDs here;
    // guessed IDs 401 on fresh installs. gpt-4o/o1-mini are stale but the routing logic
    // (max_completion_tokens for o1/o3/o4/gpt-5) is current. See change refresh-direct-api-models-and-routing.
    OPENAI("OpenAI", List.of("gpt-4o", "gpt-4o-mini", "o1-mini")),
    // Gemini 2.5 (pro/flash/flash-lite) is retiring 2026-10-16 (flash already deprecated), so its
    // defaults 404'd on fresh AI Studio keys. Current stable Flash line (free-tier friendly):
    GEMINI("Gemini", List.of("gemini-3.5-flash", "gemini-3.8-flash", "gemini-3.5-flash-lite"));

    private final String displayName;
    private final List<String> models;

    AiProvider(String displayName, List<String> models) {
        this.displayName = displayName;
        this.models = models;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getModels() {
        return models;
    }

    public String getDefaultModel() {
        return models.isEmpty() ? "" : models.get(0);
    }

    public static AiProvider fromString(String name) {
        if (name == null || name.isBlank()) return NONE;
        // Try display name first, then enum name
        for (AiProvider p : values()) {
            if (p.displayName.equalsIgnoreCase(name.trim())) return p;
        }
        try {
            return valueOf(name.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
