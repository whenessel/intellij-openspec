package com.johnnyblabs.openspec.ai.backend;

import com.johnnyblabs.openspec.ai.AiApiException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
/** A cancellation, timeout or failure cannot be overwritten by a late completion. */
public final class TurnTerminalState {
 public enum Status { COMPLETED, FAILED, CANCELLED, TIMED_OUT }
 public record Outcome(Status status, String text) {}
 private final AtomicReference<Outcome> terminal = new AtomicReference<>();
 public boolean finish(Status status, String text) {
  Objects.requireNonNull(status);
  if(status == Status.COMPLETED && (text == null || text.isBlank()))throw new IllegalArgumentException("Completed result must contain text");
  return terminal.compareAndSet(null, new Outcome(status, status == Status.COMPLETED ? text : ""));
 }
 public Outcome outcome() { return terminal.get(); }
 public String requireCompleted() throws AiApiException {
  Outcome value=terminal.get();
  if(value==null || value.status()!=Status.COMPLETED)throw new AiApiException("AI turn has no accepted successful completion.");
  return value.text();
 }
}
