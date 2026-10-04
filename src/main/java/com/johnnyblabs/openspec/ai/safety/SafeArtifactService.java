package com.johnnyblabs.openspec.ai.safety;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.model.ArtifactInstruction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.BooleanSupplier;

/** Shared generation-only writer. No delete, workspace source patch, or autonomous Apply operation. */
public final class SafeArtifactService {
    private final Project project;
    private final Predicate<ReviewedArtifactBatch> reviewer;
    public SafeArtifactService(Project project) {
        this(project, batch -> new ArtifactPreviewDialog(project, batch).showAndGet());
    }
    // Package-private injection keeps pure filesystem tests independent from UI acceptance.
    SafeArtifactService(Project project, Predicate<ReviewedArtifactBatch> reviewer) {
        this.project = project;
        this.reviewer = reviewer;
    }

    public ArtifactRequestSnapshot begin(ArtifactInstruction instruction) throws IOException {
        Path root = root(instruction);
        ArtifactResultValidator.checkRoot(root);
        String pattern = instruction.outputPath();
        if (pattern == null) throw new IOException("Missing output scope");
        // Validate scope before walking any directory.
        if (!pattern.equals("specs/**/*.md")) ArtifactResultValidator.checkRelativePath(pattern);
        Map<String, String> hashes = new HashMap<>();
        Map<String, String> contents = new HashMap<>();
        if (!ArtifactResultValidator.isGlob(pattern)) {
            Path target = ArtifactResultValidator.checkedTarget(root, pattern);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) captureBase(pattern, target, hashes, contents);
        } else {
            Path specs = root.resolve("specs");
            if (Files.isSymbolicLink(specs)) throw new IOException("Symlink context roots are not permitted");
            if (Files.exists(specs)) {
                try (var paths = Files.walk(specs, 12)) {
                    int visited = 0;
                    for (Path path : (Iterable<Path>) paths::iterator) {
                        ProgressManager.checkCanceled();
                        if (++visited > 512) throw new IOException("Artifact scope is too large; reduce the selected scope");
                        if (Files.isSymbolicLink(path)) throw new IOException("Symlinks in artifact scope are not supported");
                        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                        String relative = root.relativize(path).toString().replace('\\', '/');
                        if (ArtifactResultValidator.matches(pattern, relative)) {
                            ArtifactResultValidator.checkedTarget(root, relative);
                            if (hashes.size() >= ArtifactResultValidator.MAX_FILES) throw new IOException("Too many existing artifact files");
                            captureBase(relative, path, hashes, contents);
                        }
                    }
                }
            }
        }
        ensureSaved(hashes.keySet().stream().map(root::resolve).toList());
        return new ArtifactRequestSnapshot(root, instruction.artifactId(), pattern, hashes, contents);
    }

    public ReviewedArtifactBatch prepare(ArtifactInstruction instruction, String response) throws IOException {
        return prepare(begin(instruction), response);
    }
    public ReviewedArtifactBatch prepare(ArtifactRequestSnapshot snapshot, String response) throws IOException {
        ProgressManager.checkCanceled();
        ReviewedArtifactBatch batch = ArtifactResultValidator.validate(snapshot.root(), snapshot.artifactId(), snapshot.outputPattern(), response);
        for (var edit : batch.files()) {
            if (!java.util.Objects.equals(snapshot.baseHashes().get(edit.relativePath()), edit.baseHash())) {
                throw new IOException("Artifact changed during generation: " + edit.relativePath());
            }
        }
        return batch;
    }

    public List<Path> apply(ReviewedArtifactBatch batch) throws IOException {
        return apply(batch, () -> false);
    }
    public List<Path> apply(ReviewedArtifactBatch batch, BooleanSupplier canceled) throws IOException {
        checkCanceled(canceled);
        preflight(batch);
        AtomicReference<Boolean> accepted = new AtomicReference<>(false);
        onEdt(() -> { checkCanceled(canceled); accepted.set(reviewer.test(batch)); });
        if (!accepted.get()) throw new ProcessCanceledException();
        checkCanceled(canceled);
        preflight(batch);
        // Backups are staged before mutation; recovery is explicit rather than an atomicity claim.
        Path recovery = Files.createTempDirectory("openspec-artifact-recovery-");
        for (var edit : batch.files()) if (edit.original() != null) {
            Path backup = recovery.resolve(edit.relativePath());
            Files.createDirectories(backup.getParent());
            Files.writeString(backup, edit.original(), StandardCharsets.UTF_8);
        }
        List<Path> written = new ArrayList<>();
        List<Path> attempted = new ArrayList<>();
        AtomicReference<IOException> failure = new AtomicReference<>();
        Runnable write = () -> {
            // Check cancellation before entering the bounded commit, then finish/report consistently.
            checkCanceled(canceled);
            try {
                preflight(batch); // Last bounded check after review, immediately before commit.
                for (var edit : batch.files()) {
                    Path target = ArtifactResultValidator.checkedTarget(batch.root(), edit.relativePath());
                    // An API may mutate before it throws (including document listeners/save).
                    // Track attempts before any directory/file/document mutation, not just successes.
                    attempted.add(target);
                    if (ApplicationManager.getApplication() == null) {
                        Files.createDirectories(target.getParent());
                        Files.writeString(target, edit.content(), StandardCharsets.UTF_8);
                    } else {
                        VirtualFile parent = VfsUtil.createDirectoryIfMissing(target.getParent().toString());
                        if (parent == null) throw new IOException("Unable to create artifact parent");
                        VirtualFile file = parent.findChild(target.getFileName().toString());
                        if (file == null) file = parent.createChildData(this, target.getFileName().toString());
                        Document document = FileDocumentManager.getInstance().getDocument(file);
                        if (document != null) {
                            document.setText(edit.content());
                            FileDocumentManager.getInstance().saveDocument(document);
                        } else file.setBinaryContent(edit.content().getBytes(StandardCharsets.UTF_8));
                    }
                    written.add(target);
                }
            } catch (ProcessCanceledException ex) {
                if (attempted.isEmpty()) throw ex;
                failure.set(new IOException("Artifact application interrupted during mutation; review the workspace and recovery copies", ex));
            } catch (IOException ex) { failure.set(ex); }
            catch (RuntimeException ex) { failure.set(new IOException("IDE artifact mutation failed", ex)); }
        };
        try {
            if (ApplicationManager.getApplication() == null) write.run();
            else onEdt(() -> {
                if (project.isDisposed()) { failure.set(new IOException("Project closed before artifact application")); return; }
                WriteCommandAction.runWriteCommandAction(project, "Apply Reviewed OpenSpec Artifacts", "openspec.generated.artifacts", write);
            });
        } catch (ProcessCanceledException ex) {
            if (attempted.isEmpty()) {
                deleteRecovery(recovery);
                throw ex;
            }
            failure.set(new IOException("Artifact write command interrupted after mutation started; review recovery copies", ex));
        } catch (RuntimeException ex) {
            failure.set(new IOException("IDE write command failed", ex));
        }
        if (failure.get() != null) {
            Files.writeString(recovery.resolve("recovery.txt"), "Batch may be partially applied. Original replacement files are backed up here.\nApplied destinations:\n"
                    + String.join("\n", written.stream().map(Path::toString).toList())
                    + "\nAttempted destinations (may include a partially changed file):\n"
                    + String.join("\n", attempted.stream().map(Path::toString).toList()), StandardCharsets.UTF_8);
            throw new IOException("Artifact batch failed; " + attempted.size() + " destinations may have changed (" + written.size()
                    + " completed). Recovery copies: " + recovery, failure.get());
        }
        deleteRecovery(recovery);
        return List.copyOf(written);
    }

    private void preflight(ReviewedArtifactBatch batch) throws IOException {
        // Revalidate a public immutable batch as strictly as a new provider result. A caller
        // cannot use a manually constructed record to bypass content/path/count checks.
        var envelope = new com.google.gson.JsonObject();
        envelope.addProperty("schemaVersion", 1);
        envelope.addProperty("artifactId", batch.artifactId());
        var files = new com.google.gson.JsonArray();
        for (var edit : batch.files()) {
            var file = new com.google.gson.JsonObject();
            file.addProperty("relativePath", edit.relativePath());
            file.addProperty("operation", edit.operation());
            if (edit.patch() != null) file.add("patch", ArtifactPatchCodec.encode(edit.patch()));
            else file.addProperty("content", edit.content());
            if (edit.baseHash() != null) file.addProperty("baseHash", edit.baseHash());
            files.add(file);
        }
        envelope.add("files", files);
        ReviewedArtifactBatch validated = ArtifactResultValidator.validate(batch.root(), batch.artifactId(), batch.outputPattern(), envelope.toString());
        if (!validated.equals(batch)) throw new IOException("Reviewed artifact bases or contents are invalid");
        if (batch.files().isEmpty() || batch.files().size() > ArtifactResultValidator.MAX_FILES) throw new IOException("Invalid reviewed artifact batch");
        List<Path> paths = new ArrayList<>();
        for (var edit : batch.files()) {
            Path target = ArtifactResultValidator.checkedTarget(batch.root(), edit.relativePath());
            paths.add(target);
            Path writableParent = target;
            while (!Files.exists(writableParent, LinkOption.NOFOLLOW_LINKS)) writableParent = writableParent.getParent();
            if (!Files.isWritable(writableParent)) throw new IOException("Artifact destination is read-only: " + edit.relativePath());
            if (!ArtifactResultValidator.matches(batch.outputPattern(), edit.relativePath())) throw new IOException("Reviewed path changed scope");
            String currentHash = Files.exists(target, LinkOption.NOFOLLOW_LINKS) ? readHash(target) : null;
            if (!java.util.Objects.equals(currentHash, edit.baseHash())) throw new IOException("Artifact changed after review: " + edit.relativePath());
        }
        ensureSaved(paths);
    }
    private void ensureSaved(List<Path> paths) throws IOException {
        if (ApplicationManager.getApplication() == null) return;
        AtomicReference<IOException> conflict = new AtomicReference<>();
        onEdt(() -> {
            var manager = FileDocumentManager.getInstance();
            for (Path path : paths) {
                VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path.toString());
                if (file == null) continue;
                Document document = manager.getCachedDocument(file);
                if (!file.isWritable() || (document != null && !document.isWritable())) {
                    conflict.set(new IOException("Artifact editor or destination is read-only: " + path.getFileName()));
                    break;
                }
                if (document != null && manager.isDocumentUnsaved(document)) {
                    conflict.set(new IOException("Save or discard existing editor edits before applying: " + path.getFileName()));
                    break;
                }
            }
        });
        if (conflict.get() != null) throw conflict.get();
    }
    private static void captureBase(String relative, Path path, Map<String, String> hashes, Map<String, String> contents) throws IOException {
        if (Files.size(path) > ArtifactResultValidator.MAX_FILE_BYTES) throw new IOException("Existing artifact exceeds review budget");
        String content = Files.readString(path, StandardCharsets.UTF_8);
        ArtifactTextPolicy.canonicalContent(content); // Block unsupported disk separators before inference.
        long total = ArtifactResultValidator.utf8(content).length;
        for (String existing : contents.values()) total += ArtifactResultValidator.utf8(existing).length;
        if (total > ArtifactResultValidator.MAX_TOTAL_BYTES) throw new IOException("Existing artifact batch exceeds context snapshot budget");
        contents.put(relative, content);
        hashes.put(relative, ArtifactResultValidator.hash(content));
    }
    private static String readHash(Path path) throws IOException {
        if (Files.size(path) > ArtifactResultValidator.MAX_FILE_BYTES) throw new IOException("Existing artifact exceeds review budget");
        return ArtifactResultValidator.hash(Files.readString(path, StandardCharsets.UTF_8));
    }
    private static Path root(ArtifactInstruction instruction) throws IOException {
        if (instruction.changeDir() == null) throw new IOException("Missing artifact planning root");
        return Path.of(instruction.changeDir()).toAbsolutePath().normalize();
    }
    private static void deleteRecovery(Path recovery) throws IOException {
        try (var paths = Files.walk(recovery)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
    private static void checkCanceled(BooleanSupplier canceled) {
        ProgressManager.checkCanceled();
        if (canceled.getAsBoolean()) throw new ProcessCanceledException();
    }
    private static void onEdt(Runnable runnable) {
        var application = ApplicationManager.getApplication();
        if (application == null || application.isDispatchThread()) runnable.run();
        else application.invokeAndWait(runnable);
    }
}
