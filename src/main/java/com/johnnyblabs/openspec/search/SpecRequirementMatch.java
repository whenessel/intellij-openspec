package com.johnnyblabs.openspec.search;

import com.johnnyblabs.openspec.model.Requirement;
import com.johnnyblabs.openspec.model.SpecFile;

/**
 * A single (spec, requirement) hit surfaced by the spec content Search Everywhere contributor.
 * Pure value type — no Swing/platform dependency, so the search core stays headlessly testable.
 */
public record SpecRequirementMatch(SpecFile spec, Requirement requirement) {

    /** The requirement name — the primary text of a Search Everywhere result row. */
    public String presentableText() {
        return requirement.getName();
    }

    /** The owning capability (domain) — the secondary/location text of a result row. */
    public String locationText() {
        return spec.getDomain();
    }

    /** Absolute path of the backing {@code spec.md}, used to open the file on selection. */
    public String filePath() {
        return spec.getFilePath();
    }
}
