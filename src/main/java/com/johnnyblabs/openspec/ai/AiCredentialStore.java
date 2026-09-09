package com.johnnyblabs.openspec.ai;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AiCredentialStore {

    private static final String SERVICE_PREFIX = "OpenSpec-AI-";

    /**
     * App-global cache of "does a key exist for this provider". {@link PasswordSafe} get/set are
     * blocking ({@code @RequiresBackgroundThread}); this lets EDT paths (e.g. {@code isConfigured()}
     * gating UI) answer the has-key question without a synchronous keystore read. Warmed lazily on the
     * first {@link #hasApiKeyCached} miss (that one read may run off-EDT) and kept fresh by
     * {@link #storeApiKey}/{@link #removeApiKey}. Keys are provider-scoped and app-global (matching the
     * PasswordSafe service name), so a static cache is the correct scope.
     */
    private static final Map<AiProvider, Boolean> HAS_KEY_CACHE = new ConcurrentHashMap<>();

    private AiCredentialStore() {
    }

    /**
     * Stores an API key for the given provider. Blocking — call off the EDT.
     */
    public static void storeApiKey(AiProvider provider, String apiKey) {
        markHasApiKey(provider, apiKey != null && !apiKey.isBlank());
        CredentialAttributes attributes = createAttributes(provider);
        PasswordSafe.getInstance().set(attributes, new Credentials(provider.name(), apiKey));
    }

    /**
     * Updates the has-key cache without touching {@link PasswordSafe}. Cheap and EDT-safe — call this
     * synchronously right before scheduling a blocking {@link #storeApiKey}/{@link #removeApiKey} off the
     * EDT, so a UI gate ({@link #hasApiKeyCached}) reflects the change immediately rather than after the
     * async keystore write lands.
     */
    public static void markHasApiKey(AiProvider provider, boolean present) {
        if (provider != null && provider != AiProvider.NONE) {
            HAS_KEY_CACHE.put(provider, present);
        }
    }

    /**
     * Retrieves the API key for the given provider. Blocking — call off the EDT.
     */
    @Nullable
    public static String getApiKey(AiProvider provider) {
        CredentialAttributes attributes = createAttributes(provider);
        Credentials credentials = PasswordSafe.getInstance().get(attributes);
        return credentials != null ? credentials.getPasswordAsString() : null;
    }

    /**
     * Removes the stored API key for the given provider. Blocking — call off the EDT.
     */
    public static void removeApiKey(AiProvider provider) {
        markHasApiKey(provider, false);
        CredentialAttributes attributes = createAttributes(provider);
        PasswordSafe.getInstance().set(attributes, null);
    }

    /**
     * Whether an API key is stored for the given provider. Blocking ({@code PasswordSafe.get}) —
     * call off the EDT. Prefer {@link #hasApiKeyCached} on UI paths.
     */
    public static boolean hasApiKey(AiProvider provider) {
        String key = getApiKey(provider);
        return key != null && !key.isBlank();
    }

    /**
     * EDT-safe has-key check backed by {@link #HAS_KEY_CACHE}. A cache hit is a pure map read that never
     * touches {@link PasswordSafe}. On a cold miss the behavior depends on the caller's thread:
     * <ul>
     *   <li><b>Off the EDT</b> — warms the cache with one blocking {@link #hasApiKey} read and returns
     *       the true answer. This is the intended warm path (e.g. project-open startup activity).</li>
     *   <li><b>On the EDT</b> — never blocks: schedules an off-EDT warm and returns {@code false}
     *       conservatively for now. The startup warm normally populates the cache before any EDT gate
     *       runs, so this fallback is a rare race; when it does happen a UI affordance simply appears on
     *       the next refresh rather than freezing the EDT on a keychain read.</li>
     * </ul>
     */
    public static boolean hasApiKeyCached(AiProvider provider) {
        if (provider == null || provider == AiProvider.NONE) {
            return false;
        }
        Boolean cached = HAS_KEY_CACHE.get(provider);
        if (cached != null) {
            return cached;
        }
        // Cache cold. PasswordSafe.get is blocking (@RequiresBackgroundThread) and MUST NOT run on the
        // EDT — that synchronous read is the freeze this whole cache exists to avoid. When there is no
        // Application (plain unit test / headless), there is no EDT to protect, so read directly.
        var app = ApplicationManager.getApplication();
        if (app != null && app.isDispatchThread()) {
            app.executeOnPooledThread(() -> hasApiKeyCached(provider));
            return false;
        }
        boolean present = hasApiKey(provider);
        HAS_KEY_CACHE.put(provider, present);
        return present;
    }

    @TestOnly
    static void resetHasKeyCacheForTests() {
        HAS_KEY_CACHE.clear();
    }

    private static CredentialAttributes createAttributes(AiProvider provider) {
        return new CredentialAttributes(
                CredentialAttributesKt.generateServiceName(SERVICE_PREFIX, provider.name()));
    }
}
