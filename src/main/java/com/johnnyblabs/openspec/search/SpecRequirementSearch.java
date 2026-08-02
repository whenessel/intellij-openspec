package com.johnnyblabs.openspec.search;

import com.johnnyblabs.openspec.model.Requirement;
import com.johnnyblabs.openspec.model.SpecFile;
import com.johnnyblabs.openspec.toolwindow.SpecContentMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure, headless-testable <em>content</em> search over parsed specs — the reusable core of the spec
 * Search Everywhere contributor. A requirement matches when the query occurs (case-insensitively) in
 * its name, body, or scenario text (via {@link SpecContentMatcher} — the same matcher the old
 * Browse-tree filter used, so behavior is preserved by construction). A blank query returns every
 * requirement.
 *
 * <p>Deliberately matches requirement <em>content</em> only, not the capability (domain) / filename:
 * that is the reach the Browse tree's content-search box uniquely provided (and which the Project
 * View can't do), whereas finding a spec by its file/domain name is already covered by Search
 * Everywhere's file contributor — matching the domain here would only duplicate it.
 *
 * <p>Free of any Swing/IntelliJ-platform dependency so the search behavior is unit-tested without a
 * running IDE; the {@code SearchEverywhereContributor} glue is a thin wrapper over this.
 */
public final class SpecRequirementSearch {

    private SpecRequirementSearch() {
    }

    public static List<SpecRequirementMatch> find(List<SpecFile> specs, String pattern) {
        List<SpecRequirementMatch> out = new ArrayList<>();
        if (specs == null) {
            return out;
        }
        String query = pattern == null ? "" : pattern.trim();
        for (SpecFile spec : specs) {
            for (Requirement requirement : spec.getRequirements()) {
                if (SpecContentMatcher.matches(requirement, query)) {
                    out.add(new SpecRequirementMatch(spec, requirement));
                }
            }
        }
        return out;
    }
}
