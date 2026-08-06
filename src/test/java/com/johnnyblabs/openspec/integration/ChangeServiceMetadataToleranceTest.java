package com.johnnyblabs.openspec.integration;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.mockito.MockedStatic;

import java.io.InputStream;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

/**
 * End-to-end reproduction of the change-metadata tolerance bug through the real {@link ChangeService}.
 * Writes a captured 1.7.0 {@code .openspec.yaml} into a change dir and asserts both symptoms are gone:
 * a CLI-valid file carrying newer keys loads to non-null metadata and fires NO parse-error balloon; a
 * genuinely malformed file still degrades to null metadata and fires exactly one warning (the positive
 * control proving the never()-assertion above can actually fail).
 */
public class ChangeServiceMetadataToleranceTest extends OpenSpecIntegrationTestBase {

    private static byte[] fixture(String name) {
        String path = "/fixtures/cli/1.7.0/change-metadata/" + name;
        try (InputStream is = ChangeServiceMetadataToleranceTest.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + path);
            }
            return is.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void writeChange(String name, byte[] metaBytes) throws Exception {
        VirtualFile changesDir = getChangesDir();
        assertNotNull("changes dir", changesDir);
        WriteAction.runAndWait(() -> {
            VirtualFile dir = changesDir.createChildDirectory(this, name);
            VirtualFile meta = dir.createChildData(this, ".openspec.yaml");
            meta.setBinaryContent(metaBytes);
        });
    }

    private static Change byName(List<Change> changes, String name) {
        return changes.stream().filter(c -> name.equals(c.getName())).findFirst().orElse(null);
    }

    public void testValidGoalFileLoadsWithoutBalloon() throws Exception {
        writeChange("goal-change", fixture("new-change-goal.openspec.yaml"));
        ChangeService changeService = getProject().getService(ChangeService.class);

        try (MockedStatic<OpenSpecNotifier> notif = mockStatic(OpenSpecNotifier.class)) {
            List<Change> changes = changeService.getActiveChanges();

            Change goal = byName(changes, "goal-change");
            assertNotNull("goal-change discovered", goal);
            assertNotNull("metadata parsed (not dropped)", goal.getMetadata());
            assertEquals("spec-driven", goal.getMetadata().getSchema());
            assertEquals("Honor upstream change metadata so a CLI-valid file never red-flags",
                    goal.getMetadata().getGoal());

            // The regression: no ".openspec.yaml parse error" balloon for a CLI-valid file.
            notif.verify(() -> OpenSpecNotifier.notify(
                    any(Project.class), any(), any(), contains(".openspec.yaml parse error"),
                    any(NotificationType.class)), never());
        }
    }

    public void testMalformedFileStillWarns() throws Exception {
        writeChange("broken-change", fixture("malformed.openspec.yaml"));
        ChangeService changeService = getProject().getService(ChangeService.class);

        try (MockedStatic<OpenSpecNotifier> notif = mockStatic(OpenSpecNotifier.class)) {
            List<Change> changes = changeService.getActiveChanges();

            Change broken = byName(changes, "broken-change");
            assertNotNull("broken-change still discovered", broken);
            assertNull("genuinely malformed YAML yields null metadata", broken.getMetadata());

            // Positive control: a real syntax error DOES still warn (so the never() assertion can fail).
            notif.verify(() -> OpenSpecNotifier.notify(
                    any(Project.class), any(), any(), contains(".openspec.yaml parse error"),
                    any(NotificationType.class)));
        }
    }
}
