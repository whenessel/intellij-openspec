package com.johnnyblabs.openspec.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.MarkedYAMLException;

import java.util.Map;

/**
 * Pure, platform-free parser for a change's {@code .openspec.yaml}.
 *
 * <p>Replaces the strict typed SnakeYAML {@code Constructor(ChangeMetadata.class)} that threw a
 * {@code ConstructorException} (a {@link MarkedYAMLException}) on any unmodeled key — which surfaced a
 * spurious parse-error warning and dropped the metadata for a CLI-valid file carrying newer keys. This
 * seam does an <em>untyped</em> {@link Yaml#load(String)} and hands the resulting map to
 * {@link ChangeMetadata#fromMap(Map)}, so unknown/newer keys are ignored (upstream strip contract).
 *
 * <p>Being static and off-platform, both the ok and malformed branches are unit/contract-testable
 * without the IDE, like {@code CliOutputParser}.
 */
public final class ChangeMetadataParser {

    private ChangeMetadataParser() {
    }

    /** Outcome of a parse: non-null {@code metadata} unless the YAML is genuinely malformed. */
    public static final class ParseResult {
        private final ChangeMetadata metadata;
        private final boolean malformed;
        private final String problem;

        private ParseResult(ChangeMetadata metadata, boolean malformed, String problem) {
            this.metadata = metadata;
            this.malformed = malformed;
            this.problem = problem;
        }

        static ParseResult of(@NotNull ChangeMetadata metadata) {
            return new ParseResult(metadata, false, null);
        }

        static ParseResult malformed(@NotNull String problem) {
            return new ParseResult(null, true, problem);
        }

        /** The parsed metadata, or {@code null} iff {@link #isMalformed()}. */
        @Nullable
        public ChangeMetadata metadata() {
            return metadata;
        }

        public boolean isMalformed() {
            return malformed;
        }

        /** The YAML parse problem, set iff {@link #isMalformed()}. */
        @Nullable
        public String problem() {
            return problem;
        }
    }

    /**
     * Parse {@code .openspec.yaml} text leniently.
     *
     * <ul>
     *   <li>A genuinely malformed document (a {@link MarkedYAMLException} at load) yields a
     *       {@link ParseResult#isMalformed() malformed} result carrying the problem — the one case that
     *       still warrants a warning.</li>
     *   <li>A mapping is cherry-picked via {@link ChangeMetadata#fromMap(Map)} (unknown keys ignored).</li>
     *   <li>A {@code null} or non-mapping top-level document yields empty, non-null metadata with no
     *       warning (favor never-stricter-than-the-CLI).</li>
     *   <li>Any non-marked {@link RuntimeException} propagates, preserving the caller's log-only path.</li>
     * </ul>
     */
    @NotNull
    public static ParseResult parse(@Nullable String yaml) {
        Object loaded;
        try {
            loaded = new Yaml().load(yaml);
        } catch (MarkedYAMLException e) {
            String problem = e.getProblem() != null ? e.getProblem() : e.getMessage();
            return ParseResult.malformed(problem);
        }
        if (loaded instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) loaded;
            return ParseResult.of(ChangeMetadata.fromMap(map));
        }
        // null (empty file) or a scalar/list top-level: valid YAML but no metadata — not an error.
        return ParseResult.of(new ChangeMetadata());
    }
}
