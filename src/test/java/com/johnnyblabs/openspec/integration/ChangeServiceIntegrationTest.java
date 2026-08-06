package com.johnnyblabs.openspec.integration;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.services.ChangeService;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Integration tests for ChangeService with a real IntelliJ project.
 * Verifies change discovery, metadata parsing, and delta spec detection.
 */
public class ChangeServiceIntegrationTest extends OpenSpecIntegrationTestBase {

    public void testFindsActiveChanges() {
        ChangeService changeService = getProject().getService(ChangeService.class);
        List<Change> changes = changeService.getActiveChanges();

        assertNotNull("Changes list should not be null", changes);
        assertEquals("Should find one active change", 1, changes.size());
        assertEquals("test-change", changes.get(0).getName());
    }

    public void testParsesChangeMetadata() {
        ChangeService changeService = getProject().getService(ChangeService.class);
        List<Change> changes = changeService.getActiveChanges();
        Change change = changes.get(0);

        assertNotNull("Metadata should be parsed", change.getMetadata());
        assertEquals("spec-driven", change.getMetadata().getSchema());
    }

    public void testFindsArtifactFiles() {
        ChangeService changeService = getProject().getService(ChangeService.class);
        List<Change> changes = changeService.getActiveChanges();
        Change change = changes.get(0);

        assertTrue("Should find proposal.md as artifact",
                change.getArtifactFiles().contains("proposal.md"));
    }

    public void testFindsDeltaSpecs() {
        ChangeService changeService = getProject().getService(ChangeService.class);
        List<Change> changes = changeService.getActiveChanges();
        Change change = changes.get(0);

        List<String> deltaSpecs = changeService.getDeltaSpecNames(change);
        assertNotNull(deltaSpecs);
        assertEquals("Should find one delta spec domain", 1, deltaSpecs.size());
        assertEquals("actions", deltaSpecs.get(0));
    }

    public void testArchivedChangesEmptyInitially() {
        ChangeService changeService = getProject().getService(ChangeService.class);
        List<Change> archived = changeService.getArchivedChanges();
        assertNotNull(archived);
        assertTrue("No archived changes in fixture", archived.isEmpty());
    }

    public void testArchivesChangeWithNullMetadata() throws Exception {
        // A change with NO .openspec.yaml → metadata is null. Archiving must NOT throw: the retired
        // setStatus deref NPE'd on null metadata AFTER the dir move committed → a half-succeeded archive.
        VirtualFile changesDir = getChangesDir();
        WriteAction.runAndWait(() -> {
            VirtualFile dir = changesDir.createChildDirectory(this, "no-meta");
            dir.createChildData(this, "proposal.md")
                    .setBinaryContent("## Why\n\nx\n".getBytes(StandardCharsets.UTF_8));
        });
        ChangeService changeService = getProject().getService(ChangeService.class);
        Change noMeta = byName(changeService.getActiveChanges(), "no-meta");
        assertNotNull("no-meta change discovered", noMeta);
        assertNull("precondition: metadata is null (no .openspec.yaml)", noMeta.getMetadata());

        changeService.archiveChange(noMeta); // must not NPE (red on pre-retirement code)

        assertNotNull("listed as archived after archiveChange (proves no NPE aborted the move)",
                byName(changeService.getArchivedChanges(), "no-meta"));
        assertNull("no longer active", byName(changeService.getActiveChanges(), "no-meta"));
    }

    public void testArchivesChangeWithMalformedMetadata() throws Exception {
        // The other null-metadata path: a malformed .openspec.yaml (parser warns + leaves metadata null).
        VirtualFile changesDir = getChangesDir();
        WriteAction.runAndWait(() -> {
            VirtualFile dir = changesDir.createChildDirectory(this, "bad-meta");
            dir.createChildData(this, ".openspec.yaml")
                    .setBinaryContent("schema: x\n  bad: : indent\n:::".getBytes(StandardCharsets.UTF_8));
            dir.createChildData(this, "proposal.md")
                    .setBinaryContent("## Why\n\nx\n".getBytes(StandardCharsets.UTF_8));
        });
        ChangeService changeService = getProject().getService(ChangeService.class);
        Change bad = byName(changeService.getActiveChanges(), "bad-meta");
        assertNotNull(bad);
        assertNull("malformed .openspec.yaml leaves metadata null", bad.getMetadata());

        changeService.archiveChange(bad); // must not NPE

        assertTrue(byName(changeService.getArchivedChanges(), "bad-meta") != null);
    }

    private static Change byName(List<Change> changes, String name) {
        return changes.stream().filter(c -> name.equals(c.getName())).findFirst().orElse(null);
    }
}
