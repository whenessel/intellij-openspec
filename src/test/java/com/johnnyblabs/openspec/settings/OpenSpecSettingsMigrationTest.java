package com.johnnyblabs.openspec.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The one-time migration for the removed persistent strict-validation setting. Pure — exercises the
 * {@code consumeStrictMigrationNotice()} seam directly, so one-time-ness and orphaned-option tolerance
 * are headlessly gated (not observable only through the notification center).
 */
class OpenSpecSettingsMigrationTest {

    @Test
    void firesExactlyOnce_forAPriorStrictOnUser() {
        OpenSpecSettings settings = new OpenSpecSettings();
        OpenSpecSettings.State state = new OpenSpecSettings.State();
        state.strictValidation = true; // the user had the old checkbox enabled
        settings.loadState(state);

        assertTrue(settings.consumeStrictMigrationNotice(), "fires once for a prior strict-on user");
        assertFalse(settings.consumeStrictMigrationNotice(), "and never again");
        assertFalse(settings.consumeStrictMigrationNotice(), "still never again after repeated calls");
    }

    @Test
    void neverFires_forADefaultOffUser() {
        OpenSpecSettings settings = new OpenSpecSettings(); // default state: strictValidation = false
        assertFalse(settings.consumeStrictMigrationNotice(), "a default (off) user sees no migration notice");
    }

    @Test
    void toleratesOrphanedStrictOption_otherFieldsSurvive() {
        // An existing openspec.xml still carrying strictValidation loads cleanly and unrelated
        // settings are untouched — the migration read never throws.
        OpenSpecSettings.State state = new OpenSpecSettings.State();
        state.strictValidation = true;
        state.cliTimeoutSeconds = 42;
        state.defaultSchema = "workspace-planning";

        OpenSpecSettings settings = new OpenSpecSettings();
        settings.loadState(state);

        assertNotNull(settings.getState());
        assertEquals(42, settings.getState().cliTimeoutSeconds, "unrelated fields survive the load");
        assertEquals("workspace-planning", settings.getState().defaultSchema);
    }
}
