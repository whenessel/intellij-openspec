package com.johnnyblabs.openspec.search;

import com.johnnyblabs.openspec.model.SpecFile;
import com.johnnyblabs.openspec.services.SpecParsingService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Content-search behavior for the re-homed spec Search Everywhere contributor. Seeds specs through
 * the REAL parser ({@link SpecParsingService#parseSpecContent}) — not hand-built model objects — so
 * a parser/matcher divergence can't make these green-but-vacuous. These port the assertions the old
 * Browse-tree content filter (`SpecContentFilterTest`) guarded, which now live in Search Everywhere.
 */
class SpecRequirementSearchTest {

    // A seeded spec whose distinguishing terms live ONLY in the body ("salutation") and ONLY in a
    // scenario clause ("waves") — absent from every requirement NAME and from the domain — so a
    // hit on them proves body/scenario content search, not label/filename matching.
    private static final String GREETING = String.join("\n",
            "# Greeting",
            "",
            "## Purpose",
            "Demonstrates content search.",
            "",
            "## Requirements",
            "",
            "### Requirement: Friendly greeting",
            "The system SHALL greet the user with a warm salutation.",
            "",
            "#### Scenario: On open",
            "- **WHEN** the user opens the project",
            "- **THEN** the app waves hello",
            "");

    private List<SpecFile> seed() {
        // Domain "gateway" + its file path carry a token ("gateway") that appears in NO requirement
        // name/body/scenario — so the doesNotMatchDomainOrFilenameAlone guard has a clean probe.
        SpecParsingService parser = new SpecParsingService(mock(com.intellij.openapi.project.Project.class));
        SpecFile spec = parser.parseSpecContent(GREETING, "gateway", "/openspec/specs/gateway/spec.md");
        return List.of(spec);
    }

    @Test
    void matchesBodyOnlyTerm() {
        List<SpecRequirementMatch> hits = SpecRequirementSearch.find(seed(), "salutation");
        assertEquals(1, hits.size(), "a body-only term must surface its requirement");
        assertEquals("Friendly greeting", hits.get(0).requirement().getName());
        assertEquals("gateway", hits.get(0).spec().getDomain());
    }

    @Test
    void matchesScenarioOnlyTerm() {
        List<SpecRequirementMatch> hits = SpecRequirementSearch.find(seed(), "waves");
        assertEquals(1, hits.size(), "a scenario-clause-only term must surface its requirement");
        assertEquals("Friendly greeting", hits.get(0).requirement().getName());
    }

    @Test
    void doesNotMatchDomainOrFilenameAlone() {
        // "gateway" occurs in the domain + file path but NOT in the requirement's name/body/scenario
        // text — so a content search must NOT surface it. This is the regression guard against
        // degrading back to label/filename matching (the Project View + SE file search cover that).
        assertTrue(SpecRequirementSearch.find(seed(), "gateway").isEmpty(),
                "a term present only in the domain/filename must not produce a content hit");
    }

    @Test
    void nonMatchingTermReturnsEmpty() {
        assertTrue(SpecRequirementSearch.find(seed(), "nonexistent-token-xyzzy").isEmpty());
    }

    @Test
    void isCaseInsensitive() {
        assertEquals(1, SpecRequirementSearch.find(seed(), "SALUTATION").size());
    }

    @Test
    void blankQueryReturnsEveryRequirement() {
        assertEquals(1, SpecRequirementSearch.find(seed(), "").size());
        assertEquals(1, SpecRequirementSearch.find(seed(), "   ").size());
    }

    @Test
    void nullSpecsReturnsEmpty() {
        assertTrue(SpecRequirementSearch.find(null, "x").isEmpty());
    }
}
