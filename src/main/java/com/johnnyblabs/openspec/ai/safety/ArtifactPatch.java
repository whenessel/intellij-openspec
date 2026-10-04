package com.johnnyblabs.openspec.ai.safety;

import java.util.List;

/** Version 1 exact text-range patch; offsets use zero-based UTF-16 code units, end exclusive. */
public record ArtifactPatch(int schemaVersion, List<TextEdit> edits) {
    public ArtifactPatch { edits = List.copyOf(edits); }
    public record TextEdit(int start, int end, String oldText, String newText) { }
}
