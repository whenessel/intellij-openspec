package com.johnnyblabs.openspec.ai.backend;

public record BackendCapabilities(boolean streaming, boolean cancellation, boolean structuredOutput, boolean workspaceWrites) {}
