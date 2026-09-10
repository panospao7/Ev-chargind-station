package com.evplatform.libraries.eventenvelope;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CloudEvents 1.0 structured envelope (ARC-020 §2; canonical schema:
 * contracts/schemas/common/cloud-event.json).
 *
 * Validation logic mirrors the canonical schema; {@code EnvelopeParityTest}
 * guards against drift between this class and the executable schema.
 */
public record CloudEventEnvelope(
        String type,
        String source,
        String id,
        String time,
        String subject,
        String datacontenttype,
        Map<String, Object> data) {

    public static final String SPEC_VERSION = "1.0";

    public CloudEventEnvelope {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(id, "id");
    }

    /** @return the envelope as an attribute map ready for JSON serialization. */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("specversion", SPEC_VERSION);
        m.put("type", type);
        m.put("source", source);
        m.put("id", id);
        if (time != null) m.put("time", time);
        if (subject != null) m.put("subject", subject);
        if (datacontenttype != null) m.put("datacontenttype", datacontenttype);
        if (data != null) m.put("data", data);
        return m;
    }

    /** Validates an attribute map; returns all violations (empty = valid). */
    public static List<String> validate(Map<String, Object> attributes) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        if (attributes != null) {
            m.putAll(attributes);
        }
        List<String> violations = new java.util.ArrayList<>();

        for (String required : List.of("specversion", "type", "source", "id")) {
            Object v = m.get(required);
            if (!(v instanceof String s) || s.isBlank()) {
                violations.add("missing or blank required attribute: " + required);
            }
        }
        Object sv = m.get("specversion");
        if (sv != null && !SPEC_VERSION.equals(sv)) {
            violations.add("specversion must be \"" + SPEC_VERSION + "\"");
        }
        Object time = m.get("time");
        if (time != null) {
            try {
                OffsetDateTime.parse(String.valueOf(time));
            } catch (Exception e) {
                violations.add("time must be RFC 3339 date-time");
            }
        }
        Object source = m.get("source");
        if (source instanceof String s && (s.isBlank() || s.contains(" "))) {
            violations.add("source must be a non-blank uri-reference");
        }
        return violations;
    }
}
