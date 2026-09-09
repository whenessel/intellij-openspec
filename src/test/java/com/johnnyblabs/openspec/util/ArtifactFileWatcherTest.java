package com.johnnyblabs.openspec.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The real defect this fix addresses is a leaked subscription: the watcher is disposed directly
 * (not through the Disposer), so the {@code connect(this)} parent never fires and the app-wide
 * {@code VFS_CHANGES} subscription accumulates one-per-generation. The load-bearing assertion is
 * {@code verify(connection).disconnect()} — NOT a fire-event-after-dispose test, which passes against
 * the un-fixed code because the {@code disposed} flag already neuters the callback.
 */
class ArtifactFileWatcherTest {

    @Test
    void dispose_disconnectsTheVfsSubscription() {
        Application app = mock(Application.class);
        MessageBus bus = mock(MessageBus.class);
        MessageBusConnection connection = mock(MessageBusConnection.class);
        when(app.getMessageBus()).thenReturn(bus);
        // connect is overloaded (Disposable | CoroutineScope); production passes the watcher (a
        // Disposable), so pin the matcher to that overload.
        when(bus.connect(any(Disposable.class))).thenReturn(connection);

        try (MockedStatic<ApplicationManager> am = mockStatic(ApplicationManager.class)) {
            am.when(ApplicationManager::getApplication).thenReturn(app);

            ArtifactFileWatcher watcher =
                    new ArtifactFileWatcher("/dir", "out.md", () -> { }, () -> { });
            watcher.start();
            watcher.dispose();

            verify(connection).disconnect();
        }
    }
}
