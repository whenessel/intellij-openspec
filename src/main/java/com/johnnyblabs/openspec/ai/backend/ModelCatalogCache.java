package com.johnnyblabs.openspec.ai.backend;

import java.time.Clock;
import java.time.Duration;
import java.util.*;
/** Bounded metadata-only cache; keys never contain credentials or raw account identifiers. */
public final class ModelCatalogCache {
 public record Key(String executable, String authMode, String accountFingerprint, String protocolVersion) {
  public Key { Objects.requireNonNull(executable); Objects.requireNonNull(authMode); Objects.requireNonNull(accountFingerprint); Objects.requireNonNull(protocolVersion); }
  public boolean cacheable() { return !accountFingerprint.isBlank(); }
 }
 private final Clock clock;
 private final Duration ttl;
 private final int capacity;
 private final LinkedHashMap<Key, ModelCatalogSnapshot> entries = new LinkedHashMap<>(16, .75f, true);
 public ModelCatalogCache(Clock clock, Duration ttl, int capacity) {
  this.clock=Objects.requireNonNull(clock); this.ttl=Objects.requireNonNull(ttl); this.capacity=capacity;
  if(ttl.isNegative() || ttl.isZero() || capacity<1)throw new IllegalArgumentException("Positive TTL and capacity required");
 }
 public synchronized Optional<ModelCatalogSnapshot> fresh(Key key) {
  ModelCatalogSnapshot value = entries.get(key); if(value==null || !key.cacheable())return Optional.empty();
  Duration age=Duration.between(value.fetchedAt(), clock.instant());
  return age.isNegative() || age.compareTo(ttl)>=0 ? Optional.empty() : Optional.of(value);
 }
 public synchronized Optional<ModelCatalogSnapshot> stale(Key key) { return key.cacheable() ? Optional.ofNullable(entries.get(key)) : Optional.empty(); }
 public synchronized void put(Key key, ModelCatalogSnapshot snapshot) {
  if(!key.cacheable() || snapshot.stale() || !snapshot.accountVerified())return;
  entries.keySet().removeIf(existing -> existing.executable().equals(key.executable()) && !existing.equals(key));
  entries.put(key, snapshot);
  while(entries.size()>capacity) entries.remove(entries.keySet().iterator().next());
 }
 public synchronized void invalidateExecutable(String executable) { entries.keySet().removeIf(key -> key.executable().equals(executable)); }
 public Clock clock() { return clock; }
}
