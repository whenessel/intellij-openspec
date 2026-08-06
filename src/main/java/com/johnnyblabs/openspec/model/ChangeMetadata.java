package com.johnnyblabs.openspec.model;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A change's {@code .openspec.yaml} metadata.
 *
 * <p>Historically parsed with a strict typed SnakeYAML {@code Constructor}, which threw on any key
 * the plugin did not model — so a CLI-valid file carrying upstream {@code goal}/{@code affected_areas}/
 * {@code initiative} (since 1.4) or {@code skip_specs} (1.7) raised a spurious parse-error warning and
 * dropped the metadata. This type is now populated via {@link #fromMap(Map)} from a lenient untyped
 * load (see {@link ChangeMetadataParser}), mirroring {@link OpenSpecConfig#fromMap(Map)} and upstream's
 * strip contract: unknown/newer keys are ignored, never errored.
 *
 * <p>{@code schema}/{@code status}/{@code created} are retained (the status reader is still consumed
 * elsewhere). The newer fields are <strong>read-only</strong> — surfaced for display/labeling only;
 * no validation gate keys off them.
 */
public class ChangeMetadata {
    private String schema;
    private String status;
    private String created;
    private String goal;
    private List<String> affectedAreas = Collections.emptyList();
    private Map<String, String> initiative = Collections.emptyMap();
    private Boolean skipSpecs;

    public ChangeMetadata() {
    }

    /**
     * Cherry-pick known keys from an untyped YAML map, ignoring everything else (upstream strip contract).
     * Never throws on unrecognized keys.
     */
    public static ChangeMetadata fromMap(Map<String, Object> map) {
        ChangeMetadata meta = new ChangeMetadata();
        if (map == null) {
            return meta;
        }
        meta.schema = asString(map.get("schema"));
        meta.status = asString(map.get("status"));
        meta.created = asDateString(map.get("created"));
        meta.goal = asString(map.get("goal"));
        meta.affectedAreas = asStringList(map.get("affected_areas"));
        meta.initiative = asStringMap(map.get("initiative"));
        meta.skipSpecs = asBoolean(map.get("skip_specs"));
        return meta;
    }

    private static String asString(Object v) {
        return v instanceof String ? (String) v : null;
    }

    /**
     * {@code created} is a {@code YYYY-MM-DD} scalar; the untyped SnakeYAML resolver turns a bare
     * date scalar into a {@link Date} (UTC midnight), so accept String OR Date and normalize a Date to
     * {@code yyyy-MM-dd} <strong>in UTC</strong> — local-timezone formatting would day-shift it.
     */
    private static String asDateString(Object v) {
        if (v instanceof String) {
            return (String) v;
        }
        if (v instanceof Date) {
            return ((Date) v).toInstant().atZone(ZoneOffset.UTC).toLocalDate().toString();
        }
        return null;
    }

    private static List<String> asStringList(Object v) {
        if (!(v instanceof List)) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (Object item : (List<?>) v) {
            if (item instanceof String) {
                out.add((String) item);
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static Map<String, String> asStringMap(Object v) {
        if (!(v instanceof Map)) {
            return Collections.emptyMap();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
            if (e.getKey() instanceof String && e.getValue() instanceof String) {
                out.put((String) e.getKey(), (String) e.getValue());
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static Boolean asBoolean(Object v) {
        return v instanceof Boolean ? (Boolean) v : null;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreated() {
        return created;
    }

    public void setCreated(String created) {
        this.created = created;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public List<String> getAffectedAreas() {
        return affectedAreas;
    }

    public void setAffectedAreas(List<String> affectedAreas) {
        this.affectedAreas = affectedAreas == null ? Collections.emptyList() : affectedAreas;
    }

    public Map<String, String> getInitiative() {
        return initiative;
    }

    public void setInitiative(Map<String, String> initiative) {
        this.initiative = initiative == null ? Collections.emptyMap() : initiative;
    }

    /** {@code null} = the change did not declare {@code skip_specs}. */
    public Boolean getSkipSpecs() {
        return skipSpecs;
    }

    public boolean isSkipSpecs() {
        return Boolean.TRUE.equals(skipSpecs);
    }

    public void setSkipSpecs(Boolean skipSpecs) {
        this.skipSpecs = skipSpecs;
    }
}
