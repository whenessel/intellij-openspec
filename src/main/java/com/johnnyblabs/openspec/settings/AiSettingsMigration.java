package com.johnnyblabs.openspec.settings;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure migration of plugin-owned preferences. Legacy fields remain available for rollback. */
public final class AiSettingsMigration {
    public static final int CURRENT_VERSION = 1;
    private static final Set<String> REST_PROVIDERS = Set.of("CLAUDE", "OPENAI", "GEMINI", "OPENROUTER");
    private AiSettingsMigration() {}

    public record Result(int version, String backend, Map<String, String> providerModels) {
        public Result { providerModels = Map.copyOf(providerModels); }
    }

    public static Result migrate(int version, String backend, String legacyProvider, String legacyModel,
                                 Map<String, String> existingModels) {
        Map<String, String> models = new LinkedHashMap<>();
        if (existingModels != null) existingModels.forEach((key, value) -> {
            if (key != null && value != null) models.put(key, value);
        });
        String provider = canonicalProvider(legacyProvider);
        // Seed only the selected provider. A legacy model must never become another provider's default.
        if (version < CURRENT_VERSION && provider != null && legacyModel != null) {
            models.putIfAbsent(provider, legacyModel);
        }
        String route = backend == null || backend.isBlank() ? "REST" : backend;
        // Unknown future versions/values remain visible to compatibility checks; never silently route them.
        return new Result(Math.max(version, CURRENT_VERSION), route, models);
    }

    public record PersistedResult(Map<String, String> fields, Map<String, String> providerModels) {
        public PersistedResult {
            fields = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(fields));
            providerModels = Map.copyOf(providerModels);
        }
    }

    /** Full preference projection: unrelated CLI/delivery options and raw rollback values survive. */
    public static PersistedResult migratePersistedFields(Map<String, String> fields, Map<String, String> models) {
        Map<String, String> result = fields == null ? new LinkedHashMap<>() : new LinkedHashMap<>(fields);
        int version = 0;
        try { version = Integer.parseInt(result.getOrDefault("aiSettingsVersion", "0")); }
        catch (RuntimeException ignored) { /* Missing/invalid legacy metadata starts at version zero. */ }
        Result migration = migrate(version, result.get("aiBackend"), result.get("aiProvider"), result.get("aiModel"), models);
        result.put("aiSettingsVersion", Integer.toString(migration.version()));
        result.put("aiBackend", migration.backend());
        return new PersistedResult(result, migration.providerModels());
    }

    public record ProviderSelection(String provider, String model, Map<String, String> providerModels) {
        public ProviderSelection { providerModels = Map.copyOf(providerModels); }
    }

    public static ProviderSelection selectProvider(String previousProvider, String previousModel,
                                                    Map<String, String> existing, String nextProvider) {
        Map<String, String> models = new LinkedHashMap<>(migrate(CURRENT_VERSION, "REST", null, null, existing).providerModels());
        String previous = canonicalProvider(previousProvider);
        if (previous != null && previousModel != null) models.putIfAbsent(previous, previousModel);
        String next = canonicalProvider(nextProvider);
        return new ProviderSelection(nextProvider, next == null ? "" : models.getOrDefault(next, ""), models);
    }

    public static ProviderSelection updateSelectedModel(String provider, String model, Map<String, String> existing) {
        Map<String, String> models = new LinkedHashMap<>(migrate(CURRENT_VERSION, "REST", null, null, existing).providerModels());
        String key = canonicalProvider(provider);
        if (key != null) models.put(key, model == null ? "" : model);
        return new ProviderSelection(provider, model, models);
    }

    /** Adopt all edited provider drafts before changing the active provider. */
    public static ProviderSelection applyProviderModels(String provider, String legacyModel, Map<String, String> incoming) {
        Map<String, String> models = new LinkedHashMap<>(migrate(CURRENT_VERSION, "REST", null, null, incoming).providerModels());
        String key = canonicalProvider(provider);
        String model = legacyModel;
        if (key != null) {
            if (models.containsKey(key)) model = models.get(key);
            else models.put(key, legacyModel == null ? "" : legacyModel);
        }
        return new ProviderSelection(provider, model, models);
    }

    public static String readModel(String provider, String selectedProvider, String legacyModel, Map<String, String> models) {
        String key = canonicalProvider(provider);
        if (key != null && models != null && models.containsKey(key)) return models.get(key) == null ? "" : models.get(key);
        return java.util.Objects.equals(provider, selectedProvider) && legacyModel != null ? legacyModel : "";
    }

    public static String canonicalProvider(String provider) {
        if (provider == null) return null;
        String normalized = provider.trim().toUpperCase(Locale.ROOT);
        return REST_PROVIDERS.contains(normalized) ? normalized : null;
    }
}
