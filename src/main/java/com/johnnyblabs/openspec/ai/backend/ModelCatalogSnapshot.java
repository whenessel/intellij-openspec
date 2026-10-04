package com.johnnyblabs.openspec.ai.backend;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
/** Stale metadata is suggestion-only; accountVerified reflects the most recent live auth check. */
public record ModelCatalogSnapshot(List<ModelDescriptor> models, Instant fetchedAt, boolean stale,
                                   boolean accountVerified, String detail) {
 public ModelCatalogSnapshot { models = List.copyOf(models); Objects.requireNonNull(fetchedAt); Objects.requireNonNull(detail); }
}
