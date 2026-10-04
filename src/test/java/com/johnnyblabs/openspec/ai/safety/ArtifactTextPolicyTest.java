package com.johnnyblabs.openspec.ai.safety;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ArtifactTextPolicyTest {
    @Test void consistentCrLfHasCanonicalLogicalLfText() throws Exception {
        assertEquals("# Heading\nbody\n", ArtifactTextPolicy.canonicalContent("# Heading\r\nbody\r\n"));
        assertEquals("# Heading\nbody\n", ArtifactTextPolicy.canonicalContent("# Heading\nbody\n"));
        assertEquals("one line", ArtifactTextPolicy.canonicalContent("one line"));
    }
    @Test void mixedAndLoneCrAreRejectedRatherThanSilentlyReinterpreted() {
        for (String value : java.util.List.of("first\r\nsecond\n", "first\nsecond\r\n", "first\rsecond", "last\r")) {
            assertThrows(IOException.class, () -> ArtifactTextPolicy.canonicalContent(value));
        }
    }
    @Test void invalidUnicodeIsNotLossilyEncoded() {
        assertThrows(IOException.class, () -> ArtifactTextPolicy.canonicalContent("\ud800"));
    }
}
