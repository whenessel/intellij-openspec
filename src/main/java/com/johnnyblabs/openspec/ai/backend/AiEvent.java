package com.johnnyblabs.openspec.ai.backend;

import java.util.Objects;

/** Backend-neutral execution events; only Completed carries an applicable successful result. */
public sealed interface AiEvent permits AiEvent.Delta, AiEvent.Completed, AiEvent.Cancelled, AiEvent.Failed {
    record Delta(String text) implements AiEvent { public Delta { Objects.requireNonNull(text); } }
    record Completed(AiResult result) implements AiEvent { public Completed { Objects.requireNonNull(result); } }
    record Cancelled() implements AiEvent { }
    record Failed(String detail) implements AiEvent { public Failed { Objects.requireNonNull(detail); } }
}
