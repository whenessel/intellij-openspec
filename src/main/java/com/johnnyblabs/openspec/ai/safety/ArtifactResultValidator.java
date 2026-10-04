package com.johnnyblabs.openspec.ai.safety;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Pure, bounded validation. No model-provided path is used before lexical and scope checks. */
public final class ArtifactResultValidator {
    public static final int MAX_FILES = 64;
    public static final int MAX_FILE_BYTES = 32 * 1024;
    public static final int MAX_TOTAL_BYTES = 256 * 1024;
    private ArtifactResultValidator() { }

    public static ReviewedArtifactBatch validate(Path root, String artifactId, String outputPattern,
                                                  String response) throws IOException {
        if (root == null || artifactId == null || outputPattern == null || response == null) {
            throw new IOException("Missing artifact output scope");
        }
        root = root.toAbsolutePath().normalize();
        checkRoot(root);
        checkPattern(outputPattern);
        if (response.length() > MAX_TOTAL_BYTES * 2 || utf8(response).length > MAX_TOTAL_BYTES * 2) {
            throw new IOException("Artifact response exceeds the review budget");
        }
        List<ProposedFile> proposals = parse(artifactId, outputPattern, response);
        if (proposals.isEmpty() || proposals.size() > MAX_FILES) throw new IOException("Invalid artifact file count");
        Set<String> destinations = new HashSet<>();
        java.util.Map<String, String> components = new java.util.HashMap<>();
        List<ReviewedArtifactBatch.FileEdit> edits = new ArrayList<>();
        int bytes = 0;
        long originalBytes = 0;
        for (ProposedFile file : proposals) {
            checkRelativePath(file.path());
            if (!matches(outputPattern, file.path())) throw new IOException("Artifact destination is outside requested output scope: " + file.path());
            if (!destinations.add(file.path().toLowerCase(Locale.ROOT))) throw new IOException("Duplicate or case-colliding artifact destination");
            String prefix = "";
            for (String component : file.path().split("/")) {
                prefix = prefix.isEmpty() ? component : prefix + "/" + component;
                String existing = components.putIfAbsent(prefix.toLowerCase(Locale.ROOT), prefix);
                if (existing != null && !existing.equals(prefix)) throw new IOException("Case-colliding artifact parent paths");
            }
            if (!Set.of("create", "replace", "patch", "legacy").contains(file.operation())) {
                throw new IOException("Only create, replace and exact artifact patch operations are supported");
            }
            Path target = checkedTarget(root, file.path());
            boolean exists = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
            if (file.operation().equals("create") && exists) throw new IOException("Create destination already exists: " + file.path());
            if ((file.operation().equals("replace") || file.operation().equals("patch")) && !exists) throw new IOException("Replace/patch destination is missing: " + file.path());
            if (exists) {
                long size = Files.size(target);
                originalBytes += size;
                if (size > MAX_FILE_BYTES || originalBytes > MAX_TOTAL_BYTES) throw new IOException("Existing artifact exceeds the review budget");
            }
            String original = exists ? Files.readString(target, StandardCharsets.UTF_8) : null;
            if (original != null) ArtifactTextPolicy.canonicalContent(original); // Validate supported disk text without altering its hash/patch offsets.
            String hash = original == null ? null : hash(original);
            if (file.baseHash() != null && !file.baseHash().equals(hash)) throw new IOException("Artifact base hash conflicts: " + file.path());
            if (file.operation().equals("patch") && file.baseHash() == null) throw new IOException("Artifact patch requires its exact base hash");
            if (file.operation().equals("patch") && ContextPayloadPolicy.redact(original).count() > 0) {
                throw new IOException("A redacted artifact base cannot be patched; review a full replacement or reduce sensitive context");
            }
            String resolved = file.patch() == null ? file.content() : ArtifactPatchCodec.apply(original, file.patch());
            // Validate the provider's raw projection, then bind review/application to logical LF text.
            // Patch offsets/oldText and base hashes always refer to the exact unnormalized disk base.
            if (utf8(resolved).length > MAX_FILE_BYTES) throw new IOException("Artifact content exceeds the review budget");
            resolved = ArtifactTextPolicy.canonicalContent(resolved);
            byte[] content = utf8(resolved);
            bytes += content.length;
            if (content.length > MAX_FILE_BYTES || bytes > MAX_TOTAL_BYTES) throw new IOException("Artifact content exceeds the review budget");
            edits.add(new ReviewedArtifactBatch.FileEdit(file.path(), file.operation().equals("patch") ? "patch" : exists ? "replace" : "create",
                    resolved, original, hash, file.patch()));
        }
        return new ReviewedArtifactBatch(root, artifactId, outputPattern, edits);
    }

    private static List<ProposedFile> parse(String artifactId, String pattern, String response) throws IOException {
        String trimmed = response.strip();
        // A plain legacy artifact is permitted only for exactly one concrete output. A malformed
        // JSON-looking response is never silently reinterpreted as text.
        if (!trimmed.startsWith("{")) {
            if (isGlob(pattern)) throw new IOException("Multi-file output requires a schemaVersion 1 JSON artifact envelope");
            return List.of(new ProposedFile(pattern, "legacy", response, null, null));
        }
        try {
            JsonObject envelope = ArtifactEnvelopeJson.parse(trimmed);
            requireOnlyKeys(envelope, Set.of("schemaVersion", "artifactId", "files"));
            if (!envelope.has("schemaVersion") || !envelope.get("schemaVersion").isJsonPrimitive()
                    || !envelope.get("schemaVersion").getAsJsonPrimitive().isNumber()
                    || !envelope.get("schemaVersion").getAsString().equals("1")) throw new IOException("Unsupported artifact result schema");
            if (!artifactId.equals(string(envelope, "artifactId"))) throw new IOException("Artifact result ID mismatch");
            JsonArray files = envelope.getAsJsonArray("files");
            if (files == null || files.size() > MAX_FILES) throw new IOException("Invalid artifact files array");
            List<ProposedFile> result = new ArrayList<>();
            for (JsonElement element : files) {
                JsonObject file = element.getAsJsonObject();
                String operation = string(file, "operation");
                if (!Set.of("create", "replace", "patch").contains(operation)) throw new IOException("Unknown artifact result operation");
                String baseHash = file.has("baseHash") ? string(file, "baseHash") : null;
                if (baseHash != null && !baseHash.matches("[a-f0-9]{64}")) throw new IOException("Artifact base hash must be lowercase SHA-256");
                if (operation.equals("patch")) {
                    requireOnlyKeys(file, Set.of("relativePath", "operation", "baseHash", "patch"));
                    JsonElement patch = file.get("patch");
                    if (baseHash == null || patch == null || !patch.isJsonObject()) throw new IOException("Artifact patch requires baseHash and versioned patch object");
                    result.add(new ProposedFile(string(file, "relativePath"), operation, null, baseHash, ArtifactPatchCodec.parse(patch.getAsJsonObject())));
                } else {
                    requireOnlyKeys(file, Set.of("relativePath", "operation", "content", "baseHash"));
                    if (operation.equals("create") && baseHash != null) throw new IOException("Create operations cannot name an existing base");
                    result.add(new ProposedFile(string(file, "relativePath"), operation, string(file, "content"), baseHash, null));
                }
            }
            return result;
        } catch (RuntimeException ex) {
            throw new IOException("Malformed structured artifact result", ex);
        }
    }

    private static void requireOnlyKeys(JsonObject object, Set<String> allowed) throws IOException {
        if (!allowed.containsAll(object.keySet())) throw new IOException("Unknown structured artifact result field");
    }

    private static String string(JsonObject object, String key) throws IOException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IOException("Missing or invalid artifact field: " + key);
        return value.getAsString();
    }

    public static void checkRoot(Path root) throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) throw new IOException("Artifact planning root must be an existing real directory");
        Path current = root.toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isSymbolicLink(current)) throw new IOException("Symlink planning roots are not supported");
            current = current.getParent();
        }
    }

    public static Path checkedTarget(Path root, String relative) throws IOException {
        checkRelativePath(relative);
        checkRoot(root);
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root) || target.equals(root)) throw new IOException("Artifact path escapes its planning root");
        Path current = root;
        for (Path component : root.relativize(target)) {
            if (Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                try (var children = Files.list(current)) {
                    String name = component.toString();
                    if (children.anyMatch(p -> p.getFileName().toString().equalsIgnoreCase(name)
                            && !p.getFileName().toString().equals(name))) throw new IOException("Case-colliding existing destination");
                }
            }
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) throw new IOException("Symlink artifact destinations are not permitted");
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (current.equals(target) ? !Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS)
                        : !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Artifact target is not a regular file/directory");
            }
        }
        return target;
    }

    public static void checkRelativePath(String path) throws IOException {
        if (path == null || path.isBlank() || path.length() > 1024 || path.startsWith("/") || path.contains("\\")
                || path.contains(":") || path.contains("\u0000") || isGlob(path)) throw new IOException("Invalid concrete artifact path");
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.endsWith(".") || part.endsWith(" ")
                    || part.startsWith(".") || part.indexOf('<') >= 0 || part.indexOf('>') >= 0 || part.indexOf('"') >= 0
                    || part.indexOf('|') >= 0 || part.chars().anyMatch(c -> c < 32)
                    || part.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")) throw new IOException("Unsafe artifact path component");
        }
        if (Path.of(path).isAbsolute()) throw new IOException("Absolute artifact paths are not permitted");
    }

    private static void checkPattern(String pattern) throws IOException {
        if (pattern.equals("specs/**/*.md")) return;
        if (isGlob(pattern)) throw new IOException("Unsupported artifact output glob; supported multi-file scope is specs/**/*.md");
        checkRelativePath(pattern);
    }

    public static boolean matches(String pattern, String path) {
        // OpenSpec's recursive specs scope permits both specs/name.md and specs/domain/spec.md.
        return pattern.equals("specs/**/*.md") ? path.startsWith("specs/") && path.endsWith(".md") : pattern.equals(path);
    }
    public static boolean isGlob(String path) { return path != null && Pattern.compile("[*?\\[\\]{}]").matcher(path).find(); }
    public static String hash(String content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    static byte[] utf8(String content) throws IOException {
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= content.length() || !Character.isLowSurrogate(content.charAt(i))) throw new IOException("Invalid Unicode artifact content");
            } else if (Character.isLowSurrogate(c)) throw new IOException("Invalid Unicode artifact content");
        }
        return content.getBytes(StandardCharsets.UTF_8);
    }
    private record ProposedFile(String path, String operation, String content, String baseHash, ArtifactPatch patch) { }
}
