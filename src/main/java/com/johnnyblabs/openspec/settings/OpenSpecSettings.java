package com.johnnyblabs.openspec.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.services.ConfigService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
@State(name = "OpenSpecSettings", storages = @Storage("openspec.xml"))
public final class OpenSpecSettings implements PersistentStateComponent<OpenSpecSettings.State> {

    private State state = new State();

    public static OpenSpecSettings getInstance(@NotNull Project project) {
        return project.getService(OpenSpecSettings.class);
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("aiSettingsVersion", Integer.toString(state.aiSettingsVersion));
        fields.put("aiBackend", state.aiBackend);
        fields.put("aiProvider", state.aiProvider);
        fields.put("aiModel", state.aiModel);
        fields.put("cliPath", state.cliPath);
        fields.put("profile", state.profile);
        fields.put("preferredDeliveryMethod", state.preferredDeliveryMethod);
        fields.put("preferredTool", state.preferredTool);
        AiSettingsMigration.PersistedResult migration = AiSettingsMigration.migratePersistedFields(fields, state.aiProviderModels);
        state.aiSettingsVersion = Integer.parseInt(migration.fields().get("aiSettingsVersion"));
        state.aiBackend = migration.fields().get("aiBackend");
        state.aiProviderModels = new java.util.LinkedHashMap<>(migration.providerModels());
        this.state = state;
    }

    public String getVersionOverride() {
        return state.versionOverride;
    }

    public void setVersionOverride(String version) {
        state.versionOverride = version;
    }

    public String getCliPath() {
        return state.cliPath;
    }

    public void setCliPath(String path) {
        state.cliPath = path;
    }

    /**
     * Returns the active <b>workflow profile</b> (per-user; switched via
     * {@code openspec config profile <preset>}). One of {@code "core"}, {@code "custom"},
     * or empty string ({@code ""}) for "use the CLI's active profile."
     *
     * <p>Distinct from:
     * <ul>
     *   <li>{@link #getDefaultSchema()} — the OpenSpec schema concept (e.g. {@code spec-driven});
     *       per-project, used by {@code WorkflowActionPanel} and {@code ProposeChangeDialog}.</li>
     *   <li>{@code OpenSpecConfig.profile} (a {@link java.util.Map}) — the per-project metadata
     *       block from {@code openspec/config.yaml}'s {@code profile:} section
     *       (name, description, language, framework); displayed in the spec tree.</li>
     * </ul>
     *
     * <p>The field name {@code profile} predates the OpenSpec 1.2.0+ "workflow profile" /
     * "schema" / "project profile" three-way split. The semantic intent of this field has
     * always been the workflow profile.
     */
    public String getProfile() {
        return state.profile;
    }

    /** @see #getProfile() */
    public void setProfile(String profile) {
        state.profile = profile;
    }

    public boolean isAutoRefresh() {
        return state.autoRefresh;
    }

    public void setAutoRefresh(boolean autoRefresh) {
        state.autoRefresh = autoRefresh;
    }

    /**
     * One-time migration signal for the removed persistent strict-validation setting. Returns
     * {@code true} exactly once — for a user who had the old checkbox ON and hasn't yet been notified —
     * then records the notice so it never fires again. Strict is now a per-run choice (the
     * {@code OpenSpec.ValidateStrict} action); this only routes a prior strict-on user to it. A user on
     * the default (off) never gets a signal. Package-visible for the migration-trigger + tests.
     */
    public boolean consumeStrictMigrationNotice() {
        if (state.strictValidation && !state.migratedStrictNotice) {
            state.migratedStrictNotice = true;
            state.strictValidation = false; // clear the orphaned preference; the notice fires once
            return true;
        }
        return false;
    }

    /** Empty legacy state keeps the existing REST route; selecting Codex is explicit. */
    public String getAiBackend() { return state.aiBackend == null || state.aiBackend.isBlank() ? "REST" : state.aiBackend; }
    public void setAiBackend(String backend) { state.aiBackend = backend; }
    public String getCodexExecutable() { return state.codexExecutable == null || state.codexExecutable.isBlank() ? "codex" : state.codexExecutable; }
    public void setCodexExecutable(String executable) { state.codexExecutable = executable; }
    public String getCodexModel() { return state.codexModel == null ? "" : state.codexModel; }
    public void setCodexModel(String model) { state.codexModel = model; }
    public int getCodexTimeoutSeconds() { return state.codexTimeoutSeconds > 0 ? state.codexTimeoutSeconds : 180; }
    public void setCodexTimeoutSeconds(int timeout) { state.codexTimeoutSeconds = timeout; }
    public boolean isCodexApiBillingAcknowledged() { return state.codexApiBillingAcknowledged; }
    public void setCodexApiBillingAcknowledged(boolean acknowledged) { state.codexApiBillingAcknowledged = acknowledged; }
    public int getAiContextMaxBytes() { return state.aiContextMaxBytes > 0 ? state.aiContextMaxBytes : 262144; }
    public void setAiContextMaxBytes(int maxBytes) { state.aiContextMaxBytes = maxBytes; }

    public String getAiProvider() {
        return state.aiProvider == null ? "" : state.aiProvider;
    }

    public void setAiProvider(String provider) {
        AiSettingsMigration.ProviderSelection selected = AiSettingsMigration.selectProvider(
                state.aiProvider, state.aiModel, state.aiProviderModels, provider);
        state.aiProvider = selected.provider();
        state.aiModel = selected.model();
        state.aiProviderModels = new java.util.LinkedHashMap<>(selected.providerModels());
        state.aiSettingsVersion = Math.max(state.aiSettingsVersion, AiSettingsMigration.CURRENT_VERSION);
    }

    public String getAiModel() { return getAiModel(state.aiProvider); }

    public String getAiModel(String provider) {
        return AiSettingsMigration.readModel(provider, state.aiProvider, state.aiModel, state.aiProviderModels);
    }

    public void setAiModel(String model) {
        AiSettingsMigration.ProviderSelection selected = AiSettingsMigration.updateSelectedModel(
                state.aiProvider, model, state.aiProviderModels);
        state.aiModel = selected.model(); // retained for downgrade/rollback compatibility
        state.aiProviderModels = new java.util.LinkedHashMap<>(selected.providerModels());
        state.aiSettingsVersion = Math.max(state.aiSettingsVersion, AiSettingsMigration.CURRENT_VERSION);
    }

    public java.util.Map<String, String> getAiProviderModels() {
        return state.aiProviderModels == null ? java.util.Map.of() : java.util.Map.copyOf(state.aiProviderModels);
    }
    public void setAiProviderModels(java.util.Map<String, String> models) {
        if (models != null) {
            AiSettingsMigration.ProviderSelection selected = AiSettingsMigration.applyProviderModels(
                    state.aiProvider, state.aiModel, models);
            state.aiProviderModels = new java.util.LinkedHashMap<>(selected.providerModels());
            state.aiModel = selected.model();
            state.aiSettingsVersion = Math.max(state.aiSettingsVersion, AiSettingsMigration.CURRENT_VERSION);
        }
    }

    public String getCodexReasoningEffort() { return state.codexReasoningEffort == null ? "" : state.codexReasoningEffort; }
    public void setCodexReasoningEffort(String effort) { state.codexReasoningEffort = effort; }

    public String getPreferredDeliveryMethod() {
        return state.preferredDeliveryMethod;
    }

    public void setPreferredDeliveryMethod(String method) {
        state.preferredDeliveryMethod = method;
    }

    public String getPreferredTool() {
        return state.preferredTool;
    }

    public void setPreferredTool(String tool) {
        state.preferredTool = tool;
    }

    public boolean isSetupCompleted() {
        return state.setupCompleted;
    }

    public void setSetupCompleted(boolean completed) {
        state.setupCompleted = completed;
    }

    public boolean isFirstProposalCompleted() {
        return state.firstProposalCompleted;
    }

    public void setFirstProposalCompleted(boolean completed) {
        state.firstProposalCompleted = completed;
    }

    /**
     * Returns the effective version: settings override if set, else config.yaml version.
     */
    public String getEffectiveVersion(@NotNull Project project) {
        if (state.versionOverride != null && !state.versionOverride.isEmpty()) {
            return state.versionOverride;
        }
        ConfigService configService = project.getService(ConfigService.class);
        if (configService != null && configService.getConfig() != null) {
            return configService.getConfig().getVersion();
        }
        return null;
    }

    public int getCliTimeoutSeconds() {
        return state.cliTimeoutSeconds;
    }

    public void setCliTimeoutSeconds(int timeout) {
        state.cliTimeoutSeconds = timeout;
    }

    /**
     * Returns the raw Default schema setting — may be empty (user hasn't chosen one).
     *
     * <p><b>When to use this vs {@link #getEffectiveSchema(Project)}:</b>
     * <ul>
     *   <li><b>Combo / dropdown population</b> (e.g., {@code ProposeChangeDialog},
     *       {@code WorkflowActionPanel}): call this. An empty result lets the combo
     *       keep its first option highlighted; forcing the {@code "spec-driven"} fallback
     *       would silently overwrite the user's "no preference yet" state.</li>
     *   <li><b>Write paths</b> (e.g., {@code ScaffoldingService.initBuiltIn} writing
     *       {@code openspec/config.yaml}): call {@code getEffectiveSchema(project)}. Init
     *       MUST write a schema string — there is no blank option — so the fallback is
     *       required.</li>
     * </ul>
     *
     * @return the raw setting value (possibly empty string, never null in practice
     *         since {@link State#defaultSchema} initializes to empty)
     */
    public String getDefaultSchema() {
        return state.defaultSchema;
    }

    public void setDefaultSchema(String schema) {
        state.defaultSchema = schema;
    }

    /**
     * Returns the effective default schema for write paths (e.g. built-in init).
     *
     * <p>When the user has chosen a Default schema in Settings → Tools → OpenSpec, returns
     * that value. Otherwise falls back to the literal {@code "spec-driven"}, which is the
     * historical default and matches the upstream CLI's {@code DEFAULT_SCHEMA} constant
     * ({@code @fission-ai/openspec/dist/core/init.js}). The fallback is intentionally a
     * literal — not computed from {@link com.johnnyblabs.openspec.version.VersionSupport}'s
     * valid-schemas set — so the default stays stable across upstream schema additions.
     *
     * <p>Sibling to {@link #getEffectiveVersion(Project)} — callers writing config.yaml
     * should resolve both fields through these helpers so the setting-vs-fallback contract
     * is encapsulated near the field, not duplicated at every call site.
     *
     * @param project the current project (currently unused; reserved for a future config.yaml
     *                fallback if the helper expands beyond init-time use, mirroring
     *                {@code getEffectiveVersion}'s reach into {@code ConfigService}).
     *                TODO(post-v0.4.0): if no caller reaches into ConfigService through this
     *                helper by then, drop the parameter and the @NotNull contract.
     * @return non-null, non-empty schema name
     */
    public String getEffectiveSchema(@NotNull Project project) {
        if (state.defaultSchema != null && !state.defaultSchema.isEmpty()) {
            return state.defaultSchema;
        }
        return "spec-driven";
    }

    public static class State {
        public String versionOverride = "";
        public String cliPath = "";
        /** Workflow profile preset: {@code "core"}, {@code "custom"}, or {@code ""} (CLI default). See {@link OpenSpecSettings#getProfile()}. */
        public String profile = "";
        public boolean autoRefresh = true;
        /**
         * Migration-only (strict is now a per-run action, not a setting). Read once by
         * {@link OpenSpecSettings#consumeStrictMigrationNotice()} to route a prior strict-on user to the
         * new action, then cleared. Retained one release so an existing {@code openspec.xml} with this
         * option loads cleanly; scheduled for removal thereafter.
         */
        public boolean strictValidation = false;
        /** Guards the one-time strict-removal migration notice; see {@code consumeStrictMigrationNotice()}. */
        public boolean migratedStrictNotice = false;
        // Plugin routing preferences, independent of the OpenSpec CLI configuration.
        public int aiSettingsVersion = 0;
        public java.util.Map<String, String> aiProviderModels = new java.util.LinkedHashMap<>();
        public String aiBackend = "";
        public String codexExecutable = "codex";
        public String codexModel = "";
        public String codexReasoningEffort = "";
        public int codexTimeoutSeconds = 180;
        public boolean codexApiBillingAcknowledged = false;
        public int aiContextMaxBytes = 262144;
        public String aiProvider = "NONE";
        public String aiModel = "";
        public String preferredDeliveryMethod = "";
        public String preferredTool = "";
        public boolean setupCompleted = false;
        public boolean firstProposalCompleted = false;
        public int cliTimeoutSeconds = 30;
        public String defaultSchema = "";
    }
}
