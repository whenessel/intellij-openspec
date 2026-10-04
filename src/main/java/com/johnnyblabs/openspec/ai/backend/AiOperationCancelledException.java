package com.johnnyblabs.openspec.ai.backend;

import com.johnnyblabs.openspec.ai.AiApiException;

/** Explicit cancellation outcome; callers must not render it as backend unavailability. */
public final class AiOperationCancelledException extends AiApiException {
    public AiOperationCancelledException() {
        super("AI operation cancelled; no result was applied.");
    }
}
