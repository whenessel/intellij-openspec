package com.johnnyblabs.openspec.ai;

import java.util.List;

/** Immutable, non-secret routing requirements captured before an OpenRouter request. */
public record OpenRouterPolicy(List<String> only, List<String> order, boolean allowFallbacks,
                               String dataCollection, boolean zdr, int maxOutputTokens) {
    public OpenRouterPolicy {
        only = normalize(only);
        order = normalize(order);
        dataCollection = dataCollection == null ? "allow" : dataCollection;
        if (!dataCollection.equals("allow") && !dataCollection.equals("deny")) {
            throw new IllegalArgumentException("OpenRouter data collection must be allow or deny");
        }
        maxOutputTokens = maxOutputTokens > 0 ? Math.min(maxOutputTokens, 16000) : 4096;
    }
    private static List<String> normalize(List<String> providers) {
        return providers == null ? List.of() : providers.stream().filter(java.util.Objects::nonNull)
                .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }
    public String reviewSummary() {
        return "OpenRouter route: allowed=" + (only.isEmpty() ? "any eligible provider" : String.join(", ", only))
                + "; order=" + (order.isEmpty() ? "default" : String.join(", ", order))
                + "; fallback=" + allowFallbacks + "; data collection=" + dataCollection
                + "; ZDR=" + zdr + "; maximum output=" + maxOutputTokens + " tokens";
    }
    public static OpenRouterPolicy defaults() {
        return new OpenRouterPolicy(List.of(), List.of(), true, "allow", false, 4096);
    }
}
