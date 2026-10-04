package com.johnnyblabs.openspec.ai.backend;

import java.util.List;
import java.util.Objects;
/** Catalog IDs and wire model names can differ; absent modality evidence remains unknown. */
public record ModelDescriptor(String id, String displayName, String description, boolean isDefault,
                              String wireModel, List<ReasoningEffortDescriptor> reasoningEfforts,
                              String defaultReasoningEffort, CapabilitySupport textInput) {
 public ModelDescriptor {
  Objects.requireNonNull(id); Objects.requireNonNull(displayName); Objects.requireNonNull(wireModel);
  reasoningEfforts = List.copyOf(reasoningEfforts); Objects.requireNonNull(textInput);
 }
 public ModelDescriptor(String id, String displayName, String description, boolean isDefault) {
  this(id, displayName, description, isDefault, id, List.of(), "", CapabilitySupport.UNKNOWN);
 }
 public List<String> supportedReasoningEfforts() { return reasoningEfforts.stream().map(ReasoningEffortDescriptor::id).toList(); }
}
