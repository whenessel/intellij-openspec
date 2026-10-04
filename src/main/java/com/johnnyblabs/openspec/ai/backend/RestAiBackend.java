package com.johnnyblabs.openspec.ai.backend;

import com.johnnyblabs.openspec.ai.AiApiException;
import com.johnnyblabs.openspec.ai.AiProvider;
import com.johnnyblabs.openspec.ai.DirectApiService;
import java.util.List;
import java.util.function.Consumer;

/** Existing provider contracts stay in their transport; workflows depend on AiBackend. */
public final class RestAiBackend implements AiBackend {
    private final DirectApiService transport;
    private final AiProvider provider;
    private final String selectedModel;
    public RestAiBackend(DirectApiService transport, AiProvider provider, String selectedModel) {
        this.transport = transport;
        this.provider = provider;
        this.selectedModel = selectedModel == null || selectedModel.isBlank() ? provider.getDefaultModel() : selectedModel;
    }
    @Override public String id() { return "REST:" + provider.name(); }
    @Override public BackendCapabilities capabilities() { return new BackendCapabilities(false, true, false, false); }
    @Override public BackendStatus probe() {
        return new BackendStatus(transport != null && transport.isConfigured(), "apikey", provider.getDisplayName() + " API billing", "HTTP");
    }
    @Override public List<ModelDescriptor> models() {
        return provider.getModels().stream().map(id -> new ModelDescriptor(id, id, "Built-in REST suggestion; manual override supported", id.equals(provider.getDefaultModel()))).toList();
    }
    @Override public AiResult generate(AiRequest request, CancellationToken cancellation, Consumer<String> onDelta) throws AiApiException {
        if (cancellation.isCancelled()) throw new AiApiException("AI request canceled");
        if (transport == null || !transport.isConfigured()) throw new AiApiException("REST provider is not configured");
        String text = transport.generateRaw(request.prompt(), cancellation::isCancelled);
        if (cancellation.isCancelled()) throw new AiApiException("AI request canceled");
        onDelta.accept(text);
        return new AiResult(text, id(), selectedModel);
    }
}
