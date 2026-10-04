package com.johnnyblabs.openspec.ai.backend;

import com.johnnyblabs.openspec.ai.AiApiException;
import java.util.Objects;
import java.util.Set;
/** Unknown support never satisfies a required execution capability. */
public record BackendCapabilities(CapabilitySupport streamingSupport, CapabilitySupport cancellationSupport,
                                  CapabilitySupport structuredOutputSupport, CapabilitySupport workspaceWritesSupport) {
 public BackendCapabilities { Objects.requireNonNull(streamingSupport); Objects.requireNonNull(cancellationSupport); Objects.requireNonNull(structuredOutputSupport); Objects.requireNonNull(workspaceWritesSupport); }
 public BackendCapabilities(boolean streaming, boolean cancellation, boolean structuredOutput, boolean workspaceWrites) {
  this(support(streaming), support(cancellation), support(structuredOutput), support(workspaceWrites));
 }
 private static CapabilitySupport support(boolean value) { return value ? CapabilitySupport.SUPPORTED : CapabilitySupport.UNSUPPORTED; }
 public static BackendCapabilities unknown() { return new BackendCapabilities(CapabilitySupport.UNKNOWN, CapabilitySupport.UNKNOWN, CapabilitySupport.UNKNOWN, CapabilitySupport.UNKNOWN); }
 public boolean streaming() { return streamingSupport == CapabilitySupport.SUPPORTED; }
 public boolean cancellation() { return cancellationSupport == CapabilitySupport.SUPPORTED; }
 public boolean structuredOutput() { return structuredOutputSupport == CapabilitySupport.SUPPORTED; }
 public boolean workspaceWrites() { return workspaceWritesSupport == CapabilitySupport.SUPPORTED; }
 public CapabilitySupport support(BackendCapability capability) { return switch(capability) {
  case STREAMING -> streamingSupport; case CANCELLATION -> cancellationSupport; case STRUCTURED_OUTPUT -> structuredOutputSupport; case WORKSPACE_WRITES -> workspaceWritesSupport;
 }; }
 public void require(Set<BackendCapability> required) throws AiApiException {
  for (BackendCapability capability : required) if (support(capability) != CapabilitySupport.SUPPORTED)
   throw new AiApiException("Required backend capability " + capability + " is " + support(capability) + ". Choose a supported backend; no fallback was performed.");
 }
}
