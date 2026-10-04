package com.johnnyblabs.openspec.ai.backend;

import java.util.Objects;
public record ReasoningEffortDescriptor(String id, String description) {
 public ReasoningEffortDescriptor { Objects.requireNonNull(id); Objects.requireNonNull(description); }
}
