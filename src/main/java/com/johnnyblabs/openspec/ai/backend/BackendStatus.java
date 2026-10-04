package com.johnnyblabs.openspec.ai.backend;

/** Auth mode is reported by the provider; unknown must never be treated as subscription. */
public record BackendStatus(boolean available, String authMode, String detail, String version, String limitSummary, String accountFingerprint) {
    public BackendStatus(boolean available, String authMode, String detail, String version, String limitSummary) {
        this(available, authMode, detail, version, limitSummary, "");
    }
    public BackendStatus(boolean available, String authMode, String detail, String version) {
        this(available, authMode, detail, version, "Limits unavailable", "");
    }
}
