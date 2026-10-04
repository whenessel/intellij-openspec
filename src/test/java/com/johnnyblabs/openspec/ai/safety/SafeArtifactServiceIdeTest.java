package com.johnnyblabs.openspec.ai.safety;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.model.ArtifactInstruction;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** SDK tests cover writer/document integration; filesystem/parser tests cover adversarial data. */
public class SafeArtifactServiceIdeTest extends BasePlatformTestCase {
    private ArtifactInstruction instruction(Path root) {
        return new ArtifactInstruction("example", "proposal", root.toString(), "proposal.md", "Write proposal", "", List.of(), List.of());
    }
    public void testAcceptAppliesReviewedContentToVfsAndDocument() throws Exception {
        var file = myFixture.addFileToProject("openspec/changes/example/proposal.md", "original").getVirtualFile();
        var service = new SafeArtifactService(getProject(), ignored -> true);
        var batch = service.prepare(instruction(Path.of(file.getParent().getPath())), "reviewed");
        assertEquals(List.of(Path.of(file.getPath())), service.apply(batch));
        assertEquals("reviewed", FileDocumentManager.getInstance().getDocument(file).getText());
        assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(FileDocumentManager.getInstance().getDocument(file)));
    }
    public void testRejectedPreviewWritesNothing() throws Exception {
        var file = myFixture.addFileToProject("openspec/changes/example/proposal.md", "original").getVirtualFile();
        var service = new SafeArtifactService(getProject(), ignored -> false);
        var batch = service.prepare(instruction(Path.of(file.getParent().getPath())), "reviewed");
        try { service.apply(batch); fail("Preview rejection must cancel"); }
        catch (ProcessCanceledException expected) { }
        assertEquals("original", FileDocumentManager.getInstance().getDocument(file).getText());
    }
    public void testCancellationDuringReviewWritesNothing() throws Exception {
        var file = myFixture.addFileToProject("openspec/changes/example/proposal.md", "original").getVirtualFile();
        var canceled = new java.util.concurrent.atomic.AtomicBoolean();
        var service = new SafeArtifactService(getProject(), ignored -> { canceled.set(true); return true; });
        var batch = service.prepare(instruction(Path.of(file.getParent().getPath())), "reviewed");
        try { service.apply(batch, canceled::get); fail("Canceled preview must not apply"); }
        catch (ProcessCanceledException expected) { }
        assertEquals("original", FileDocumentManager.getInstance().getDocument(file).getText());
    }
    public void testCancellationAfterFirstMutationRetainsRecoveryCopies() throws Exception {
        var firstFile = myFixture.addFileToProject("openspec/changes/example/specs/a/spec.md", "first original").getVirtualFile();
        var secondFile = myFixture.addFileToProject("openspec/changes/example/specs/b/spec.md", "second original").getVirtualFile();
        Path root = Path.of(firstFile.getParent().getParent().getParent().getPath());
        var instruction = new ArtifactInstruction("example", "specs", root.toString(), "specs/**/*.md", "Write specs", "", List.of(), List.of());
        var service = new SafeArtifactService(getProject(), ignored -> true);
        var batch = service.prepare(instruction, """
                {"schemaVersion":1,"artifactId":"specs","files":[
                  {"relativePath":"specs/a/spec.md","operation":"replace","content":"first reviewed"},
                  {"relativePath":"specs/b/spec.md","operation":"replace","content":"second reviewed"}
                ]}
                """);
        var realManager = FileDocumentManager.getInstance();
        var secondDocument = realManager.getDocument(secondFile);
        var interruptedDocument = org.mockito.Mockito.spy(secondDocument);
        org.mockito.Mockito.doThrow(new ProcessCanceledException()).when(interruptedDocument).setText("second reviewed");
        var manager = org.mockito.Mockito.mock(FileDocumentManager.class);
        org.mockito.Mockito.when(manager.getCachedDocument(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> realManager.getCachedDocument(call.getArgument(0)));
        org.mockito.Mockito.when(manager.isDocumentUnsaved(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> realManager.isDocumentUnsaved(call.getArgument(0)));
        org.mockito.Mockito.when(manager.getDocument(org.mockito.ArgumentMatchers.any())).thenAnswer(call ->
                secondFile.equals(call.getArgument(0)) ? interruptedDocument : realManager.getDocument(call.getArgument(0)));
        org.mockito.Mockito.doAnswer(call -> { realManager.saveDocument(call.getArgument(0)); return null; }).when(manager).saveDocument(org.mockito.ArgumentMatchers.any());
        Path recovery = null;
        try (var mocked = org.mockito.Mockito.mockStatic(FileDocumentManager.class)) {
            mocked.when(FileDocumentManager::getInstance).thenReturn(manager);
            try { service.apply(batch); fail("Interrupted mutation must report partial application"); }
            catch (IOException expected) {
                assertTrue(expected.getMessage().contains("2 destinations may have changed (1 completed)"));
                recovery = Path.of(expected.getMessage().substring(expected.getMessage().indexOf("Recovery copies: ") + "Recovery copies: ".length()));
                assertEquals("first original", java.nio.file.Files.readString(recovery.resolve("specs/a/spec.md")));
                assertEquals("second original", java.nio.file.Files.readString(recovery.resolve("specs/b/spec.md")));
                assertTrue(java.nio.file.Files.readString(recovery.resolve("recovery.txt")).contains("Attempted destinations"));
            }
        } finally {
            if (recovery != null) try (var paths = java.nio.file.Files.walk(recovery)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.deleteIfExists(path);
            }
        }
        assertEquals("first reviewed", realManager.getDocument(firstFile).getText());
        assertEquals("second original", realManager.getDocument(secondFile).getText());
    }
    public void testUnsavedEditorIsRejectedBeforeAnyOverwrite() throws Exception {
        var file = myFixture.addFileToProject("openspec/changes/example/proposal.md", "original").getVirtualFile();
        var document = FileDocumentManager.getInstance().getDocument(file);
        var service = new SafeArtifactService(getProject(), ignored -> true);
        var batch = service.prepare(instruction(Path.of(file.getParent().getPath())), "reviewed");
        WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText("unsaved"));
        try { service.apply(batch); fail("Unsaved conflict must reject"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("editor edits")); }
        assertEquals("unsaved", document.getText());
    }
    public void testGenerationSnapshotRejectsInterveningSavedEdit() throws Exception {
        var file = myFixture.addFileToProject("openspec/changes/example/proposal.md", "original").getVirtualFile();
        var service = new SafeArtifactService(getProject(), ignored -> true);
        var snapshot = service.begin(instruction(Path.of(file.getParent().getPath())));
        var document = FileDocumentManager.getInstance().getDocument(file);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            document.setText("intervening");
            FileDocumentManager.getInstance().saveDocument(document);
        });
        try { service.prepare(snapshot, "reviewed"); fail("Generation base conflict must reject"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("during generation")); }
        assertEquals("intervening", document.getText());
    }
}
