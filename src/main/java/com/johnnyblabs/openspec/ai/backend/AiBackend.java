package com.johnnyblabs.openspec.ai.backend;

import com.johnnyblabs.openspec.ai.AiApiException;
import java.util.List;
import java.util.function.Consumer;
/** Backend boundary shared by local processes and remote provider adapters. */
public interface AiBackend {
 String id();
 BackendCapabilities capabilities();
 BackendStatus probe() throws AiApiException;
 List<ModelDescriptor> models() throws AiApiException;
 AiResult generate(AiRequest request, CancellationToken cancellation, Consumer<String> onDelta) throws AiApiException;
}
