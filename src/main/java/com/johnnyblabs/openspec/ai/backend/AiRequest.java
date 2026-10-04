package com.johnnyblabs.openspec.ai.backend;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
/** Context root must contain only the reviewed snapshot, never the project directory. */
public record AiRequest(String prompt, String model, Path contextRoot, Duration timeout, JsonObject outputSchema, String expectedAuthMode, String expectedAccountFingerprint, String reasoningEffort, Set<BackendCapability> requiredCapabilities) {
 public AiRequest {
  Objects.requireNonNull(prompt, "prompt");
  Objects.requireNonNull(contextRoot, "reviewed context root");
  Objects.requireNonNull(timeout, "timeout");
  if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("Timeout must be positive");
  outputSchema = outputSchema == null ? null : outputSchema.deepCopy();
  reasoningEffort = reasoningEffort == null ? "" : reasoningEffort.trim();
  requiredCapabilities = requiredCapabilities == null ? Set.of() : Set.copyOf(requiredCapabilities);
 }
 public JsonObject outputSchema() { return outputSchema == null ? null : outputSchema.deepCopy(); }
 public AiRequest(String prompt, String model, Path contextRoot, Duration timeout, JsonObject outputSchema, String expectedAuthMode, String expectedAccountFingerprint, String reasoningEffort) {
  this(prompt, model, contextRoot, timeout, outputSchema, expectedAuthMode, expectedAccountFingerprint, reasoningEffort, Set.of());
 }
 public AiRequest(String prompt, String model, Path contextRoot, Duration timeout, JsonObject outputSchema, String expectedAuthMode, String expectedAccountFingerprint) {
  this(prompt, model, contextRoot, timeout, outputSchema, expectedAuthMode, expectedAccountFingerprint, "", Set.of());
 }
 public AiRequest(String prompt, String model, Path contextRoot, Duration timeout, JsonObject outputSchema, String expectedAuthMode) {
  this(prompt, model, contextRoot, timeout, outputSchema, expectedAuthMode, null);
 }
 public AiRequest(String prompt, String model, Path contextRoot, Duration timeout, JsonObject outputSchema) {
  this(prompt, model, contextRoot, timeout, outputSchema, null, null);
 }
 public AiRequest(String prompt, String model, Path contextRoot, Duration timeout) {
  this(prompt, model, contextRoot, timeout, null, null, null);
 }
}
