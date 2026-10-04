package com.johnnyblabs.openspec.ai.safety;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Immutable, ordered, reviewed payload provenance. Only explicit selections are read. */
public record ContextManifest(String prompt, List<Entry> entries, Budget budget, int utf8Bytes,
                              int conservativeInputTokens, boolean modelBoundsKnown) {
    public ContextManifest { entries = List.copyOf(entries); }
    public enum Origin { INSTRUCTION, TEMPLATE, TOPIC, SELECTED_CHANGE, ADDITIONAL_CHANGE, SOURCE }
    public record Entry(Origin origin, String label, boolean essential, boolean included,
                        String reason, int bytes, int redactions, String contentHash, String scopeId) { }
    /** Unknown tokenizers use one UTF-8 byte per token as a conservative admission estimate.
     * This is not an exact tokenizer measurement; server-side/history overhead is reserved separately. */
    public record Budget(int maxFiles, int maxFileBytes, int maxBytes, int maxInputTokens,
                         Integer modelContextTokens, int outputReserveTokens, int safetyTokens) {
        public Budget {
            if (maxFiles < 1 || maxFiles > 64 || maxFileBytes < 1 || maxFileBytes > 32768
                    || maxBytes < 1 || maxBytes > 262144 || maxInputTokens < 1 || maxInputTokens > 65536
                    || modelContextTokens != null && modelContextTokens < 1
                    || outputReserveTokens < 0 || safetyTokens < 0)
                throw new IllegalArgumentException("Invalid context budget");
        }
        public static Budget defaults() { return new Budget(64, 32768, 49152, 12000, null, 4096, 1024); }
        public int effectiveInputTokens() {
            return modelContextTokens == null ? maxInputTokens : (int) Math.max(0L,
                    Math.min(maxInputTokens, (long) modelContextTokens - outputReserveTokens - safetyTokens));
        }
        public int effectiveBytes() { return Math.min(maxBytes, effectiveInputTokens()); }
        public String disclosure() {
            return "UTF-8 bytes ≤ " + effectiveBytes() + "; files ≤ " + maxFiles + "; per file ≤ " + maxFileBytes
                    + "; conservative input tokens ≤ " + effectiveInputTokens()
                    + ". Tokenizer unknown: one UTF-8 byte per token admission estimate; not an exact count. "
                    + (modelContextTokens == null ? "Model context limit unknown; configured conservative cap applies."
                    : "Model context " + modelContextTokens + ", output reserve " + outputReserveTokens + ", safety reserve " + safetyTokens + ".");
        }
    }
    public record Selection(Path root, String relativePath, Origin origin, boolean essential) {
        public Selection {
            if (root == null || relativePath == null || origin == null) throw new IllegalArgumentException("Missing context selection");
            root = root.toAbsolutePath().normalize();
            if (origin == Origin.INSTRUCTION || origin == Origin.TEMPLATE || origin == Origin.TOPIC) throw new IllegalArgumentException("File origin required");
        }
    }
    public static ContextManifest fromPrompt(String prompt, List<Selection> selections, Budget budget) throws IOException {
        Builder builder = new Builder(budget).inline("Required instructions and context", Origin.INSTRUCTION, prompt, true);
        for (Selection selection : selections) builder.file(selection);
        return builder.build();
    }
    public static final class Builder {
        private final Budget budget;
        private final List<Entry> entries = new ArrayList<>();
        private final List<String> sections = new ArrayList<>();
        private int files;
        public Builder(Budget budget) { this.budget = java.util.Objects.requireNonNull(budget); }
        public Builder inline(String label, Origin origin, String text, boolean essential) throws IOException {
            return add(label, origin, text == null ? "" : text, essential, false, "");
        }
        public Builder omission(String label, Origin origin, String reason) throws IOException {
            return omission(label, origin, reason, "");
        }
        private Builder omission(String label, Origin origin, String reason, String scopeId) throws IOException {
            addEntry(new Entry(origin, safeLabel(label), false, false, safeLabel(reason), 0, 0, "", scopeId));
            requireFits();
            return this;
        }
        /** An already captured file is still counted against the same file and byte budgets. */
        public Builder capturedFile(String label, Origin origin, String content, boolean essential) throws IOException {
            return capturedFile(label, origin, content, essential, "");
        }
        public Builder capturedFile(String label, Origin origin, String content, boolean essential, String scopeId) throws IOException {
            if (!scopeId.isEmpty() && !scopeId.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid context scope identity");
            if (files >= budget.maxFiles()) {
                if (essential) throw new IOException("Required context exceeds file-count budget; reduce scope");
                return omission(label, origin, "File-count budget", scopeId);
            }
            if (content.getBytes(StandardCharsets.UTF_8).length > budget.maxFileBytes()) {
                if (essential) throw new IOException("Required context exceeds per-file byte budget; reduce scope");
                return omission(label, origin, "Per-file byte budget", scopeId);
            }
            add(label, origin, content, essential, true, scopeId);
            if (entries.getLast().included()) files++;
            return this;
        }
        public Builder file(Selection selection) throws IOException {
            String label = selection.relativePath().startsWith("/") || selection.relativePath().contains(":")
                    || selection.relativePath().contains("\\") ? "Unsafe context selection" : safeLabel(selection.relativePath());
            String content;
            try {
                if (excluded(selection.relativePath())) throw new IOException("Credential, private, VCS or generated file excluded");
                ArtifactResultValidator.checkRelativePath(selection.relativePath());
                if (selection.relativePath().length() > 512 || selection.relativePath().contains("\n") || selection.relativePath().contains("\r"))
                    throw new IOException("Unsafe context name");
                ArtifactResultValidator.checkRoot(selection.root());
                Path path = ArtifactResultValidator.checkedTarget(selection.root(), selection.relativePath());
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing or non-regular file omitted");
                if (ignored(selection.root(), path)) throw new IOException("Ignored file excluded (conservative ignore policy)");
                if (files >= budget.maxFiles()) throw new IOException("File-count budget");
                if (Files.size(path) > budget.maxFileBytes()) throw new IOException("Per-file byte budget");
                byte[] bytes;
                try (var input = Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                    bytes = input.readNBytes(budget.maxFileBytes() + 1);
                }
                ArtifactResultValidator.checkedTarget(selection.root(), selection.relativePath());
                if (bytes.length > budget.maxFileBytes()) throw new IOException("Per-file byte budget");
                content = decode(bytes);
            } catch (IOException | SecurityException ex) {
                // No filesystem exception text: it can contain an absolute path or private content.
                if (selection.essential()) throw new IOException("Required context unavailable or excluded: " + label + "; reduce scope or correct the selection.");
                return omission(label, selection.origin(), omissionReason(ex), hash(selection.root().toString()));
            }
            return capturedFile(label, selection.origin(), content, selection.essential(), hash(selection.root().toString()));
        }
        private Builder add(String label, Origin origin, String text, boolean essential, boolean file, String scopeId) throws IOException {
            String safeLabel = safeLabel(label);
            if (text.length() > budget.maxBytes() || text.getBytes(StandardCharsets.UTF_8).length > budget.maxBytes()) {
                if (essential) throw new IOException("Essential context exceeds byte budget; reduce selected scope.");
                return omission(safeLabel, origin, "Effective byte/token budget", scopeId);
            }
            ContextPayloadPolicy.Redaction redaction = ContextPayloadPolicy.redact(text);
            String content = redaction.text();
            if (file && content.getBytes(StandardCharsets.UTF_8).length > budget.maxFileBytes()) {
                if (essential) throw new IOException("Required context exceeds per-file budget: " + safeLabel);
                return omission(safeLabel, origin, "Per-file byte budget", scopeId);
            }
            Entry entry = new Entry(origin, safeLabel, essential, true, "", content.getBytes(StandardCharsets.UTF_8).length,
                    redaction.count(), hash(content), scopeId);
            addEntry(entry);
            sections.add("\n\n### " + safeLabel + "\n" + content);
            if (!fits()) {
                sections.removeLast(); entries.removeLast();
                if (essential) throw new IOException("Essential context exceeds effective byte/token budget; reduce selected scope. No required instructions were truncated.");
                return omission(safeLabel, origin, "Effective byte/token budget", scopeId);
            }
            return this;
        }
        private void addEntry(Entry entry) throws IOException {
            if (entries.size() >= 256) throw new IOException("Too many context selections; reduce selected scope");
            entries.add(entry);
        }
        private String render() {
            StringBuilder prompt = new StringBuilder();
            sections.forEach(prompt::append);
            prompt.append("\n\n## Reviewed context manifest\n").append(budget.disclosure()).append('\n');
            for (Entry entry : entries) {
                prompt.append("- ").append(entry.origin()).append(": ").append(entry.label()).append(" — ");
                if (!entry.included()) prompt.append("OMITTED: ").append(entry.reason());
                else if (entry.origin() == Origin.TOPIC) prompt.append("Reviewed user input; included in total payload budget");
                else if (entry.origin() == Origin.INSTRUCTION || entry.origin() == Origin.TEMPLATE)
                    prompt.append("Reviewed text; redactions ").append(entry.redactions()).append("; included in total payload budget");
                else prompt.append(entry.bytes()).append(" bytes; redactions ").append(entry.redactions())
                        .append("; SHA-256 ").append(entry.contentHash());
                if (!entry.scopeId().isEmpty()) prompt.append("; scope SHA-256 ").append(entry.scopeId());
                prompt.append('\n');
            }
            return prompt.toString();
        }
        private boolean fits() { return render().getBytes(StandardCharsets.UTF_8).length <= budget.effectiveBytes(); }
        private void requireFits() throws IOException {
            if (!fits()) throw new IOException("Context and visible omission manifest exceed effective byte/token budget; reduce selected scope.");
        }
        public ContextManifest build() throws IOException {
            requireFits(); String prompt = render(); int bytes = prompt.getBytes(StandardCharsets.UTF_8).length;
            return new ContextManifest(prompt, entries, budget, bytes, bytes, budget.modelContextTokens() != null);
        }
    }
    private static String safeLabel(String label) {
        String bounded = label == null ? "Context" : label.length() > 512 ? "Unsafe overlong selection name" : label;
        return ContextPayloadPolicy.redact(bounded).text().replace('\n', ' ').replace('\r', ' ');
    }
    private static String omissionReason(Exception ex) {
        String reason = ex.getMessage();
        return List.of("Credential, private, VCS or generated file excluded", "Missing or non-regular file omitted",
                "Ignored file excluded (conservative ignore policy)", "File-count budget", "Per-file byte budget").contains(reason)
                ? reason : "Unsafe, binary, symlinked or unreadable context omitted";
    }
    private static boolean excluded(String relative) {
        for (String segment : relative.toLowerCase(Locale.ROOT).split("/")) {
            if (segment.startsWith(".") || List.of("node_modules", "build", "target", "dist", "out", "vendor").contains(segment)
                    || segment.contains("credential") || segment.contains("secret") || segment.contains("password")
                    || segment.contains("private-key") || segment.equals("auth.json") || segment.endsWith(".pem")
                    || segment.endsWith(".key") || segment.endsWith(".p12") || segment.endsWith(".pfx")) return true;
        }
        return false;
    }
    private static String decode(byte[] bytes) throws IOException {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            for (int i = 0; i < text.length(); i++) {
                char value = text.charAt(i);
                if (value < 32 && value != '\n' && value != '\r' && value != '\t' || value == 127)
                    throw new IOException("Binary context omitted");
            }
            return text;
        } catch (CharacterCodingException ex) { throw new IOException("Binary context omitted"); }
    }
    /** Conservative subset: negations never restore an exclusion; unsupported syntax excludes candidates.
     * Deliberately avoids claiming full gitignore semantics or traversing the workspace. */
    private static boolean ignored(Path root, Path path) throws IOException {
        Path ignoreRoot = root;
        // A selected change may live below the project root; inherit repository exclusions.
        for (Path ancestor = root; ancestor != null; ancestor = ancestor.getParent()) {
            if (Files.exists(ancestor.resolve(".git"), LinkOption.NOFOLLOW_LINKS)) { ignoreRoot = ancestor; break; }
            if (root.getNameCount() - ancestor.getNameCount() >= 32) break;
        }
        for (Path directory = ignoreRoot; directory != null && path.startsWith(directory); ) {
            Path ignore = directory.resolve(".gitignore");
            if (Files.exists(ignore, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(ignore, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(ignore) || Files.size(ignore) > 32768) return true;
                byte[] bytes;
                try (var input = Files.newInputStream(ignore, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes(32769); }
                if (bytes.length > 32768) return true;
                String candidate = directory.relativize(path).toString().replace('\\', '/');
                for (String raw : decode(bytes).split("\\R")) {
                    String rule = raw.strip();
                    if (rule.isEmpty() || rule.startsWith("#") || rule.startsWith("!")) continue;
                    if (rule.contains("\\") || rule.contains("[") || rule.contains("]")) return true;
                    boolean directoryRule = rule.endsWith("/");
                    if (rule.startsWith("/")) rule = rule.substring(1);
                    if (directoryRule) rule = rule.substring(0, rule.length() - 1);
                    String regex = globRegex(rule);
                    if (rule.contains("/")) {
                        if (candidate.matches(regex) || candidate.matches(regex + "/.*")) return true;
                    } else for (String segment : candidate.split("/")) if (segment.matches(regex)) return true;
                }
            }
            Path remainder = directory.relativize(path);
            if (remainder.getNameCount() <= 1) break;
            directory = directory.resolve(remainder.getName(0));
        }
        return false;
    }
    private static String globRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char value = glob.charAt(i);
            if (value == '*' && i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                if (i + 2 < glob.length() && glob.charAt(i + 2) == '/') { regex.append("(?:.*/)?"); i += 2; }
                else { regex.append(".*"); i++; }
            }
            else if (value == '*') regex.append("[^/]*");
            else if (value == '?') regex.append("[^/]");
            else regex.append(java.util.regex.Pattern.quote(String.valueOf(value)));
        }
        return regex.toString();
    }
    public static String hash(String content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
