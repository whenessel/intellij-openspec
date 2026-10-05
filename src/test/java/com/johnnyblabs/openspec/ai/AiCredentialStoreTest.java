package com.johnnyblabs.openspec.ai;

import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The has-key cache is the whole point of the EDT-safety fix: {@code isConfigured()} (reached from
 * ~8 UI paths) must answer "is a key stored" without a blocking {@code PasswordSafe.get()} on the EDT.
 * These tests prove the cache short-circuits the keystore — the load-bearing assertion is
 * {@code verify(never()).get()} on the cached path, which fails against the pre-cache code where every
 * {@code hasApiKey*} call reached {@code PasswordSafe.get()}.
 */
class AiCredentialStoreTest {

    @BeforeEach
    @AfterEach
    void resetCache() {
        AiCredentialStore.resetHasKeyCacheForTests();
    }

    @Test
    void openRouterCredentialsAreSeparateFromOpenAiAndRemovalDoesNotAffectOtherProvider() {
        PasswordSafe safe = mock(PasswordSafe.class);
        try (MockedStatic<PasswordSafe> ps = mockStatic(PasswordSafe.class)) {
            ps.when(PasswordSafe::getInstance).thenReturn(safe);
            AiCredentialStore.storeApiKey(AiProvider.OPENAI, "synthetic-openai");
            AiCredentialStore.storeApiKey(AiProvider.OPENROUTER, "synthetic-router");
            AiCredentialStore.removeApiKey(AiProvider.OPENROUTER);
            var attributes = org.mockito.ArgumentCaptor.forClass(com.intellij.credentialStore.CredentialAttributes.class);
            verify(safe, times(3)).set(attributes.capture(), any());
            var stored = attributes.getAllValues();
            org.junit.jupiter.api.Assertions.assertNotEquals(stored.get(0).getServiceName(), stored.get(1).getServiceName());
            org.junit.jupiter.api.Assertions.assertEquals(stored.get(1).getServiceName(), stored.get(2).getServiceName());
            assertTrue(AiCredentialStore.hasApiKeyCached(AiProvider.OPENAI));
            assertFalse(AiCredentialStore.hasApiKeyCached(AiProvider.OPENROUTER));
        }
    }

    @Test
    void storeApiKey_marksProviderHasKey_withoutAKeystoreRead() {
        PasswordSafe safe = mock(PasswordSafe.class);
        try (MockedStatic<PasswordSafe> ps = mockStatic(PasswordSafe.class)) {
            ps.when(PasswordSafe::getInstance).thenReturn(safe);

            AiCredentialStore.storeApiKey(AiProvider.CLAUDE, "sk-123");

            assertTrue(AiCredentialStore.hasApiKeyCached(AiProvider.CLAUDE));
            verify(safe).set(any(), any());       // the store wrote once
            verify(safe, never()).get(any());     // has-key answered from cache, never the keystore
        }
    }

    @Test
    void removeApiKey_marksProviderNoKey_withoutAKeystoreRead() {
        PasswordSafe safe = mock(PasswordSafe.class);
        try (MockedStatic<PasswordSafe> ps = mockStatic(PasswordSafe.class)) {
            ps.when(PasswordSafe::getInstance).thenReturn(safe);

            AiCredentialStore.storeApiKey(AiProvider.CLAUDE, "sk-123");
            AiCredentialStore.removeApiKey(AiProvider.CLAUDE);

            assertFalse(AiCredentialStore.hasApiKeyCached(AiProvider.CLAUDE));
            verify(safe, never()).get(any());
        }
    }

    @Test
    void hasApiKeyCached_warmsOnceThenNeverReadsTheKeystoreAgain() {
        PasswordSafe safe = mock(PasswordSafe.class);
        when(safe.get(any())).thenReturn(new Credentials("OPENAI", "sk-123"));
        try (MockedStatic<PasswordSafe> ps = mockStatic(PasswordSafe.class)) {
            ps.when(PasswordSafe::getInstance).thenReturn(safe);

            // First call warms the cache with exactly one keystore read.
            assertTrue(AiCredentialStore.hasApiKeyCached(AiProvider.OPENAI));
            verify(safe, times(1)).get(any());

            // Subsequent calls are cached — no further keystore reads. Fails against pre-cache code.
            clearInvocations(safe);
            assertTrue(AiCredentialStore.hasApiKeyCached(AiProvider.OPENAI));
            assertTrue(AiCredentialStore.hasApiKeyCached(AiProvider.OPENAI));
            verify(safe, never()).get(any());
        }
    }
}
