package com.johnnyblabs.openspec.ai.backend;

@FunctionalInterface
public interface CancellationToken {
 CancellationToken NONE = () -> false;
 boolean isCancelled();
}
