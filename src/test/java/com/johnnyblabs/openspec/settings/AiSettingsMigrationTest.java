package com.johnnyblabs.openspec.settings;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.LinkedHashMap;
import javax.xml.parsers.DocumentBuilderFactory;
import static org.junit.jupiter.api.Assertions.*;

class AiSettingsMigrationTest {
    @Test void historicalPersistedPreferencesMigrateWithoutInventingRoutesOrCrossProviderModels() throws Exception {
        for (String name : java.util.List.of("none", "blank", "invalid", "clipboard-provider", "provider-configured")) {
            Map<String, String> legacy = fixture(name);
            Map<String, String> original = Map.copyOf(legacy);
            var migrated = AiSettingsMigration.migratePersistedFields(legacy, Map.of());
            assertEquals("1", migrated.fields().get("aiSettingsVersion"));
            assertEquals("REST", migrated.fields().get("aiBackend"));
            String provider = AiSettingsMigration.canonicalProvider(legacy.get("aiProvider"));
            assertEquals(provider == null ? Map.of() : Map.of(provider, legacy.get("aiModel")), migrated.providerModels());
            for (var option : original.entrySet()) {
                assertEquals(option.getValue(), migrated.fields().get(option.getKey()),
                        "Result must preserve raw rollback and independent delivery/CLI option: " + option.getKey());
            }
            assertEquals(original, legacy, "Input is immutable to migration");
            var repeated = AiSettingsMigration.migratePersistedFields(migrated.fields(), migrated.providerModels());
            assertEquals(migrated, repeated);
        }
    }

    @Test void migrationIsIdempotentAndPreservesExplicitModelsAndFutureState() {
        Map<String, String> before = new LinkedHashMap<>(Map.of("OPENAI", "saved", "future-provider", "manual"));
        var once = AiSettingsMigration.migrate(0, "LOCAL_CODEX", "OpenAI", "legacy", before);
        var twice = AiSettingsMigration.migrate(once.version(), once.backend(), "OpenAI", "legacy", once.providerModels());
        assertEquals(once, twice);
        assertEquals("saved", once.providerModels().get("OPENAI"));
        assertEquals("LOCAL_CODEX", once.backend());
        assertEquals(Map.of("OPENAI", "saved", "future-provider", "manual"), before);
        var future = AiSettingsMigration.migrate(42, "UNKNOWN_BACKEND", "CLAUDE", "legacy", Map.of());
        assertEquals(42, future.version());
        assertEquals("UNKNOWN_BACKEND", future.backend());
        assertTrue(future.providerModels().isEmpty());
    }

    @Test void onlyRecognizedProviderOwnsTheLegacyModel() {
        assertEquals(Map.of("CLAUDE", ""), AiSettingsMigration.migrate(0, "", " claude ", "", null).providerModels());
        assertTrue(AiSettingsMigration.migrate(0, "", "NONE", "orphan", null).providerModels().isEmpty());
        assertTrue(AiSettingsMigration.migrate(0, "", "INVALID", "orphan", null).providerModels().isEmpty());
        assertEquals("REST", AiSettingsMigration.migrate(0, " ", null, null, null).backend());
    }

    @Test void selectedProviderModelsRemainIndependentAcrossRepeatedSwitchesAndBlankOverrides() {
        var initial = AiSettingsMigration.migrate(0, null, "OPENAI", "legacy-openai", Map.of());
        var gemini = AiSettingsMigration.selectProvider("OPENAI", "legacy-openai", initial.providerModels(), "GEMINI");
        assertEquals("", gemini.model(), "Do not copy OpenAI model to Gemini");
        gemini = AiSettingsMigration.updateSelectedModel("GEMINI", "manual-gemini", gemini.providerModels());
        var openai = AiSettingsMigration.selectProvider("GEMINI", "manual-gemini", gemini.providerModels(), "OPENAI");
        assertEquals("legacy-openai", openai.model());
        assertEquals("manual-gemini", AiSettingsMigration.readModel("GEMINI", "OPENAI", openai.model(), openai.providerModels()));
        var blank = AiSettingsMigration.updateSelectedModel("OPENAI", "", openai.providerModels());
        assertEquals("", AiSettingsMigration.readModel("OPENAI", "OPENAI", "legacy-openai", blank.providerModels()));
        assertEquals("manual-gemini", AiSettingsMigration.selectProvider("OPENAI", "", blank.providerModels(), "GEMINI").model());
        assertEquals("", AiSettingsMigration.readModel(null, null, null, Map.of()));
        assertEquals("historical", AiSettingsMigration.readModel("INVALID", "INVALID", "historical", Map.of()));
    }

    @Test void applyAdoptsOldProviderDraftBeforeSwitchingSelectedProvider() {
        // Settings still selects OpenAI, but the user edited it and switched the UI to Claude before Apply.
        var incoming = Map.of("OPENAI", "edited-openai-draft", "CLAUDE", "edited-claude-draft");
        var bound = AiSettingsMigration.applyProviderModels("OPENAI", "old-openai", incoming);
        assertEquals("edited-openai-draft", bound.model(), "Rollback field must follow incoming selected-provider draft");
        var selected = AiSettingsMigration.selectProvider(bound.provider(), bound.model(), bound.providerModels(), "CLAUDE");
        var applied = AiSettingsMigration.updateSelectedModel("CLAUDE", "edited-claude-draft", selected.providerModels());
        assertEquals("edited-openai-draft", applied.providerModels().get("OPENAI"));
        assertEquals("edited-claude-draft", applied.model());
        assertEquals("edited-openai-draft", AiSettingsMigration.selectProvider("CLAUDE", applied.model(), applied.providerModels(), "OPENAI").model());
        assertEquals(incoming, applied.providerModels());
    }

    private static Map<String, String> fixture(String name) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var stream = AiSettingsMigrationTest.class.getResourceAsStream("/fixtures/settings/legacy-" + name + ".xml")) {
            assertNotNull(stream);
            var options = factory.newDocumentBuilder().parse(stream).getElementsByTagName("option");
            Map<String, String> result = new LinkedHashMap<>();
            for (int i = 0; i < options.getLength(); i++) {
                var attributes = options.item(i).getAttributes();
                result.put(attributes.getNamedItem("name").getNodeValue(), attributes.getNamedItem("value").getNodeValue());
            }
            return result;
        }
    }
}
