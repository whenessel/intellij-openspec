package com.johnnyblabs.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.AiExecutionService;
import com.johnnyblabs.openspec.ai.AiProvider;
import com.johnnyblabs.openspec.ai.DeliveryMode;
import com.johnnyblabs.openspec.ai.routing.AiRoutingPolicy;
import com.johnnyblabs.openspec.ai.routing.AiRoutingPolicy.*;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;

/** Resolves every delivery through the pure policy using cached readiness, without launching probes. */
@Service(Service.Level.PROJECT)
public final class DeliveryMethodResolver {
    private final Project project;
    public DeliveryMethodResolver(Project project) { this.project = project; }

    public ResolvedMethod resolve() {
        RoutingSnapshot snapshot = resolveSnapshot(null);
        return new ResolvedMethod(snapshot.mode(), snapshot.label());
    }
    public RoutingSnapshot resolveSnapshot(RunOverride override) {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        DeliveryMode saved = parse(settings.getPreferredDeliveryMethod());
        // Manual overrides/preferences must not even consult an execution service or credentials.
        if (override != null && override.mode() != DeliveryMode.DIRECT_API
                || override == null && saved != null && saved != DeliveryMode.DIRECT_API) {
            return AiRoutingPolicy.resolve(new Inputs(saved, null, null, false, false, "", ""), override, null, "");
        }
        String backend = settings.getAiBackend();
        if (backend == null || backend.isBlank()) backend = "REST";
        AiProvider provider = "REST".equals(backend) ? AiProvider.fromString(settings.getAiProvider()) : AiProvider.NONE;
        BackendSelection configured = new BackendSelection(backend, provider.name(),
                "LOCAL_CODEX".equals(backend) ? settings.getCodexModel() : settings.getAiModel(),
                "LOCAL_CODEX".equals(backend) ? settings.getCodexExecutable() : "",
                "LOCAL_CODEX".equals(backend) ? settings.getCodexReasoningEffort() : "");
        boolean explicit = !"REST".equals(backend);
        boolean legacy = provider != AiProvider.NONE;
        String tool = "";
        BackendReadiness readiness = new BackendReadiness(false, "Backend not configured");
        boolean executionSelected = saved == DeliveryMode.DIRECT_API || explicit || legacy || override != null;
        if (executionSelected) {
            AiExecutionService execution = project.getService(AiExecutionService.class);
            if (execution != null) readiness = execution.cachedBackendReadiness();
        } else {
            AiToolDetectionService detection = project.getService(AiToolDetectionService.class);
            if (detection != null && detection.hasDetectedTools()) tool = detection.getPrimaryToolLabel();
        }
        BackendReadiness overridden = override != null && override.backend() != null && !override.backend().equals(configured)
                ? new BackendReadiness(false, "Override has not been probed; execution must validate its own readiness") : readiness;
        return AiRoutingPolicy.resolve(new Inputs(saved, configured, readiness, explicit, legacy, label(configured), tool),
                override, overridden, override == null ? "" : label(override.backend()));
    }
    private static DeliveryMode parse(String value) {
        if (value == null || value.isBlank()) return null;
        try { return DeliveryMode.valueOf(value); } catch (IllegalArgumentException invalid) { return null; }
    }
    private static String label(BackendSelection backend) {
        if (backend == null) return "Selected AI backend";
        if ("LOCAL_CODEX".equals(backend.backendId())) return "Generate via Local Codex [integrated]";
        if ("REST".equals(backend.backendId())) return "Generate via " + AiProvider.fromString(backend.provider()).getDisplayName();
        return "Unsupported backend: " + backend.backendId();
    }
    public void savePreference(DeliveryMode mode) { OpenSpecSettings.getInstance(project).setPreferredDeliveryMethod(mode.name()); }
    public boolean hasPreference() {
        String preferred = OpenSpecSettings.getInstance(project).getPreferredDeliveryMethod();
        return preferred != null && !preferred.isBlank();
    }
    public record ResolvedMethod(DeliveryMode mode, String label) {}
}
