package com.johnnyblabs.openspec.ai.routing;

import com.johnnyblabs.openspec.ai.DeliveryMode;
import java.util.Objects;

/** Pure routing policy: an unavailable chosen backend stays chosen and never falls through. */
public final class AiRoutingPolicy {
    private AiRoutingPolicy() {}

    public record BackendSelection(String backendId, String provider, String model, String executable, String reasoningEffort) {
        public BackendSelection {
            backendId = text(backendId); provider = text(provider); model = text(model);
            executable = text(executable); reasoningEffort = text(reasoningEffort);
        }
    }
    public record BackendReadiness(boolean available, String detail) {
        public BackendReadiness { detail = text(detail); }
    }
    /** A null backend inherits the configured backend; a manual override requires none. */
    public record RunOverride(DeliveryMode mode, BackendSelection backend) {
        public RunOverride { Objects.requireNonNull(mode); }
        public RunOverride(DeliveryMode mode) { this(mode, null); }
    }
    public enum Source { RUN_OVERRIDE, SAVED_PREFERENCE, SELECTED_BACKEND, LEGACY_REST, DETECTED_TOOL, CLIPBOARD }
    public record RoutingSnapshot(DeliveryMode mode, BackendSelection backend, BackendReadiness readiness,
                                  Source source, String label) {
        public RoutingSnapshot { Objects.requireNonNull(mode); Objects.requireNonNull(readiness); Objects.requireNonNull(source); label = text(label); }
        public boolean executesBackend() { return mode == DeliveryMode.DIRECT_API; }
        public boolean available() { return !executesBackend() || readiness.available(); }
    }
    public record Inputs(DeliveryMode savedPreference, BackendSelection configuredBackend,
                         BackendReadiness configuredReadiness, boolean explicitBackend, boolean legacyRestConfigured,
                         String backendLabel, String detectedTool) {}

    public static RoutingSnapshot resolve(Inputs inputs, RunOverride override, BackendReadiness overrideReadiness,
                                          String overrideLabel) {
        Objects.requireNonNull(inputs);
        if (override != null) {
            BackendSelection selection = override.backend() == null ? inputs.configuredBackend() : override.backend();
            BackendReadiness readiness = override.backend() == null ? inputs.configuredReadiness() : overrideReadiness;
            return selected(override.mode(), selection, readiness, Source.RUN_OVERRIDE,
                    override.backend() == null ? inputs.backendLabel() : overrideLabel);
        }
        if (inputs.savedPreference() != null) return selected(inputs.savedPreference(), inputs.configuredBackend(),
                inputs.configuredReadiness(), Source.SAVED_PREFERENCE, inputs.backendLabel());
        if (inputs.explicitBackend()) return selected(DeliveryMode.DIRECT_API, inputs.configuredBackend(),
                inputs.configuredReadiness(), Source.SELECTED_BACKEND, inputs.backendLabel());
        if (inputs.legacyRestConfigured()) return selected(DeliveryMode.DIRECT_API, inputs.configuredBackend(),
                inputs.configuredReadiness(), Source.LEGACY_REST, inputs.backendLabel());
        if (!text(inputs.detectedTool()).isBlank()) return new RoutingSnapshot(DeliveryMode.CLIPBOARD, null,
                new BackendReadiness(true, "Manual paste suggested"), Source.DETECTED_TOOL, "Copy for " + inputs.detectedTool());
        return selected(DeliveryMode.CLIPBOARD, null, null, Source.CLIPBOARD, "");
    }
    private static RoutingSnapshot selected(DeliveryMode mode, BackendSelection backend, BackendReadiness readiness,
                                            Source source, String label) {
        if (mode != DeliveryMode.DIRECT_API) return new RoutingSnapshot(mode, null,
                new BackendReadiness(true, "Manual delivery"), source, mode.getDisplayName());
        return new RoutingSnapshot(mode, backend, readiness == null ? new BackendReadiness(false, "Backend status unavailable") : readiness,
                source, label == null || label.isBlank() ? "Selected AI backend" : label);
    }
    private static String text(String value) { return value == null ? "" : value; }
}
